package com.smartrecruit.referential.infrastructure;

import com.smartrecruit.referential.domain.Skill;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<Skill, UUID>, JpaSpecificationExecutor<Skill> {

    /**
     * Autocomplétion : compétences actives dont le nom ou un alias commence par le préfixe.
     * Le préfixe arrive déjà normalisé et échappé ({@code !} protège {@code %} et {@code _}).
     */
    @Query("""
            select s from Skill s
            where s.active = true
              and (s.normalizedName like :prefix escape '!'
                   or s.id in (select a.skillId from SkillAlias a where a.normalizedAlias like :prefix escape '!'))
            order by s.normalizedName""")
    List<Skill> searchActive(@Param("prefix") String prefix, Limit limit);

    Optional<Skill> findByNormalizedName(String normalizedName);

    java.util.List<Skill> findByNormalizedNameIn(java.util.Collection<String> normalizedNames);
}
