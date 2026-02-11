---
title: Query Patterns
layout: default
parent: Reference
nav_order: 6
---

# Query Patterns

MeshQL query templates are [Handlebars](https://jknack.github.io/handlebars.java/) strings that render to your backend's native query syntax. This means anything your database supports — range filters, regex, compound conditions, cursor-based pagination — works in a template.

This page shows concrete patterns across backends.

---

## Exact Match

The simplest pattern. Most examples use this.

```java
// By ID
.singleton("getById", "{\"id\": \"{{id}}\"}")

// By foreign key
.vector("getByCoop", "{\"payload.coop_id\": \"{{id}}\"}")

// By field value
.vector("getByCategory", "{\"payload.category\": \"{{category}}\"}")
```

```graphql
{ getByCoop(id: "coop-1") { name eggs } }
```

**Index**: Single field index on the matched field.

---

## Range Queries

Filter by numeric or date ranges using your backend's native operators.

### MongoDB

```java
// Price between min and max
.vector("getByPriceRange",
    "{\"payload.price\": {\"$gte\": {{min}}, \"$lte\": {{max}}}}")

// Cheaper than a threshold
.vector("getCheap",
    "{\"payload.price\": {\"$lt\": {{maxPrice}}}}")

// Created after a date
.vector("getRecent",
    "{\"payload.created\": {\"$gte\": \"{{since}}\"}}")

// Compound: category + price range
.vector("getByBudget",
    "{\"payload.category\": \"{{category}}\", \"payload.price\": {\"$lte\": {{max}}}}")
```

```graphql
{ getByPriceRange(min: 10, max: 50) { name price } }
{ getByBudget(category: "tools", max: 25) { name price } }
```

**Index**: `db.items.createIndex({"payload.price": 1})` or compound `{"payload.category": 1, "payload.price": 1}` for the budget query.

### PostgreSQL

```java
// Price range (JSONB extraction)
.vector("getByPriceRange",
    "(payload->>'price')::numeric >= {{min}} AND (payload->>'price')::numeric <= {{max}}")

// Date range
.vector("getRecent",
    "payload->>'created' >= '{{since}}'")
```

**Index**: `CREATE INDEX ON items ((payload->>'price')::numeric)` or a GIN index on the payload column.

---

## Regex / Text Search

### MongoDB

```java
// Name contains substring (case-insensitive)
.vector("searchByName",
    "{\"payload.name\": {\"$regex\": \"{{term}}\", \"$options\": \"i\"}}")

// Name starts with prefix
.vector("getByPrefix",
    "{\"payload.name\": {\"$regex\": \"^{{prefix}}\"}}")
```

```graphql
{ searchByName(term: "henri") { name eggs } }
```

**Index**: `db.items.createIndex({"payload.name": 1})` — prefix regex (`^prefix`) uses the index; substring regex does a scan.

### PostgreSQL

```java
// ILIKE for case-insensitive search
.vector("searchByName",
    "payload->>'name' ILIKE '%{{term}}%'")

// Prefix match (index-friendly)
.vector("getByPrefix",
    "payload->>'name' LIKE '{{prefix}}%'")
```

**Index**: `CREATE INDEX ON items ((payload->>'name') text_pattern_ops)` for prefix queries.

---

## Compound Filters

Combine multiple conditions in a single template.

### MongoDB

```java
// Category + minimum rating + in stock
.vector("getTopRated",
    "{\"payload.category\": \"{{category}}\", \"payload.rating\": {\"$gte\": {{minRating}}}, \"payload.in_stock\": true}")

// Multiple foreign keys (intersection)
.vector("getByAuthorAndTag",
    "{\"payload.author_id\": \"{{authorId}}\", \"payload.tags\": \"{{tag}}\"}")
```

```graphql
{ getTopRated(category: "electronics", minRating: 4) { name rating price } }
```

**Index**: Compound index matching the query fields: `{"payload.category": 1, "payload.rating": 1, "payload.in_stock": 1}`

### PostgreSQL

```java
.vector("getTopRated",
    "payload->>'category' = '{{category}}' AND (payload->>'rating')::numeric >= {{minRating}} AND (payload->>'in_stock')::boolean = true")
```

---

## Set Membership

### MongoDB

```java
// Status is one of several values
.vector("getActive",
    "{\"payload.status\": {\"$in\": [\"active\", \"pending\"]}}")

// Tags array contains a value
.vector("getByTag",
    "{\"payload.tags\": \"{{tag}}\"}")
```

### PostgreSQL

```java
.vector("getActive",
    "payload->>'status' IN ('active', 'pending')")

.vector("getByTag",
    "payload->'tags' ? '{{tag}}'")
```

---

## Cursor-Based Pagination

For datasets at scale (millions+ documents), offset-based pagination scans past rows. Cursor-based pagination uses an index to seek directly to the right position.

**The pattern**: create two queries — one for the first page, one for subsequent pages. The cursor is just another template parameter.

### MongoDB

```java
// First page: all hens in a coop, ordered by name
.vector("getByCoop",
    "{\"payload.coop_id\": \"{{id}}\"}")

// Subsequent pages: seek past the cursor
.vector("getByCoopAfter",
    "{\"payload.coop_id\": \"{{id}}\", \"payload.name\": {\"$gt\": \"{{after}}\"}}")
```

```graphql
# First page
{ getByCoop(id: "coop-1", limit: 20) { name eggs } }

# Next page — pass the last name from previous results
{ getByCoopAfter(id: "coop-1", after: "Henrietta", limit: 20) { name eggs } }
```

**Index**: Compound index on the filter + cursor field: `{"payload.coop_id": 1, "payload.name": 1}`. The database seeks directly to `"Henrietta"` in the index — no scanning.

### PostgreSQL

```java
.vector("getByCoop",
    "payload->>'coop_id' = '{{id}}'")

.vector("getByCoopAfter",
    "payload->>'coop_id' = '{{id}}' AND payload->>'name' > '{{after}}'")
```

**Index**: `CREATE INDEX ON hens ((payload->>'coop_id'), (payload->>'name'))`

### Why Two Queries?

The first page has no cursor — there's nothing to seek past. You could use Handlebars conditionals (`{{#if after}}`) in a single template, but two explicit queries are clearer and easier to index:

```graphql
type Query {
    getByCoop(id: ID!, limit: Int, at: Float): [Hen]
    getByCoopAfter(id: ID!, after: String!, limit: Int, at: Float): [Hen]
}
```

Each query maps to a specific index access pattern. The domain expert who creates the index also creates the query template that uses it. No abstraction between intent and execution.

---

## Sorting

Sorting is a backend-native concern — include it in the query template or apply it through the backend's sort mechanism.

### MongoDB

For MongoDB, sorting is typically applied via the aggregation pipeline or cursor options rather than in the query filter. MeshQL's Searcher implementations pass the template to the database as a filter; sort order can be embedded in the query template for backends that support it, or handled through a sorted index that returns results in the desired order.

The practical approach: **create an index with the desired sort order, and the database returns results in that order naturally.**

```javascript
// Index returns hens sorted by name within each coop
db.hens.createIndex({"payload.coop_id": 1, "payload.name": 1})
```

Results from `getByCoop` will be returned in index order — sorted by name — with no explicit sort clause needed.

---

## Limit

`limit` is a **reserved argument name** that constrains the number of results returned by vector queries. Unlike cursor and range operations (which are query filters that live in templates), `limit` is a result-set constraint — each backend extracts it from the query args and applies it natively (`Aggregates.limit()` in MongoDB, `LIMIT` in SQL, `stream.limit()` in memory).

```graphql
type Query {
    getByCoop(id: ID!, limit: Int, at: Float): [Hen]
}
```

```graphql
{ getByCoop(id: "coop-1", limit: 20) { name eggs } }
```

No template change needed — `limit` is extracted from the args automatically by all backends. Queries without a `limit` argument return all matching results.

---

## Pattern Summary

| Pattern | Template Approach | Index Strategy |
|:--------|:-----------------|:---------------|
| **Exact match** | `{"field": "{{value}}"}` | Single field |
| **Range** | `{"field": {"$gte": {{min}}, "$lte": {{max}}}}` | Single field or compound |
| **Regex/text** | `{"field": {"$regex": "{{term}}"}}` | Prefix regex uses index; substring scans |
| **Compound** | Multiple conditions in one template | Compound index matching query fields |
| **Set membership** | `{"field": {"$in": [...]}}` | Single field |
| **Cursor pagination** | Separate query with `$gt`/`>` on cursor field | Compound index (filter + cursor) |
| **Limit** | Reserved arg — extracted from args automatically | N/A — applied after query |

**The principle**: the template is the query. Whatever your database supports, you can write in a template. MeshQL doesn't abstract away your database's query language — it gives you direct access to it through Handlebars parameterization. The domain expert who knows the access patterns writes the templates and creates the matching indexes.
