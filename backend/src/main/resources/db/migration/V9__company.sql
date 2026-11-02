-- V9 : module Entreprises (US-47, US-49) — entreprises clientes et profils recruteurs.

CREATE TABLE company (
    id           UUID          NOT NULL DEFAULT uuidv7(),
    name         VARCHAR(120)  NOT NULL,
    sector       VARCHAR(80),
    size_range   VARCHAR(16),
    city         VARCHAR(80),
    country_code CHAR(2),
    website      VARCHAR(255),
    description  TEXT,                                   -- 5 000 caractères au plus, contrôlé par l'application
    timezone     VARCHAR(64)   NOT NULL DEFAULT 'Africa/Casablanca',   -- expiration des offres (analyse 6.2)
    active       BOOLEAN       NOT NULL DEFAULT TRUE,    -- RG-31
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    version      BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_company PRIMARY KEY (id),
    CONSTRAINT ck_company_size_range CHECK (size_range IN ('1-10', '11-50', '51-200', '201-1000', '1000+')),
    CONSTRAINT ck_company_website CHECK (website LIKE 'https://%')
);

-- Nom unique sans tenir compte de la casse ; traduit en RESOURCE_IN_USE
CREATE UNIQUE INDEX uk_company_name ON company (lower(name));

CREATE TABLE recruiter_profile (
    user_id    UUID          NOT NULL,
    company_id UUID          NOT NULL,                   -- une seule entreprise par recruteur (RG-05)
    job_title  VARCHAR(120),
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_recruiter_profile PRIMARY KEY (user_id),
    CONSTRAINT fk_recruiter_profile_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_recruiter_profile_company FOREIGN KEY (company_id) REFERENCES company (id)
);

-- Recruteurs d'une entreprise ; désactivation en cascade (RG-31)
CREATE INDEX ix_recruiter_profile_company ON recruiter_profile (company_id);
