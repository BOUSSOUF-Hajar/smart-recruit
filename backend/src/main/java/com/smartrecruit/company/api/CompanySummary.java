package com.smartrecruit.company.api;

import java.time.ZoneId;
import java.util.UUID;

/** @param timezone fuseau de l'entreprise, qui fixe l'heure d'expiration de ses offres (analyse 6.2) */
public record CompanySummary(UUID id, String name, boolean active, ZoneId timezone) {
}
