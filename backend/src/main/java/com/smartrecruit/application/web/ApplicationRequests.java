package com.smartrecruit.application.web;

import com.smartrecruit.application.api.ApplicationStatus;
import com.smartrecruit.application.domain.Application;
import com.smartrecruit.application.domain.RejectionReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corps des requêtes du module ({@code docs/api.md} §5 et §7). */
final class ApplicationRequests {

    private ApplicationRequests() {
    }

    /** {@code rejectionReason} : obligatoire pour REJECTED (vérifié par le workflow, 422), ignoré sinon. */
    record ChangeStatusRequest(@NotNull ApplicationStatus targetStatus, RejectionReason rejectionReason,
                               @Size(max = Application.MAX_COMMENT) String comment, @NotNull Long version) {
    }

    record WithdrawRequest(@Size(max = Application.MAX_COMMENT) String comment) {
    }
}
