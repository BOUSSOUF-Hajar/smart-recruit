package com.smartrecruit.referential.infrastructure;

import com.smartrecruit.referential.domain.SkillAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SkillAliasRepository extends JpaRepository<SkillAlias, String> {

    List<SkillAlias> findBySkillIdIn(Collection<UUID> skillIds);

    /** Fusion (US-51) : les alias de la compétence absorbée désignent désormais la cible. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SkillAlias a set a.skillId = :target where a.skillId = :source")
    int reassign(@Param("source") UUID sourceId, @Param("target") UUID targetId);
}
