mod cdc;
mod cache;
mod meshql_client;
mod projections;

use anyhow::{Context, Result};
use clap::Parser;
use log::{error, info, warn};
use merkql::broker::{Broker, BrokerConfig};
use merkql::consumer::{ConsumerConfig, OffsetReset};
use merkql::record::ProducerRecord;
use std::path::PathBuf;
use std::time::Duration;

use crate::cdc::CdcPoller;
use crate::cache::ProjectionCache;
use crate::meshql_client::MeshqlClient;
use crate::projections::route_event;

#[derive(Parser)]
#[command(name = "egg-economy-processor")]
#[command(about = "CDC processor for egg-economy-merkql: polls SQLite events, publishes to merkql, updates projections")]
struct Cli {
    /// MeshQL server URL
    #[arg(long, default_value = "http://localhost:5088")]
    meshql_url: String,

    /// Path to events.db SQLite file
    #[arg(long, default_value = "data/events.db")]
    events_db: String,

    /// Path to merkql data directory
    #[arg(long, default_value = "data/merkql")]
    merkql_dir: String,

    /// Poll interval in milliseconds
    #[arg(long, default_value = "500")]
    poll_interval_ms: u64,

    /// Reset watermarks and replay from beginning
    #[arg(long)]
    replay: bool,
}

fn main() -> Result<()> {
    env_logger::init();
    let cli = Cli::parse();

    info!("Starting egg-economy processor");
    info!("  MeshQL URL: {}", cli.meshql_url);
    info!("  Events DB: {}", cli.events_db);
    info!("  merkql dir: {}", cli.merkql_dir);
    info!("  Poll interval: {}ms", cli.poll_interval_ms);
    info!("  Replay: {}", cli.replay);

    // Wait for MeshQL to be ready
    let client = MeshqlClient::new(&cli.meshql_url);
    wait_for_meshql(&client)?;

    // Open merkql broker
    let broker = Broker::open(BrokerConfig::new(&cli.merkql_dir))
        .context("Failed to open merkql broker")?;

    // Create CDC poller
    let watermark_path = PathBuf::from(&cli.merkql_dir).join("watermarks.json");
    let mut poller = CdcPoller::new(&cli.events_db, &watermark_path, cli.replay)
        .context("Failed to create CDC poller")?;

    // Create merkql producer
    let producer = Broker::producer(&broker);

    // Create merkql consumer
    let consumer_config = ConsumerConfig {
        group_id: "egg-economy-processor".into(),
        auto_commit: false,
        offset_reset: if cli.replay {
            OffsetReset::Earliest
        } else {
            OffsetReset::Earliest
        },
    };

    let topics = [
        "lay_report",
        "storage_deposit",
        "storage_withdrawal",
        "container_transfer",
        "consumption_report",
    ];

    let mut consumer = Broker::consumer(&broker, consumer_config);

    // Create projection cache
    let mut cache = ProjectionCache::new();

    let poll_interval = Duration::from_millis(cli.poll_interval_ms);

    info!("Entering main loop");

    loop {
        // Phase 1: CDC poll → merkql produce
        let cdc_records = poller.poll()?;
        if !cdc_records.is_empty() {
            info!("CDC polled {} records", cdc_records.len());
            for rec in &cdc_records {
                producer.send(&ProducerRecord::new(
                    &rec.table,
                    Some(rec.id.clone()),
                    &rec.payload,
                ))?;
            }
            poller.save_watermarks()?;
        }

        // Phase 2: merkql consume → process projections
        // Subscribe each iteration to pick up newly auto-created topics
        let topic_refs: Vec<&str> = topics.iter().map(|s| *s).collect();
        if let Err(e) = consumer.subscribe(&topic_refs) {
            // Topics may not exist yet if no events have been produced
            if cdc_records.is_empty() {
                std::thread::sleep(poll_interval);
                continue;
            }
            warn!("Failed to subscribe to topics: {}", e);
        }

        let records = consumer.poll(Duration::from_millis(100))?;
        if !records.is_empty() {
            info!("Processing {} merkql records", records.len());
            for record in &records {
                if let Err(e) = route_event(&client, &mut cache, record) {
                    error!("Error processing record from {}: {}", record.topic, e);
                }
            }
            consumer.commit_sync()?;
        }

        if cdc_records.is_empty() && records.is_empty() {
            std::thread::sleep(poll_interval);
        }
    }
}

fn wait_for_meshql(client: &MeshqlClient) -> Result<()> {
    let max_retries = 30;
    let mut delay = Duration::from_millis(500);

    for attempt in 1..=max_retries {
        match client.health_check() {
            Ok(true) => {
                info!("MeshQL is ready (attempt {})", attempt);
                return Ok(());
            }
            Ok(false) => {
                warn!("MeshQL not ready (attempt {}/{}), retrying in {:?}", attempt, max_retries, delay);
            }
            Err(e) => {
                warn!("MeshQL health check failed (attempt {}/{}): {}", attempt, max_retries, e);
            }
        }
        std::thread::sleep(delay);
        delay = std::cmp::min(delay * 2, Duration::from_secs(5));
    }

    anyhow::bail!("MeshQL did not become ready after {} attempts", max_retries);
}
