package com.smartrecruit.ai.domain;

import com.smartrecruit.ai.domain.matching.MatchingResult;

import java.util.List;

/**
 * Parties documentaires d'un résultat de matching (colonnes JSONB de {@code match_result}).
 *
 * @param warnings avertissements affichés avec le score (« expérience non renseignée », « CV non analysable »)
 */
public record MatchDocument(List<MatchingResult.MatchedSkill> matched, List<MatchingResult.MissingSkill> missing,
                            List<String> strengths, List<String> weaknesses, String summary,
                            List<String> warnings) {
}
