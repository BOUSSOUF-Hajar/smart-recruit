package com.smartrecruit.shared.web;

import com.smartrecruit.shared.domain.DomainException;
import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.domain.FieldValidationException;
import com.smartrecruit.shared.domain.RateLimitExceededException;
import com.smartrecruit.shared.web.ApiProblem.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Point unique de traduction des exceptions en réponses {@link ApiProblem}.
 * <p>
 * Les contrôleurs ne contiennent aucun {@code try/catch} : toute exception remonte jusqu'ici.
 * <ul>
 *   <li>exception métier ({@link DomainException}) → statut du catalogue, message destiné à l'utilisateur ;</li>
 *   <li>erreur technique de Spring MVC (corps illisible, méthode non supportée…) → code déduit du statut ;</li>
 *   <li>toute autre exception → 500 INTERNAL_ERROR, sans aucun détail technique dans la réponse,
 *       mais journalisée avec son identifiant de corrélation.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String INTERNAL_ERROR_DETAIL =
            "Une erreur inattendue est survenue. Communiquez l'identifiant de corrélation au support.";

    private final List<KnownConstraint> knownConstraints;

    public GlobalExceptionHandler(ObjectProvider<KnownConstraint> knownConstraints) {
        this.knownConstraints = knownConstraints.orderedStream().toList();
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Object> handleDomain(DomainException ex, HttpServletRequest request) {
        log.debug("Erreur métier {} : {}", ex.code(), ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof RateLimitExceededException limited) {
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, limited.retryAfter().toSeconds())));
        }
        List<FieldViolation> violations = ex instanceof FieldValidationException invalid
                ? invalid.errors().stream().map(e -> new FieldViolation(e.field(), e.message())).toList()
                : List.of();
        return problem(ex.code(), ex.getMessage(), request.getRequestURI(), violations, ex.details(), headers);
    }

    /**
     * Violation d'une contrainte de base : traduite en erreur métier si la contrainte est déclarée
     * par un module ({@link KnownConstraint}), sinon c'est un bug (validation manquante) → 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String cause = String.valueOf(NestedExceptionUtils.getMostSpecificCause(ex).getMessage());
        for (KnownConstraint constraint : knownConstraints) {
            if (cause.contains(constraint.name())) {
                log.info("Contrainte {} violée, traduite en {}", constraint.name(), constraint.code());
                return problem(constraint.code(), constraint.message(), request.getRequestURI(),
                        List.of(), Map.of(), new HttpHeaders());
            }
        }
        return handleUnexpected(ex, request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Object> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.info("Modification concurrente détectée : {}", ex.getMessage());
        return problem(ErrorCode.CONCURRENT_MODIFICATION,
                "Cette ressource a été modifiée par quelqu'un d'autre. Rechargez-la avant de réessayer.",
                request.getRequestURI(), List.of(), Map.of(), new HttpHeaders());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Seul endroit où la trace complète est écrite : dans les logs, jamais dans la réponse
        log.error("Erreur inattendue sur {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(ErrorCode.INTERNAL_ERROR, INTERNAL_ERROR_DETAIL, request.getRequestURI(),
                List.of(), Map.of(), new HttpHeaders());
    }

    /**
     * Toutes les exceptions standard de Spring MVC passent par ici (méthode héritée) :
     * on remplace le corps par défaut par notre format.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = ErrorCatalog.fallbackFor(statusCode.value());
        List<FieldViolation> violations = violationsOf(ex);
        String detail = switch (ex) {
            case MethodArgumentNotValidException e -> "Un ou plusieurs champs sont invalides.";
            case HandlerMethodValidationException e -> "Un ou plusieurs paramètres sont invalides.";
            case HttpMessageNotReadableException e -> "Le corps de la requête est absent ou mal formé.";
            case MaxUploadSizeExceededException e -> "Le fichier dépasse la taille maximale de 5 Mo.";
            default -> code == ErrorCode.INTERNAL_ERROR ? INTERNAL_ERROR_DETAIL : ErrorCatalog.title(code) + ".";
        };
        if (code == ErrorCode.INTERNAL_ERROR) {
            log.error("Erreur technique {}", statusCode.value(), ex);
        } else {
            log.debug("Requête refusée ({}) : {}", statusCode.value(), ex.getMessage());
        }
        return problem(code, detail, uriOf(request), violations, Map.of(), headers, statusCode.value());
    }

    private static List<FieldViolation> violationsOf(Exception ex) {
        List<FieldViolation> violations = new ArrayList<>();
        if (ex instanceof MethodArgumentNotValidException e) {
            BindingResult result = e.getBindingResult();
            result.getFieldErrors().forEach(f -> violations.add(new FieldViolation(f.getField(), f.getDefaultMessage())));
            result.getGlobalErrors().forEach(g -> violations.add(new FieldViolation(g.getObjectName(), g.getDefaultMessage())));
        } else if (ex instanceof HandlerMethodValidationException e) {
            e.getParameterValidationResults().forEach(r -> r.getResolvableErrors().forEach(err ->
                    violations.add(new FieldViolation(r.getMethodParameter().getParameterName(), err.getDefaultMessage()))));
        }
        return violations;
    }

    private static String uriOf(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : null;
    }

    private static ResponseEntity<Object> problem(ErrorCode code, String detail, String instance,
                                                  List<FieldViolation> violations, Map<String, Object> extensions,
                                                  HttpHeaders headers) {
        return problem(code, detail, instance, violations, extensions, headers, ErrorCatalog.status(code).value());
    }

    private static ResponseEntity<Object> problem(ErrorCode code, String detail, String instance,
                                                  List<FieldViolation> violations, Map<String, Object> extensions,
                                                  HttpHeaders headers, int status) {
        ApiProblem body = new ApiProblem(code, status, detail, instance, CorrelationIdFilter.current(),
                violations, extensions);
        HttpHeaders responseHeaders = new HttpHeaders(headers);   // conserve par ex. l'en-tête Allow d'un 405
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return ResponseEntity.status(status).headers(responseHeaders).body(body);
    }
}
