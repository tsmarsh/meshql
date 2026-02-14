"""DuckDB SQL query definitions for the Lake API."""

FARM_OUTPUT_QUERY = """
SELECT
    json_extract_string(data, '$.farm_id') as farm_id,
    COUNT(*) as report_count,
    SUM(CAST(json_extract_string(data, '$.eggs') AS INTEGER)) as total_eggs,
    AVG(CAST(json_extract_string(data, '$.eggs') AS DOUBLE)) as avg_eggs_per_report
FROM read_json_auto('s3://enterprise-lake/lake/lay_report/**/*.jsonl',
    format='newline_delimited', hive_partitioning=false)
AS t(data)
GROUP BY json_extract_string(data, '$.farm_id')
ORDER BY total_eggs DESC
"""

HEN_PRODUCTIVITY_QUERY = """
SELECT
    json_extract_string(data, '$.hen_id') as hen_id,
    json_extract_string(data, '$.farm_id') as farm_id,
    COUNT(*) as report_count,
    SUM(CAST(json_extract_string(data, '$.eggs') AS INTEGER)) as total_eggs,
    AVG(CAST(json_extract_string(data, '$.eggs') AS DOUBLE)) as avg_eggs_per_report,
    SUM(CASE WHEN json_extract_string(data, '$.quality') IN ('grade_a', 'double_yolk') THEN 1 ELSE 0 END)::DOUBLE
        / NULLIF(COUNT(*), 0) as quality_rate
FROM read_json_auto('s3://enterprise-lake/lake/lay_report/**/*.jsonl',
    format='newline_delimited', hive_partitioning=false)
AS t(data)
GROUP BY json_extract_string(data, '$.hen_id'), json_extract_string(data, '$.farm_id')
ORDER BY total_eggs DESC
"""

CONTAINER_INVENTORY_QUERY = """
WITH deposits AS (
    SELECT
        json_extract_string(data, '$.container_id') as container_id,
        SUM(CAST(json_extract_string(data, '$.eggs') AS INTEGER)) as deposited
    FROM read_json_auto('s3://enterprise-lake/lake/storage_deposit/**/*.jsonl',
        format='newline_delimited', hive_partitioning=false) AS t(data)
    GROUP BY json_extract_string(data, '$.container_id')
),
withdrawals AS (
    SELECT
        json_extract_string(data, '$.container_id') as container_id,
        SUM(CAST(json_extract_string(data, '$.eggs') AS INTEGER)) as withdrawn
    FROM read_json_auto('s3://enterprise-lake/lake/storage_withdrawal/**/*.jsonl',
        format='newline_delimited', hive_partitioning=false) AS t(data)
    GROUP BY json_extract_string(data, '$.container_id')
),
transfers_out AS (
    SELECT
        json_extract_string(data, '$.source_container_id') as container_id,
        SUM(CAST(json_extract_string(data, '$.eggs') AS INTEGER)) as transferred_out
    FROM read_json_auto('s3://enterprise-lake/lake/container_transfer/**/*.jsonl',
        format='newline_delimited', hive_partitioning=false) AS t(data)
    GROUP BY json_extract_string(data, '$.source_container_id')
),
transfers_in AS (
    SELECT
        json_extract_string(data, '$.dest_container_id') as container_id,
        SUM(CAST(json_extract_string(data, '$.eggs') AS INTEGER)) as transferred_in
    FROM read_json_auto('s3://enterprise-lake/lake/container_transfer/**/*.jsonl',
        format='newline_delimited', hive_partitioning=false) AS t(data)
    GROUP BY json_extract_string(data, '$.dest_container_id')
),
consumed AS (
    SELECT
        json_extract_string(data, '$.container_id') as container_id,
        SUM(CAST(json_extract_string(data, '$.eggs') AS INTEGER)) as consumed_total
    FROM read_json_auto('s3://enterprise-lake/lake/consumption_report/**/*.jsonl',
        format='newline_delimited', hive_partitioning=false) AS t(data)
    GROUP BY json_extract_string(data, '$.container_id')
)
SELECT
    COALESCE(d.container_id, w.container_id, to2.container_id, ti.container_id, c.container_id) as container_id,
    COALESCE(d.deposited, 0) as total_deposits,
    COALESCE(w.withdrawn, 0) as total_withdrawals,
    COALESCE(to2.transferred_out, 0) as total_transfers_out,
    COALESCE(ti.transferred_in, 0) as total_transfers_in,
    COALESCE(c.consumed_total, 0) as total_consumed,
    COALESCE(d.deposited, 0) - COALESCE(w.withdrawn, 0)
        - COALESCE(to2.transferred_out, 0) + COALESCE(ti.transferred_in, 0)
        - COALESCE(c.consumed_total, 0) as current_eggs
FROM deposits d
FULL OUTER JOIN withdrawals w ON d.container_id = w.container_id
FULL OUTER JOIN transfers_out to2 ON COALESCE(d.container_id, w.container_id) = to2.container_id
FULL OUTER JOIN transfers_in ti ON COALESCE(d.container_id, w.container_id, to2.container_id) = ti.container_id
FULL OUTER JOIN consumed c ON COALESCE(d.container_id, w.container_id, to2.container_id, ti.container_id) = c.container_id
ORDER BY current_eggs DESC
"""

SUMMARY_QUERY_FARMS = """
SELECT COUNT(DISTINCT json_extract_string(data, '$.legacy_werks')) as total_farms
FROM read_json_auto('s3://enterprise-lake/lake/farm/**/*.jsonl',
    format='newline_delimited', hive_partitioning=false) AS t(data)
"""

SUMMARY_QUERY_EGGS_PRODUCED = """
SELECT COALESCE(SUM(CAST(json_extract_string(data, '$.eggs') AS INTEGER)), 0) as eggs_produced
FROM read_json_auto('s3://enterprise-lake/lake/lay_report/**/*.jsonl',
    format='newline_delimited', hive_partitioning=false) AS t(data)
"""

SUMMARY_QUERY_EGGS_CONSUMED = """
SELECT COALESCE(SUM(CAST(json_extract_string(data, '$.eggs') AS INTEGER)), 0) as eggs_consumed
FROM read_json_auto('s3://enterprise-lake/lake/consumption_report/**/*.jsonl',
    format='newline_delimited', hive_partitioning=false) AS t(data)
"""

SUMMARY_QUERY_CONTAINERS = """
SELECT COUNT(DISTINCT json_extract_string(data, '$.legacy_distro_id')) as total_containers
FROM read_json_auto('s3://enterprise-lake/lake/container/**/*.jsonl',
    format='newline_delimited', hive_partitioning=false) AS t(data)
"""

SUMMARY_QUERY_CONSUMERS = """
SELECT COUNT(DISTINCT json_extract_string(data, '$.legacy_distro_id')) as total_consumers
FROM read_json_auto('s3://enterprise-lake/lake/consumer/**/*.jsonl',
    format='newline_delimited', hive_partitioning=false) AS t(data)
"""
