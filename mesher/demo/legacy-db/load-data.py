#!/usr/bin/env python3
"""
ETL script: Load Global Power Plant Database CSV into the legacy PostgreSQL schema.

Usage:
    python3 load-data.py [--csv PATH] [--host HOST] [--port PORT] [--db DB] [--user USER] [--password PASS]

Defaults connect to the Docker Compose PostgreSQL instance.
"""

import argparse
import csv
import math
import os
import sys

import psycopg2

# Fuel name → legacy code mapping
FUEL_CODES = {
    "Biomass": "BIOM",
    "Coal": "COAL",
    "Cogeneration": "COGN",
    "Gas": "GAS",
    "Geothermal": "GTHM",
    "Hydro": "HYDR",
    "Nuclear": "NUCL",
    "Oil": "OIL",
    "Other": "OTHR",
    "Petcoke": "PTKC",
    "Solar": "SOLR",
    "Storage": "STOR",
    "Waste": "WAST",
    "Wave and Tidal": "WAVT",
    "Wind": "WIND",
}

# Fuel category mapping
FUEL_CATEGORIES = {
    "BIOM": "RE", "COAL": "FF", "COGN": "OT", "GAS": "FF",
    "GTHM": "RE", "HYDR": "RE", "NUCL": "NR", "OIL": "FF",
    "OTHR": "OT", "PTKC": "FF", "SOLR": "RE", "STOR": "OT",
    "WAST": "OT", "WAVT": "RE", "WIND": "RE",
}


def parse_float(val):
    if not val or val.strip() == "":
        return None
    try:
        return float(val)
    except ValueError:
        return None


def parse_int(val):
    if not val or val.strip() == "":
        return None
    try:
        return int(float(val))
    except ValueError:
        return None


def to_lat_lon_int(val):
    """Convert decimal degrees to integer micro-degrees (x 1,000,000)."""
    f = parse_float(val)
    if f is None:
        return None
    return int(round(f * 1_000_000))


def to_kw(mw_str):
    """Convert MW (float) to kW (integer)."""
    f = parse_float(mw_str)
    if f is None:
        return 0
    return int(round(f * 1000))


def commission_date(year_str):
    """Convert commissioning_year (e.g. '1998.0') to YYYYMMDD string."""
    yr = parse_int(year_str)
    if yr is None:
        return None
    return f"{yr}0101"


def main():
    parser = argparse.ArgumentParser(description="Load power plant CSV into legacy DB")
    parser.add_argument("--csv", default="/data/global_power_plant_database.csv")
    parser.add_argument("--host", default=os.environ.get("PGHOST", "localhost"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("PGPORT", "5432")))
    parser.add_argument("--db", default=os.environ.get("PGDATABASE", "power_plants"))
    parser.add_argument("--user", default=os.environ.get("PGUSER", "postgres"))
    parser.add_argument("--password", default=os.environ.get("PGPASSWORD", "postgres"))
    args = parser.parse_args()

    conn = psycopg2.connect(
        host=args.host, port=args.port, dbname=args.db,
        user=args.user, password=args.password
    )
    conn.autocommit = False
    cur = conn.cursor()

    # 1. Load fuel type reference
    print("Loading fuel types...")
    for fuel_name, fuel_cd in FUEL_CODES.items():
        cur.execute(
            "INSERT INTO FUEL_TYPE_REF (FUEL_CD, FUEL_DESC, FUEL_CAT_CD) VALUES (%s, %s, %s) ON CONFLICT DO NOTHING",
            (fuel_cd, fuel_name, FUEL_CATEGORIES[fuel_cd])
        )
    conn.commit()

    # 2. Read CSV
    print(f"Reading CSV: {args.csv}")
    with open(args.csv, "r", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        rows = list(reader)
    print(f"  {len(rows)} rows")

    # 3. Load countries (deduplicate from data)
    print("Loading countries...")
    countries = {}
    for row in rows:
        cd = row["country"]
        nm = row["country_long"]
        if cd and cd not in countries:
            countries[cd] = nm
    for cd, nm in sorted(countries.items()):
        cur.execute(
            "INSERT INTO CNTRY_REF (CNTRY_CD, CNTRY_NM) VALUES (%s, %s) ON CONFLICT DO NOTHING",
            (cd, nm)
        )
    conn.commit()
    print(f"  {len(countries)} countries")

    # 4. Load plants
    print("Loading power plants...")
    plant_ids = {}  # gppd_idnr -> PLT_ID
    batch = []
    for i, row in enumerate(rows):
        ext_cd = row["gppd_idnr"]
        if not ext_cd:
            continue

        batch.append((
            ext_cd,
            row.get("name", "")[:200],
            row.get("country", ""),
            to_kw(row.get("capacity_mw", "0")),
            to_lat_lon_int(row.get("latitude", "")),
            to_lat_lon_int(row.get("longitude", "")),
            commission_date(row.get("commissioning_year", "")),
            (row.get("owner", "") or "")[:200],
            "OP",  # all plants default to operational
            (row.get("wepp_id", "") or "")[:20] or None,
            parse_int(row.get("year_of_capacity_data", "")),
            (row.get("source", "") or "")[:100],
            (row.get("url", "") or "")[:500],
            (row.get("geolocation_source", "") or "")[:100],
        ))

        if len(batch) >= 1000:
            _insert_plants(cur, batch, plant_ids)
            batch = []
            print(f"  {i+1}/{len(rows)} plants...")

    if batch:
        _insert_plants(cur, batch, plant_ids)
    conn.commit()
    print(f"  {len(plant_ids)} plants loaded")

    # 5. Load fuel assignments
    print("Loading fuel assignments...")
    fuel_count = 0
    batch = []
    for row in rows:
        ext_cd = row["gppd_idnr"]
        plt_id = plant_ids.get(ext_cd)
        if plt_id is None:
            continue

        fuels = []
        for col in ["primary_fuel", "other_fuel1", "other_fuel2", "other_fuel3"]:
            fuel_name = (row.get(col, "") or "").strip()
            if fuel_name and fuel_name in FUEL_CODES:
                fuels.append(FUEL_CODES[fuel_name])

        for rank, fuel_cd in enumerate(fuels, 1):
            batch.append((plt_id, fuel_cd, rank))
            fuel_count += 1

        if len(batch) >= 5000:
            _insert_fuels(cur, batch)
            batch = []

    if batch:
        _insert_fuels(cur, batch)
    conn.commit()
    print(f"  {fuel_count} fuel assignments loaded")

    # 6. Load generation data
    print("Loading generation data...")
    gen_count = 0
    batch = []
    years = range(2013, 2020)  # 2013-2019
    for row in rows:
        ext_cd = row["gppd_idnr"]
        plt_id = plant_ids.get(ext_cd)
        if plt_id is None:
            continue

        for yr in years:
            act_gwh = parse_float(row.get(f"generation_gwh_{yr}", ""))
            est_gwh = parse_float(row.get(f"estimated_generation_gwh_{yr}", ""))
            est_note = row.get(f"estimated_generation_note_{yr}", "") or None

            if act_gwh is None and est_gwh is None:
                continue

            # Convert GWh to MWh (integer) for legacy storage
            act_mwh = int(round(act_gwh * 1000)) if act_gwh is not None else None
            est_mwh = int(round(est_gwh * 1000)) if est_gwh is not None else None

            data_src = row.get("generation_data_source", "") or None

            batch.append((plt_id, yr, act_mwh, est_mwh, est_note, data_src))
            gen_count += 1

        if len(batch) >= 5000:
            _insert_gen_data(cur, batch)
            batch = []

    if batch:
        _insert_gen_data(cur, batch)
    conn.commit()
    print(f"  {gen_count} generation records loaded")

    cur.close()
    conn.close()
    print("Done!")


def _insert_plants(cur, batch, plant_ids):
    for rec in batch:
        cur.execute("""
            INSERT INTO PWR_PLT (PLT_EXT_CD, PLT_NM, CNTRY_CD, CAP_KW, LAT_DEG, LON_DEG,
                                  COMM_DT, OWNR_NM, STAT_CD, WEPP_CD, CAP_YR, SRC_NM, SRC_URL, GEO_SRC_NM)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            RETURNING PLT_ID
        """, rec)
        plt_id = cur.fetchone()[0]
        plant_ids[rec[0]] = plt_id


def _insert_fuels(cur, batch):
    for rec in batch:
        cur.execute("""
            INSERT INTO PLT_FUEL_ASGN (PLT_ID, FUEL_CD, FUEL_RNK)
            VALUES (%s, %s, %s)
            ON CONFLICT (PLT_ID, FUEL_RNK) DO NOTHING
        """, rec)


def _insert_gen_data(cur, batch):
    for rec in batch:
        cur.execute("""
            INSERT INTO GEN_DATA (PLT_ID, RPT_YR, ACT_GEN_MWH, EST_GEN_MWH, EST_MTHD_CD, DATA_SRC_NM)
            VALUES (%s, %s, %s, %s, %s, %s)
            ON CONFLICT (PLT_ID, RPT_YR) DO NOTHING
        """, rec)


if __name__ == "__main__":
    main()
