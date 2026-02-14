-- Egg Economy Salesforce Legacy Database
-- Salesforce-style naming: CamelCase__c custom objects, 18-char IDs, picklist values

-- ============================================================
-- FARM (Farm__c)
-- ============================================================
CREATE TABLE farm__c (
    id                  VARCHAR(18) PRIMARY KEY,       -- Salesforce 18-char ID
    name                VARCHAR(80) NOT NULL,          -- Auto Name field
    farm_type__c        VARCHAR(40) NOT NULL,          -- Picklist: Megafarm, Local Farm, Homestead
    zone__c             VARCHAR(40) NOT NULL,
    owner_name__c       VARCHAR(80),
    isdeleted           BOOLEAN DEFAULT FALSE,
    createddate         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE farm__c REPLICA IDENTITY FULL;

-- ============================================================
-- COOP (Coop__c)
-- ============================================================
CREATE TABLE coop__c (
    id                  VARCHAR(18) PRIMARY KEY,
    name                VARCHAR(80) NOT NULL,
    farm__c             VARCHAR(18) NOT NULL REFERENCES farm__c(id),    -- Lookup to Farm
    capacity__c         INTEGER NOT NULL,
    coop_type__c        VARCHAR(40) NOT NULL,          -- Picklist: Free Range, Cage, Barn
    isdeleted           BOOLEAN DEFAULT FALSE,
    createddate         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE coop__c REPLICA IDENTITY FULL;

-- ============================================================
-- HEN (Hen__c)
-- ============================================================
CREATE TABLE hen__c (
    id                  VARCHAR(18) PRIMARY KEY,
    name                VARCHAR(80) NOT NULL,
    coop__c             VARCHAR(18) NOT NULL REFERENCES coop__c(id),    -- Lookup to Coop
    breed__c            VARCHAR(40) NOT NULL,          -- Picklist: Rhode Island Red, Leghorn, etc.
    date_of_birth__c    DATE,
    status__c           VARCHAR(20) DEFAULT 'Active',  -- Picklist: Active, Retired, Deceased
    isdeleted           BOOLEAN DEFAULT FALSE,
    createddate         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE hen__c REPLICA IDENTITY FULL;

-- ============================================================
-- STORAGE CONTAINER (Storage_Container__c)
-- ============================================================
CREATE TABLE storage_container__c (
    id                  VARCHAR(18) PRIMARY KEY,
    name                VARCHAR(80) NOT NULL,
    container_type__c   VARCHAR(40) NOT NULL,          -- Picklist: Warehouse, Shelf, Fridge
    capacity__c         INTEGER NOT NULL,
    zone__c             VARCHAR(40) NOT NULL,
    isdeleted           BOOLEAN DEFAULT FALSE,
    createddate         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE storage_container__c REPLICA IDENTITY FULL;

-- ============================================================
-- CONSUMER ACCOUNT (Consumer_Account__c)
-- ============================================================
CREATE TABLE consumer_account__c (
    id                  VARCHAR(18) PRIMARY KEY,
    name                VARCHAR(80) NOT NULL,
    consumer_type__c    VARCHAR(40) NOT NULL,          -- Picklist: Household, Restaurant, Bakery
    zone__c             VARCHAR(40) NOT NULL,
    weekly_demand__c    INTEGER DEFAULT 0,
    isdeleted           BOOLEAN DEFAULT FALSE,
    createddate         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE consumer_account__c REPLICA IDENTITY FULL;

-- ============================================================
-- LAY REPORT (Lay_Report__c)
-- ============================================================
CREATE TABLE lay_report__c (
    id                  VARCHAR(18) PRIMARY KEY,
    hen__c              VARCHAR(18) NOT NULL REFERENCES hen__c(id),     -- Lookup to Hen
    farm__c             VARCHAR(18) NOT NULL REFERENCES farm__c(id),    -- Lookup to Farm
    egg_count__c        INTEGER NOT NULL,
    quality__c          VARCHAR(20) DEFAULT 'Grade A',  -- Picklist: Grade A, Grade B, Cracked, Double Yolk
    isdeleted           BOOLEAN DEFAULT FALSE,
    createddate         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE lay_report__c REPLICA IDENTITY FULL;

-- ============================================================
-- STORAGE DEPOSIT (Storage_Deposit__c)
-- ============================================================
CREATE TABLE storage_deposit__c (
    id                  VARCHAR(18) PRIMARY KEY,
    container__c        VARCHAR(18) NOT NULL REFERENCES storage_container__c(id),
    source_type__c      VARCHAR(20) NOT NULL,          -- Picklist: Farm, Transfer
    egg_count__c        INTEGER NOT NULL,
    isdeleted           BOOLEAN DEFAULT FALSE,
    createddate         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE storage_deposit__c REPLICA IDENTITY FULL;

-- ============================================================
-- STORAGE WITHDRAWAL (Storage_Withdrawal__c)
-- ============================================================
CREATE TABLE storage_withdrawal__c (
    id                  VARCHAR(18) PRIMARY KEY,
    container__c        VARCHAR(18) NOT NULL REFERENCES storage_container__c(id),
    egg_count__c        INTEGER NOT NULL,
    reason__c           VARCHAR(20) DEFAULT 'Spoilage',  -- Picklist: Spoilage, Breakage, Quality Check
    isdeleted           BOOLEAN DEFAULT FALSE,
    createddate         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE storage_withdrawal__c REPLICA IDENTITY FULL;

-- ============================================================
-- CONTAINER TRANSFER (Container_Transfer__c)
-- ============================================================
CREATE TABLE container_transfer__c (
    id                  VARCHAR(18) PRIMARY KEY,
    source_container__c VARCHAR(18) NOT NULL REFERENCES storage_container__c(id),
    dest_container__c   VARCHAR(18) NOT NULL REFERENCES storage_container__c(id),
    egg_count__c        INTEGER NOT NULL,
    transport_method__c VARCHAR(20) DEFAULT 'Truck',    -- Picklist: Truck, Van, Cart
    isdeleted           BOOLEAN DEFAULT FALSE,
    createddate         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE container_transfer__c REPLICA IDENTITY FULL;

-- ============================================================
-- CONSUMPTION REPORT (Consumption_Report__c)
-- ============================================================
CREATE TABLE consumption_report__c (
    id                  VARCHAR(18) PRIMARY KEY,
    consumer__c         VARCHAR(18) NOT NULL REFERENCES consumer_account__c(id),
    container__c        VARCHAR(18) NOT NULL REFERENCES storage_container__c(id),
    egg_count__c        INTEGER NOT NULL,
    purpose__c          VARCHAR(20) DEFAULT 'Cooking',  -- Picklist: Cooking, Baking, Resale, Raw
    isdeleted           BOOLEAN DEFAULT FALSE,
    createddate         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE consumption_report__c REPLICA IDENTITY FULL;

-- ============================================================
-- SEED DATA
-- ============================================================

-- Farms (18-char Salesforce-style IDs)
INSERT INTO farm__c (id, name, farm_type__c, zone__c, owner_name__c, createddate) VALUES
('a0B5f000001ABC01', 'Sunny Valley Megafarm', 'Megafarm', 'north', 'John Henderson', '2015-03-01T08:00:00'),
('a0B5f000001ABC02', 'Green Pastures Farm', 'Local Farm', 'south', 'Anna Williams', '2018-06-15T09:30:00'),
('a0B5f000001ABC03', 'Mountain View Homestead', 'Homestead', 'east', 'Klaus Weber', '2020-01-01T07:00:00');

-- Coops
INSERT INTO coop__c (id, name, farm__c, capacity__c, coop_type__c, createddate) VALUES
('a0C5f000002DEF01', 'Alpha Coop', 'a0B5f000001ABC01', 500, 'Cage', '2015-04-01T10:00:00'),
('a0C5f000002DEF02', 'Beta Coop', 'a0B5f000001ABC01', 500, 'Cage', '2015-04-15T10:00:00'),
('a0C5f000002DEF03', 'Free Range Coop', 'a0B5f000001ABC02', 100, 'Free Range', '2018-07-01T11:00:00'),
('a0C5f000002DEF04', 'Mountain Coop', 'a0B5f000001ABC03', 25, 'Barn', '2020-02-01T08:00:00');

-- Hens
INSERT INTO hen__c (id, name, coop__c, breed__c, date_of_birth__c, status__c, createddate) VALUES
('a0D5f000003GHI01', 'Greta', 'a0C5f000002DEF01', 'Rhode Island Red', '2023-01-15', 'Active', '2023-01-15T06:00:00'),
('a0D5f000003GHI02', 'Liesel', 'a0C5f000002DEF01', 'Leghorn', '2023-02-20', 'Active', '2023-02-20T06:00:00'),
('a0D5f000003GHI03', 'Berta', 'a0C5f000002DEF02', 'Plymouth Rock', '2022-08-01', 'Active', '2022-08-01T06:00:00'),
('a0D5f000003GHI04', 'Frieda', 'a0C5f000002DEF03', 'Sussex', '2023-06-01', 'Active', '2023-06-01T06:00:00'),
('a0D5f000003GHI05', 'Klara', 'a0C5f000002DEF03', 'Rhode Island Red', '2023-09-15', 'Active', '2023-09-15T06:00:00'),
('a0D5f000003GHI06', 'Rosa', 'a0C5f000002DEF04', 'Orpington', '2024-01-01', 'Active', '2024-01-01T06:00:00'),
('a0D5f000003GHI07', 'Elsa', 'a0C5f000002DEF01', 'Leghorn', '2022-03-15', 'Retired', '2022-03-15T06:00:00');

-- Containers
INSERT INTO storage_container__c (id, name, container_type__c, capacity__c, zone__c, createddate) VALUES
('a0E5f000004JKL01', 'North Warehouse', 'Warehouse', 10000, 'north', '2015-01-01T08:00:00'),
('a0E5f000004JKL02', 'South Warehouse', 'Warehouse', 5000, 'south', '2018-01-01T08:00:00'),
('a0E5f000004JKL03', 'Market Shelf North', 'Shelf', 200, 'north', '2019-06-01T08:00:00'),
('a0E5f000004JKL04', 'East Fridge', 'Fridge', 50, 'east', '2020-03-01T08:00:00');

-- Consumers
INSERT INTO consumer_account__c (id, name, consumer_type__c, zone__c, weekly_demand__c, createddate) VALUES
('a0F5f000005MNO01', 'Brown Family', 'Household', 'north', 12, '2020-01-01T08:00:00'),
('a0F5f000005MNO02', 'Eagle Restaurant', 'Restaurant', 'south', 200, '2019-06-01T08:00:00'),
('a0F5f000005MNO03', 'Meier Bakery', 'Bakery', 'north', 500, '2018-03-01T08:00:00');

-- Lay Reports
INSERT INTO lay_report__c (id, hen__c, farm__c, egg_count__c, quality__c, createddate) VALUES
('a0G5f000006PQR01', 'a0D5f000003GHI01', 'a0B5f000001ABC01', 3, 'Grade A', '2026-02-01T06:30:00'),
('a0G5f000006PQR02', 'a0D5f000003GHI02', 'a0B5f000001ABC01', 2, 'Grade A', '2026-02-01T06:45:00'),
('a0G5f000006PQR03', 'a0D5f000003GHI03', 'a0B5f000001ABC01', 4, 'Double Yolk', '2026-02-01T07:00:00'),
('a0G5f000006PQR04', 'a0D5f000003GHI04', 'a0B5f000001ABC02', 2, 'Grade A', '2026-02-01T07:15:00'),
('a0G5f000006PQR05', 'a0D5f000003GHI05', 'a0B5f000001ABC02', 1, 'Grade B', '2026-02-01T07:30:00'),
('a0G5f000006PQR06', 'a0D5f000003GHI06', 'a0B5f000001ABC03', 1, 'Grade A', '2026-02-01T08:00:00'),
('a0G5f000006PQR07', 'a0D5f000003GHI01', 'a0B5f000001ABC01', 4, 'Grade A', '2026-02-02T06:30:00'),
('a0G5f000006PQR08', 'a0D5f000003GHI04', 'a0B5f000001ABC02', 3, 'Grade A', '2026-02-02T07:15:00');

-- Storage Deposits
INSERT INTO storage_deposit__c (id, container__c, source_type__c, egg_count__c, createddate) VALUES
('a0H5f000007STU01', 'a0E5f000004JKL01', 'Farm', 9, '2026-02-01T10:00:00'),
('a0H5f000007STU02', 'a0E5f000004JKL02', 'Farm', 3, '2026-02-01T11:00:00'),
('a0H5f000007STU03', 'a0E5f000004JKL04', 'Farm', 1, '2026-02-01T12:00:00'),
('a0H5f000007STU04', 'a0E5f000004JKL01', 'Farm', 7, '2026-02-02T10:00:00');

-- Storage Withdrawals
INSERT INTO storage_withdrawal__c (id, container__c, egg_count__c, reason__c, createddate) VALUES
('a0I5f000008VWX01', 'a0E5f000004JKL01', 2, 'Spoilage', '2026-02-01T14:00:00'),
('a0I5f000008VWX02', 'a0E5f000004JKL02', 1, 'Breakage', '2026-02-01T15:00:00');

-- Container Transfers
INSERT INTO container_transfer__c (id, source_container__c, dest_container__c, egg_count__c, transport_method__c, createddate) VALUES
('a0J5f000009YZA01', 'a0E5f000004JKL01', 'a0E5f000004JKL03', 24, 'Van', '2026-02-01T16:00:00'),
('a0J5f000009YZA02', 'a0E5f000004JKL02', 'a0E5f000004JKL04', 6, 'Cart', '2026-02-02T09:00:00');

-- Consumption Reports
INSERT INTO consumption_report__c (id, consumer__c, container__c, egg_count__c, purpose__c, createddate) VALUES
('a0K5f00000ABCD01', 'a0F5f000005MNO01', 'a0E5f000004JKL03', 12, 'Cooking', '2026-02-01T18:00:00'),
('a0K5f00000ABCD02', 'a0F5f000005MNO02', 'a0E5f000004JKL02', 48, 'Cooking', '2026-02-01T17:00:00'),
('a0K5f00000ABCD03', 'a0F5f000005MNO03', 'a0E5f000004JKL01', 120, 'Baking', '2026-02-02T08:00:00');

-- ============================================================
-- PUBLICATION FOR DEBEZIUM CDC
-- ============================================================
CREATE PUBLICATION salesforce_pub FOR ALL TABLES;
