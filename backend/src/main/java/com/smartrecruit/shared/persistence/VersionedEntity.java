package com.smartrecruit.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/**
 * Horodatage plus {@code version} : verrouillage optimiste pour les tables modifiées par plusieurs
 * personnes. Deux modifications concurrentes de la même ligne donnent {@code 409 CONCURRENT_MODIFICATION}
 * au lieu d'écraser silencieusement la première.
 */
@MappedSuperclass
public abstract class VersionedEntity extends TimestampedEntity {

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Long getVersion() {
        return version;
    }
}
