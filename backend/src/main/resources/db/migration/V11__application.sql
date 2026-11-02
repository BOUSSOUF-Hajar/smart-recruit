-- V11 : module Candidatures (US-27 à US-34, US-19).

CREATE TABLE application (
    id                 UUID           NOT NULL DEFAULT uuidv7(),
    job_offer_id       UUID           NOT NULL,
    candidate_id       UUID           NOT NULL,
    resume_id          UUID           NOT NULL,        -- version du CV figée à l'envoi (RG-12)
    status             VARCHAR(16)    NOT NULL,
    rejection_reason   VARCHAR(32),
    rejection_comment  VARCHAR(1000),                  -- interne, jamais exposé au candidat (RG-14, RG-23)
    withdrawal_comment VARCHAR(1000),                  -- saisi par le candidat
    last_match_score   SMALLINT,                       -- copie du dernier matching (phase 8), pour filtrer la liste
    submitted_at       TIMESTAMPTZ    NOT NULL,
    status_changed_at  TIMESTAMPTZ    NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL,
    version            BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_application PRIMARY KEY (id),
    -- RESTRICT (défaut) : une offre ou un CV référencés par une candidature ne se suppriment pas
    CONSTRAINT fk_application_job_offer FOREIGN KEY (job_offer_id) REFERENCES job_offer (id),
    CONSTRAINT fk_application_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_profile (user_id),
    CONSTRAINT fk_application_resume FOREIGN KEY (resume_id) REFERENCES resume (id),
    -- RG-10 : une seule candidature par candidat et par offre, quel que soit son statut, même sous concurrence
    CONSTRAINT uk_application_job_candidate UNIQUE (job_offer_id, candidate_id),
    CONSTRAINT ck_application_status CHECK (status IN ('SUBMITTED', 'SCREENING', 'SHORTLISTED', 'INTERVIEW',
                                                       'OFFER', 'HIRED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT ck_application_rejection_reason CHECK (rejection_reason IN ('PROFILE_MISMATCH',
        'INSUFFICIENT_EXPERIENCE', 'POSITION_FILLED', 'CANDIDATE_UNRESPONSIVE', 'OTHER')),
    -- RG-14 : pas de rejet sans motif ; un motif n'existe que pour un rejet
    CONSTRAINT ck_application_rejection CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL)),
    CONSTRAINT ck_application_score CHECK (last_match_score BETWEEN 0 AND 100)
);

-- uk_application_job_candidate sert aussi « candidatures d'une offre » ; celui-ci sert les compteurs par statut
CREATE INDEX ix_application_job_status ON application (job_offer_id, status);
-- « Mes candidatures »
CREATE INDEX ix_application_candidate ON application (candidate_id, submitted_at DESC);
-- Un CV est-il joint à une candidature ? (US-19) ; PostgreSQL n'indexe pas les clés étrangères
CREATE INDEX ix_application_resume ON application (resume_id);

-- Journal des événements d'une candidature (F-HIST-01). Les entretiens, notes et analyses IA
-- des phases 7 et 8 y ajouteront leurs propres types.
CREATE TABLE application_event (
    id             UUID          NOT NULL DEFAULT uuidv7(),
    application_id UUID          NOT NULL,
    event_type     VARCHAR(32)   NOT NULL,
    from_status    VARCHAR(16),                       -- pour STATUS_CHANGED
    to_status      VARCHAR(16),                       -- pour CREATED et STATUS_CHANGED
    actor_id       UUID,                              -- vide si l'action vient du système
    details        JSONB,                             -- motif de rejet, dates d'entretien…
    occurred_at    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_application_event PRIMARY KEY (id),
    CONSTRAINT fk_application_event_application FOREIGN KEY (application_id) REFERENCES application (id),
    CONSTRAINT fk_application_event_actor FOREIGN KEY (actor_id) REFERENCES app_user (id),
    CONSTRAINT ck_application_event_type CHECK (event_type IN ('CREATED', 'STATUS_CHANGED', 'NOTE_ADDED',
        'INTERVIEW_SCHEDULED', 'INTERVIEW_RESCHEDULED', 'INTERVIEW_CANCELLED', 'INTERVIEW_COMPLETED',
        'AI_ANALYSIS_REQUESTED')),
    CONSTRAINT ck_application_event_statuses CHECK (
        (event_type = 'CREATED' AND from_status IS NULL AND to_status IS NOT NULL)
        OR (event_type = 'STATUS_CHANGED' AND from_status IS NOT NULL AND to_status IS NOT NULL)
        OR (event_type NOT IN ('CREATED', 'STATUS_CHANGED') AND from_status IS NULL AND to_status IS NULL))
);

-- Chronologie d'un dossier
CREATE INDEX ix_application_event_application ON application_event (application_id, occurred_at DESC);
-- Embauches de la période (indicateurs, analyse fonctionnelle §6.3)
CREATE INDEX ix_application_event_hires ON application_event (to_status, occurred_at)
    WHERE event_type = 'STATUS_CHANGED';

-- RG-24 : l'historique est immuable. L'application n'a aucune requête d'écriture dessus hormis
-- l'insertion ; ce déclencheur refuse en plus toute modification venue d'ailleurs (script, console).
CREATE FUNCTION forbid_application_event_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'application_event est en ajout seul (RG-24)' USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER trg_application_event_immutable
    BEFORE UPDATE OR DELETE ON application_event
    FOR EACH ROW EXECUTE FUNCTION forbid_application_event_change();
