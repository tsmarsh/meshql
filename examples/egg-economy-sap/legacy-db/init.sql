-- Egg Economy SAP Legacy Database
-- SAP-style naming conventions: Z-prefix custom tables, abbreviated column names

-- ============================================================
-- FARM MASTER (ZFARM_MSTR)
-- ============================================================
CREATE TABLE ZFARM_MSTR (
    MANDT           VARCHAR(3) DEFAULT '100',    -- SAP client
    WERKS           VARCHAR(8) PRIMARY KEY,       -- Plant code (farm ID)
    FARM_NM         VARCHAR(60) NOT NULL,
    FARM_TYP_CD     VARCHAR(1) NOT NULL,          -- M=Megafarm, L=Local, H=Homestead
    ZONE_CD         VARCHAR(30) NOT NULL,
    EIGR            VARCHAR(60),                  -- Owner name
    ERDAT           VARCHAR(8) NOT NULL            -- Creation date YYYYMMDD
);

ALTER TABLE ZFARM_MSTR REPLICA IDENTITY FULL;

-- ============================================================
-- STALL MASTER (ZSTALL_MSTR) - Coops
-- ============================================================
CREATE TABLE ZSTALL_MSTR (
    STALL_NR        VARCHAR(10) PRIMARY KEY,      -- Stall number (coop ID)
    WERKS           VARCHAR(8) NOT NULL REFERENCES ZFARM_MSTR(WERKS),
    STALL_NM        VARCHAR(60) NOT NULL,
    KAPZT           INTEGER NOT NULL,              -- Capacity
    STALL_TYP_CD    VARCHAR(1) NOT NULL            -- F=Free range, C=Cage, B=Barn
);

ALTER TABLE ZSTALL_MSTR REPLICA IDENTITY FULL;

-- ============================================================
-- EQUIPMENT HEN (ZEQUI_HEN)
-- ============================================================
CREATE TABLE ZEQUI_HEN (
    EQUNR           VARCHAR(12) PRIMARY KEY,       -- Equipment number (hen ID)
    STALL_NR        VARCHAR(10) NOT NULL REFERENCES ZSTALL_MSTR(STALL_NR),
    HENNE_NM        VARCHAR(60) NOT NULL,
    RASSE_CD        VARCHAR(3) NOT NULL,           -- RIR, LEG, PLY, SUS, ORG
    GEB_DT          VARCHAR(8),                    -- Birth date YYYYMMDD
    STAT_CD         VARCHAR(1) DEFAULT 'A'          -- A=Active, R=Retired, D=Deceased
);

ALTER TABLE ZEQUI_HEN REPLICA IDENTITY FULL;

-- ============================================================
-- CONTAINER MASTER (ZBEHAELT_MSTR)
-- ============================================================
CREATE TABLE ZBEHAELT_MSTR (
    BEHAELT_NR      VARCHAR(10) PRIMARY KEY,       -- Container number
    BEHAELT_NM      VARCHAR(60) NOT NULL,
    BEHAELT_TYP_CD  VARCHAR(1) NOT NULL,           -- W=Warehouse, S=Shelf, F=Fridge
    KAPZT           INTEGER NOT NULL,              -- Capacity
    ZONE_CD         VARCHAR(30) NOT NULL
);

ALTER TABLE ZBEHAELT_MSTR REPLICA IDENTITY FULL;

-- ============================================================
-- CUSTOMER CONSUMER (ZKUNDE_VBR)
-- ============================================================
CREATE TABLE ZKUNDE_VBR (
    KUNNR           VARCHAR(10) PRIMARY KEY,        -- Customer number
    KUND_NM         VARCHAR(60) NOT NULL,
    VBR_TYP_CD      VARCHAR(1) NOT NULL,           -- H=Household, R=Restaurant, B=Bakery
    ZONE_CD         VARCHAR(30) NOT NULL,
    WOCH_BEDARF     INTEGER DEFAULT 0               -- Weekly demand
);

ALTER TABLE ZKUNDE_VBR REPLICA IDENTITY FULL;

-- ============================================================
-- LAY REPORT (ZAFRU_LEGE)
-- ============================================================
CREATE TABLE ZAFRU_LEGE (
    AUFNR           VARCHAR(12) PRIMARY KEY,        -- Order number
    EQUNR           VARCHAR(12) NOT NULL REFERENCES ZEQUI_HEN(EQUNR),
    WERKS           VARCHAR(8) NOT NULL REFERENCES ZFARM_MSTR(WERKS),
    EI_MENGE        INTEGER NOT NULL,               -- Egg quantity
    ERFAS_DT        VARCHAR(8) NOT NULL,            -- Entry date YYYYMMDD
    ERFAS_ZT        VARCHAR(6),                     -- Entry time HHMMSS
    QUAL_CD         VARCHAR(1) DEFAULT 'A'           -- A=Grade A, B=Grade B, C=Cracked, D=Double yolk
);

ALTER TABLE ZAFRU_LEGE REPLICA IDENTITY FULL;

-- ============================================================
-- GOODS RECEIPT - STORAGE DEPOSIT (ZMSEG_101)
-- ============================================================
CREATE TABLE ZMSEG_101 (
    MBLNR           VARCHAR(12) PRIMARY KEY,        -- Material document number
    BEHAELT_NR      VARCHAR(10) NOT NULL REFERENCES ZBEHAELT_MSTR(BEHAELT_NR),
    QUELL_TYP_CD    VARCHAR(1) NOT NULL,            -- F=Farm, T=Transfer
    EI_MENGE        INTEGER NOT NULL,
    ERFAS_DT        VARCHAR(8) NOT NULL,
    ERFAS_ZT        VARCHAR(6)
);

ALTER TABLE ZMSEG_101 REPLICA IDENTITY FULL;

-- ============================================================
-- GOODS ISSUE - STORAGE WITHDRAWAL (ZMSEG_201)
-- ============================================================
CREATE TABLE ZMSEG_201 (
    MBLNR           VARCHAR(12) PRIMARY KEY,
    BEHAELT_NR      VARCHAR(10) NOT NULL REFERENCES ZBEHAELT_MSTR(BEHAELT_NR),
    EI_MENGE        INTEGER NOT NULL,
    ERFAS_DT        VARCHAR(8) NOT NULL,
    ERFAS_ZT        VARCHAR(6),
    GRUND_CD        VARCHAR(1) DEFAULT 'S'           -- S=Spoilage, B=Breakage, Q=Quality check
);

ALTER TABLE ZMSEG_201 REPLICA IDENTITY FULL;

-- ============================================================
-- TRANSFER POSTING (ZMSEG_301)
-- ============================================================
CREATE TABLE ZMSEG_301 (
    MBLNR           VARCHAR(12) PRIMARY KEY,
    QUELL_BEH_NR    VARCHAR(10) NOT NULL REFERENCES ZBEHAELT_MSTR(BEHAELT_NR),
    ZIEL_BEH_NR     VARCHAR(10) NOT NULL REFERENCES ZBEHAELT_MSTR(BEHAELT_NR),
    EI_MENGE        INTEGER NOT NULL,
    ERFAS_DT        VARCHAR(8) NOT NULL,
    ERFAS_ZT        VARCHAR(6),
    TRANS_CD        VARCHAR(1) DEFAULT 'T'           -- T=Truck, V=Van, K=Cart
);

ALTER TABLE ZMSEG_301 REPLICA IDENTITY FULL;

-- ============================================================
-- CONSUMPTION POSTING (ZMSEG_261)
-- ============================================================
CREATE TABLE ZMSEG_261 (
    MBLNR           VARCHAR(12) PRIMARY KEY,
    KUNNR           VARCHAR(10) NOT NULL REFERENCES ZKUNDE_VBR(KUNNR),
    BEHAELT_NR      VARCHAR(10) NOT NULL REFERENCES ZBEHAELT_MSTR(BEHAELT_NR),
    EI_MENGE        INTEGER NOT NULL,
    ERFAS_DT        VARCHAR(8) NOT NULL,
    ERFAS_ZT        VARCHAR(6),
    ZWECK_CD        VARCHAR(1) DEFAULT 'K'           -- K=Cooking, B=Baking, W=Resale, R=Raw
);

ALTER TABLE ZMSEG_261 REPLICA IDENTITY FULL;

-- ============================================================
-- SEED DATA
-- ============================================================

-- Farms
INSERT INTO ZFARM_MSTR (WERKS, FARM_NM, FARM_TYP_CD, ZONE_CD, EIGR, ERDAT) VALUES
('FARM0001', 'Sonnenhof Megafarm', 'M', 'north', 'Heinrich Mueller', '20150301'),
('FARM0002', 'Gruener Hof', 'L', 'south', 'Anna Schmidt', '20180615'),
('FARM0003', 'Berghof Homestead', 'H', 'east', 'Klaus Weber', '20200101');

-- Coops
INSERT INTO ZSTALL_MSTR (STALL_NR, WERKS, STALL_NM, KAPZT, STALL_TYP_CD) VALUES
('ST-001', 'FARM0001', 'Mega Stall Alpha', 500, 'C'),
('ST-002', 'FARM0001', 'Mega Stall Beta', 500, 'C'),
('ST-003', 'FARM0002', 'Freiland Stall', 100, 'F'),
('ST-004', 'FARM0003', 'Berg Stall', 25, 'B');

-- Hens
INSERT INTO ZEQUI_HEN (EQUNR, STALL_NR, HENNE_NM, RASSE_CD, GEB_DT, STAT_CD) VALUES
('HEN-000001', 'ST-001', 'Henne Greta', 'RIR', '20230115', 'A'),
('HEN-000002', 'ST-001', 'Henne Liesel', 'LEG', '20230220', 'A'),
('HEN-000003', 'ST-002', 'Henne Berta', 'PLY', '20220801', 'A'),
('HEN-000004', 'ST-003', 'Henne Frieda', 'SUS', '20230601', 'A'),
('HEN-000005', 'ST-003', 'Henne Klara', 'RIR', '20230915', 'A'),
('HEN-000006', 'ST-004', 'Henne Rosa', 'ORG', '20240101', 'A'),
('HEN-000007', 'ST-001', 'Henne Elsa', 'LEG', '20220315', 'R');

-- Containers
INSERT INTO ZBEHAELT_MSTR (BEHAELT_NR, BEHAELT_NM, BEHAELT_TYP_CD, KAPZT, ZONE_CD) VALUES
('BEH-001', 'Nordlager', 'W', 10000, 'north'),
('BEH-002', 'Suedlager', 'W', 5000, 'south'),
('BEH-003', 'Markt Regal Nord', 'S', 200, 'north'),
('BEH-004', 'Kuehlschrank Ost', 'F', 50, 'east');

-- Consumers
INSERT INTO ZKUNDE_VBR (KUNNR, KUND_NM, VBR_TYP_CD, ZONE_CD, WOCH_BEDARF) VALUES
('KD-0001', 'Familie Braun', 'H', 'north', 12),
('KD-0002', 'Gasthaus zum Adler', 'R', 'south', 200),
('KD-0003', 'Baeckerei Meier', 'B', 'north', 500);

-- Lay Reports
INSERT INTO ZAFRU_LEGE (AUFNR, EQUNR, WERKS, EI_MENGE, ERFAS_DT, ERFAS_ZT, QUAL_CD) VALUES
('LAY-000001', 'HEN-000001', 'FARM0001', 3, '20260201', '063000', 'A'),
('LAY-000002', 'HEN-000002', 'FARM0001', 2, '20260201', '064500', 'A'),
('LAY-000003', 'HEN-000003', 'FARM0001', 4, '20260201', '070000', 'D'),
('LAY-000004', 'HEN-000004', 'FARM0002', 2, '20260201', '071500', 'A'),
('LAY-000005', 'HEN-000005', 'FARM0002', 1, '20260201', '073000', 'B'),
('LAY-000006', 'HEN-000006', 'FARM0003', 1, '20260201', '080000', 'A'),
('LAY-000007', 'HEN-000001', 'FARM0001', 4, '20260202', '063000', 'A'),
('LAY-000008', 'HEN-000004', 'FARM0002', 3, '20260202', '071500', 'A');

-- Storage Deposits
INSERT INTO ZMSEG_101 (MBLNR, BEHAELT_NR, QUELL_TYP_CD, EI_MENGE, ERFAS_DT, ERFAS_ZT) VALUES
('DEP-000001', 'BEH-001', 'F', 9, '20260201', '100000'),
('DEP-000002', 'BEH-002', 'F', 3, '20260201', '110000'),
('DEP-000003', 'BEH-004', 'F', 1, '20260201', '120000'),
('DEP-000004', 'BEH-001', 'F', 7, '20260202', '100000');

-- Storage Withdrawals
INSERT INTO ZMSEG_201 (MBLNR, BEHAELT_NR, EI_MENGE, ERFAS_DT, ERFAS_ZT, GRUND_CD) VALUES
('WDR-000001', 'BEH-001', 2, '20260201', '140000', 'S'),
('WDR-000002', 'BEH-002', 1, '20260201', '150000', 'B');

-- Container Transfers
INSERT INTO ZMSEG_301 (MBLNR, QUELL_BEH_NR, ZIEL_BEH_NR, EI_MENGE, ERFAS_DT, ERFAS_ZT, TRANS_CD) VALUES
('TRF-000001', 'BEH-001', 'BEH-003', 24, '20260201', '160000', 'V'),
('TRF-000002', 'BEH-002', 'BEH-004', 6, '20260202', '090000', 'K');

-- Consumption Reports
INSERT INTO ZMSEG_261 (MBLNR, KUNNR, BEHAELT_NR, EI_MENGE, ERFAS_DT, ERFAS_ZT, ZWECK_CD) VALUES
('CON-000001', 'KD-0001', 'BEH-003', 12, '20260201', '180000', 'K'),
('CON-000002', 'KD-0002', 'BEH-002', 48, '20260201', '170000', 'K'),
('CON-000003', 'KD-0003', 'BEH-001', 120, '20260202', '080000', 'B');

-- ============================================================
-- PUBLICATION FOR DEBEZIUM CDC
-- ============================================================
CREATE PUBLICATION sap_pub FOR ALL TABLES;
