/**
 * MongoDB implementation of the Repository interface for MeshQL.
 * 
 * This package provides classes for storing and retrieving Envelope objects in
 * MongoDB.
 * The implementation supports all standard repository operations including
 * create, read,
 * update (via create of new versions), and soft delete.
 * 
 * Security is implemented via token-based authorization, where documents can be
 * filtered
 * based on the authorized readers.
 */
package com.meshql.repositories.mongo;
