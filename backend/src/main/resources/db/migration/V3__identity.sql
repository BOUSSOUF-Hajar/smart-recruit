-- V3 : module Identité (US-06 à US-12) — comptes et sessions.

CREATE TABLE app_user (
    id                   UUID         NOT NULL DEFAULT uuidv7(),
    email                VARCHAR(254) NOT NULL,          -- normalisé en minuscules par l'application (RG-01)
    password_hash        VARCHAR(100) NOT NULL,          -- {bcrypt}... : préfixe d'algorithme du DelegatingPasswordEncoder
    first_name           VARCHAR(80)  NOT NULL,
    last_name            VARCHAR(80)  NOT NULL,
    role                 VARCHAR(16)  NOT NULL,
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    privacy_accepted_at  TIMESTAMPTZ,
    last_login_at        TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT ck_app_user_role CHECK (role IN ('ADMIN', 'RECRUITER', 'CANDIDATE')),
    CONSTRAINT ck_app_user_privacy CHECK (role <> 'CANDIDATE' OR privacy_accepted_at IS NOT NULL)
);

-- RG-01 : email unique sans tenir compte de la casse ; le nom est traduit en EMAIL_ALREADY_USED
CREATE UNIQUE INDEX uk_app_user_email ON app_user (lower(email));

CREATE TABLE refresh_token (
    id          UUID         NOT NULL DEFAULT uuidv7(),
    user_id     UUID         NOT NULL,
    token_hash  CHAR(64)     NOT NULL,                   -- SHA-256 en hexadécimal ; le jeton lui-même n'est jamais stocké
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL,
    created_ip  INET,
    user_agent  VARCHAR(255),
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash)
);

-- Révoquer toutes les sessions d'un utilisateur
CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);

-- Clé étrangère annoncée en V2 : l'auteur d'une entrée d'audit est un utilisateur
ALTER TABLE audit_log
    ADD CONSTRAINT fk_audit_log_actor FOREIGN KEY (actor_id) REFERENCES app_user (id) ON DELETE SET NULL;
