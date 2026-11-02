package com.smartrecruit.identity.application;

import java.security.SecureRandom;

/**
 * Mots de passe temporaires des comptes créés par l'admin (US-47) : 16 caractères aléatoires en
 * 4 groupes, sans caractères ambigus (0/O, 1/l/I), faciles à dicter ou recopier.
 */
final class TemporaryPasswords {

    private static final char[] ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int GROUPS = 4;
    private static final int GROUP_SIZE = 4;

    private TemporaryPasswords() {
    }

    /** Environ 94 bits d'entropie : impossible à deviner pendant sa courte vie. */
    static String generate() {
        StringBuilder password = new StringBuilder();
        for (int group = 0; group < GROUPS; group++) {
            if (group > 0) {
                password.append('-');
            }
            for (int i = 0; i < GROUP_SIZE; i++) {
                password.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
            }
        }
        return password.toString();
    }
}
