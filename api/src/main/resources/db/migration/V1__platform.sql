-- Platform database schema (PostgreSQL)

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(320) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE tenants (
    id              UUID PRIMARY KEY,
    owner_user_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    display_name    VARCHAR(200) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX ux_tenants_owner ON tenants(owner_user_id);

CREATE TABLE tenant_db_configs (
    tenant_id           UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    host                VARCHAR(255) NOT NULL,
    port                INT NOT NULL DEFAULT 3306,
    database_name       VARCHAR(128) NOT NULL,
    username            VARCHAR(128) NOT NULL,
    encrypted_password  TEXT NOT NULL,
    ssl_enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    last_test_ok_at     TIMESTAMPTZ NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE station_profiles (
    user_id         UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    callsign        VARCHAR(32) NOT NULL DEFAULT '',
    gridsquare      VARCHAR(16) NOT NULL DEFAULT '',
    name            VARCHAR(200) NOT NULL DEFAULT '',
    rig             VARCHAR(200) NOT NULL DEFAULT '',
    dxcc            VARCHAR(8) NOT NULL DEFAULT '',
    default_rst_sent VARCHAR(8) NOT NULL DEFAULT '59',
    default_rst_rcvd VARCHAR(8) NOT NULL DEFAULT '59',
    default_band    VARCHAR(16) NOT NULL DEFAULT '20m',
    default_mode    VARCHAR(16) NOT NULL DEFAULT 'SSB',
    default_txpwr   VARCHAR(16) NOT NULL DEFAULT '',
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE lookup_credentials (
    user_id             UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    qrz_user            VARCHAR(128) NOT NULL DEFAULT '',
    encrypted_qrz_password TEXT NOT NULL DEFAULT '',
    hamqth_user         VARCHAR(128) NOT NULL DEFAULT '',
    encrypted_hamqth_password TEXT NOT NULL DEFAULT '',
    encrypted_clublog_api_key TEXT NOT NULL DEFAULT '',
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(128) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMPTZ NULL
);

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens(expires_at);
