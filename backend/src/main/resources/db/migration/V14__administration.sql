-- V14 : administration (US-46, US-48 à US-51, US-53).

-- US-48 : désactiver un compte ou changer l'entreprise d'un recruteur invalide tout de suite ses
-- access tokens. Chaque token porte la version de session du compte au moment de son émission ;
-- un token d'une version antérieure est refusé (claim « sv », 0 par défaut).
ALTER TABLE app_user ADD COLUMN session_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_session_version CHECK (session_version >= 0);

-- RG-29 : compter et verrouiller les admins actifs ; filtres de la liste des comptes
CREATE INDEX ix_app_user_role_enabled ON app_user (role, enabled);

-- US-53 : filtre du journal par résultat (les échecs d'abord recherchés)
CREATE INDEX ix_audit_log_result ON audit_log (result, occurred_at DESC);

-- US-50 : nombre d'offres par catégorie, refus de supprimer une catégorie utilisée (RG-30)
CREATE INDEX ix_job_offer_category ON job_offer (category_id);

-- US-48 : sessions fermées parce que l'admin a changé l'entreprise du recruteur
ALTER TABLE refresh_token DROP CONSTRAINT ck_refresh_token_revocation_reason;
ALTER TABLE refresh_token ADD CONSTRAINT ck_refresh_token_revocation_reason
    CHECK (revocation_reason IN ('ROTATED', 'LOGOUT', 'PASSWORD_CHANGED', 'REUSE_DETECTED', 'ACCOUNT_DISABLED',
                                 'COMPANY_CHANGED'));

-- US-53, RG-24 : le journal d'audit est consultable, jamais modifiable. L'application n'a aucune
-- requête d'écriture dessus hormis l'insertion ; ce déclencheur refuse en plus toute modification
-- venue d'ailleurs (script, console). Seule exception : la clé étrangère vers app_user
-- (ON DELETE SET NULL) peut effacer l'auteur si un compte est un jour supprimé (droit à l'effacement).
-- La purge à 12 mois (V2) passera par une fonction dédiée qui désactive le déclencheur le temps de la purge.
CREATE FUNCTION forbid_audit_log_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.actor_id IS NULL AND OLD.actor_id IS NOT NULL
       AND (NEW.id, NEW.occurred_at, NEW.actor_email, NEW.action, NEW.resource_type, NEW.resource_id,
            NEW.result, NEW.ip_address, NEW.correlation_id, NEW.details::text)
           IS NOT DISTINCT FROM
           (OLD.id, OLD.occurred_at, OLD.actor_email, OLD.action, OLD.resource_type, OLD.resource_id,
            OLD.result, OLD.ip_address, OLD.correlation_id, OLD.details::text) THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'audit_log est en ajout seul (RG-24)' USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER trg_audit_log_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION forbid_audit_log_change();
