use log::info;
use std::collections::HashMap;

use crate::meshql_client::MeshqlClient;

/// Caches FK -> MeshQL projection ID mappings.
/// Lazy bootstrap: on first access per type, calls GraphQL getAll to populate.
pub struct ProjectionCache {
    container_inventory: HashMap<String, String>,
    hen_productivity: HashMap<String, String>,
    farm_output: HashMap<String, String>,

    container_inventory_bootstrapped: bool,
    hen_productivity_bootstrapped: bool,
    farm_output_bootstrapped: bool,
}

impl ProjectionCache {
    pub fn new() -> Self {
        Self {
            container_inventory: HashMap::new(),
            hen_productivity: HashMap::new(),
            farm_output: HashMap::new(),
            container_inventory_bootstrapped: false,
            hen_productivity_bootstrapped: false,
            farm_output_bootstrapped: false,
        }
    }

    pub fn get_container_inventory_id(&mut self, client: &MeshqlClient, container_id: &str) -> Option<String> {
        if !self.container_inventory_bootstrapped {
            self.bootstrap_container_inventory(client);
        }
        self.container_inventory.get(container_id).cloned()
    }

    pub fn register_container_inventory(&mut self, container_id: &str, meshql_id: &str) {
        self.container_inventory.insert(container_id.to_string(), meshql_id.to_string());
    }

    pub fn get_hen_productivity_id(&mut self, client: &MeshqlClient, hen_id: &str) -> Option<String> {
        if !self.hen_productivity_bootstrapped {
            self.bootstrap_hen_productivity(client);
        }
        self.hen_productivity.get(hen_id).cloned()
    }

    pub fn register_hen_productivity(&mut self, hen_id: &str, meshql_id: &str) {
        self.hen_productivity.insert(hen_id.to_string(), meshql_id.to_string());
    }

    pub fn get_farm_output_id(&mut self, client: &MeshqlClient, farm_id: &str) -> Option<String> {
        if !self.farm_output_bootstrapped {
            self.bootstrap_farm_output(client);
        }
        self.farm_output.get(farm_id).cloned()
    }

    pub fn register_farm_output(&mut self, farm_id: &str, meshql_id: &str) {
        self.farm_output.insert(farm_id.to_string(), meshql_id.to_string());
    }

    fn bootstrap_container_inventory(&mut self, client: &MeshqlClient) {
        self.container_inventory_bootstrapped = true;
        self.populate_cache(client, "/container_inventory/graph", "container_id", &mut |cache, fk, id| {
            cache.container_inventory.insert(fk, id);
        });
        info!("Bootstrapped container inventory cache: {} entries", self.container_inventory.len());
    }

    fn bootstrap_hen_productivity(&mut self, client: &MeshqlClient) {
        self.hen_productivity_bootstrapped = true;
        self.populate_cache(client, "/hen_productivity/graph", "hen_id", &mut |cache, fk, id| {
            cache.hen_productivity.insert(fk, id);
        });
        info!("Bootstrapped hen productivity cache: {} entries", self.hen_productivity.len());
    }

    fn bootstrap_farm_output(&mut self, client: &MeshqlClient) {
        self.farm_output_bootstrapped = true;
        self.populate_cache(client, "/farm_output/graph", "farm_id", &mut |cache, fk, id| {
            cache.farm_output.insert(fk, id);
        });
        info!("Bootstrapped farm output cache: {} entries", self.farm_output.len());
    }

    fn populate_cache(
        &mut self,
        client: &MeshqlClient,
        graph_path: &str,
        fk_field: &str,
        inserter: &mut dyn FnMut(&mut Self, String, String),
    ) {
        let fields = format!("id {}", fk_field);
        match client.get_all(graph_path, &fields) {
            Ok(items) => {
                // Collect into a temp vec to avoid borrow issues
                let entries: Vec<(String, String)> = items.iter().filter_map(|item| {
                    let meshql_id = item.get("id")?.as_str()?;
                    let fk_value = item.get(fk_field)?.as_str()?;
                    if fk_value == "null" { return None; }
                    Some((fk_value.to_string(), meshql_id.to_string()))
                }).collect();

                for (fk, id) in entries {
                    inserter(self, fk, id);
                }
            }
            Err(e) => {
                log::warn!("Failed to bootstrap cache from {}: {}", graph_path, e);
            }
        }
    }
}
