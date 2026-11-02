-- V13 : assistance IA (US-40 à US-43).

CREATE TABLE ai_task (
    id             UUID          NOT NULL DEFAULT uuidv7(),
    type           VARCHAR(16)   NOT NULL,
    application_id UUID          NOT NULL,          -- contexte et contrôle d'accès
    company_id     UUID          NOT NULL,          -- copie immuable : contrôle d'accès sans joindre les candidatures
    resume_id      UUID          NOT NULL,          -- version du CV analysée (celle de la candidature)
    requested_by   UUID          NOT NULL,          -- RG-27
    status         VARCHAR(16)   NOT NULL,
    error_code     VARCHAR(32),
    created_at     TIMESTAMPTZ   NOT NULL,
    started_at     TIMESTAMPTZ,
    finished_at    TIMESTAMPTZ,
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_task PRIMARY KEY (id),
    CONSTRAINT fk_ai_task_application FOREIGN KEY (application_id) REFERENCES application (id),
    CONSTRAINT fk_ai_task_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_ai_task_resume FOREIGN KEY (resume_id) REFERENCES resume (id),
    CONSTRAINT fk_ai_task_requested_by FOREIGN KEY (requested_by) REFERENCES app_user (id),
    CONSTRAINT ck_ai_task_type CHECK (type IN ('RESUME_ANALYSIS', 'MATCHING')),
    CONSTRAINT ck_ai_task_status CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')),
    CONSTRAINT ck_ai_task_error CHECK ((status = 'FAILED') = (error_code IS NOT NULL)),
    CONSTRAINT ck_ai_task_error_code CHECK (error_code IN ('AI_UNAVAILABLE', 'AI_INVALID_RESPONSE', 'FILE_UNREADABLE'))
);

-- Rattrapage des tâches orphelines au démarrage ; tâche déjà en cours pour un dossier
CREATE INDEX ix_ai_task_active ON ai_task (application_id, type) WHERE status IN ('PENDING', 'RUNNING');

CREATE TABLE resume_analysis (
    id             UUID          NOT NULL DEFAULT uuidv7(),
    resume_id      UUID          NOT NULL,          -- une analyse vaut pour une version de CV, quelle que soit l'offre
    task_id        UUID          NOT NULL,
    model_name     VARCHAR(80)   NOT NULL,
    prompt_version VARCHAR(32)   NOT NULL,
    input_sha256   CHAR(64)      NOT NULL,          -- empreinte du texte pseudonymisé envoyé
    result         JSONB         NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_resume_analysis PRIMARY KEY (id),
    CONSTRAINT fk_resume_analysis_resume FOREIGN KEY (resume_id) REFERENCES resume (id),
    CONSTRAINT fk_resume_analysis_task FOREIGN KEY (task_id) REFERENCES ai_task (id),
    CONSTRAINT uk_resume_analysis_task UNIQUE (task_id)
);

-- Dernière analyse d'un CV
CREATE INDEX ix_resume_analysis_resume ON resume_analysis (resume_id, created_at DESC);

CREATE TABLE match_result (
    id                 UUID           NOT NULL DEFAULT uuidv7(),
    application_id     UUID           NOT NULL,
    resume_analysis_id UUID,                          -- vide si le CV n'était pas analysable (calcul sur le profil seul)
    task_id            UUID           NOT NULL,
    score              SMALLINT       NOT NULL,       -- calculé par le backend, jamais par le modèle
    required_coverage  NUMERIC(4, 3)  NOT NULL,
    preferred_coverage NUMERIC(4, 3),                 -- vide si l'offre n'a aucune compétence souhaitée
    experience_fit     NUMERIC(4, 3)  NOT NULL,
    formula_version    VARCHAR(16)    NOT NULL,
    matched_skills     JSONB          NOT NULL,
    missing_skills     JSONB          NOT NULL,
    explanation        JSONB          NOT NULL,       -- points forts, points faibles, synthèse, avertissements
    model_name         VARCHAR(80)    NOT NULL,
    prompt_version     VARCHAR(32)    NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_match_result PRIMARY KEY (id),
    CONSTRAINT fk_match_result_application FOREIGN KEY (application_id) REFERENCES application (id),
    CONSTRAINT fk_match_result_analysis FOREIGN KEY (resume_analysis_id) REFERENCES resume_analysis (id),
    CONSTRAINT fk_match_result_task FOREIGN KEY (task_id) REFERENCES ai_task (id),
    CONSTRAINT uk_match_result_task UNIQUE (task_id),
    CONSTRAINT ck_match_result_score CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_match_result_required CHECK (required_coverage BETWEEN 0 AND 1),
    CONSTRAINT ck_match_result_preferred CHECK (preferred_coverage BETWEEN 0 AND 1),
    CONSTRAINT ck_match_result_experience CHECK (experience_fit BETWEEN 0 AND 1)
);

-- Dernier matching d'une candidature
CREATE INDEX ix_match_result_application ON match_result (application_id, created_at DESC);

-- Date du dernier score, à côté de sa copie (last_match_score, V11) ; la source reste match_result
ALTER TABLE application ADD COLUMN last_matched_at TIMESTAMPTZ;
ALTER TABLE application ADD CONSTRAINT ck_application_last_match
    CHECK ((last_match_score IS NULL) = (last_matched_at IS NULL));
