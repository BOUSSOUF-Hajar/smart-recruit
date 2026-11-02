package com.smartrecruit.job.infrastructure;

import com.smartrecruit.referential.api.ReferentialUsage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Références des offres aux catégories et aux compétences (US-50, US-51). SQL direct : la fusion
 * touche d'un coup toutes les offres concernées, sans les charger une à une.
 */
@Component
class JobReferentialUsage implements ReferentialUsage {

    /** Niveaux dans l'ordre croissant : la position sert à garder le plus exigeant. */
    private static final String LEVELS = "ARRAY['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT']::varchar[]";

    private final JdbcClient jdbc;

    JobReferentialUsage(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> categoryUsage(Collection<UUID> categoryIds) {
        return counts("select category_id, count(*) from job_offer where category_id in (:ids) group by category_id",
                categoryIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> skillUsage(Collection<UUID> skillIds) {
        return counts("select skill_id, count(*) from job_offer_skill where skill_id in (:ids) group by skill_id",
                skillIds);
    }

    /**
     * Une offre qui avait les deux compétences garde la cible, requise si l'une des deux l'était,
     * au niveau minimal le plus élevé (RG-20 : une compétence n'apparaît qu'une fois par offre).
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int reassignSkill(UUID sourceId, UUID targetId) {
        int merged = jdbc.sql("""
                        update job_offer_skill t set
                            requirement = case when 'REQUIRED' in (t.requirement, s.requirement)
                                               then 'REQUIRED' else 'PREFERRED' end,
                            min_level = case when 'REQUIRED' in (t.requirement, s.requirement)
                                             then ({levels})[greatest(coalesce(array_position({levels}, t.min_level), 0),
                                                                  coalesce(array_position({levels}, s.min_level), 0))]
                                        end
                        from job_offer_skill s
                        where t.job_offer_id = s.job_offer_id and t.skill_id = :target and s.skill_id = :source""".replace("{levels}", LEVELS))
                .param("source", sourceId).param("target", targetId).update();
        jdbc.sql("""
                        delete from job_offer_skill s
                        where s.skill_id = :source and exists (select 1 from job_offer_skill t
                                                               where t.job_offer_id = s.job_offer_id
                                                                 and t.skill_id = :target)""")
                .param("source", sourceId).param("target", targetId).update();
        int moved = jdbc.sql("update job_offer_skill set skill_id = :target where skill_id = :source")
                .param("source", sourceId).param("target", targetId).update();
        return merged + moved;
    }

    private Map<UUID, Long> counts(String sql, Collection<UUID> ids) {
        Map<UUID, Long> counts = new HashMap<>();
        if (!ids.isEmpty()) {
            jdbc.sql(sql).param("ids", ids)
                    .query((rs, row) -> Map.entry(rs.getObject(1, UUID.class), rs.getLong(2)))
                    .list().forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        }
        return counts;
    }
}
