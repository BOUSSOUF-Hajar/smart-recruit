package com.smartrecruit.referential.application;

import com.smartrecruit.audit.api.AuditEvent;
import com.smartrecruit.audit.api.AuditRecorder;
import com.smartrecruit.referential.api.ReferentialUsage;
import com.smartrecruit.referential.domain.Skill;
import com.smartrecruit.referential.domain.SkillAlias;
import com.smartrecruit.referential.domain.SkillNames;
import com.smartrecruit.referential.infrastructure.SkillAliasRepository;
import com.smartrecruit.referential.infrastructure.SkillRepository;
import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.domain.FieldValidationException;
import com.smartrecruit.shared.domain.InvalidStateException;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import com.smartrecruit.shared.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * US-51 : le référentiel de compétences vu par l'admin — recherche avec nombre d'utilisations,
 * renommage, fusion des doublons, (dés)activation (RG-30).
 */
@Service
public class AdminSkillService {

    static final String SKILL_RENAMED = "SKILL_RENAMED";
    static final String SKILL_MERGED = "SKILL_MERGED";
    static final String SKILL_DEACTIVATED = "SKILL_DEACTIVATED";
    static final String SKILL_ACTIVATED = "SKILL_ACTIVATED";

    private final SkillRepository skills;
    private final SkillAliasRepository aliases;
    private final ObjectProvider<ReferentialUsage> usages;
    private final AuditRecorder audit;

    public AdminSkillService(SkillRepository skills, SkillAliasRepository aliases,
                             ObjectProvider<ReferentialUsage> usages, AuditRecorder audit) {
        this.skills = skills;
        this.aliases = aliases;
        this.usages = usages;
        this.audit = audit;
    }

    /** Nom ou alias contenant le texte ; quatre requêtes par page (compétences, total, alias, utilisations). */
    @Transactional(readOnly = true)
    public Page<SkillAdminView> search(String query, Boolean active, int page, int size) {
        Page<Skill> found = skills.findAll(matching(query, active),
                PageRequest.of(page, size, Sort.by("normalizedName", "id")));
        List<UUID> ids = found.getContent().stream().map(Skill::getId).toList();
        Map<UUID, List<String>> aliasesById = ids.isEmpty() ? Map.of() : aliases.findBySkillIdIn(ids).stream()
                .collect(Collectors.groupingBy(SkillAlias::getSkillId,
                        Collectors.mapping(SkillAlias::getNormalizedAlias, Collectors.toList())));
        Map<UUID, Long> usage = usage(ids);
        return found.map(skill -> new SkillAdminView(skill, usage.getOrDefault(skill.getId(), 0L),
                aliasesById.getOrDefault(skill.getId(), List.of()).stream().sorted().toList()));
    }

    /**
     * Corrige le nom affiché. L'ancien nom devient un alias : profils, offres et CV qui l'écrivaient
     * ainsi restent reconnus. Un nom déjà pris par une autre compétence appelle une fusion, pas un renommage.
     */
    @Transactional
    public SkillAdminView rename(UUID id, String rawName, CurrentUser admin) {
        Skill skill = load(id);
        String normalized = SkillNames.normalize(rawName);
        if (normalized.length() < 2) {
            throw new FieldValidationException("name", "doit contenir au moins 2 caractères");
        }
        Optional<UUID> owner = ownerOf(normalized);
        if (owner.isPresent() && !owner.get().equals(id)) {
            throw new InvalidStateException(ErrorCode.RESOURCE_IN_USE,
                    "Ce nom désigne déjà une autre compétence : fusionnez-les plutôt.",
                    Map.of("skillId", owner.get()));
        }
        String previousName = skill.getName();
        String previousNormalized = skill.getNormalizedName();
        aliases.findById(normalized).ifPresent(aliases::delete);
        aliases.flush();
        skill.rename(rawName);
        skills.flush();
        if (!previousNormalized.equals(normalized)) {
            aliases.save(SkillAlias.of(previousNormalized, id));
        }
        audit.record(AuditEvent.success(SKILL_RENAMED).by(admin.id(), null).on("SKILL", id)
                .with("from", previousName).with("to", skill.getName()));
        return view(skill);
    }

    /**
     * Fusionne {@code sourceId} dans {@code targetId}, dans une seule transaction : les profils et
     * offres sont reportés sur la cible (un seul exemplaire, au niveau le plus élevé), les alias de la
     * source passent à la cible, le nom de la source devient un alias, puis la source disparaît.
     */
    @Transactional
    public MergeResult merge(UUID sourceId, UUID targetId, CurrentUser admin) {
        if (sourceId.equals(targetId)) {
            throw new FieldValidationException("targetSkillId", "Une compétence ne se fusionne pas avec elle-même.");
        }
        Skill source = load(sourceId);
        Skill target = load(targetId);
        if (!target.isActive()) {
            throw new InvalidStateException(ErrorCode.INVALID_STATE,
                    "La compétence cible est désactivée : réactivez-la avant la fusion.");
        }
        int references = usages.orderedStream().mapToInt(usage -> usage.reassignSkill(sourceId, targetId)).sum();
        int movedAliases = aliases.reassign(sourceId, targetId);
        aliases.save(SkillAlias.of(source.getNormalizedName(), targetId));
        aliases.flush();
        skills.delete(skills.getReferenceById(sourceId));
        skills.flush();
        audit.record(AuditEvent.success(SKILL_MERGED).by(admin.id(), null).on("SKILL", targetId)
                .with("sourceId", sourceId.toString()).with("sourceName", source.getName())
                .with("targetName", target.getName()).with("references", references)
                .with("aliases", movedAliases + 1));
        return new MergeResult(view(load(targetId)), references);
    }

    @Transactional
    public SkillAdminView setActive(UUID id, boolean active, CurrentUser admin) {
        Skill skill = load(id);
        if (skill.isActive() != active) {
            if (active) {
                skill.activate();
            } else {
                skill.deactivate();
            }
            audit.record(AuditEvent.success(active ? SKILL_ACTIVATED : SKILL_DEACTIVATED).by(admin.id(), null)
                    .on("SKILL", id));
        }
        return view(skill);
    }

    /** Compétence désignée par cette forme normalisée, par son nom ou par un alias. */
    private Optional<UUID> ownerOf(String normalized) {
        return skills.findByNormalizedName(normalized).map(Skill::getId)
                .or(() -> aliases.findById(normalized).map(SkillAlias::getSkillId));
    }

    private SkillAdminView view(Skill skill) {
        UUID id = skill.getId();
        List<String> names = aliases.findBySkillIdIn(List.of(id)).stream().map(SkillAlias::getNormalizedAlias)
                .sorted().toList();
        return new SkillAdminView(skill, usage(List.of(id)).getOrDefault(id, 0L), names);
    }

    private Skill load(UUID id) {
        return skills.findById(id).orElseThrow(() -> new ResourceNotFoundException("Compétence"));
    }

    private Map<UUID, Long> usage(Collection<UUID> ids) {
        Map<UUID, Long> total = new HashMap<>();
        if (!ids.isEmpty()) {
            usages.orderedStream().forEach(usage -> usage.skillUsage(ids).forEach((id, count) ->
                    total.merge(id, count, Long::sum)));
        }
        return total;
    }

    private static Specification<Skill> matching(String query, Boolean active) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            String text = query == null ? "" : SkillNames.normalize(query);
            if (!text.isEmpty()) {
                String pattern = "%" + SkillService.escapeLike(text) + "%";
                Subquery<UUID> aliased = criteriaQuery.subquery(UUID.class);
                var alias = aliased.from(SkillAlias.class);
                aliased.select(alias.get("skillId")).where(cb.like(alias.get("normalizedAlias"), pattern, '!'));
                predicates.add(cb.or(cb.like(root.get("normalizedName"), pattern, '!'), root.get("id").in(aliased)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public record SkillAdminView(Skill skill, long usageCount, List<String> aliases) {
    }

    /** @param references profils et offres reportés sur la cible */
    public record MergeResult(SkillAdminView target, int references) {
    }
}
