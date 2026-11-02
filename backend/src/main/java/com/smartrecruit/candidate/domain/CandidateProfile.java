package com.smartrecruit.candidate.domain;

import com.smartrecruit.shared.domain.FieldValidationException;
import com.smartrecruit.shared.domain.FieldValidationException.FieldError;
import com.smartrecruit.shared.persistence.VersionedEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OptimisticLock;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Profil d'un candidat, de même identifiant que son compte (clé primaire partagée).
 * <p>
 * Compétences, langues et liens sont des valeurs du profil (collections d'éléments), remplacées
 * en bloc. Expériences et formations ont leur propre identité ({@link Experience}, {@link Education}).
 * <p>
 * {@code @OptimisticLock(excluded = true)} : modifier une rubrique n'incrémente pas la version du
 * profil. Sinon, ajouter une compétence rendrait obsolète le formulaire d'informations ouvert à côté.
 */
@Entity
@Table(name = "candidate_profile")
public class CandidateProfile extends VersionedEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(length = 20)
    private String phone;

    @Column(length = 80)
    private String city;

    /** Code pays ISO 3166-1 alpha-2, en {@code CHAR(2)}. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(length = 120)
    private String headline;

    @Column(length = 2000)
    private String summary;

    /** {@code Short} pour correspondre au {@code SMALLINT} de la base lors de la validation du schéma. */
    @Column(name = "years_of_experience")
    private Short yearsOfExperience;

    @ElementCollection
    @CollectionTable(name = "candidate_skill", joinColumns = @JoinColumn(name = "candidate_id"))
    @OptimisticLock(excluded = true)
    private List<CandidateSkill> skills = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "candidate_language", joinColumns = @JoinColumn(name = "candidate_id"))
    @OrderBy("languageCode")
    @OptimisticLock(excluded = true)
    private List<CandidateLanguage> languages = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "candidate_link", joinColumns = @JoinColumn(name = "candidate_id"))
    @OrderBy("type, url")
    @OptimisticLock(excluded = true)
    private List<CandidateLink> links = new ArrayList<>();

    protected CandidateProfile() {
        // JPA
    }

    public static CandidateProfile emptyFor(UUID userId) {
        CandidateProfile profile = new CandidateProfile();
        profile.userId = Objects.requireNonNull(userId, "userId");
        return profile;
    }

    /** US-13. Aucun champ sur l'âge, la photo, le sexe, la nationalité ou la situation familiale. */
    public void updateBasics(String phone, String city, String countryCode, String headline, String summary,
                             Integer yearsOfExperience) {
        this.phone = blankToNull(phone);
        this.city = blankToNull(city);
        this.countryCode = countryCode == null || countryCode.isBlank() ? null : countryCode.strip().toUpperCase(Locale.ROOT);
        this.headline = blankToNull(headline);
        this.summary = blankToNull(summary);
        this.yearsOfExperience = yearsOfExperience == null ? null : yearsOfExperience.shortValue();
    }

    /** US-15. Remplace toute la liste : 50 au maximum, une compétence au plus une fois. */
    public void replaceSkills(List<CandidateSkill> newSkills) {
        checkMax("skills", newSkills.size(), ProfileRules.MAX_SKILLS);
        checkUnique("skills", newSkills, CandidateSkill::getSkillId, "skillId", "Compétence en double.");
        skills.clear();
        skills.addAll(newSkills);
    }

    /** US-16. Une langue au plus une fois. */
    public void replaceLanguages(List<CandidateLanguage> newLanguages) {
        checkMax("languages", newLanguages.size(), ProfileRules.MAX_LANGUAGES);
        checkUnique("languages", newLanguages, CandidateLanguage::getLanguageCode, "languageCode", "Langue en double.");
        languages.clear();
        languages.addAll(newLanguages);
    }

    /** US-16. 5 liens au maximum, tous en https (vérifié par {@link CandidateLink}). */
    public void replaceLinks(List<CandidateLink> newLinks) {
        checkMax("links", newLinks.size(), ProfileRules.MAX_LINKS);
        checkUnique("links", newLinks, CandidateLink::getUrl, "url", "Lien en double.");
        links.clear();
        links.addAll(newLinks);
    }

    private static void checkMax(String field, int size, int max) {
        if (size > max) {
            throw new FieldValidationException(field, "Au plus " + max + " éléments.");
        }
    }

    private static <T, K> void checkUnique(String field, List<T> items, Function<T, K> key, String keyField,
                                           String message) {
        Set<K> seen = new HashSet<>();
        List<FieldError> errors = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!seen.add(key.apply(items.get(i)))) {
                errors.add(new FieldError(field + "[" + i + "]." + keyField, message));
            }
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPhone() {
        return phone;
    }

    public String getCity() {
        return city;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getHeadline() {
        return headline;
    }

    public String getSummary() {
        return summary;
    }

    public Integer getYearsOfExperience() {
        return yearsOfExperience == null ? null : yearsOfExperience.intValue();
    }

    /** Copies : la liste interne ne se modifie que par les méthodes {@code replace…}. */
    public List<CandidateSkill> getSkills() {
        return List.copyOf(skills);
    }

    public List<CandidateLanguage> getLanguages() {
        return List.copyOf(languages);
    }

    public List<CandidateLink> getLinks() {
        return List.copyOf(links);
    }
}
