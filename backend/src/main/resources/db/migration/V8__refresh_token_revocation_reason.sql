-- V8 : cause de la révocation d'un refresh token (correction de la phase 3).
-- Seul un jeton remplacé par rotation (ROTATED) signale un vol s'il est présenté à nouveau.
-- Un jeton fermé par une déconnexion ou un changement de mot de passe est simplement refusé :
-- sinon, le téléphone resté connecté déconnecterait l'utilisateur qui vient de changer son mot de passe.

ALTER TABLE refresh_token ADD COLUMN revocation_reason VARCHAR(16);

-- Jetons déjà révoqués : traités comme des rotations, le cas le plus strict
UPDATE refresh_token SET revocation_reason = 'ROTATED' WHERE revoked_at IS NOT NULL;

ALTER TABLE refresh_token
    ADD CONSTRAINT ck_refresh_token_revocation_reason
        CHECK (revocation_reason IN ('ROTATED', 'LOGOUT', 'PASSWORD_CHANGED', 'REUSE_DETECTED', 'ACCOUNT_DISABLED')),
    ADD CONSTRAINT ck_refresh_token_revocation
        CHECK ((revoked_at IS NULL) = (revocation_reason IS NULL));
