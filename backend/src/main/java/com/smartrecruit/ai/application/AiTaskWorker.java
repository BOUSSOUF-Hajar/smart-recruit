package com.smartrecruit.ai.application;

import com.smartrecruit.ai.domain.AiTask;
import com.smartrecruit.ai.domain.AiTaskType;
import com.smartrecruit.ai.domain.AnalysisDocument;
import com.smartrecruit.ai.domain.AnalysisDocument.AnalyzedSkill;
import com.smartrecruit.ai.domain.MatchDocument;
import com.smartrecruit.ai.domain.MatchExplanation;
import com.smartrecruit.ai.domain.Pseudonymizer;
import com.smartrecruit.ai.domain.ResumeExtraction;
import com.smartrecruit.ai.domain.matching.ExperienceYears;
import com.smartrecruit.ai.domain.matching.MatchingCalculator;
import com.smartrecruit.ai.domain.matching.MatchingInput;
import com.smartrecruit.ai.domain.matching.MatchingResult;
import com.smartrecruit.ai.infrastructure.AiResultRepository;
import com.smartrecruit.application.api.ApplicationDirectory;
import com.smartrecruit.application.api.ApplicationRef;
import com.smartrecruit.candidate.api.CandidateDirectory;
import com.smartrecruit.candidate.api.CandidateDossier;
import com.smartrecruit.job.api.JobCatalog;
import com.smartrecruit.job.api.JobRequirements;
import com.smartrecruit.referential.api.SkillCatalog;
import com.smartrecruit.referential.api.SkillSummary;
import com.smartrecruit.shared.domain.DomainException;
import com.smartrecruit.shared.domain.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Exécution d'une tâche IA en arrière-plan (analyse fonctionnelle §3.5). Aucune transaction ici :
 * {@link AiTaskStore} ouvre les transactions courtes, les appels au modèle se font entre elles.
 * <p>
 * Analyse : texte du PDF → pseudonymisation → extraction par le modèle → rattachement au référentiel.
 * Matching : analyse réutilisée (ou faite d'abord) → score par {@link MatchingCalculator} → le modèle
 * rédige l'explication d'un score qu'il ne peut pas modifier.
 */
@Component
public class AiTaskWorker {

    private static final Logger log = LoggerFactory.getLogger(AiTaskWorker.class);
    static final String WARNING_UNREADABLE = "CV non analysable automatiquement : score calculé sur le seul profil.";
    static final String WARNING_EXPERIENCE = "Expérience non renseignée : le critère d'expérience compte pour 0.";

    private final AiTaskStore store;
    private final AiResultRepository results;
    private final AIService ai;
    private final ResumeTextExtractor textExtractor;
    private final ApplicationDirectory applications;
    private final CandidateDirectory candidates;
    private final JobCatalog jobs;
    private final SkillCatalog skills;
    private final AiProperties properties;
    private final JsonMapper json;
    private final Clock clock;

    public AiTaskWorker(AiTaskStore store, AiResultRepository results, AIService ai, ResumeTextExtractor textExtractor,
                        ApplicationDirectory applications, CandidateDirectory candidates, JobCatalog jobs,
                        SkillCatalog skills, AiProperties properties, JsonMapper json, Clock clock) {
        this.store = store;
        this.results = results;
        this.ai = ai;
        this.textExtractor = textExtractor;
        this.applications = applications;
        this.candidates = candidates;
        this.jobs = jobs;
        this.skills = skills;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    public void run(UUID taskId) {
        Optional<AiTask> started = store.start(taskId);
        if (started.isEmpty()) {
            return;   // déjà prise, ou passée FAILED entre-temps
        }
        AiTask task = started.get();
        try {
            ApplicationRef application = applications.find(task.getApplicationId()).orElseThrow();
            if (task.getType() == AiTaskType.RESUME_ANALYSIS) {
                analyse(task, application);
                store.succeed(task.getId());
            } else {
                match(task, application);
            }
        } catch (DomainException e) {
            ErrorCode code = AiTask.ERROR_CODES.contains(e.code()) ? e.code() : ErrorCode.AI_UNAVAILABLE;
            log.info("Tâche IA {} en échec : {}", task.getId(), code);
            store.fail(task.getId(), code);
        } catch (RuntimeException e) {
            log.error("Tâche IA {} : erreur inattendue", task.getId(), e);
            store.fail(task.getId(), ErrorCode.AI_UNAVAILABLE);
        }
    }

    /** @return identifiant de l'analyse enregistrée */
    private UUID analyse(AiTask task, ApplicationRef application) {
        String text = textExtractor.extract(read(task.getResumeId()));
        String pseudonymized = Pseudonymizer.pseudonymize(truncate(text), List.of(application.candidateName()));
        ResumeExtraction extraction = ai.extractResume(pseudonymized);
        Map<String, SkillSummary> resolved = skills.resolve(
                extraction.skills().stream().map(ResumeExtraction.Skill::name).toList());
        List<AnalyzedSkill> analyzed = new ArrayList<>();
        Set<UUID> seen = new LinkedHashSet<>();
        for (ResumeExtraction.Skill skill : extraction.skills()) {
            SkillSummary reference = resolved.get(skill.name());
            if (reference == null || seen.add(reference.id())) {
                analyzed.add(new AnalyzedSkill(skill.name(), skill.evidence(),
                        reference == null ? null : reference.id(), reference == null ? null : reference.name()));
            }
        }
        AnalysisDocument document = new AnalysisDocument(analyzed, extraction.experiences(), extraction.educations(),
                extraction.languages(), extraction.summary());
        return store.saveAnalysis(task, ai.modelName(), sha256(pseudonymized), document);
    }

    private void match(AiTask task, ApplicationRef application) {
        List<String> warnings = new ArrayList<>();
        UUID analysisId = null;
        AnalysisDocument analysis = null;
        var existing = results.latestAnalysis(task.getResumeId(), Prompts.RESUME_EXTRACTION);
        if (existing.isPresent()) {
            analysisId = existing.get().id();
            analysis = existing.get().document();
        } else {
            try {
                analysisId = analyse(task, application);   // US-42 : l'analyse est lancée d'abord
                analysis = results.latestAnalysis(task.getResumeId(), Prompts.RESUME_EXTRACTION).orElseThrow()
                        .document();
            } catch (DomainException e) {
                if (e.code() != ErrorCode.FILE_UNREADABLE) {
                    throw e;
                }
                warnings.add(WARNING_UNREADABLE);   // §5.3 : calcul sur le seul profil
            }
        }

        JobRequirements job = jobs.requirements(application.jobOfferId()).orElseThrow();
        CandidateDossier profile = candidates.dossier(application.candidateId());
        MatchingInput input = input(job, profile, analysis);
        MatchingResult result = MatchingCalculator.compute(input);
        if (!result.experienceKnown()) {
            warnings.add(WARNING_EXPERIENCE);
        }
        MatchExplanation explanation = ai.explainMatch(explanationInput(job, result, analysis));
        store.saveMatch(task, analysisId, result, new MatchDocument(result.matched(), result.missing(),
                explanation.strengths(), explanation.weaknesses(), explanation.summary(), warnings), ai.modelName());
    }

    /** §5.1 : compétences du profil (prioritaires) et du CV ; années déclarées, sinon calculées. */
    private MatchingInput input(JobRequirements job, CandidateDossier profile, AnalysisDocument analysis) {
        Set<UUID> jobSkillIds = Stream.concat(job.requiredSkills().stream().map(JobRequirements.RequiredSkill::skillId),
                job.preferredSkillIds().stream()).collect(Collectors.toSet());
        Map<UUID, SkillSummary> names = skills.findByIds(jobSkillIds);
        Map<UUID, MatchingInput.ProfileSkill> declared = new LinkedHashMap<>();
        profile.skills().forEach(skill ->
                declared.put(skill.skillId(), new MatchingInput.ProfileSkill(skill.name(), skill.level())));
        Map<UUID, MatchingInput.ResumeSkill> found = new LinkedHashMap<>();
        if (analysis != null) {
            analysis.skills().stream().filter(skill -> skill.skillId() != null).forEach(skill ->
                    found.putIfAbsent(skill.skillId(), new MatchingInput.ResumeSkill(skill.referenceName(),
                            skill.evidence())));
        }
        Double years = ExperienceYears.of(profile.yearsOfExperience(),
                profile.experiences().stream()
                        .map(e -> new ExperienceYears.Period(e.startDate(), e.endDate())).toList(),
                LocalDate.now(clock.withZone(ZoneOffset.UTC)));
        if (years == null && analysis != null) {
            years = yearsFromResume(analysis);   // extraction IA en dernier recours
        }
        return new MatchingInput(
                job.requiredSkills().stream().map(skill -> new MatchingInput.Required(skill.skillId(),
                        name(names, skill.skillId()), skill.minLevel())).toList(),
                job.preferredSkillIds().stream().map(id -> new MatchingInput.Preferred(id, name(names, id))).toList(),
                job.minYearsExperience(), declared, found, years);
    }

    private static Double yearsFromResume(AnalysisDocument analysis) {
        int months = 0;
        int currentYear = LocalDate.now().getYear();
        for (ResumeExtraction.Experience experience : analysis.experiences()) {
            if (experience.durationMonths() != null) {
                months += experience.durationMonths();
            } else if (experience.startYear() != null) {
                int end = experience.endYear() == null ? currentYear : experience.endYear();
                months += Math.max(0, end - experience.startYear()) * 12;
            }
        }
        return months == 0 ? null : months / 12.0;
    }

    /** Données de l'explication : la décomposition déjà calculée, rien qui permette d'identifier le candidat. */
    private String explanationInput(JobRequirements job, MatchingResult result, AnalysisDocument analysis) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("offre", job.title());
        data.put("anneesExperienceDemandees", job.minYearsExperience());
        data.put("score", result.score());
        data.put("decomposition", Map.of(
                "competencesRequises", result.requiredCoverage(),
                "competencesSouhaitees", result.preferredCoverage() == null ? "aucune demandée" : result.preferredCoverage(),
                "experience", result.experienceKnown() ? result.experienceFit() : "non renseignée"));
        data.put("competencesCorrespondantes", result.matched().stream()
                .map(skill -> skill.name() + (skill.required() ? " (requise" : " (souhaitée")
                        + (skill.belowRequiredLevel() ? ", niveau inférieur à celui demandé)" : ")"))
                .toList());
        data.put("competencesManquantes", result.missing().stream()
                .map(skill -> skill.name() + (skill.required() ? " (requise)" : " (souhaitée)")).toList());
        if (analysis != null) {
            data.put("syntheseDuCv", analysis.summary());
            data.put("experiences", analysis.experiences().stream().map(ResumeExtraction.Experience::title).toList());
        }
        return json.writeValueAsString(data);
    }

    private byte[] read(UUID resumeId) {
        try (InputStream in = candidates.resumeContent(resumeId).file().getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw AiException.unreadable();
        }
    }

    private String truncate(String text) {
        return text.length() <= properties.maxInputChars() ? text : text.substring(0, properties.maxInputChars());
    }

    private static String name(Map<UUID, SkillSummary> names, UUID id) {
        return names.containsKey(id) ? names.get(id).name() : "?";
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
