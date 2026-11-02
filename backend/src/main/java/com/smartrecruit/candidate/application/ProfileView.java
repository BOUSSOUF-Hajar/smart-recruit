package com.smartrecruit.candidate.application;

import com.smartrecruit.candidate.domain.CandidateLanguage;
import com.smartrecruit.candidate.domain.CandidateLink;
import com.smartrecruit.candidate.domain.Completeness;
import com.smartrecruit.candidate.domain.Education;
import com.smartrecruit.candidate.domain.Experience;
import com.smartrecruit.candidate.domain.Resume;
import com.smartrecruit.identity.api.AccountSummary;

import java.util.List;

/**
 * Profil complet tel que l'affiche l'écran du candidat, assemblé dans la transaction de lecture :
 * toutes les collections sont déjà chargées, le contrôleur n'en déclenche aucune.
 *
 * @param resume CV actif, ou {@code null}
 */
public record ProfileView(AccountSummary account, Basics basics, List<Experience> experiences,
                          List<Education> educations, List<SkillLine> skills, List<CandidateLanguage> languages,
                          List<CandidateLink> links, Resume resume, Completeness completeness, long version) {

    public record Basics(String phone, String city, String countryCode, String headline, String summary,
                         Integer yearsOfExperience) {
    }
}
