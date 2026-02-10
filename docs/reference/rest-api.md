---
title: REST API
layout: default
parent: Reference
nav_order: 2
---

# REST API Reference

Every Restlette automatically provides a full CRUD API with bulk operations and Swagger documentation.

---

## Endpoints

For a restlette configured at `/item/api`:

| Method | Path | Description | Status |
|:-------|:-----|:------------|:-------|
| `POST /item/api/` | Create a document | `201 Created` |
| `GET /item/api/` | List all documents | `200 OK` |
| `GET /item/api/{id}` | Read a document by ID | `200 OK` / `404 Not Found` |
| `PUT /item/api/{id}` | Update a document | `200 OK` / `404 Not Found` |
| `DELETE /item/api/{id}` | Soft-delete a document | `200 OK` / `404 Not Found` |
| `POST /item/api/bulk` | Batch create documents | `201 Created` |
| `GET /item/api/bulk?ids=a,b,c` | Batch read documents | `200 OK` |
| `GET /item/api/api-docs` | Swagger UI | `200 OK` |
| `GET /item/api/api-docs/swagger.json` | OpenAPI JSON spec | `200 OK` |

---

## Create

```
POST /item/api/
Content-Type: application/json

{"name": "Widget", "category": "tools", "price": 9.99}
```

**Response** (`201 Created`):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Widget",
  "category": "tools",
  "price": 9.99
}
```

- The `id` is auto-generated (UUID)
- Input is validated against the JSON Schema
- Authorization tokens from the request are stored on the document

---

## Read

```
GET /item/api/550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer eyJ...
```

**Response** (`200 OK`):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Widget",
  "category": "tools",
  "price": 9.99
}
```

- Returns the latest version of the document
- Authorization is enforced — only documents matching the caller's credentials are returned
- `404 Not Found` if the document doesn't exist **or** the caller isn't authorized (indistinguishable to prevent information leakage)

---

## Update

```
PUT /item/api/550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json
Authorization: Bearer eyJ...

{"name": "Super Widget", "category": "tools", "price": 14.99}
```

**Response** (`200 OK`):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Super Widget",
  "category": "tools",
  "price": 14.99
}
```

- Creates a new version (the previous version is preserved)
- Input is validated against the JSON Schema

---

## Delete

```
DELETE /item/api/550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer eyJ...
```

**Response** (`200 OK`):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "deleted"
}
```

- Soft delete — creates a new version with `deleted: true`
- Previous versions are preserved and queryable via temporal queries

---

## List

```
GET /item/api/
Authorization: Bearer eyJ...
```

**Response** (`200 OK`):
```json
[
  {"id": "...", "name": "Widget", "category": "tools"},
  {"id": "...", "name": "Gadget", "category": "electronics"}
]
```

- Returns the latest version of each document
- Filtered by authorization tokens

---

## Bulk Create

```
POST /item/api/bulk
Content-Type: application/json

[
  {"name": "Widget", "price": 9.99},
  {"name": "Gadget", "price": 19.99}
]
```

**Response** (`201 Created`):
```json
[
  {"id": "...", "name": "Widget", "price": 9.99},
  {"id": "...", "name": "Gadget", "price": 19.99}
]
```

---

## Bulk Read

```
GET /item/api/bulk?ids=id1,id2,id3
Authorization: Bearer eyJ...
```

**Response** (`200 OK`):
```json
[
  {"id": "id1", "name": "Widget"},
  {"id": "id2", "name": "Gadget"}
]
```

Only returns documents the caller is authorized to see.

---

## Error Responses

All errors return JSON:

```json
{
  "error": "Description of what went wrong"
}
```

| Status | Meaning |
|:-------|:--------|
| `400 Bad Request` | JSON parse error or schema validation failure |
| `404 Not Found` | Document not found or not authorized |
| `500 Internal Server Error` | Unexpected server error |

---

## Authentication

Pass credentials via the `Authorization` header:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

The Auth implementation extracts credentials from this header. With `JWTSubAuthorizer`, it decodes the JWT and extracts the `sub` claim.

Requests without an `Authorization` header use empty credentials — which means only documents with no authorization restrictions will be visible.
