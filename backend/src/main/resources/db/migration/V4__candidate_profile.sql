-- V4 : profil candidat, créé vide à l'inscription (US-06).
-- Les rubriques (expériences, formations, compétences, langues, liens) et les CV arrivent en phase 4.

CREATE TABLE candidate_profile (
    user_id             UUID          NOT NULL,
    phone               VARCHAR(20),
    city                VARCHAR(80),
    country_code        CHAR(2),
    headline            VARCHAR(120),
    summary             VARCHAR(2000),
    years_of_experience SMALLINT,
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_candidate_profile PRIMARY KEY (user_id),
    CONSTRAINT fk_candidate_profile_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_candidate_profile_years CHECK (years_of_experience BETWEEN 0 AND 50)
);
