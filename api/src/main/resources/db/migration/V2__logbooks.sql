-- Multiple Log4OM MySQL logbooks per account. Secrets stay encrypted.

CREATE TABLE logbooks (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    display_name                VARCHAR(200) NOT NULL,
    station_callsign            VARCHAR(32) NOT NULL DEFAULT '',
    host                        VARCHAR(255) NOT NULL,
    port                        INT NOT NULL DEFAULT 3306,
    database_name               VARCHAR(128) NOT NULL,
    username                    VARCHAR(128) NOT NULL,
    encrypted_password          TEXT NOT NULL,
    ssl_enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    last_test_ok_at             TIMESTAMPTZ NULL,
    encrypted_qrz_logbook_key   TEXT NOT NULL DEFAULT '',
    eqsl_user                   VARCHAR(128) NOT NULL DEFAULT '',
    encrypted_eqsl_password     TEXT NOT NULL DEFAULT '',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_logbooks_tenant ON logbooks(tenant_id);

INSERT INTO logbooks (
    id, tenant_id, display_name, station_callsign,
    host, port, database_name, username, encrypted_password, ssl_enabled,
    last_test_ok_at, encrypted_qrz_logbook_key, eqsl_user, encrypted_eqsl_password,
    created_at, updated_at
)
SELECT
    gen_random_uuid(),
    t.id,
    CASE
        WHEN COALESCE(s.callsign, '') <> '' THEN s.callsign
        ELSE t.display_name
    END,
    COALESCE(s.callsign, ''),
    c.host,
    c.port,
    c.database_name,
    c.username,
    c.encrypted_password,
    c.ssl_enabled,
    c.last_test_ok_at,
    '',
    '',
    '',
    NOW(),
    c.updated_at
FROM tenant_db_configs c
JOIN tenants t ON t.id = c.tenant_id
LEFT JOIN station_profiles s ON s.user_id = t.owner_user_id;

ALTER TABLE tenants ADD COLUMN active_logbook_id UUID NULL;

UPDATE tenants t
SET active_logbook_id = (
    SELECT l.id
    FROM logbooks l
    WHERE l.tenant_id = t.id
    ORDER BY l.created_at
    LIMIT 1
)
WHERE t.active_logbook_id IS NULL;

ALTER TABLE tenants
    ADD CONSTRAINT fk_tenants_active_logbook
    FOREIGN KEY (active_logbook_id) REFERENCES logbooks(id) ON DELETE SET NULL;
