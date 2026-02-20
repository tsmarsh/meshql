pub mod container_inventory;
pub mod hen_productivity;
pub mod farm_output;

use anyhow::Result;
use log::warn;
use merkql::record::Record;
use serde_json::Value;

use crate::cache::ProjectionCache;
use crate::meshql_client::MeshqlClient;

/// Route a merkql record to the appropriate projection updaters
pub fn route_event(client: &MeshqlClient, cache: &mut ProjectionCache, record: &Record) -> Result<()> {
    let event_data: Value = serde_json::from_str(&record.value)?;

    match record.topic.as_str() {
        "lay_report" => {
            hen_productivity::on_lay_report(client, cache, &event_data)?;
            farm_output::on_lay_report(client, cache, &event_data)?;
        }
        "storage_deposit" => {
            container_inventory::on_storage_deposit(client, cache, &event_data)?;
        }
        "storage_withdrawal" => {
            container_inventory::on_storage_withdrawal(client, cache, &event_data)?;
        }
        "container_transfer" => {
            let source_id = event_data.get("source_container_id").and_then(|v| v.as_str());
            let dest_id = event_data.get("dest_container_id").and_then(|v| v.as_str());
            let eggs = event_data.get("eggs").and_then(|v| v.as_i64()).unwrap_or(0);

            if let Some(source) = source_id {
                container_inventory::on_container_transfer_source(client, cache, source, eggs)?;
            }
            if let Some(dest) = dest_id {
                container_inventory::on_container_transfer_dest(client, cache, dest, eggs)?;
            }
        }
        "consumption_report" => {
            container_inventory::on_consumption(client, cache, &event_data)?;
        }
        _ => {
            warn!("Unknown topic: {}", record.topic);
        }
    }

    Ok(())
}
