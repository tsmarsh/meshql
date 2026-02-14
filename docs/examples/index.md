---
title: Examples
layout: default
nav_order: 5
has_children: true
---

# Examples

MeshQL ships with six complete example applications:

| Example | Entities | Demonstrates |
|:--------|:---------|:------------|
| [**Farm**](farm) | Farm, Coop, Hen, LayReport | Multi-entity federation, internal/external resolvers, full CRUD |
| [**Events**](events) | RawEvent, ProcessedEvent | CDC pipeline with Kafka/Debezium, event-driven processing |
| [**SwiftShip Logistics**](logistics) | Warehouse, Shipment, Package, TrackingUpdate | Full-stack case study: 3 frontend apps, 8 federation resolvers, Docker + Kubernetes deployment |
| [**National Egg Economy**](egg-economy) | Farm, Coop, Hen, Container, Consumer + 5 events + 3 projections | Event-sourced CDC pipeline, MongoDB sharding, materialized projections, 3 frontend apps |
| [**Springfield Electric**](legacy) | Customer, MeterReading, Bill, Payment | Anti-corruption layer over legacy PostgreSQL, internal resolvers, data transformation via CDC, 3 frontend apps |
| [**Global Power Plants**](power-plants) | Country, FuelType, PowerPlant, GenerationData, PlantFuelAssignment | Mesher-generated anti-corruption layer from real-world dataset (34,936 power plants), 5-entity federation, 3-phase CDC processing |
