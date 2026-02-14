"""
Lake API: Flask + DuckDB query service for BI/analytics.
Reads JSON lines from MinIO (S3-compatible) via DuckDB's S3 extension.
"""

import os
import json
import duckdb
from flask import Flask, jsonify

app = Flask(__name__)

MINIO_ENDPOINT = os.getenv("MINIO_ENDPOINT", "minio:9000")
MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "minioadmin")
MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "minioadmin")


def get_connection():
    """Create a DuckDB connection with S3/MinIO configured."""
    conn = duckdb.connect()
    conn.execute("INSTALL httpfs; LOAD httpfs;")
    conn.execute(f"SET s3_endpoint='{MINIO_ENDPOINT}';")
    conn.execute(f"SET s3_access_key_id='{MINIO_ACCESS_KEY}';")
    conn.execute(f"SET s3_secret_access_key='{MINIO_SECRET_KEY}';")
    conn.execute("SET s3_use_ssl=false;")
    conn.execute("SET s3_url_style='path';")
    return conn


def safe_query(query):
    """Execute a DuckDB query, returning empty list on error (e.g., no data yet)."""
    try:
        conn = get_connection()
        result = conn.execute(query).fetchdf()
        conn.close()
        return json.loads(result.to_json(orient="records"))
    except Exception as e:
        app.logger.warning(f"Query failed (data may not exist yet): {e}")
        return []


def safe_scalar(query, default=0):
    """Execute a scalar query, returning default on error."""
    try:
        conn = get_connection()
        result = conn.execute(query).fetchone()
        conn.close()
        return result[0] if result else default
    except Exception:
        return default


@app.route("/api/v1/farm_output")
def farm_output():
    from duckdb_queries import FARM_OUTPUT_QUERY
    data = safe_query(FARM_OUTPUT_QUERY)
    return jsonify({"data": data, "count": len(data)})


@app.route("/api/v1/hen_productivity")
def hen_productivity():
    from duckdb_queries import HEN_PRODUCTIVITY_QUERY
    data = safe_query(HEN_PRODUCTIVITY_QUERY)
    return jsonify({"data": data, "count": len(data)})


@app.route("/api/v1/container_inventory")
def container_inventory():
    from duckdb_queries import CONTAINER_INVENTORY_QUERY
    data = safe_query(CONTAINER_INVENTORY_QUERY)
    return jsonify({"data": data, "count": len(data)})


@app.route("/api/v1/summary")
def summary():
    from duckdb_queries import (
        SUMMARY_QUERY_FARMS,
        SUMMARY_QUERY_EGGS_PRODUCED,
        SUMMARY_QUERY_EGGS_CONSUMED,
        SUMMARY_QUERY_CONTAINERS,
        SUMMARY_QUERY_CONSUMERS,
    )
    return jsonify({
        "total_farms": safe_scalar(SUMMARY_QUERY_FARMS),
        "eggs_produced": safe_scalar(SUMMARY_QUERY_EGGS_PRODUCED),
        "eggs_consumed": safe_scalar(SUMMARY_QUERY_EGGS_CONSUMED),
        "total_containers": safe_scalar(SUMMARY_QUERY_CONTAINERS),
        "total_consumers": safe_scalar(SUMMARY_QUERY_CONSUMERS),
    })


@app.route("/health")
def health():
    return jsonify({"status": "ok"})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5092, debug=False)
