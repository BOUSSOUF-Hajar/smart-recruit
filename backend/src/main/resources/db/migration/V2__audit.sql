-- V2 : journal d'audit (US-52, F-AUDIT-01).
-- Table en ajout seul (RG-24) : l'application n'exécute jamais d'UPDATE ni de DELETE dessus.
-- La purge à 12 mois (V2 du produit) se fera par une tâche planifiée dédiée.

CREATE TABLE audit_log (
    id             UUID         NOT NULL DEFAULT uuidv7(),
    occurred_at    TIMESTAMPTZ  NOT NULL,
    actor_id       UUID,                       -- clé étrangère vers app_user ajoutée avec le module identité (US-06)
    actor_email    VARCHAR(254),               -- copie au moment de l'action : utile pour une connexion échouée
    action         VARCHAR(64)  NOT NULL,
    resource_type  VARCHAR(32),
    resource_id    UUID,                       -- pas de clé étrangère : la ressource peut être de n'importe quel type
    result         VARCHAR(8)   NOT NULL,
    ip_address     INET,
    correlation_id VARCHAR(64),
    details        JSONB,                      -- jamais de mot de passe, de token ni de contenu de CV
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT ck_audit_log_result CHECK (result IN ('SUCCESS', 'FAILURE'))
);

-- Consultation filtrée du journal par l'admin (US-53)
CREATE INDEX ix_audit_log_occurred_at ON audit_log (occurred_at DESC);
CREATE INDEX ix_audit_log_actor ON audit_log (actor_id, occurred_at DESC);
CREATE INDEX ix_audit_log_action ON audit_log (action, occurred_at DESC);
