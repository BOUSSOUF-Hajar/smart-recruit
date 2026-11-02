package com.smartrecruit.shared.security;

/** Les trois rôles de la V1 : un utilisateur en a exactement un (RG-02). */
public enum Role {
    ADMIN,
    RECRUITER,
    CANDIDATE;

    /** Autorité Spring Security correspondante, utilisée par {@code hasRole(...)}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
