package com.smartrecruit.job.domain;

import com.smartrecruit.referential.api.SkillLevel;
import com.smartrecruit.shared.domain.BusinessRuleViolationException;
import com.smartrecruit.shared.domain.DomainException;
import com.smartrecruit.shared.domain.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US-23 : chaque transition du diagramme d'états, permise ou refusée. */
class JobOfferLifecycleTest {

    private static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");
    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");
    private static final LocalDate IN_30_DAYS = LocalDate.parse("2026-11-18");

    /**
     * Pour chaque statut de départ et chaque action : {@code OK} si la transition est permise,
     * sinon le code d'erreur attendu.
     */
    @ParameterizedTest(name = "{0} --{1}--> {2}")
    @CsvSource(textBlock = """
            DRAFT,     publish,   OK
            DRAFT,     unpublish, INVALID_STATUS_TRANSITION
            DRAFT,     close,     INVALID_STATUS_TRANSITION
            DRAFT,     reopen,    INVALID_STATUS_TRANSITION
            DRAFT,     archive,   INVALID_STATUS_TRANSITION
            DRAFT,     delete,    OK
            PUBLISHED, publish,   INVALID_STATUS_TRANSITION
            PUBLISHED, unpublish, OK
            PUBLISHED, close,     OK
            PUBLISHED, reopen,    INVALID_STATUS_TRANSITION
            PUBLISHED, archive,   INVALID_STATUS_TRANSITION
            PUBLISHED, delete,    RESOURCE_IN_USE
            CLOSED,    publish,   INVALID_STATUS_TRANSITION
            CLOSED,    unpublish, INVALID_STATUS_TRANSITION
            CLOSED,    close,     INVALID_STATUS_TRANSITION
            CLOSED,    reopen,    OK
            CLOSED,    archive,   OK
            CLOSED,    delete,    RESOURCE_IN_USE
            ARCHIVED,  publish,   INVALID_STATUS_TRANSITION
            ARCHIVED,  unpublish, INVALID_STATUS_TRANSITION
            ARCHIVED,  close,     INVALID_STATUS_TRANSITION
            ARCHIVED,  reopen,    INVALID_STATUS_TRANSITION
            ARCHIVED,  archive,   INVALID_STATUS_TRANSITION
            ARCHIVED,  delete,    RESOURCE_IN_USE
            """)
    void onlyTheTransitionsOfTheStateDiagramAreAllowed(JobStatus from, String action, String expected) {
        JobOffer offer = offerIn(from);
        Consumer<JobOffer> transition = switch (action) {
            case "publish" -> o -> o.publish(IN_30_DAYS, CASABLANCA, NOW);
            case "unpublish" -> o -> o.unpublish(false);
            case "close" -> JobOffer::close;
            case "reopen" -> o -> o.reopen(IN_30_DAYS, CASABLANCA, NOW);
            case "archive" -> JobOffer::archive;
            case "delete" -> JobOffer::checkDeletable;
            default -> throw new IllegalArgumentException(action);
        };

        if (expected.equals("OK")) {
            assertThatCode(() -> transition.accept(offer)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> transition.accept(offer))
                    .isInstanceOf(DomainException.class)
                    .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.valueOf(expected)));
            assertThat(offer.getStatus()).isEqualTo(from);   // rien n'a changé
        }
    }

    @Test
    void publicationSetsTheDatesAndExpiresAtEndOfDayInTheCompanyTimezone() {
        JobOffer offer = offerIn(JobStatus.DRAFT);

        offer.publish(IN_30_DAYS, CASABLANCA, NOW);

        assertThat(offer.getStatus()).isEqualTo(JobStatus.PUBLISHED);
        assertThat(offer.getPublishedAt()).isEqualTo(NOW);
        assertThat(offer.getExpiresAt()).isEqualTo(IN_30_DAYS.atTime(23, 59, 59).atZone(CASABLANCA).toInstant());
        assertThat(offer.isOpen(NOW)).isTrue();
    }

    @Test
    void expiryIsTheEndOfTheDayInTheGivenTimezone() {
        // Décalage fixe : le test ne dépend pas des règles d'heure d'été, qui changent (Maroc : UTC+0 depuis fin 2026)
        JobOffer offer = offerIn(JobStatus.DRAFT);

        offer.publish(IN_30_DAYS, ZoneId.of("+01:00"), NOW);

        assertThat(offer.getExpiresAt()).isEqualTo(Instant.parse("2026-11-18T22:59:59Z"));
        assertThat(offer.isOpen(Instant.parse("2026-11-18T22:59:58Z"))).isTrue();
        assertThat(offer.isOpen(Instant.parse("2026-11-18T22:59:59Z"))).isFalse();
    }

    @Test
    void publicationLimitsAreReportedTogether() {
        JobOffer withoutRequiredSkill = JobOffer.draft(UUID.randomUUID(), UUID.randomUUID(),
                content(List.of(JobSkill.preferred(UUID.randomUUID()))));

        assertThatThrownBy(() -> withoutRequiredSkill.publish(LocalDate.parse("2027-06-01"), CASABLANCA, NOW))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(e -> {
                    BusinessRuleViolationException refused = (BusinessRuleViolationException) e;
                    assertThat(refused.code()).isEqualTo(ErrorCode.JOB_NOT_PUBLISHABLE);
                    assertThat(refused.details()).containsEntry("reasons", List.of("EXPIRY_TOO_FAR", "NO_REQUIRED_SKILL"));
                });
    }

    @Test
    void expiryMustBeInTheFutureAndAtMost180DaysAway() {
        assertThatThrownBy(() -> offerIn(JobStatus.DRAFT).publish(LocalDate.parse("2026-10-18"), CASABLANCA, NOW))
                .satisfies(e -> assertThat(((DomainException) e).details())
                        .containsEntry("reasons", List.of("EXPIRY_IN_PAST")));
        // Aujourd'hui reste possible : l'offre est ouverte jusqu'à 23:59:59
        assertThatCode(() -> offerIn(JobStatus.DRAFT).publish(LocalDate.parse("2026-10-19"), CASABLANCA, NOW))
                .doesNotThrowAnyException();
        assertThatCode(() -> offerIn(JobStatus.DRAFT).publish(LocalDate.parse("2027-04-17"), CASABLANCA, NOW))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> offerIn(JobStatus.DRAFT).publish(LocalDate.parse("2027-04-18"), CASABLANCA, NOW))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void publishedOfferWithApplicationsCannotGoBackToDraft() {
        assertThatThrownBy(() -> offerIn(JobStatus.PUBLISHED).unpublish(true))
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));
    }

    @Test
    void closedOfferCannotBeReopenedWithAPastExpiry() {
        JobOffer closed = offerIn(JobStatus.CLOSED);

        assertThatThrownBy(() -> closed.reopen(LocalDate.parse("2026-10-01"), CASABLANCA, NOW))
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.JOB_NOT_PUBLISHABLE));
        assertThat(closed.getStatus()).isEqualTo(JobStatus.CLOSED);
    }

    @Test
    void reopeningKeepsTheFirstPublicationDate() {
        JobOffer closed = offerIn(JobStatus.CLOSED);
        Instant later = NOW.plusSeconds(86_400 * 10);

        closed.reopen(LocalDate.parse("2026-12-31"), CASABLANCA, later);

        assertThat(closed.getPublishedAt()).isEqualTo(NOW);
        assertThat(closed.getStatus()).isEqualTo(JobStatus.PUBLISHED);
    }

    @Test
    void closedAndArchivedOffersAreReadOnly() {
        for (JobStatus status : List.of(JobStatus.CLOSED, JobStatus.ARCHIVED)) {
            assertThatThrownBy(() -> offerIn(status).update(content(defaultSkills())))
                    .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.INVALID_STATE));
        }
        assertThatCode(() -> offerIn(JobStatus.PUBLISHED).update(content(defaultSkills()))).doesNotThrowAnyException();
    }

    @Test
    void publishedOfferMustKeepARequiredSkill() {
        assertThatThrownBy(() -> offerIn(JobStatus.PUBLISHED).update(content(List.of(JobSkill.preferred(UUID.randomUUID())))))
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.JOB_NOT_PUBLISHABLE));
    }

    @Test
    void salaryMinimumCannotExceedMaximum() {
        assertThatThrownBy(() -> new Salary(new BigDecimal("20000"), new BigDecimal("15000"), "MAD", SalaryPeriod.MONTHLY, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static JobOffer offerIn(JobStatus status) {
        JobOffer offer = JobOffer.draft(UUID.randomUUID(), UUID.randomUUID(), content(defaultSkills()));
        if (status == JobStatus.DRAFT) {
            return offer;
        }
        offer.publish(IN_30_DAYS, CASABLANCA, NOW);
        if (status == JobStatus.PUBLISHED) {
            return offer;
        }
        offer.close();
        if (status == JobStatus.CLOSED) {
            return offer;
        }
        offer.archive();
        return offer;
    }

    private static List<JobSkill> defaultSkills() {
        return List.of(JobSkill.required(UUID.randomUUID(), SkillLevel.ADVANCED), JobSkill.preferred(UUID.randomUUID()));
    }

    private static JobContent content(List<JobSkill> skills) {
        return new JobContent("Développeur backend Java", "Description ".repeat(10), UUID.randomUUID(), "Casablanca",
                "ma", WorkMode.HYBRID, ContractType.CDI, ExperienceLevel.MID, 3,
                new Salary(new BigDecimal("15000"), new BigDecimal("22000"), "MAD", SalaryPeriod.MONTHLY, true), skills);
    }
}
