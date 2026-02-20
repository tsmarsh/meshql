use anyhow::Result;
use log::{debug, info};
use serde_json::{json, Value};

use crate::cache::ProjectionCache;
use crate::meshql_client::MeshqlClient;

const GRAPH_PATH: &str = "/farm_output/graph";
const API_PATH: &str = "/farm_output/api";
const FIELDS: &str = "id farm_id farm_type eggs_today eggs_week eggs_month active_hens total_hens avg_per_hen_per_week";

pub fn on_lay_report(client: &MeshqlClient, cache: &mut ProjectionCache, event: &Value) -> Result<()> {
    let farm_id = match event.get("farm_id").and_then(|v| v.as_str()) {
        Some(id) => id,
        None => return Ok(()),
    };
    let eggs = event.get("eggs").and_then(|v| v.as_i64()).unwrap_or(0);

    let meshql_id = cache.get_farm_output_id(client, farm_id);

    if meshql_id.is_none() {
        let farm_type = resolve_farm_type(client, farm_id);

        let data = json!({
            "farm_id": farm_id,
            "farm_type": farm_type,
            "eggs_today": eggs,
            "eggs_week": eggs,
            "eggs_month": eggs,
            "active_hens": 1,
            "total_hens": 1,
            "avg_per_hen_per_week": eggs as f64
        });

        if let Ok(Some(new_id)) = client.create_projection(
            API_PATH, &data, GRAPH_PATH, "getByFarm", farm_id,
        ) {
            cache.register_farm_output(farm_id, &new_id);
            info!("Created farm output projection for farm {}", farm_id);
        }
        return Ok(());
    }

    let meshql_id = meshql_id.unwrap();
    let current = client.get_projection(GRAPH_PATH, &meshql_id, FIELDS)?;

    if let Some(current) = current {
        let eggs_today = get_i64(&current, "eggs_today") + eggs;
        let eggs_week = get_i64(&current, "eggs_week") + eggs;
        let eggs_month = get_i64(&current, "eggs_month") + eggs;
        let total_hens = get_i64(&current, "total_hens").max(1);
        let avg_per_hen = if total_hens > 0 { eggs_week as f64 / total_hens as f64 } else { 0.0 };

        let mut farm_type = current.get("farm_type")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_string();
        if farm_type.is_empty() {
            farm_type = resolve_farm_type(client, farm_id);
        }

        let updated = json!({
            "farm_id": farm_id,
            "farm_type": farm_type,
            "eggs_today": eggs_today,
            "eggs_week": eggs_week,
            "eggs_month": eggs_month,
            "active_hens": get_i64(&current, "active_hens"),
            "total_hens": total_hens,
            "avg_per_hen_per_week": avg_per_hen
        });

        client.update_projection(API_PATH, &meshql_id, &updated)?;
        debug!("Updated farm output for farm {}: +{} eggs, week_total={}", farm_id, eggs, eggs_week);
    }

    Ok(())
}

fn resolve_farm_type(client: &MeshqlClient, farm_id: &str) -> String {
    match client.get_projection("/farm/graph", farm_id, "id farm_type") {
        Ok(Some(farm)) => {
            farm.get("farm_type")
                .and_then(|v| v.as_str())
                .filter(|s| !s.is_empty())
                .unwrap_or("local_farm")
                .to_string()
        }
        _ => "local_farm".to_string(),
    }
}

fn get_i64(v: &Value, field: &str) -> i64 {
    v.get(field).and_then(|v| v.as_i64()).unwrap_or(0)
}
