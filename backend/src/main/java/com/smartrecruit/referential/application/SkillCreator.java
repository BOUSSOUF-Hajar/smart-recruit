package com.smartrecruit.referential.application;

import com.smartrecruit.referential.domain.Skill;
import com.smartrecruit.referential.infrastructure.SkillRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Insertion d'une compétence dans sa propre transaction. Classe séparée de {@link SkillService} :
 * un appel interne à la même classe ne passerait pas par le proxy transactionnel.
 * <p>
 * Si deux utilisateurs créent « Quarkus » au même instant, l'un des deux INSERT viole
 * {@code uk_skill_normalized_name} ; seule cette petite transaction est annulée, et l'appelant
 * relit la compétence créée par l'autre.
 */
@Component
class SkillCreator {

    private final SkillRepository skills;
    private final Clock clock;

    SkillCreator(SkillRepository skills, Clock clock) {
        this.skills = skills;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Skill create(String rawName, UUID createdBy) {
        return skills.saveAndFlush(Skill.create(rawName, createdBy, clock.instant()));
    }
}
