use anyhow::Result;
use log::{debug, info};
use serde_json::{json, Value};

use crate::cache::ProjectionCache;
use crate::meshql_client::MeshqlClient;

const GRAPH_PATH: &str = "/container_inventory/graph";
const API_PATH: &str = "/container_inventory/api";
const FIELDS: &str = "id container_id current_eggs total_deposits total_withdrawals total_transfers_in total_transfers_out total_consumed utilization_pct";

pub fn on_storage_deposit(client: &MeshqlClient, cache: &mut ProjectionCache, event: &Value) -> Result<()> {
    let container_id = event.get("container_id").and_then(|v| v.as_str());
    let eggs = event.get("eggs").and_then(|v| v.as_i64()).unwrap_or(0);

    if let Some(container_id) = container_id {
        update_container(client, cache, container_id, eggs, 0, 0, 0, eggs, 0)?;
        debug!("StorageDeposit: +{} eggs to container {}", eggs, container_id);
    }
    Ok(())
}

pub fn on_storage_withdrawal(client: &MeshqlClient, cache: &mut ProjectionCache, event: &Value) -> Result<()> {
    let container_id = event.get("container_id").and_then(|v| v.as_str());
    let eggs = event.get("eggs").and_then(|v| v.as_i64()).unwrap_or(0);

    if let Some(container_id) = container_id {
        update_container(client, cache, container_id, -eggs, 0, eggs, 0, 0, 0)?;
        debug!("StorageWithdrawal: -{} eggs from container {}", eggs, container_id);
    }
    Ok(())
}

pub fn on_container_transfer_source(client: &MeshqlClient, cache: &mut ProjectionCache, container_id: &str, eggs: i64) -> Result<()> {
    update_container(client, cache, container_id, -eggs, 0, 0, 0, 0, eggs)?;
    debug!("ContainerTransfer: -{} eggs from source container {}", eggs, container_id);
    Ok(())
}

pub fn on_container_transfer_dest(client: &MeshqlClient, cache: &mut ProjectionCache, container_id: &str, eggs: i64) -> Result<()> {
    update_container(client, cache, container_id, eggs, 0, 0, eggs, 0, 0)?;
    debug!("ContainerTransfer: +{} eggs to dest container {}", eggs, container_id);
    Ok(())
}

pub fn on_consumption(client: &MeshqlClient, cache: &mut ProjectionCache, event: &Value) -> Result<()> {
    let container_id = event.get("container_id").and_then(|v| v.as_str());
    let eggs = event.get("eggs").and_then(|v| v.as_i64()).unwrap_or(0);

    if let Some(container_id) = container_id {
        update_container(client, cache, container_id, -eggs, 0, 0, 0, 0, 0)?;
        // total_consumed tracked separately
        update_container_consumed(client, cache, container_id, eggs)?;
        debug!("ConsumptionReport: -{} eggs from container {}", eggs, container_id);
    }
    Ok(())
}

fn update_container(
    client: &MeshqlClient,
    cache: &mut ProjectionCache,
    container_id: &str,
    eggs_delta: i64,
    _deposits_delta: i64,
    withdrawals_delta: i64,
    transfers_in_delta: i64,
    deposit_total: i64,
    transfers_out_delta: i64,
) -> Result<()> {
    let meshql_id = cache.get_container_inventory_id(client, container_id);

    if meshql_id.is_none() {
        let data = json!({
            "container_id": container_id,
            "current_eggs": std::cmp::max(0, eggs_delta),
            "total_deposits": deposit_total,
            "total_withdrawals": withdrawals_delta,
            "total_transfers_in": transfers_in_delta,
            "total_transfers_out": transfers_out_delta,
            "total_consumed": 0,
            "utilization_pct": 0.0
        });

        if let Ok(Some(new_id)) = client.create_projection(
            API_PATH, &data, GRAPH_PATH, "getByContainer", container_id,
        ) {
            cache.register_container_inventory(container_id, &new_id);
            info!("Created container inventory projection for container {}", container_id);
        }
        return Ok(());
    }

    let meshql_id = meshql_id.unwrap();
    let current = client.get_projection(GRAPH_PATH, &meshql_id, FIELDS)?;

    if let Some(current) = current {
        let updated = json!({
            "container_id": container_id,
            "current_eggs": std::cmp::max(0, get_i64(&current, "current_eggs") + eggs_delta),
            "total_deposits": get_i64(&current, "total_deposits") + deposit_total,
            "total_withdrawals": get_i64(&current, "total_withdrawals") + withdrawals_delta,
            "total_transfers_in": get_i64(&current, "total_transfers_in") + transfers_in_delta,
            "total_transfers_out": get_i64(&current, "total_transfers_out") + transfers_out_delta,
            "total_consumed": get_i64(&current, "total_consumed"),
            "utilization_pct": get_f64(&current, "utilization_pct")
        });

        client.update_projection(API_PATH, &meshql_id, &updated)?;
    }

    Ok(())
}

fn update_container_consumed(
    client: &MeshqlClient,
    cache: &mut ProjectionCache,
    container_id: &str,
    eggs: i64,
) -> Result<()> {
    let meshql_id = match cache.get_container_inventory_id(client, container_id) {
        Some(id) => id,
        None => return Ok(()),
    };

    let current = client.get_projection(GRAPH_PATH, &meshql_id, FIELDS)?;

    if let Some(current) = current {
        let updated = json!({
            "container_id": container_id,
            "current_eggs": get_i64(&current, "current_eggs"),
            "total_deposits": get_i64(&current, "total_deposits"),
            "total_withdrawals": get_i64(&current, "total_withdrawals"),
            "total_transfers_in": get_i64(&current, "total_transfers_in"),
            "total_transfers_out": get_i64(&current, "total_transfers_out"),
            "total_consumed": get_i64(&current, "total_consumed") + eggs,
            "utilization_pct": get_f64(&current, "utilization_pct")
        });

        client.update_projection(API_PATH, &meshql_id, &updated)?;
    }

    Ok(())
}

fn get_i64(v: &Value, field: &str) -> i64 {
    v.get(field).and_then(|v| v.as_i64()).unwrap_or(0)
}

fn get_f64(v: &Value, field: &str) -> f64 {
    v.get(field).and_then(|v| v.as_f64()).unwrap_or(0.0)
}
