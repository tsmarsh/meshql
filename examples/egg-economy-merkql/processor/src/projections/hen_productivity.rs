use anyhow::Result;
use log::{debug, info};
use serde_json::{json, Value};

use crate::cache::ProjectionCache;
use crate::meshql_client::MeshqlClient;

const GRAPH_PATH: &str = "/hen_productivity/graph";
const API_PATH: &str = "/hen_productivity/api";
const FIELDS: &str = "id hen_id farm_id eggs_today eggs_week eggs_month avg_per_week total_eggs quality_rate";

pub fn on_lay_report(client: &MeshqlClient, cache: &mut ProjectionCache, event: &Value) -> Result<()> {
    let hen_id = match event.get("hen_id").and_then(|v| v.as_str()) {
        Some(id) => id,
        None => return Ok(()),
    };
    let farm_id = event.get("farm_id").and_then(|v| v.as_str()).unwrap_or("");
    let eggs = event.get("eggs").and_then(|v| v.as_i64()).unwrap_or(0);
    let quality = event.get("quality").and_then(|v| v.as_str()).unwrap_or("grade_a");
    let is_grade_a = quality == "grade_a" || quality == "double_yolk";

    let meshql_id = cache.get_hen_productivity_id(client, hen_id);

    if meshql_id.is_none() {
        let data = json!({
            "hen_id": hen_id,
            "farm_id": farm_id,
            "eggs_today": eggs,
            "eggs_week": eggs,
            "eggs_month": eggs,
            "avg_per_week": eggs as f64,
            "total_eggs": eggs,
            "quality_rate": if is_grade_a { 1.0 } else { 0.0 }
        });

        if let Ok(Some(new_id)) = client.create_projection(
            API_PATH, &data, GRAPH_PATH, "getByHen", hen_id,
        ) {
            cache.register_hen_productivity(hen_id, &new_id);
            info!("Created hen productivity projection for hen {}", hen_id);
        }
        return Ok(());
    }

    let meshql_id = meshql_id.unwrap();
    let current = client.get_projection(GRAPH_PATH, &meshql_id, FIELDS)?;

    if let Some(current) = current {
        let previous_total = get_i64(&current, "total_eggs");
        let total_eggs = previous_total + eggs;
        let eggs_week = get_i64(&current, "eggs_week") + eggs;
        let eggs_month = get_i64(&current, "eggs_month") + eggs;
        let eggs_today = get_i64(&current, "eggs_today") + eggs;

        // Weighted quality rate
        let current_rate = get_f64(&current, "quality_rate");
        let new_rate = if previous_total > 0 {
            (current_rate * previous_total as f64 + if is_grade_a { eggs as f64 } else { 0.0 }) / total_eggs as f64
        } else {
            if is_grade_a { 1.0 } else { 0.0 }
        };

        let current_farm_id = current.get("farm_id").and_then(|v| v.as_str()).unwrap_or("");
        let resolved_farm_id = if !farm_id.is_empty() { farm_id } else { current_farm_id };

        let updated = json!({
            "hen_id": hen_id,
            "farm_id": resolved_farm_id,
            "eggs_today": eggs_today,
            "eggs_week": eggs_week,
            "eggs_month": eggs_month,
            "avg_per_week": eggs_week as f64,
            "total_eggs": total_eggs,
            "quality_rate": new_rate
        });

        client.update_projection(API_PATH, &meshql_id, &updated)?;
        debug!("Updated hen productivity for hen {}: +{} eggs, total={}", hen_id, eggs, total_eggs);
    }

    Ok(())
}

fn get_i64(v: &Value, field: &str) -> i64 {
    v.get(field).and_then(|v| v.as_i64()).unwrap_or(0)
}

fn get_f64(v: &Value, field: &str) -> f64 {
    v.get(field).and_then(|v| v.as_f64()).unwrap_or(0.0)
}
