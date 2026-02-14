---
title: Examples
layout: default
nav_order: 5
has_children: true
---

# Examples

MeshQL ships with nine complete example applications:

| Example | Entities | Demonstrates |
|:--------|:---------|:------------|
| [**Farm**](farm) | Farm, Coop, Hen, LayReport | Multi-entity federation, internal/external resolvers, full CRUD |
| [**Events**](events) | RawEvent, ProcessedEvent | CDC pipeline with Kafka/Debezium, event-driven processing |
| [**SwiftShip Logistics**](logistics) | Warehouse, Shipment, Package, TrackingUpdate | Full-stack case study: 3 frontend apps, 8 federation resolvers, Docker + Kubernetes deployment |
| [**National Egg Economy**](egg-economy) | 13 entities (5 actors, 5 events, 3 projections) | Event-sourced CDC pipeline, MongoDB sharding, materialized projections, 3 frontend apps |
| [**Egg Economy SAP**](egg-economy-sap) | Same 13 entities | Anti-corruption layer over SAP-style database — transitional architecture for vendor replacement |
| [**Egg Economy Salesforce**](egg-economy-salesforce) | Same 13 entities | Anti-corruption layer over Salesforce-style database — platform migration without big-bang cutover |
| [**Springfield Electric**](legacy) | Customer, MeterReading, Bill, Payment | Anti-corruption layer over legacy PostgreSQL, internal resolvers, data transformation via CDC, 3 frontend apps |
| [**Global Power Plants**](power-plants) | Country, FuelType, PowerPlant, GenerationData, PlantFuelAssignment | Mesher-generated anti-corruption layer from real-world dataset (34,936 power plants), 5-entity federation, 3-phase CDC processing |
| [**The Enterprise**](enterprise) | Same 13 entities | Capstone: multi-source anti-corruption (SAP + Distribution PG), data lake (MinIO + DuckDB), bidirectional write-back, 13 services |

The egg-economy family demonstrates the same clean domain served from progressively more complex sources — native MeshQL, SAP alone, Salesforce alone, and finally The Enterprise combining two sources with a data lake and write-back. The frontends and API contracts are identical across all variants, proving that downstream applications remain unchanged regardless of the data source.
