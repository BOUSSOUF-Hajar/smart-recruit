package com.smartrecruit.candidate.infrastructure;

import com.smartrecruit.referential.api.ReferentialUsage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Compétences des profils candidats, vues par le référentiel (US-51). */
@Component
class CandidateReferentialUsage implements ReferentialUsage {

    private static final String LEVELS = "ARRAY['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT']::varchar[]";

    private final JdbcClient jdbc;

    CandidateReferentialUsage(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> skillUsage(Collection<UUID> skillIds) {
        Map<UUID, Long> counts = new HashMap<>();
        if (!skillIds.isEmpty()) {
            jdbc.sql("select skill_id, count(*) from candidate_skill where skill_id in (:ids) group by skill_id")
                    .param("ids", skillIds)
                    .query((rs, row) -> Map.entry(rs.getObject(1, UUID.class), rs.getLong(2)))
                    .list().forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        }
        return counts;
    }

    /**
     * Un profil qui avait les deux compétences n'en garde qu'une : la cible, au niveau le plus élevé
     * et avec le plus grand nombre d'années ({@code greatest} ignore les valeurs absentes).
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int reassignSkill(UUID sourceId, UUID targetId) {
        int merged = jdbc.sql("""
                        update candidate_skill t set
                            level = ({levels})[greatest(array_position({levels}, t.level), array_position({levels}, s.level))],
                            years = greatest(t.years, s.years)
                        from candidate_skill s
                        where t.candidate_id = s.candidate_id and t.skill_id = :target and s.skill_id = :source""".replace("{levels}", LEVELS))
                .param("source", sourceId).param("target", targetId).update();
        jdbc.sql("""
                        delete from candidate_skill s
                        where s.skill_id = :source and exists (select 1 from candidate_skill t
                                                               where t.candidate_id = s.candidate_id
                                                                 and t.skill_id = :target)""")
                .param("source", sourceId).param("target", targetId).update();
        int moved = jdbc.sql("update candidate_skill set skill_id = :target where skill_id = :source")
                .param("source", sourceId).param("target", targetId).update();
        return merged + moved;
    }
}
