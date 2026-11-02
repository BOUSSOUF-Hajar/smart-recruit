package com.smartrecruit.referential.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Formes d'un nom de compétence. La forme normalisée sert à reconnaître une compétence quelle
 * que soit la saisie : « Spring  BOOT » et « spring boot » désignent la même.
 * <p>
 * Doit rester identique à la règle utilisée par les données de départ (V7).
 */
public final class SkillNames {

    private static final Pattern SPACES = Pattern.compile("\\s+");

    private SkillNames() {
    }

    /** Forme affichée : espaces superflus retirés, casse conservée. */
    public static String display(String raw) {
        return SPACES.matcher(Normalizer.normalize(raw, Normalizer.Form.NFKC).strip()).replaceAll(" ");
    }

    /** Forme de comparaison : forme affichée en minuscules. */
    public static String normalize(String raw) {
        return display(raw).toLowerCase(Locale.ROOT);
    }
}
