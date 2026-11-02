package com.smartrecruit.shared.security;

/**
 * Noms des claims de l'access token. Le token ne contient aucune donnée personnelle :
 * ni email, ni nom (US-07, critère 4).
 */
public final class JwtClaims {

    /** Rôle unique de l'utilisateur. */
    public static final String ROLE = "role";
    /** Entreprise d'un recruteur, absente pour les autres rôles. */
    public static final String COMPANY_ID = "companyId";
    /** Présent et vrai tant que le mot de passe temporaire n'a pas été changé (US-11). */
    public static final String PASSWORD_CHANGE_REQUIRED = "pwdChange";
    /** Version de session du compte à l'émission (US-48) ; absente quand elle vaut 0. */
    public static final String SESSION_VERSION = "sv";

    private JwtClaims() {
    }
}
