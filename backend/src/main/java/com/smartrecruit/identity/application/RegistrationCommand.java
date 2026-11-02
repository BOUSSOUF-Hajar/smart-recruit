package com.smartrecruit.identity.application;

/** Données validées d'une inscription ; le rôle n'en fait pas partie (RG-02). */
public record RegistrationCommand(String email, String password, String firstName, String lastName) {
}
