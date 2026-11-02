package com.smartrecruit.audit.web;

import com.smartrecruit.audit.api.AuditResult;
import com.smartrecruit.audit.infrastructure.AuditLogSearch;
import com.smartrecruit.audit.infrastructure.AuditLogSearch.Entry;
import com.smartrecruit.shared.domain.FieldValidationException;
import com.smartrecruit.shared.web.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code /admin/audit-logs} (US-53) : lecture seule. Aucun endpoint ne modifie ni ne supprime une
 * entrée, et la base le refuse aussi (déclencheur {@code trg_audit_log_immutable}).
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
class AdminAuditController {

    private final AuditLogSearch search;

    AdminAuditController(AuditLogSearch search) {
        this.search = search;
    }

    /** {@code from} inclus, {@code to} exclu, en ISO 8601 avec décalage (le frontend envoie ses jours locaux). */
    @GetMapping
    PageResponse<Entry> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                             @RequestParam(required = false) UUID actorId,
                             @RequestParam(required = false) String action,
                             @RequestParam(required = false) AuditResult result,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "50") int size) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new FieldValidationException("to", "doit être postérieure au début de la période");
        }
        int pageNumber = PageResponse.page(page);
        int pageSize = PageResponse.size(size);
        AuditLogSearch.Result found = search.search(new AuditLogSearch.Criteria(from, to, actorId, action,
                result == null ? null : result.name()), pageNumber, pageSize);
        return PageResponse.of(found.entries(), pageNumber, pageSize, found.total());
    }

    @GetMapping("/actions")
    List<String> actions() {
        return search.actions();
    }
}
