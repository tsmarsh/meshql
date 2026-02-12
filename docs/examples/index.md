---
title: Examples
layout: default
nav_order: 5
has_children: true
---

# Examples

MeshQL ships with four complete example applications:

| Example | Entities | Demonstrates |
|:--------|:---------|:------------|
| [**Farm**](farm) | Farm, Coop, Hen, LayReport | Multi-entity federation, internal/external resolvers, full CRUD |
| [**Events**](events) | RawEvent, ProcessedEvent | CDC pipeline with Kafka/Debezium, event-driven processing |
| [**SwiftShip Logistics**](logistics) | Warehouse, Shipment, Package, TrackingUpdate | Full-stack case study: 3 frontend apps, 8 federation resolvers, Docker + Kubernetes deployment |
| [**Springfield Electric**](legacy) | Customer, MeterReading, Bill, Payment | Anti-corruption layer over legacy PostgreSQL, internal resolvers, data transformation via CDC, 3 frontend apps |
