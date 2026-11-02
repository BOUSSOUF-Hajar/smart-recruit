-- V6 : rubriques du profil candidat et CV (US-14 à US-17).
-- Les rubriques appartiennent au profil (ON DELETE CASCADE) ; les CV non, car un CV joint
-- à une candidature doit survivre (RG-12).

CREATE TABLE experience (
    id           UUID          NOT NULL DEFAULT uuidv7(),
    candidate_id UUID          NOT NULL,
    job_title    VARCHAR(120)  NOT NULL,
    company_name VARCHAR(120)  NOT NULL,                   -- texte libre : employeur hors plateforme possible
    city         VARCHAR(80),
    start_date   DATE          NOT NULL,
    end_date     DATE,                                     -- NULL = poste en cours
    description  VARCHAR(2000),
    CONSTRAINT pk_experience PRIMARY KEY (id),
    CONSTRAINT fk_experience_candidate FOREIGN KEY (candidate_id)
        REFERENCES candidate_profile (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_experience_period CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX ix_experience_candidate ON experience (candidate_id);

CREATE TABLE education (
    id             UUID          NOT NULL DEFAULT uuidv7(),
    candidate_id   UUID          NOT NULL,
    degree         VARCHAR(120)  NOT NULL,
    school         VARCHAR(120)  NOT NULL,
    field_of_study VARCHAR(120),
    start_year     SMALLINT,
    end_year       SMALLINT,
    CONSTRAINT pk_education PRIMARY KEY (id),
    CONSTRAINT fk_education_candidate FOREIGN KEY (candidate_id)
        REFERENCES candidate_profile (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_education_start_year CHECK (start_year BETWEEN 1950 AND 2100),
    CONSTRAINT ck_education_end_year CHECK (end_year BETWEEN 1950 AND 2100),
    CONSTRAINT ck_education_period CHECK (start_year IS NULL OR end_year IS NULL OR end_year >= start_year)
);

CREATE INDEX ix_education_candidate ON education (candidate_id);

CREATE TABLE candidate_skill (
    candidate_id UUID         NOT NULL,
    skill_id     UUID         NOT NULL,
    level        VARCHAR(16)  NOT NULL,
    years        SMALLINT,
    CONSTRAINT pk_candidate_skill PRIMARY KEY (candidate_id, skill_id),   -- interdit les doublons
    CONSTRAINT fk_candidate_skill_candidate FOREIGN KEY (candidate_id)
        REFERENCES candidate_profile (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_candidate_skill_skill FOREIGN KEY (skill_id) REFERENCES skill (id),
    CONSTRAINT ck_candidate_skill_level CHECK (level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT')),
    CONSTRAINT ck_candidate_skill_years CHECK (years BETWEEN 0 AND 50)
);

-- Fusion de compétences (US-51) et filtre « compétence » côté recruteur
CREATE INDEX ix_candidate_skill_skill ON candidate_skill (skill_id);

CREATE TABLE candidate_language (
    candidate_id  UUID        NOT NULL,
    language_code VARCHAR(3)  NOT NULL,                    -- ISO 639 : fr, en, ar, zgh…
    level         VARCHAR(8)  NOT NULL,
    CONSTRAINT pk_candidate_language PRIMARY KEY (candidate_id, language_code),
    CONSTRAINT fk_candidate_language_candidate FOREIGN KEY (candidate_id)
        REFERENCES candidate_profile (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_candidate_language_level CHECK (level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2', 'NATIVE'))
);

CREATE TABLE candidate_link (
    id           UUID          NOT NULL DEFAULT uuidv7(),
    candidate_id UUID          NOT NULL,
    type         VARCHAR(16)   NOT NULL,
    url          VARCHAR(255)  NOT NULL,
    CONSTRAINT pk_candidate_link PRIMARY KEY (id),
    CONSTRAINT fk_candidate_link_candidate FOREIGN KEY (candidate_id)
        REFERENCES candidate_profile (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_candidate_link_type CHECK (type IN ('LINKEDIN', 'GITHUB', 'PORTFOLIO', 'OTHER')),
    CONSTRAINT ck_candidate_link_https CHECK (url LIKE 'https://%')
);

CREATE INDEX ix_candidate_link_candidate ON candidate_link (candidate_id);

CREATE TABLE resume (
    id                UUID          NOT NULL DEFAULT uuidv7(),
    candidate_id      UUID          NOT NULL,
    storage_key       VARCHAR(100)  NOT NULL,              -- nom UUID du fichier, jamais exposé
    original_filename VARCHAR(255)  NOT NULL,              -- nettoyé, seulement affiché
    content_type      VARCHAR(64)   NOT NULL,
    size_bytes        INTEGER       NOT NULL,
    sha256            CHAR(64)      NOT NULL,
    active            BOOLEAN       NOT NULL,
    uploaded_at       TIMESTAMPTZ   NOT NULL,
    deactivated_at    TIMESTAMPTZ,
    CONSTRAINT pk_resume PRIMARY KEY (id),
    CONSTRAINT fk_resume_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_profile (user_id),
    CONSTRAINT uk_resume_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_resume_size CHECK (size_bytes BETWEEN 1 AND 5242880),   -- RG-07 : 5 Mo
    CONSTRAINT ck_resume_deactivation CHECK (active = (deactivated_at IS NULL))
);

-- RG-08 : au plus un CV actif par candidat, garanti même pour deux téléversements simultanés
CREATE UNIQUE INDEX uk_resume_active_candidate ON resume (candidate_id) WHERE active;
