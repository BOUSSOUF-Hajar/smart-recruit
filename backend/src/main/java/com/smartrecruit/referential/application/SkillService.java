package com.smartrecruit.referential.application;

import com.smartrecruit.referential.api.SkillCatalog;
import com.smartrecruit.referential.api.SkillSummary;
import com.smartrecruit.referential.domain.Skill;
import com.smartrecruit.referential.domain.SkillAlias;
import com.smartrecruit.referential.domain.SkillNames;
import com.smartrecruit.referential.infrastructure.SkillAliasRepository;
import com.smartrecruit.referential.infrastructure.SkillRepository;
import com.smartrecruit.shared.domain.FieldValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Recherche et création des compétences (US-15) ; implémente aussi l'API {@link SkillCatalog}. */
@Service
public class SkillService implements SkillCatalog {

    public static final int MAX_RESULTS = 20;

    private final SkillRepository skills;
    private final SkillAliasRepository aliases;
    private final SkillCreator creator;

    public SkillService(SkillRepository skills, SkillAliasRepository aliases, SkillCreator creator) {
        this.skills = skills;
        this.aliases = aliases;
        this.creator = creator;
    }

    /** Compétences actives dont le nom ou un alias commence par {@code query}, 20 au maximum. */
    @Transactional(readOnly = true)
    public List<SkillView> search(String query, int size) {
        String prefix = escapeLike(SkillNames.normalize(query == null ? "" : query)) + "%";
        int limit = Math.clamp(size, 1, MAX_RESULTS);
        return skills.searchActive(prefix, Limit.of(limit)).stream().map(SkillView::of).toList();
    }

    /**
     * « Obtenir ou créer » : renvoie la compétence existante si la forme normalisée correspond
     * à un nom ou à un alias, sinon la crée. Jamais de doublon, même en cas de création simultanée.
     * <p>
     * Volontairement sans transaction englobante : la création a la sienne ({@link SkillCreator}),
     * pour pouvoir relire après un conflit.
     */
    public GetOrCreateResult getOrCreate(String rawName, UUID createdBy) {
        String normalized = SkillNames.normalize(rawName);
        if (normalized.length() < 2) {
            throw new FieldValidationException("name", "doit contenir au moins 2 caractères");
        }
        Optional<Skill> existing = find(normalized);
        if (existing.isPresent()) {
            return new GetOrCreateResult(SkillView.of(existing.get()), false);
        }
        try {
            return new GetOrCreateResult(SkillView.of(creator.create(rawName, createdBy)), true);
        } catch (DataIntegrityViolationException concurrentCreation) {
            return find(normalized)
                    .map(skill -> new GetOrCreateResult(SkillView.of(skill), false))
                    .orElseThrow(() -> concurrentCreation);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, SkillSummary> findByIds(Collection<UUID> ids) {
        return skills.findAllById(ids).stream()
                .map(skill -> new SkillSummary(skill.getId(), skill.getName(), skill.isActive()))
                .collect(Collectors.toMap(SkillSummary::id, Function.identity()));
    }

    /** Trois requêtes quel que soit le nombre de noms : alias, compétences des alias, noms directs. */
    @Override
    @Transactional(readOnly = true)
    public Map<String, SkillSummary> resolve(Collection<String> rawNames) {
        Map<String, String> normalizedByRaw = new java.util.LinkedHashMap<>();
        rawNames.stream().filter(name -> name != null && !name.isBlank())
                .forEach(name -> normalizedByRaw.put(name, SkillNames.normalize(name)));
        if (normalizedByRaw.isEmpty()) {
            return Map.of();
        }
        Map<String, UUID> aliasTargets = aliases.findAllById(normalizedByRaw.values()).stream()
                .collect(Collectors.toMap(SkillAlias::getNormalizedAlias, SkillAlias::getSkillId));
        Map<UUID, Skill> byId = skills.findAllById(aliasTargets.values()).stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));
        Map<String, Skill> byName = skills.findByNormalizedNameIn(normalizedByRaw.values()).stream()
                .collect(Collectors.toMap(Skill::getNormalizedName, Function.identity()));
        Map<String, SkillSummary> resolved = new java.util.LinkedHashMap<>();
        normalizedByRaw.forEach((raw, normalized) -> {
            Skill skill = aliasTargets.containsKey(normalized) ? byId.get(aliasTargets.get(normalized))
                    : byName.get(normalized);
            if (skill != null) {
                resolved.put(raw, new SkillSummary(skill.getId(), skill.getName(), skill.isActive()));
            }
        });
        return resolved;
    }

    /** L'alias d'abord : une compétence absorbée par une fusion renvoie vers sa cible. */
    private Optional<Skill> find(String normalized) {
        return aliases.findById(normalized)
                .flatMap(alias -> skills.findById(alias.getSkillId()))
                .or(() -> skills.findByNormalizedName(normalized));
    }

    static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /** @param created vrai si la compétence vient d'être ajoutée au référentiel (201), faux si elle existait (200) */
    public record GetOrCreateResult(SkillView skill, boolean created) {
    }
}
