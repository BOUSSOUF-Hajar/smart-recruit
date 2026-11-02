package com.smartrecruit.job.domain;

/** Statut d'une offre (analyse fonctionnelle §4.2). L'expiration n'est pas un statut : c'est une date dépassée, qui masque l'offre. */
public enum JobStatus {
    DRAFT, PUBLISHED, CLOSED, ARCHIVED
}
