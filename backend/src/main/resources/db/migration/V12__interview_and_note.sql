-- V12 : entretiens (US-35 à US-38) et notes internes (US-39).

CREATE TABLE interview (
    id                     UUID           NOT NULL DEFAULT uuidv7(),
    application_id         UUID           NOT NULL,
    -- Copies immuables de la candidature, pour l'agenda de l'entreprise et son filtre par offre
    -- sans joindre les tables du module Candidatures
    company_id             UUID           NOT NULL,
    job_offer_id           UUID           NOT NULL,
    scheduled_by           UUID           NOT NULL,
    starts_at              TIMESTAMPTZ    NOT NULL,          -- UTC (RG-32)
    duration_minutes       SMALLINT       NOT NULL,
    type                   VARCHAR(16)    NOT NULL,
    meeting_url            VARCHAR(500),
    address                VARCHAR(255),
    candidate_instructions VARCHAR(1000),                    -- visible par le candidat
    status                 VARCHAR(16)    NOT NULL,
    cancellation_reason    VARCHAR(500),
    feedback_rating        VARCHAR(8),                       -- interne (RG-23)
    feedback_comment       VARCHAR(2000),                    -- interne (RG-23)
    completed_at           TIMESTAMPTZ,
    created_at             TIMESTAMPTZ    NOT NULL,
    updated_at             TIMESTAMPTZ    NOT NULL,
    version                BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_interview PRIMARY KEY (id),
    CONSTRAINT fk_interview_application FOREIGN KEY (application_id) REFERENCES application (id),
    CONSTRAINT fk_interview_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_interview_job_offer FOREIGN KEY (job_offer_id) REFERENCES job_offer (id),
    CONSTRAINT fk_interview_scheduled_by FOREIGN KEY (scheduled_by) REFERENCES app_user (id),
    CONSTRAINT ck_interview_duration CHECK (duration_minutes BETWEEN 15 AND 240),
    CONSTRAINT ck_interview_type CHECK (type IN ('PHONE', 'VIDEO', 'ON_SITE', 'TECHNICAL')),
    CONSTRAINT ck_interview_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED')),
    -- Règles de lieu : lien https pour une visioconférence, adresse pour un entretien sur place
    CONSTRAINT ck_interview_video_url CHECK (type <> 'VIDEO' OR meeting_url LIKE 'https://%'),
    CONSTRAINT ck_interview_address CHECK (type <> 'ON_SITE' OR address IS NOT NULL),
    CONSTRAINT ck_interview_rating CHECK (feedback_rating IN ('POSITIVE', 'NEUTRAL', 'NEGATIVE')),
    -- Clôture : avis et date de clôture présents si et seulement si l'entretien est terminé
    CONSTRAINT ck_interview_completion CHECK (
        (status = 'COMPLETED') = (feedback_rating IS NOT NULL AND completed_at IS NOT NULL))
);

-- Entretiens d'un dossier ; RG-33 (au moins un entretien terminé)
CREATE INDEX ix_interview_application ON interview (application_id, status);
-- Agenda de l'entreprise, à venir d'abord
CREATE INDEX ix_interview_company_starts ON interview (company_id, starts_at);

CREATE TABLE interview_participant (
    interview_id UUID NOT NULL,
    user_id      UUID NOT NULL,              -- recruteur de la même entreprise, vérifié par l'application
    CONSTRAINT pk_interview_participant PRIMARY KEY (interview_id, user_id),
    CONSTRAINT fk_interview_participant_interview FOREIGN KEY (interview_id)
        REFERENCES interview (id) ON DELETE CASCADE,
    CONSTRAINT fk_interview_participant_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

-- Agenda d'un recruteur (« mes entretiens »)
CREATE INDEX ix_interview_participant_user ON interview_participant (user_id);

CREATE TABLE note (
    id             UUID           NOT NULL DEFAULT uuidv7(),
    application_id UUID           NOT NULL,
    author_id      UUID           NOT NULL,      -- seul l'auteur modifie ou supprime
    content        VARCHAR(5000)  NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,      -- « modifiée » si postérieure à created_at
    deleted_at     TIMESTAMPTZ,                  -- suppression logique
    version        BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_note PRIMARY KEY (id),
    CONSTRAINT fk_note_application FOREIGN KEY (application_id) REFERENCES application (id),
    CONSTRAINT fk_note_author FOREIGN KEY (author_id) REFERENCES app_user (id),
    CONSTRAINT ck_note_content CHECK (char_length(btrim(content)) BETWEEN 1 AND 5000),
    CONSTRAINT ck_note_dates CHECK (updated_at >= created_at)
);

-- Notes visibles d'un dossier
CREATE INDEX ix_note_application ON note (application_id, created_at) WHERE deleted_at IS NULL;
