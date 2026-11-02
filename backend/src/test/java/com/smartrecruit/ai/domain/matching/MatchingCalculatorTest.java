package com.smartrecruit.ai.domain.matching;

import com.smartrecruit.ai.domain.matching.MatchingInput.Preferred;
import com.smartrecruit.ai.domain.matching.MatchingInput.ProfileSkill;
import com.smartrecruit.ai.domain.matching.MatchingInput.Required;
import com.smartrecruit.ai.domain.matching.MatchingInput.ResumeSkill;
import com.smartrecruit.ai.domain.matching.MatchingResult.MatchedSkill;
import com.smartrecruit.ai.domain.matching.MatchingResult.MissingSkill;
import com.smartrecruit.ai.domain.matching.MatchingResult.Source;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.smartrecruit.referential.api.SkillLevel.ADVANCED;
import static com.smartrecruit.referential.api.SkillLevel.BEGINNER;
import static com.smartrecruit.referential.api.SkillLevel.EXPERT;
import static com.smartrecruit.referential.api.SkillLevel.INTERMEDIATE;
import static org.assertj.core.api.Assertions.assertThat;

/** US-42 : formule de l'analyse fonctionnelle §5, en commençant par l'exemple chiffré §5.5. */
class MatchingCalculatorTest {

    private static final UUID JAVA = UUID.randomUUID();
    private static final UUID SPRING_BOOT = UUID.randomUUID();
    private static final UUID POSTGRESQL = UUID.randomUUID();
    private static final UUID REST_API = UUID.randomUUID();
    private static final UUID GIT = UUID.randomUUID();
    private static final UUID DOCKER = UUID.randomUUID();
    private static final UUID KUBERNETES = UUID.randomUUID();
    private static final UUID KAFKA = UUID.randomUUID();

    private static final List<Required> REQUIRED = List.of(new Required(JAVA, "Java", ADVANCED),
            new Required(SPRING_BOOT, "Spring Boot", INTERMEDIATE), new Required(POSTGRESQL, "PostgreSQL", BEGINNER),
            new Required(REST_API, "REST API", BEGINNER), new Required(GIT, "Git", BEGINNER));
    private static final List<Preferred> PREFERRED = List.of(new Preferred(DOCKER, "Docker"),
            new Preferred(KUBERNETES, "Kubernetes"), new Preferred(KAFKA, "Kafka"));

    /** Candidat de l'exemple : REST API vient du CV (niveau inconnu), le reste du profil. */
    private static final Map<UUID, ProfileSkill> PROFILE = Map.of(JAVA, new ProfileSkill("Java", ADVANCED),
            SPRING_BOOT, new ProfileSkill("Spring Boot", BEGINNER), POSTGRESQL, new ProfileSkill("PostgreSQL", ADVANCED),
            GIT, new ProfileSkill("Git", INTERMEDIATE), DOCKER, new ProfileSkill("Docker", BEGINNER));
    private static final Map<UUID, ResumeSkill> RESUME = Map.of(REST_API,
            new ResumeSkill("REST API", "Conception d'API REST pour une plateforme de paiement"));

    @Test
    void theWorkedExampleScores81StrongFit() {
        MatchingResult result = MatchingCalculator.compute(new MatchingInput(REQUIRED, PREFERRED, 3, PROFILE, RESUME, 4.0));

        assertThat(result.requiredCoverage()).isEqualTo(0.9);
        assertThat(result.preferredCoverage()).isEqualTo(0.333);
        assertThat(result.experienceFit()).isEqualTo(1.0);
        assertThat(result.score()).isEqualTo(81);
        assertThat(result.label()).isEqualTo(MatchLabel.STRONG);
        assertThat(result.formulaVersion()).isEqualTo("v1");
        assertThat(result.matched()).contains(
                new MatchedSkill(SPRING_BOOT, "Spring Boot", Source.PROFILE, BEGINNER, null, true, true),
                new MatchedSkill(REST_API, "REST API", Source.RESUME, null,
                        "Conception d'API REST pour une plateforme de paiement", true, false));
        assertThat(result.missing()).containsExactly(new MissingSkill(KUBERNETES, "Kubernetes", false),
                new MissingSkill(KAFKA, "Kafka", false));
    }

    /** §5.3 : sans compétence souhaitée, 75 % requises et 25 % expérience. */
    @Test
    void withoutPreferredSkillsTheWeightsAre75And25() {
        MatchingResult result = MatchingCalculator.compute(new MatchingInput(REQUIRED, List.of(), 3, PROFILE, RESUME, 1.5));

        assertThat(result.preferredCoverage()).isNull();
        assertThat(result.experienceFit()).isEqualTo(0.5);
        assertThat(result.score()).isEqualTo(80);   // 100 × (0,75 × 0,9 + 0,25 × 0,5) = 80
    }

    /** §5.3 : années inconnues → A = 0 et mention, sans rien inventer ; 0 an demandé → A = 1. */
    @Test
    void unknownExperienceCountsForZeroUnlessNoneIsRequired() {
        MatchingResult unknown = MatchingCalculator.compute(new MatchingInput(REQUIRED, PREFERRED, 3, PROFILE, RESUME, null));
        assertThat(unknown.experienceFit()).isZero();
        assertThat(unknown.experienceKnown()).isFalse();
        assertThat(unknown.score()).isEqualTo(61);

        MatchingResult none = MatchingCalculator.compute(new MatchingInput(REQUIRED, PREFERRED, 0, PROFILE, RESUME, null));
        assertThat(none.experienceFit()).isEqualTo(1.0);
        assertThat(none.experienceKnown()).isTrue();
    }

    /** Le profil prime sur le CV ; aucune déduction implicite ; un niveau inférieur vaut 0,5. */
    @Test
    void profileLevelWinsAndNothingIsInferred() {
        Map<UUID, ProfileSkill> profile = Map.of(JAVA, new ProfileSkill("Java", BEGINNER),
                SPRING_BOOT, new ProfileSkill("Spring Boot", EXPERT));
        Map<UUID, ResumeSkill> resume = Map.of(JAVA, new ResumeSkill("Java", "Java depuis 8 ans"));

        MatchingResult result = MatchingCalculator.compute(new MatchingInput(
                List.of(new Required(JAVA, "Java", ADVANCED), new Required(KAFKA, "Kafka", BEGINNER)),
                List.of(), 0, profile, resume, null));

        // Java : profil BEGINNER < ADVANCED → 0,5 (le CV ne remonte pas le niveau) ; Kafka absent → 0
        assertThat(result.requiredCoverage()).isEqualTo(0.25);
        assertThat(result.matched()).extracting(MatchedSkill::source).containsExactly(Source.PROFILE);
        assertThat(result.missing()).extracting(MissingSkill::name).containsExactly("Kafka");
        assertThat(result.score()).isEqualTo(44);
        assertThat(result.label()).isEqualTo(MatchLabel.WEAK);
    }

    @Test
    void labelsFollowTheReadingThresholds() {
        assertThat(MatchLabel.of(75)).isEqualTo(MatchLabel.STRONG);
        assertThat(MatchLabel.of(74)).isEqualTo(MatchLabel.PARTIAL);
        assertThat(MatchLabel.of(50)).isEqualTo(MatchLabel.PARTIAL);
        assertThat(MatchLabel.of(49)).isEqualTo(MatchLabel.WEAK);
    }
}
