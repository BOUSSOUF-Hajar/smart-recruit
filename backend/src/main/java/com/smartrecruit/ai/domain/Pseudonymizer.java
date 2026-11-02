package com.smartrecruit.ai.domain;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimisation des données avant envoi au modèle (D7.2, RG-26) : coordonnées, date de naissance,
 * âge, adresse, situation familiale, nationalité et nom du candidat sont remplacés par des marqueurs
 * neutres. Expressions régulières : efficacité imparfaite assumée et mesurée sur le jeu de CV du
 * banc d'essai ; le prompt interdit en plus de tenir compte de ces critères.
 * <p>
 * L'ordre compte : les liens avant les téléphones (ils contiennent des chiffres), les téléphones
 * avant les adresses (une adresse commence souvent par un numéro), le nom en dernier. Les motifs
 * d'adresse ne franchissent jamais une fin de ligne.
 */
public final class Pseudonymizer {

    public static final String EMAIL = "[EMAIL]";
    public static final String URL = "[LIEN]";
    public static final String PHONE = "[TÉLÉPHONE]";
    public static final String BIRTH = "[DATE_DE_NAISSANCE]";
    public static final String AGE = "[ÂGE]";
    public static final String ADDRESS = "[ADRESSE]";
    public static final String PERSONAL = "[INFORMATION_PERSONNELLE]";
    public static final String NAME = "[CANDIDAT]";

    private static final String MONTHS = "janvier|février|fevrier|mars|avril|mai|juin|juillet|août|aout|septembre"
            + "|octobre|novembre|décembre|decembre|january|february|march|april|may|june|july|august|september"
            + "|october|november|december";

    private static final Pattern EMAILS = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern URLS = Pattern.compile(
            "(?iU)\\b(https?://|www\\.)\\S+|\\b(linkedin|github|gitlab|behance)\\.com/\\S+");
    private static final Pattern BIRTH_DATES = Pattern.compile(
            "(?iU)\\b(née?|date\\s+de\\s+naissance|naissance|born|date\\s+of\\s+birth|dob)\\b[^\\n\\d]{0,20}"
                    + "(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}|\\d{1,2}(er)?\\s+(" + MONTHS + ")\\s+\\d{4}|\\d{4})");
    private static final Pattern AGES = Pattern.compile("(?iU)\\b(âge|age)\\s*:?\\s*\\d{2}\\s*(ans|years?)?\\b|\\b\\d{2}\\s*(ans|years old)\\s*(?=[,.;)\\n]|$)");
    private static final Pattern PERSONAL_LINES = Pattern.compile(
            "(?imU)^.*\\b(situation\\s+familiale|état\\s+civil|etat\\s+civil|marital\\s+status|nationalité|"
                    + "nationalite|nationality|sexe|genre|gender|religion|marié|mariée|célibataire|divorcée?|"
                    + "enfants?\\s+à\\s+charge)\\b.*$");
    private static final Pattern ADDRESSES = Pattern.compile(
            "(?imU)\\b(adresse|address)\\s*:[^\\n]*"
                    + "|^[ \\t]*(résidence|residence|lotissement|quartier|immeuble|appartement|appt)\\b[^\\n]*"
                    + "|\\b\\d{1,4}[ \\t]*,?[ \\t]*(bis[ \\t]+)?(rue|avenue|av\\.|bd|boulevard|lotissement|lot|résidence|"
                    + "residence|quartier|impasse|allée|street|road)\\b[^\\n]{0,80}"
                    + "|\\b\\d{5}[ \\t]+[A-ZÉÈ][\\p{L}'-]+([ \\t][A-ZÉÈ][\\p{L}'-]+)?");
    /** Suite de chiffres et séparateurs ; retenue comme téléphone si elle compte 9 à 15 chiffres. */
    private static final Pattern PHONE_CANDIDATES = Pattern.compile("(\\+|00)?\\d[\\d \\t.()-]{7,}\\d");

    private Pseudonymizer() {
    }

    /**
     * @param names prénom et nom du candidat (connus par la candidature), remplacés partout
     */
    public static String pseudonymize(String text, Collection<String> names) {
        String result = EMAILS.matcher(text).replaceAll(EMAIL);
        result = URLS.matcher(result).replaceAll(URL);
        result = BIRTH_DATES.matcher(result).replaceAll(BIRTH);
        result = AGES.matcher(result).replaceAll(AGE);
        result = PERSONAL_LINES.matcher(result).replaceAll(PERSONAL);
        result = replacePhones(result);
        result = ADDRESSES.matcher(result).replaceAll(ADDRESS);
        for (String name : names) {
            for (String part : name.split("[\\s-]+")) {
                if (part.length() >= 2) {
                    result = Pattern.compile("(?iu)(?<![\\p{L}])" + Pattern.quote(part) + "(?![\\p{L}])")
                            .matcher(result).replaceAll(Matcher.quoteReplacement(NAME));
                }
            }
        }
        return result;
    }

    private static String replacePhones(String text) {
        Matcher matcher = PHONE_CANDIDATES.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            long digits = matcher.group().chars().filter(Character::isDigit).count();
            matcher.appendReplacement(out, Matcher.quoteReplacement(digits >= 9 && digits <= 15 ? PHONE : matcher.group()));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
