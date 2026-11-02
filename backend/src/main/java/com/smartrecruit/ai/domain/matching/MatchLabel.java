package com.smartrecruit.ai.domain.matching;

/**
 * Libellé de lecture du score (analyse fonctionnelle §5.4). Sert à l'affichage, jamais à un tri ou
 * un filtrage automatique (RG-25).
 */
public enum MatchLabel {
    STRONG, PARTIAL, WEAK;

    public static MatchLabel of(int score) {
        return score >= 75 ? STRONG : score >= 50 ? PARTIAL : WEAK;
    }
}
