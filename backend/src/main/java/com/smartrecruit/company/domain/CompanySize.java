package com.smartrecruit.company.domain;

/** Tranche d'effectif ; la valeur en base est le libellé ({@code "11-50"}), d'où le convertisseur. */
public enum CompanySize {
    S_1_10("1-10"), S_11_50("11-50"), S_51_200("51-200"), S_201_1000("201-1000"), S_1000_PLUS("1000+");

    private final String label;

    CompanySize(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static CompanySize fromLabel(String label) {
        for (CompanySize size : values()) {
            if (size.label.equals(label)) {
                return size;
            }
        }
        throw new IllegalArgumentException("Tranche inconnue : " + label);
    }
}
