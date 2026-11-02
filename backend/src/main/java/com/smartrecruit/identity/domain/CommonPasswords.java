package com.smartrecruit.identity.domain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Liste des mots de passe courants refusés (ENF-SEC-02), chargée une fois depuis
 * {@code security/common-passwords.txt}. Comparaison insensible à la casse.
 * <p>
 * La longueur minimale de 12 caractères écarte déjà l'essentiel des listes publiques ;
 * celle-ci ne garde que les entrées assez longues pour passer ce premier filtre.
 */
public final class CommonPasswords {

    static final String RESOURCE = "/security/common-passwords.txt";

    private static final Set<String> PASSWORDS = load();

    private CommonPasswords() {
    }

    public static boolean contains(String password) {
        return password != null && PASSWORDS.contains(password.strip().toLowerCase(Locale.ROOT));
    }

    static int size() {
        return PASSWORDS.size();
    }

    private static Set<String> load() {
        try (InputStream in = CommonPasswords.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Ressource introuvable : " + RESOURCE);
            }
            Set<String> passwords = new HashSet<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String entry = line.strip();
                    if (!entry.isEmpty() && !entry.startsWith("#")) {
                        passwords.add(entry.toLowerCase(Locale.ROOT));
                    }
                }
            }
            return Set.copyOf(passwords);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
