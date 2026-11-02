package com.smartrecruit.candidate.domain;

import com.smartrecruit.referential.api.SkillLevel;

import com.smartrecruit.shared.domain.FieldValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandidateProfileTest {

    private final CandidateProfile profile = CandidateProfile.emptyFor(UUID.randomUUID());

    @Test
    void basicsAreTrimmedAndBlankBecomesEmpty() {
        profile.updateBasics(" +212600000000 ", "  ", "ma", " Développeuse Java ", "", 4);

        assertThat(profile.getPhone()).isEqualTo("+212600000000");
        assertThat(profile.getCity()).isNull();
        assertThat(profile.getCountryCode()).isEqualTo("MA");
        assertThat(profile.getHeadline()).isEqualTo("Développeuse Java");
        assertThat(profile.getSummary()).isNull();
        assertThat(profile.getYearsOfExperience()).isEqualTo(4);
    }

    @Test
    void fiftySkillsAreAcceptedButNotFiftyOne() {
        profile.replaceSkills(skills(50));
        assertThat(profile.getSkills()).hasSize(50);

        assertThatThrownBy(() -> profile.replaceSkills(skills(51)))
                .isInstanceOf(FieldValidationException.class)
                .satisfies(e -> assertThat(((FieldValidationException) e).errors())
                        .extracting(FieldValidationException.FieldError::field).containsExactly("skills"));
        assertThat(profile.getSkills()).hasSize(50);   // la liste précédente est intacte
    }

    @Test
    void duplicateSkillIsRejectedWithItsPosition() {
        UUID java = UUID.randomUUID();
        List<CandidateSkill> skills = List.of(
                new CandidateSkill(java, SkillLevel.ADVANCED, 3),
                new CandidateSkill(UUID.randomUUID(), SkillLevel.BEGINNER, null),
                new CandidateSkill(java, SkillLevel.EXPERT, 5));

        assertThatThrownBy(() -> profile.replaceSkills(skills))
                .isInstanceOf(FieldValidationException.class)
                .satisfies(e -> assertThat(((FieldValidationException) e).errors())
                        .extracting(FieldValidationException.FieldError::field)
                        .containsExactly("skills[2].skillId"));
    }

    @Test
    void sameLanguageTwiceIsRejected() {
        assertThatThrownBy(() -> profile.replaceLanguages(List.of(
                new CandidateLanguage("fr", LanguageLevel.NATIVE),
                new CandidateLanguage("FR", LanguageLevel.C1))))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void sixLinksAreRejected() {
        List<CandidateLink> links = IntStream.range(0, 6)
                .mapToObj(i -> new CandidateLink(LinkType.OTHER, "https://example.com/" + i))
                .toList();

        assertThatThrownBy(() -> profile.replaceLinks(links)).isInstanceOf(FieldValidationException.class);
    }

    @Test
    void linksMustBeHttps() {
        assertThatThrownBy(() -> new CandidateLink(LinkType.GITHUB, "http://github.com/salma"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<CandidateSkill> skills(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new CandidateSkill(UUID.randomUUID(), SkillLevel.INTERMEDIATE, null))
                .toList();
    }
}
