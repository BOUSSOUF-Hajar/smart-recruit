-- V10 : module Offres (US-21 à US-26).

-- unaccent() n'est pas déclarée IMMUTABLE (son dictionnaire pourrait changer), ce qu'exige une
-- colonne générée. L'enveloppe ci-dessous fixe le dictionnaire et peut donc l'être.
CREATE FUNCTION immutable_unaccent(text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
    RETURN public.unaccent('public.unaccent'::regdictionary, $1);

CREATE TABLE job_offer (
    id                   UUID           NOT NULL DEFAULT uuidv7(),
    company_id           UUID           NOT NULL,        -- fixée depuis le token (RG-05)
    category_id          UUID           NOT NULL,
    created_by           UUID           NOT NULL,
    title                VARCHAR(150)   NOT NULL,
    description          TEXT           NOT NULL,        -- 50 à 10 000 caractères, contrôlé par l'application
    city                 VARCHAR(80)    NOT NULL,
    country_code         CHAR(2)        NOT NULL,
    work_mode            VARCHAR(16)    NOT NULL,
    contract_type        VARCHAR(16)    NOT NULL,
    experience_level     VARCHAR(8)     NOT NULL,
    min_years_experience SMALLINT       NOT NULL DEFAULT 0,
    salary_min           NUMERIC(12, 2),
    salary_max           NUMERIC(12, 2),
    salary_currency      CHAR(3)        DEFAULT 'MAD',
    salary_period        VARCHAR(8),
    salary_visible       BOOLEAN        NOT NULL DEFAULT FALSE,
    status               VARCHAR(16)    NOT NULL,
    published_at         TIMESTAMPTZ,
    expires_at           TIMESTAMPTZ,
    -- Recherche plein texte (US-25) : titre (poids A) et description (poids B), sans accents, en français
    search_vector        TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('french', immutable_unaccent(title)), 'A') ||
        setweight(to_tsvector('french', immutable_unaccent(description)), 'B')
    ) STORED,
    created_at           TIMESTAMPTZ    NOT NULL,
    updated_at           TIMESTAMPTZ    NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_job_offer PRIMARY KEY (id),
    CONSTRAINT fk_job_offer_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_job_offer_category FOREIGN KEY (category_id) REFERENCES job_category (id),
    CONSTRAINT fk_job_offer_created_by FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT ck_job_offer_work_mode CHECK (work_mode IN ('ON_SITE', 'HYBRID', 'REMOTE')),
    CONSTRAINT ck_job_offer_contract_type
        CHECK (contract_type IN ('CDI', 'CDD', 'INTERNSHIP', 'FREELANCE', 'APPRENTICESHIP')),
    CONSTRAINT ck_job_offer_experience_level CHECK (experience_level IN ('JUNIOR', 'MID', 'SENIOR', 'LEAD')),
    CONSTRAINT ck_job_offer_min_years CHECK (min_years_experience BETWEEN 0 AND 30),
    CONSTRAINT ck_job_offer_salary CHECK (salary_min IS NULL OR salary_max IS NULL OR salary_min <= salary_max),
    CONSTRAINT ck_job_offer_salary_period CHECK (salary_period IN ('MONTHLY', 'YEARLY')),
    CONSTRAINT ck_job_offer_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED', 'ARCHIVED')),
    CONSTRAINT ck_job_offer_published CHECK (status = 'DRAFT' OR published_at IS NOT NULL),
    CONSTRAINT ck_job_offer_expiry CHECK (expires_at IS NULL OR expires_at > published_at)
);

-- Recherche publique : offres ouvertes
CREATE INDEX ix_job_offer_status_expires ON job_offer (status, expires_at);
-- Recherche plein texte
CREATE INDEX ix_job_offer_search ON job_offer USING GIN (search_vector);
-- Offres de l'entreprise, tableau de bord recruteur
CREATE INDEX ix_job_offer_company_status ON job_offer (company_id, status);

CREATE TABLE job_offer_skill (
    job_offer_id UUID         NOT NULL,
    skill_id     UUID         NOT NULL,
    requirement  VARCHAR(10)  NOT NULL,
    min_level    VARCHAR(16),
    -- Une compétence n'apparaît qu'une fois : requise ou souhaitée, jamais les deux (RG-20)
    CONSTRAINT pk_job_offer_skill PRIMARY KEY (job_offer_id, skill_id),
    CONSTRAINT fk_job_offer_skill_offer FOREIGN KEY (job_offer_id) REFERENCES job_offer (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_offer_skill_skill FOREIGN KEY (skill_id) REFERENCES skill (id),
    CONSTRAINT ck_job_offer_skill_requirement CHECK (requirement IN ('REQUIRED', 'PREFERRED')),
    CONSTRAINT ck_job_offer_skill_level CHECK (
        (requirement = 'REQUIRED' AND min_level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'))
        OR (requirement = 'PREFERRED' AND min_level IS NULL))
);

-- Filtre des offres par compétence
CREATE INDEX ix_job_offer_skill_skill ON job_offer_skill (skill_id, requirement);
