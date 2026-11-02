package com.smartrecruit.candidate.domain;

/** RG-07 : un CV est un PDF de 5 Mo au plus. Même valeur dans la configuration multipart et la contrainte SQL. */
public final class ResumeRules {

    public static final int MAX_SIZE_BYTES = 5 * 1024 * 1024;
    public static final String CONTENT_TYPE = "application/pdf";
    public static final String EXTENSION = "pdf";

    private ResumeRules() {
    }
}
