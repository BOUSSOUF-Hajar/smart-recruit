package com.smartrecruit.shared.web;

import com.smartrecruit.shared.domain.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Traduction code métier → statut HTTP et titre, conforme au catalogue de l'analyse fonctionnelle (§7).
 * <p>
 * Les {@code switch} n'ont pas de branche {@code default} : ajouter un code à {@link ErrorCode}
 * sans le traduire ici provoque une erreur de compilation. Le catalogue ne peut pas devenir incomplet.
 */
public final class ErrorCatalog {

    private static final String TYPE_BASE = "https://smartrecruit.dev/errors/";

    private ErrorCatalog() {
    }

    public static HttpStatus status(ErrorCode code) {
        return switch (code) {
            case VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
            case AUTHENTICATION_REQUIRED, AUTH_INVALID_CREDENTIALS, AUTH_TOKEN_EXPIRED, AUTH_REFRESH_INVALID ->
                    HttpStatus.UNAUTHORIZED;
            case PASSWORD_CHANGE_REQUIRED, ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case NOT_ACCEPTABLE -> HttpStatus.NOT_ACCEPTABLE;
            case EMAIL_ALREADY_USED, APPLICATION_ALREADY_EXISTS, JOB_NOT_OPEN, INVALID_STATUS_TRANSITION,
                 INVALID_STATE, OFFER_REQUIRES_COMPLETED_INTERVIEW, INTERVIEW_NOT_ALLOWED,
                 CONCURRENT_MODIFICATION, RESOURCE_IN_USE, LAST_ADMIN -> HttpStatus.CONFLICT;
            case FILE_TOO_LARGE -> HttpStatus.valueOf(413);   // Content Too Large
            case FILE_INVALID_TYPE, UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case FILE_UNREADABLE, PROFILE_INCOMPLETE, REJECTION_REASON_REQUIRED, INVALID_CURRENT_PASSWORD,
                 JOB_NOT_PUBLISHABLE -> HttpStatus.valueOf(422);   // Unprocessable Content
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case AI_DISABLED, AI_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case AI_INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public static String title(ErrorCode code) {
        return switch (code) {
            case VALIDATION_FAILED -> "Données invalides";
            case AUTHENTICATION_REQUIRED -> "Authentification requise";
            case AUTH_INVALID_CREDENTIALS -> "Identifiants incorrects";
            case AUTH_TOKEN_EXPIRED -> "Session expirée";
            case AUTH_REFRESH_INVALID -> "Session invalide";
            case PASSWORD_CHANGE_REQUIRED -> "Changement de mot de passe requis";
            case ACCESS_DENIED -> "Accès refusé";
            case RESOURCE_NOT_FOUND -> "Ressource introuvable";
            case METHOD_NOT_ALLOWED -> "Méthode non autorisée";
            case NOT_ACCEPTABLE -> "Format de réponse non disponible";
            case UNSUPPORTED_MEDIA_TYPE -> "Format de requête non pris en charge";
            case EMAIL_ALREADY_USED -> "Adresse email déjà utilisée";
            case APPLICATION_ALREADY_EXISTS -> "Candidature déjà envoyée";
            case JOB_NOT_OPEN -> "Offre fermée aux candidatures";
            case INVALID_STATUS_TRANSITION -> "Transition de statut interdite";
            case INVALID_STATE -> "Action impossible dans l'état actuel";
            case OFFER_REQUIRES_COMPLETED_INTERVIEW -> "Entretien terminé requis";
            case INTERVIEW_NOT_ALLOWED -> "Entretien impossible à ce stade";
            case CONCURRENT_MODIFICATION -> "Modification concurrente";
            case RESOURCE_IN_USE -> "Ressource utilisée";
            case LAST_ADMIN -> "Dernier administrateur";
            case FILE_TOO_LARGE -> "Fichier trop volumineux";
            case FILE_INVALID_TYPE -> "Type de fichier refusé";
            case FILE_UNREADABLE -> "Fichier illisible";
            case PROFILE_INCOMPLETE -> "Profil incomplet";
            case REJECTION_REASON_REQUIRED -> "Motif de rejet obligatoire";
            case INVALID_CURRENT_PASSWORD -> "Mot de passe actuel incorrect";
            case JOB_NOT_PUBLISHABLE -> "Offre non publiable";
            case RATE_LIMITED -> "Trop de requêtes";
            case AI_DISABLED -> "Assistance IA désactivée";
            case AI_UNAVAILABLE -> "Assistance IA indisponible";
            case AI_INVALID_RESPONSE -> "Réponse IA invalide";
            case INTERNAL_ERROR -> "Erreur interne";
        };
    }

    /** URI de type RFC 9457 : {@code INVALID_STATUS_TRANSITION} → {@code .../errors/invalid-status-transition}. */
    public static String type(ErrorCode code) {
        return TYPE_BASE + code.name().toLowerCase().replace('_', '-');
    }

    /** Code par défaut pour une erreur technique de Spring MVC, à partir de son statut. */
    public static ErrorCode fallbackFor(int status) {
        return switch (status) {
            case 400 -> ErrorCode.VALIDATION_FAILED;
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 406 -> ErrorCode.NOT_ACCEPTABLE;
            case 413 -> ErrorCode.FILE_TOO_LARGE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> status >= 400 && status < 500 ? ErrorCode.VALIDATION_FAILED : ErrorCode.INTERNAL_ERROR;
        };
    }
}
