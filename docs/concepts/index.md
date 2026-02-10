---
title: Concepts
layout: default
nav_order: 3
has_children: true
---

# Core Concepts

MeshQL is built on a small number of concepts that compose to handle complex distributed architectures.

| Concept | What It Is | Why It Matters |
|:--------|:-----------|:---------------|
| [**Meshobj**](meshobj) | An entity with dual APIs and owned storage | The unit of independent deployment |
| [**Envelope**](envelope) | Metadata wrapper around your data | Enables versioning, auth, and audit trails |
| [**Temporal Queries**](temporal) | Read data as of any point in time | Audit, compliance, debugging — built in |
| [**Authentication**](auth) | Pluggable credential extraction and authorization | Gateway-friendly, document-level access control |
