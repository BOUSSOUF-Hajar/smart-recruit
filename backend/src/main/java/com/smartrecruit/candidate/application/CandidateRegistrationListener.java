package com.smartrecruit.candidate.application;

import com.smartrecruit.candidate.domain.CandidateProfile;
import com.smartrecruit.candidate.infrastructure.CandidateProfileRepository;
import com.smartrecruit.identity.api.CandidateRegistered;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crée le profil vide d'un nouveau candidat (RG-04).
 * <p>
 * {@code @EventListener} synchrone, et non {@code @ApplicationModuleListener} (asynchrone, après
 * validation) : le profil doit exister dès la fin de l'inscription, et un échec ici doit annuler
 * l'inscription. Les deux modules restent découplés : Identité ignore l'existence de ce listener.
 */
@Component
class CandidateRegistrationListener {

    private final CandidateProfileRepository profiles;

    CandidateRegistrationListener(CandidateProfileRepository profiles) {
        this.profiles = profiles;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(CandidateRegistered event) {
        profiles.save(CandidateProfile.emptyFor(event.userId()));
    }
}
