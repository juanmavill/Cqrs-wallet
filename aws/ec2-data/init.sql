-- ============================================================================
-- CQRS Wallet · MySQL init script
-- Crea la base de datos, schema y siembra 100 cuentas de prueba.
-- Ejecutado automáticamente por la imagen mysql:8 vía /docker-entrypoint-initdb.d
-- ============================================================================

CREATE DATABASE IF NOT EXISTS wallet_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE wallet_db;

-- ----------------------------------------------------------------------------
-- Tabla accounts
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS accounts (
    id          VARCHAR(20)    NOT NULL,
    owner_name  VARCHAR(100)   NOT NULL,
    balance     DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    created_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

-- ----------------------------------------------------------------------------
-- Tabla transactions
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS transactions (
    id          VARCHAR(40)             NOT NULL,
    account_id  VARCHAR(20)             NOT NULL,
    amount      DECIMAL(15, 2)          NOT NULL,
    type        ENUM('DEBIT','CREDIT')  NOT NULL,
    status      ENUM('PENDING','COMPLETED','FAILED') NOT NULL,
    timestamp   TIMESTAMP               NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_tx_account  (account_id),
    INDEX idx_tx_timestamp (timestamp),
    CONSTRAINT fk_tx_account FOREIGN KEY (account_id) REFERENCES accounts(id)
) ENGINE = InnoDB;

-- ----------------------------------------------------------------------------
-- Seed: 100 cuentas ACC001..ACC100, balance inicial 10000.00
-- ----------------------------------------------------------------------------
INSERT INTO accounts (id, owner_name, balance) VALUES
('ACC001','User 001',10000.00),('ACC002','User 002',10000.00),('ACC003','User 003',10000.00),
('ACC004','User 004',10000.00),('ACC005','User 005',10000.00),('ACC006','User 006',10000.00),
('ACC007','User 007',10000.00),('ACC008','User 008',10000.00),('ACC009','User 009',10000.00),
('ACC010','User 010',10000.00),('ACC011','User 011',10000.00),('ACC012','User 012',10000.00),
('ACC013','User 013',10000.00),('ACC014','User 014',10000.00),('ACC015','User 015',10000.00),
('ACC016','User 016',10000.00),('ACC017','User 017',10000.00),('ACC018','User 018',10000.00),
('ACC019','User 019',10000.00),('ACC020','User 020',10000.00),('ACC021','User 021',10000.00),
('ACC022','User 022',10000.00),('ACC023','User 023',10000.00),('ACC024','User 024',10000.00),
('ACC025','User 025',10000.00),('ACC026','User 026',10000.00),('ACC027','User 027',10000.00),
('ACC028','User 028',10000.00),('ACC029','User 029',10000.00),('ACC030','User 030',10000.00),
('ACC031','User 031',10000.00),('ACC032','User 032',10000.00),('ACC033','User 033',10000.00),
('ACC034','User 034',10000.00),('ACC035','User 035',10000.00),('ACC036','User 036',10000.00),
('ACC037','User 037',10000.00),('ACC038','User 038',10000.00),('ACC039','User 039',10000.00),
('ACC040','User 040',10000.00),('ACC041','User 041',10000.00),('ACC042','User 042',10000.00),
('ACC043','User 043',10000.00),('ACC044','User 044',10000.00),('ACC045','User 045',10000.00),
('ACC046','User 046',10000.00),('ACC047','User 047',10000.00),('ACC048','User 048',10000.00),
('ACC049','User 049',10000.00),('ACC050','User 050',10000.00),('ACC051','User 051',10000.00),
('ACC052','User 052',10000.00),('ACC053','User 053',10000.00),('ACC054','User 054',10000.00),
('ACC055','User 055',10000.00),('ACC056','User 056',10000.00),('ACC057','User 057',10000.00),
('ACC058','User 058',10000.00),('ACC059','User 059',10000.00),('ACC060','User 060',10000.00),
('ACC061','User 061',10000.00),('ACC062','User 062',10000.00),('ACC063','User 063',10000.00),
('ACC064','User 064',10000.00),('ACC065','User 065',10000.00),('ACC066','User 066',10000.00),
('ACC067','User 067',10000.00),('ACC068','User 068',10000.00),('ACC069','User 069',10000.00),
('ACC070','User 070',10000.00),('ACC071','User 071',10000.00),('ACC072','User 072',10000.00),
('ACC073','User 073',10000.00),('ACC074','User 074',10000.00),('ACC075','User 075',10000.00),
('ACC076','User 076',10000.00),('ACC077','User 077',10000.00),('ACC078','User 078',10000.00),
('ACC079','User 079',10000.00),('ACC080','User 080',10000.00),('ACC081','User 081',10000.00),
('ACC082','User 082',10000.00),('ACC083','User 083',10000.00),('ACC084','User 084',10000.00),
('ACC085','User 085',10000.00),('ACC086','User 086',10000.00),('ACC087','User 087',10000.00),
('ACC088','User 088',10000.00),('ACC089','User 089',10000.00),('ACC090','User 090',10000.00),
('ACC091','User 091',10000.00),('ACC092','User 092',10000.00),('ACC093','User 093',10000.00),
('ACC094','User 094',10000.00),('ACC095','User 095',10000.00),('ACC096','User 096',10000.00),
('ACC097','User 097',10000.00),('ACC098','User 098',10000.00),('ACC099','User 099',10000.00),
('ACC100','User 100',10000.00);
