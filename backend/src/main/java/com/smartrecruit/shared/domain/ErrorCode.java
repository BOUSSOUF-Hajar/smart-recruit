package com.smartrecruit.shared.domain;

/**
 * Codes d'erreur stables de l'API (catalogue de l'analyse fonctionnelle, section 7).
 * <p>
 * Le domaine ne connaît que le code ; le statut HTTP et le titre sont choisis par la couche web
 * ({@code ErrorCatalog}). Le frontend choisit son message à partir de ce code.
 */
public enum ErrorCode {

    // 400
    VALIDATION_FAILED,
    // 401
    AUTHENTICATION_REQUIRED,
    AUTH_INVALID_CREDENTIALS,
    AUTH_TOKEN_EXPIRED,
    AUTH_REFRESH_INVALID,
    // 403
    PASSWORD_CHANGE_REQUIRED,
    ACCESS_DENIED,
    // 404
    RESOURCE_NOT_FOUND,
    // 405, 406, 415 : erreurs techniques du protocole HTTP
    METHOD_NOT_ALLOWED,
    NOT_ACCEPTABLE,
    UNSUPPORTED_MEDIA_TYPE,
    // 409
    EMAIL_ALREADY_USED,
    APPLICATION_ALREADY_EXISTS,
    JOB_NOT_OPEN,
    INVALID_STATUS_TRANSITION,
    INVALID_STATE,
    OFFER_REQUIRES_COMPLETED_INTERVIEW,
    INTERVIEW_NOT_ALLOWED,
    CONCURRENT_MODIFICATION,
    RESOURCE_IN_USE,
    LAST_ADMIN,
    // 413, 415, 422
    FILE_TOO_LARGE,
    FILE_INVALID_TYPE,
    FILE_UNREADABLE,
    PROFILE_INCOMPLETE,
    REJECTION_REASON_REQUIRED,
    INVALID_CURRENT_PASSWORD,
    JOB_NOT_PUBLISHABLE,
    // 429
    RATE_LIMITED,
    // 5xx
    AI_DISABLED,
    AI_UNAVAILABLE,
    AI_INVALID_RESPONSE,
    INTERNAL_ERROR
}
