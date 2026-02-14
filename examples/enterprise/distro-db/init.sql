-- Enterprise Distribution System Database
-- "Rails-era" conventions: plural tables, SERIAL PKs, real timestamps,
-- human-readable codes, no MANDT.

-- ============================================================
-- CONTAINERS
-- ============================================================
CREATE TABLE containers (
    id              SERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    container_type  VARCHAR(20) NOT NULL DEFAULT 'warehouse',
    capacity        INTEGER NOT NULL DEFAULT 0,
    zone            VARCHAR(30) NOT NULL,
    location_code   VARCHAR(10) NOT NULL DEFAULT 'WH01',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE containers REPLICA IDENTITY FULL;

-- ============================================================
-- CUSTOMERS
-- ============================================================
CREATE TABLE customers (
    id              SERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    customer_type   VARCHAR(20) NOT NULL DEFAULT 'household',
    zone            VARCHAR(30) NOT NULL,
    weekly_demand   INTEGER DEFAULT 0,
    email           VARCHAR(100),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE customers REPLICA IDENTITY FULL;

-- ============================================================
-- STORAGE DEPOSITS
-- ============================================================
CREATE TABLE storage_deposits (
    id              SERIAL PRIMARY KEY,
    container_id    INTEGER NOT NULL REFERENCES containers(id),
    source_type     VARCHAR(20) NOT NULL DEFAULT 'farm',
    source_id       VARCHAR(100),
    eggs            INTEGER NOT NULL DEFAULT 0,
    recorded_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE storage_deposits REPLICA IDENTITY FULL;

-- ============================================================
-- STORAGE WITHDRAWALS
-- ============================================================
CREATE TABLE storage_withdrawals (
    id              SERIAL PRIMARY KEY,
    container_id    INTEGER NOT NULL REFERENCES containers(id),
    reason          VARCHAR(30) NOT NULL DEFAULT 'spoilage',
    eggs            INTEGER NOT NULL DEFAULT 0,
    recorded_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE storage_withdrawals REPLICA IDENTITY FULL;

-- ============================================================
-- CONTAINER TRANSFERS
-- ============================================================
CREATE TABLE container_transfers (
    id                  SERIAL PRIMARY KEY,
    source_container_id INTEGER NOT NULL REFERENCES containers(id),
    dest_container_id   INTEGER NOT NULL REFERENCES containers(id),
    eggs                INTEGER NOT NULL DEFAULT 0,
    transport_method    VARCHAR(20) NOT NULL DEFAULT 'truck',
    recorded_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE container_transfers REPLICA IDENTITY FULL;

-- ============================================================
-- CONSUMPTION REPORTS
-- ============================================================
CREATE TABLE consumption_reports (
    id              SERIAL PRIMARY KEY,
    customer_id     INTEGER NOT NULL REFERENCES customers(id),
    container_id    INTEGER NOT NULL REFERENCES containers(id),
    eggs            INTEGER NOT NULL DEFAULT 0,
    purpose         VARCHAR(20) NOT NULL DEFAULT 'cooking',
    recorded_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE consumption_reports REPLICA IDENTITY FULL;

-- ============================================================
-- SEED DATA
-- ============================================================

INSERT INTO containers (name, container_type, capacity, zone, location_code) VALUES
('North Warehouse',    'warehouse',    10000, 'north', 'WH01'),
('South Warehouse',    'warehouse',    5000,  'south', 'WH02'),
('Market Shelf North', 'market_shelf', 200,   'north', 'MS01'),
('East Fridge',        'fridge',       50,    'east',  'FR01');

INSERT INTO customers (name, customer_type, zone, weekly_demand, email) VALUES
('Familie Braun',      'household',  'north', 12,  'braun@example.com'),
('Gasthaus zum Adler', 'restaurant', 'south', 200, 'adler@example.com'),
('Baeckerei Meier',    'bakery',     'north', 500, 'meier@example.com');

INSERT INTO storage_deposits (container_id, source_type, source_id, eggs, recorded_at) VALUES
(1, 'farm', 'sonnenhof', 9,  '2026-02-01 10:00:00'),
(2, 'farm', 'gruener',   3,  '2026-02-01 11:00:00'),
(4, 'farm', 'berghof',   1,  '2026-02-01 12:00:00'),
(1, 'farm', 'sonnenhof', 7,  '2026-02-02 10:00:00');

INSERT INTO storage_withdrawals (container_id, reason, eggs, recorded_at) VALUES
(1, 'spoilage', 2, '2026-02-01 14:00:00'),
(2, 'breakage', 1, '2026-02-01 15:00:00');

INSERT INTO container_transfers (source_container_id, dest_container_id, eggs, transport_method, recorded_at) VALUES
(1, 3, 24, 'van',  '2026-02-01 16:00:00'),
(2, 4, 6,  'cart', '2026-02-02 09:00:00');

INSERT INTO consumption_reports (customer_id, container_id, eggs, purpose, recorded_at) VALUES
(1, 3, 12,  'cooking', '2026-02-01 18:00:00'),
(2, 2, 48,  'cooking', '2026-02-01 17:00:00'),
(3, 1, 120, 'baking',  '2026-02-02 08:00:00');

-- ============================================================
-- PUBLICATION FOR DEBEZIUM CDC
-- ============================================================
CREATE PUBLICATION distro_pub FOR ALL TABLES;
