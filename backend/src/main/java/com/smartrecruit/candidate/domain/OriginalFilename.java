package com.smartrecruit.candidate.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Nettoyage du nom de fichier envoyé par le navigateur. Le résultat n'est qu'un libellé affiché
 * et proposé au téléchargement : il ne sert jamais à construire un chemin (le fichier est stocké
 * sous un nom UUID), mais il ne doit pas non plus casser un en-tête HTTP ni tromper l'utilisateur.
 */
public final class OriginalFilename {

    static final String FALLBACK = "cv.pdf";
    private static final int MAX_LENGTH = 255;
    private static final Pattern FORBIDDEN = Pattern.compile("[^\\p{L}\\p{N} ._()\\-]");
    private static final Pattern REPEATED = Pattern.compile("[ _]{2,}");

    private OriginalFilename() {
    }

    public static String sanitize(String raw) {
        if (raw == null) {
            return FALLBACK;
        }
        // Dernier segment seulement : « ../../etc/passwd » ou « C:\Users\cv.pdf » perdent leur chemin
        String name = raw.substring(Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\')) + 1);
        name = Normalizer.normalize(name, Normalizer.Form.NFC);
        name = FORBIDDEN.matcher(name).replaceAll("_");
        name = REPEATED.matcher(name).replaceAll("_");
        name = name.strip();
        // Pas de nom caché ni réduit à des points
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.isBlank()) {
            return FALLBACK;
        }
        if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            name = name + ".pdf";
        }
        if (name.length() > MAX_LENGTH) {
            name = name.substring(0, MAX_LENGTH - 4) + ".pdf";
        }
        return name;
    }
}
