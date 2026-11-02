package com.smartrecruit.application.domain;

import com.smartrecruit.application.api.ApplicationStatus;
import com.smartrecruit.shared.domain.DomainException;
import com.smartrecruit.shared.domain.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static com.smartrecruit.application.api.ApplicationStatus.HIRED;
import static com.smartrecruit.application.api.ApplicationStatus.INTERVIEW;
import static com.smartrecruit.application.api.ApplicationStatus.OFFER;
import static com.smartrecruit.application.api.ApplicationStatus.REJECTED;
import static com.smartrecruit.application.api.ApplicationStatus.SCREENING;
import static com.smartrecruit.application.api.ApplicationStatus.SHORTLISTED;
import static com.smartrecruit.application.api.ApplicationStatus.SUBMITTED;
import static com.smartrecruit.application.api.ApplicationStatus.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US-32 : les 64 couples (statut actuel, statut cible) de la table D4.1, sans base ni Spring. */
class ApplicationWorkflowTest {

    /** Les 15 transitions autorisées, écrites à la main pour ne pas recopier le code testé. */
    private static final Set<String> ALLOWED = Set.of(
            "SUBMITTED>SCREENING", "SCREENING>SHORTLISTED", "SHORTLISTED>INTERVIEW", "INTERVIEW>OFFER", "OFFER>HIRED",
            "SUBMITTED>REJECTED", "SCREENING>REJECTED", "SHORTLISTED>REJECTED", "INTERVIEW>REJECTED", "OFFER>REJECTED",
            "SUBMITTED>WITHDRAWN", "SCREENING>WITHDRAWN", "SHORTLISTED>WITHDRAWN", "INTERVIEW>WITHDRAWN",
            "OFFER>WITHDRAWN");

    static Stream<Arguments> allPairs() {
        return Arrays.stream(ApplicationStatus.values())
                .flatMap(from -> Arrays.stream(ApplicationStatus.values()).map(to -> Arguments.of(from, to)));
    }

    /**
     * Chaque couple est essayé par l'acteur légitime (le candidat pour WITHDRAWN, le recruteur sinon),
     * avec un motif et un entretien terminé : seule la table décide.
     */
    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("allPairs")
    void onlyTheFifteenTransitionsOfTheTableAreAllowed(ApplicationStatus from, ApplicationStatus to) {
        TransitionContext context = to == WITHDRAWN
                ? TransitionContext.candidate()
                : TransitionContext.recruiter(RejectionReason.PROFILE_MISMATCH, true);

        if (ALLOWED.contains(from + ">" + to)) {
            assertThatCode(() -> ApplicationWorkflow.check(from, to, context)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> ApplicationWorkflow.check(from, to, context))
                    .isInstanceOf(DomainException.class)
                    .extracting(e -> ((DomainException) e).code())
                    .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    @Test
    void theTableHasFifteenTransitionsAndThreeFinalStatuses() {
        long transitions = Arrays.stream(ApplicationStatus.values()).mapToLong(s -> s.allowedTargets().size()).sum();
        assertThat(transitions).isEqualTo(15);
        assertThat(Arrays.stream(ApplicationStatus.values()).filter(ApplicationStatus::isFinal))
                .containsExactlyInAnyOrder(HIRED, REJECTED, WITHDRAWN);
    }

    @Test
    void onlyTheCandidateWithdrawsAndTheCandidateOnlyWithdraws() {
        assertThatThrownBy(() -> ApplicationWorkflow.check(SCREENING, WITHDRAWN,
                TransitionContext.recruiter(null, false)))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
        assertThatThrownBy(() -> ApplicationWorkflow.check(SUBMITTED, SCREENING, TransitionContext.candidate()))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    /** US-33, RG-14 */
    @Test
    void rejectionRequiresAReason() {
        assertThatThrownBy(() -> ApplicationWorkflow.check(SCREENING, REJECTED, TransitionContext.recruiter(null, false)))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.REJECTION_REASON_REQUIRED);
    }

    /** RG-33 */
    @Test
    void offerRequiresACompletedInterview() {
        assertThatThrownBy(() -> ApplicationWorkflow.check(INTERVIEW, OFFER, TransitionContext.recruiter(null, false)))
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.OFFER_REQUIRES_COMPLETED_INTERVIEW);
    }

    @Test
    void recruiterChoicesNeverContainWithdrawalNorAnOfferWithoutInterview() {
        assertThat(ApplicationWorkflow.recruiterChoices(SHORTLISTED, false)).containsExactlyInAnyOrder(INTERVIEW, REJECTED);
        assertThat(ApplicationWorkflow.recruiterChoices(INTERVIEW, false)).containsExactly(REJECTED);
        assertThat(ApplicationWorkflow.recruiterChoices(INTERVIEW, true)).containsExactlyInAnyOrder(OFFER, REJECTED);
        assertThat(ApplicationWorkflow.recruiterChoices(HIRED, true)).isEmpty();
    }

    @Test
    void rejectionKeepsReasonAndCommentAndWithdrawalKeepsTheCandidateComment() {
        Instant now = Instant.parse("2026-10-22T10:00:00Z");
        Application rejected = Application.submit(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now);
        StatusChange change = rejected.moveTo(REJECTED,
                TransitionContext.recruiter(RejectionReason.INSUFFICIENT_EXPERIENCE, false), "  Trop junior. ", now);

        assertThat(change).isEqualTo(new StatusChange(SUBMITTED, REJECTED));
        assertThat(rejected.getRejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_EXPERIENCE);
        assertThat(rejected.getRejectionComment()).isEqualTo("Trop junior.");

        Application withdrawn = Application.submit(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now);
        withdrawn.moveTo(WITHDRAWN, TransitionContext.candidate(), "J'ai accepté une autre offre", now);
        assertThat(withdrawn.getWithdrawalComment()).isEqualTo("J'ai accepté une autre offre");
        assertThat(withdrawn.getRejectionReason()).isNull();
    }
}
