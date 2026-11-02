package com.smartrecruit.ai.infrastructure;

import com.smartrecruit.ai.domain.AnalysisDocument;
import com.smartrecruit.ai.domain.MatchDocument;
import com.smartrecruit.ai.domain.matching.MatchingResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Résultats IA en JDBC : des documents produits d'un bloc, lus d'un bloc, jamais modifiés (JSONB,
 * modèle de données §9). Les valeurs filtrées ou agrégées (score, couvertures) sont des colonnes.
 */
@Repository
public class AiResultRepository {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public AiResultRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record StoredAnalysis(UUID id, UUID resumeId, UUID taskId, String modelName, String promptVersion,
                                 Instant createdAt, AnalysisDocument document) {
    }

    public record StoredMatch(UUID id, UUID applicationId, UUID analysisId, int score, double requiredCoverage,
                              Double preferredCoverage, double experienceFit, String formulaVersion,
                              String modelName, String promptVersion, Instant createdAt, MatchDocument document) {
    }

    public UUID saveAnalysis(UUID resumeId, UUID taskId, String model, String promptVersion, String inputSha256,
                             AnalysisDocument document, Instant at) {
        return jdbc.sql("""
                        INSERT INTO resume_analysis (resume_id, task_id, model_name, prompt_version, input_sha256,
                                                     result, created_at)
                        VALUES (:resumeId, :taskId, :model, :prompt, :sha, cast(:result as jsonb), :at)
                        RETURNING id""")
                .param("resumeId", resumeId).param("taskId", taskId).param("model", model)
                .param("prompt", promptVersion).param("sha", inputSha256)
                .param("result", json.writeValueAsString(document)).param("at", Timestamp.from(at))
                .query(UUID.class).single();
    }

    /** Dernière analyse de cette version de CV avec ce prompt : réutilisée par le matching. */
    public Optional<StoredAnalysis> latestAnalysis(UUID resumeId, String promptVersion) {
        return jdbc.sql("""
                        SELECT id, resume_id, task_id, model_name, prompt_version, created_at, result
                        FROM resume_analysis WHERE resume_id = :resumeId AND prompt_version = :prompt
                        ORDER BY created_at DESC, id DESC LIMIT 1""")
                .param("resumeId", resumeId).param("prompt", promptVersion)
                .query(this::analysis).optional();
    }

    /** Dernière analyse de cette version de CV, tous prompts confondus (affichage). */
    public Optional<StoredAnalysis> latestAnalysis(UUID resumeId) {
        return jdbc.sql("""
                        SELECT id, resume_id, task_id, model_name, prompt_version, created_at, result
                        FROM resume_analysis WHERE resume_id = :resumeId
                        ORDER BY created_at DESC, id DESC LIMIT 1""")
                .param("resumeId", resumeId)
                .query(this::analysis).optional();
    }

    public void saveMatch(UUID applicationId, UUID analysisId, UUID taskId, MatchingResult result,
                          MatchDocument document, String model, String promptVersion, Instant at) {
        jdbc.sql("""
                        INSERT INTO match_result (application_id, resume_analysis_id, task_id, score, required_coverage,
                            preferred_coverage, experience_fit, formula_version, matched_skills, missing_skills,
                            explanation, model_name, prompt_version, created_at)
                        VALUES (:applicationId, :analysisId, :taskId, :score, :required, :preferred, :experience,
                            :formula, cast(:matched as jsonb), cast(:missing as jsonb), cast(:explanation as jsonb),
                            :model, :prompt, :at)""")
                .param("applicationId", applicationId).param("analysisId", analysisId).param("taskId", taskId)
                .param("score", result.score()).param("required", result.requiredCoverage())
                .param("preferred", result.preferredCoverage()).param("experience", result.experienceFit())
                .param("formula", result.formulaVersion())
                .param("matched", json.writeValueAsString(document.matched()))
                .param("missing", json.writeValueAsString(document.missing()))
                .param("explanation", json.writeValueAsString(new Explanation(document.strengths(),
                        document.weaknesses(), document.summary(), document.warnings())))
                .param("model", model).param("prompt", promptVersion).param("at", Timestamp.from(at))
                .update();
    }

    public Optional<StoredMatch> latestMatch(UUID applicationId) {
        return jdbc.sql("""
                        SELECT * FROM match_result WHERE application_id = :applicationId
                        ORDER BY created_at DESC, id DESC LIMIT 1""")
                .param("applicationId", applicationId)
                .query(this::match).optional();
    }

    private record Explanation(List<String> strengths, List<String> weaknesses, String summary,
                               List<String> warnings) {
    }

    private StoredAnalysis analysis(ResultSet rs, int rowNum) throws SQLException {
        return new StoredAnalysis(rs.getObject("id", UUID.class), rs.getObject("resume_id", UUID.class),
                rs.getObject("task_id", UUID.class), rs.getString("model_name"), rs.getString("prompt_version"),
                rs.getTimestamp("created_at").toInstant(),
                json.readValue(rs.getString("result"), AnalysisDocument.class));
    }

    private StoredMatch match(ResultSet rs, int rowNum) throws SQLException {
        Explanation explanation = json.readValue(rs.getString("explanation"), Explanation.class);
        List<MatchingResult.MatchedSkill> matched = List.of(json.readValue(rs.getString("matched_skills"),
                MatchingResult.MatchedSkill[].class));
        List<MatchingResult.MissingSkill> missing = List.of(json.readValue(rs.getString("missing_skills"),
                MatchingResult.MissingSkill[].class));
        java.math.BigDecimal preferred = rs.getBigDecimal("preferred_coverage");
        return new StoredMatch(rs.getObject("id", UUID.class), rs.getObject("application_id", UUID.class),
                rs.getObject("resume_analysis_id", UUID.class), rs.getInt("score"),
                rs.getBigDecimal("required_coverage").doubleValue(),
                preferred == null ? null : preferred.doubleValue(), rs.getBigDecimal("experience_fit").doubleValue(),
                rs.getString("formula_version"), rs.getString("model_name"), rs.getString("prompt_version"),
                rs.getTimestamp("created_at").toInstant(),
                new MatchDocument(matched, missing, explanation.strengths(), explanation.weaknesses(),
                        explanation.summary(), explanation.warnings() == null ? List.of() : explanation.warnings()));
    }
}
