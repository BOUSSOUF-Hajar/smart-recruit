package com.smartrecruit.job.api;

import java.util.UUID;

/**
 * Ce que les autres modules savent d'une offre.
 *
 * @param open publiée et non expirée à l'instant de la lecture (RG-11)
 */
public record JobReference(UUID id, UUID companyId, String title, String city, boolean open) {
}
