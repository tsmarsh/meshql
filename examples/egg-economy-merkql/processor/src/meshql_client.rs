use anyhow::Result;
use log::{debug, error};
use serde_json::Value;

/// HTTP client for MeshQL REST and GraphQL APIs.
/// Mirrors the Java ProjectionUpdater pattern.
pub struct MeshqlClient {
    base_url: String,
    agent: ureq::Agent,
}

impl MeshqlClient {
    pub fn new(base_url: &str) -> Self {
        Self {
            base_url: base_url.trim_end_matches('/').to_string(),
            agent: ureq::Agent::new_with_defaults(),
        }
    }

    /// Check if MeshQL server is ready
    pub fn health_check(&self) -> Result<bool> {
        let url = format!("{}/health", self.base_url);
        let resp = self.agent.get(&url).call()?;
        Ok(resp.status().as_u16() >= 200 && resp.status().as_u16() < 300)
    }

    /// GET a projection by its MeshQL ID via GraphQL
    pub fn get_projection(&self, graph_path: &str, meshql_id: &str, fields: &str) -> Result<Option<Value>> {
        let query = format!("{{ getById(id: \"{}\") {{ {} }} }}", meshql_id, fields);
        let body = serde_json::json!({ "query": query });

        let url = format!("{}{}", self.base_url, graph_path);
        let mut resp = self.agent.post(&url)
            .content_type("application/json")
            .send(serde_json::to_string(&body)?.as_bytes())?;

        if resp.status().as_u16() >= 200 && resp.status().as_u16() < 300 {
            let text = resp.body_mut().read_to_string()?;
            let json: Value = serde_json::from_str(&text)?;
            let result = json.get("data")
                .and_then(|d| d.get("getById"))
                .cloned();
            if let Some(ref v) = result {
                if v.is_null() {
                    return Ok(None);
                }
            }
            Ok(result)
        } else {
            debug!("GET projection failed: {} {}", resp.status(), graph_path);
            Ok(None)
        }
    }

    /// POST a new projection via REST, then discover its ID via GraphQL
    pub fn create_projection(
        &self,
        api_path: &str,
        data: &Value,
        graph_path: &str,
        fk_query_name: &str,
        fk_value: &str,
    ) -> Result<Option<String>> {
        let url = format!("{}{}", self.base_url, api_path);
        let mut resp = self.agent.post(&url)
            .content_type("application/json")
            .send(serde_json::to_string(data)?.as_bytes())?;

        if resp.status().as_u16() >= 200 && resp.status().as_u16() < 300 {
            // Consume response body
            let _ = resp.body_mut().read_to_string()?;
            // Discover the ID via GraphQL
            self.discover_id(graph_path, fk_query_name, fk_value)
        } else {
            let status = resp.status().as_u16();
            let body = resp.body_mut().read_to_string().unwrap_or_default();
            error!("POST {} failed: {} - {}", api_path, status, body);
            Ok(None)
        }
    }

    /// Query GraphQL to find a projection's MeshQL ID by FK query
    pub fn discover_id(&self, graph_path: &str, query_name: &str, fk_value: &str) -> Result<Option<String>> {
        let query = format!("{{ {}(id: \"{}\") {{ id }} }}", query_name, fk_value);
        let body = serde_json::json!({ "query": query });

        let url = format!("{}{}", self.base_url, graph_path);
        let mut resp = self.agent.post(&url)
            .content_type("application/json")
            .send(serde_json::to_string(&body)?.as_bytes())?;

        if resp.status().as_u16() >= 200 && resp.status().as_u16() < 300 {
            let text = resp.body_mut().read_to_string()?;
            let json: Value = serde_json::from_str(&text)?;
            if let Some(results) = json.get("data").and_then(|d| d.get(query_name)) {
                if let Some(arr) = results.as_array() {
                    if let Some(last) = arr.last() {
                        return Ok(last.get("id").and_then(|v| v.as_str()).map(String::from));
                    }
                }
            }
        }
        Ok(None)
    }

    /// PUT an updated projection via REST
    pub fn update_projection(&self, api_path: &str, meshql_id: &str, data: &Value) -> Result<bool> {
        let url = format!("{}{}/{}", self.base_url, api_path, meshql_id);
        let resp = self.agent.put(&url)
            .content_type("application/json")
            .send(serde_json::to_string(data)?.as_bytes())?;

        Ok(resp.status().as_u16() >= 200 && resp.status().as_u16() < 300)
    }

    /// GET all entries for a projection type (for cache bootstrap)
    pub fn get_all(&self, graph_path: &str, fields: &str) -> Result<Vec<Value>> {
        let query = format!("{{ getAll {{ {} }} }}", fields);
        let body = serde_json::json!({ "query": query });

        let url = format!("{}{}", self.base_url, graph_path);
        let mut resp = self.agent.post(&url)
            .content_type("application/json")
            .send(serde_json::to_string(&body)?.as_bytes())?;

        if resp.status().as_u16() >= 200 && resp.status().as_u16() < 300 {
            let text = resp.body_mut().read_to_string()?;
            let json: Value = serde_json::from_str(&text)?;
            if let Some(arr) = json.get("data").and_then(|d| d.get("getAll")).and_then(|v| v.as_array()) {
                return Ok(arr.clone());
            }
        }
        Ok(Vec::new())
    }
}
