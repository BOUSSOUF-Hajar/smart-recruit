-- V5 : module Référentiels (US-15) — compétences, alias et catégories d'offres.

CREATE TABLE skill (
    id              UUID         NOT NULL DEFAULT uuidv7(),
    name            VARCHAR(80)  NOT NULL,                 -- forme affichée : « Spring Boot »
    normalized_name VARCHAR(80)  NOT NULL,                 -- forme de comparaison : « spring boot »
    active          BOOLEAN      NOT NULL DEFAULT TRUE,    -- désactivée plutôt que supprimée (RG-30)
    created_by      UUID,                                  -- NULL pour les données de départ
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_skill PRIMARY KEY (id),
    CONSTRAINT uk_skill_normalized_name UNIQUE (normalized_name),
    CONSTRAINT fk_skill_created_by FOREIGN KEY (created_by) REFERENCES app_user (id) ON DELETE SET NULL
);

-- Autocomplétion : « commence par » sur la forme normalisée (opérateur LIKE 'spr%')
CREATE INDEX ix_skill_normalized_name_prefix ON skill (normalized_name varchar_pattern_ops) WHERE active;

CREATE TABLE skill_alias (
    normalized_alias VARCHAR(80) NOT NULL,                 -- « springboot », « spring-boot »
    skill_id         UUID        NOT NULL,
    CONSTRAINT pk_skill_alias PRIMARY KEY (normalized_alias),
    CONSTRAINT fk_skill_alias_skill FOREIGN KEY (skill_id) REFERENCES skill (id)
);

CREATE INDEX ix_skill_alias_skill ON skill_alias (skill_id);

CREATE TABLE job_category (
    id         UUID         NOT NULL DEFAULT uuidv7(),
    name       VARCHAR(80)  NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_job_category PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_job_category_name ON job_category (lower(name));
