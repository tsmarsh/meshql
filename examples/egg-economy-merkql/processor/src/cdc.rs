use anyhow::{Context, Result};
use log::info;
use rusqlite::{Connection, OpenFlags};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::{Path, PathBuf};

/// A CDC record read from SQLite
pub struct CdcRecord {
    pub table: String,
    pub id: String,
    pub payload: String,
}

/// Tracks per-table _id watermarks for CDC polling
#[derive(Serialize, Deserialize, Default)]
struct Watermarks {
    tables: HashMap<String, i64>,
}

pub struct CdcPoller {
    conn: Connection,
    watermarks: Watermarks,
    watermark_path: PathBuf,
}

const CDC_TABLES: &[&str] = &[
    "lay_report",
    "storage_deposit",
    "storage_withdrawal",
    "container_transfer",
    "consumption_report",
];

impl CdcPoller {
    pub fn new(db_path: &str, watermark_path: &Path, replay: bool) -> Result<Self> {
        let conn = Connection::open_with_flags(
            db_path,
            OpenFlags::SQLITE_OPEN_READ_ONLY | OpenFlags::SQLITE_OPEN_NO_MUTEX,
        )
        .context("Failed to open events.db read-only")?;

        let watermarks = if replay {
            info!("Replay mode: resetting all watermarks to 0");
            Watermarks::default()
        } else {
            load_watermarks(watermark_path)
        };

        Ok(Self {
            conn,
            watermarks,
            watermark_path: watermark_path.to_path_buf(),
        })
    }

    /// Poll all CDC tables for new records since last watermark
    pub fn poll(&mut self) -> Result<Vec<CdcRecord>> {
        let mut records = Vec::new();

        for table in CDC_TABLES {
            let watermark = self.watermarks.tables.get(*table).copied().unwrap_or(0);

            // Check if table exists before querying
            let table_exists: bool = self.conn.query_row(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?1",
                [table],
                |row| row.get::<_, i64>(0),
            ).map(|count| count > 0)
            .unwrap_or(false);

            if !table_exists {
                continue;
            }

            let mut stmt = self.conn.prepare(&format!(
                "SELECT _id, id, payload FROM {} WHERE _id > ?1 AND deleted = 0 ORDER BY _id ASC",
                table
            ))?;

            let mut rows = stmt.query([watermark])?;
            let mut max_id = watermark;

            while let Some(row) = rows.next()? {
                let row_id: i64 = row.get(0)?;
                let id: String = row.get(1)?;
                let payload: String = row.get(2)?;

                records.push(CdcRecord {
                    table: table.to_string(),
                    id,
                    payload,
                });

                if row_id > max_id {
                    max_id = row_id;
                }
            }

            if max_id > watermark {
                self.watermarks.tables.insert(table.to_string(), max_id);
            }
        }

        Ok(records)
    }

    /// Persist watermarks to disk
    pub fn save_watermarks(&self) -> Result<()> {
        if let Some(parent) = self.watermark_path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let json = serde_json::to_string_pretty(&self.watermarks)?;
        std::fs::write(&self.watermark_path, json)?;
        Ok(())
    }
}

fn load_watermarks(path: &Path) -> Watermarks {
    match std::fs::read_to_string(path) {
        Ok(content) => serde_json::from_str(&content).unwrap_or_default(),
        Err(_) => Watermarks::default(),
    }
}
