package com.smartrecruit.referential.application;

import com.smartrecruit.audit.api.AuditEvent;
import com.smartrecruit.audit.api.AuditRecorder;
import com.smartrecruit.referential.api.ReferentialUsage;
import com.smartrecruit.referential.domain.JobCategory;
import com.smartrecruit.referential.infrastructure.JobCategoryRepository;
import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.domain.InvalidStateException;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import com.smartrecruit.shared.security.CurrentUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * US-50 : catégories d'offres gérées par l'admin. RG-30 : une catégorie utilisée ne se supprime
 * pas, elle se désactive (elle n'est plus proposée pour les nouvelles offres).
 */
@Service
public class AdminCategoryService {

    static final String CATEGORY_CREATED = "CATEGORY_CREATED";
    static final String CATEGORY_RENAMED = "CATEGORY_RENAMED";
    static final String CATEGORY_DEACTIVATED = "CATEGORY_DEACTIVATED";
    static final String CATEGORY_ACTIVATED = "CATEGORY_ACTIVATED";
    static final String CATEGORY_DELETED = "CATEGORY_DELETED";

    private final JobCategoryRepository categories;
    private final ObjectProvider<ReferentialUsage> usages;
    private final AuditRecorder audit;
    private final Clock clock;

    public AdminCategoryService(JobCategoryRepository categories, ObjectProvider<ReferentialUsage> usages,
                                AuditRecorder audit, Clock clock) {
        this.categories = categories;
        this.usages = usages;
        this.audit = audit;
        this.clock = clock;
    }

    /** Actives et désactivées, triées par nom, avec leur nombre d'offres (deux requêtes). */
    @Transactional(readOnly = true)
    public List<CategoryAdminView> list() {
        List<JobCategory> all = categories.findAllByOrderByNameAsc();
        Map<UUID, Long> usage = usage(all.stream().map(JobCategory::getId).toList());
        return all.stream().map(category -> new CategoryAdminView(category, usage.getOrDefault(category.getId(), 0L)))
                .toList();
    }

    @Transactional
    public CategoryAdminView create(String name, CurrentUser admin) {
        JobCategory category = categories.saveAndFlush(JobCategory.create(name, clock.instant()));
        audit.record(AuditEvent.success(CATEGORY_CREATED).by(admin.id(), null).on("CATEGORY", category.getId())
                .with("name", category.getName()));
        return new CategoryAdminView(category, 0);
    }

    @Transactional
    public CategoryAdminView rename(UUID id, String name, CurrentUser admin) {
        JobCategory category = load(id);
        String previous = category.getName();
        category.rename(name);
        categories.flush();
        audit.record(AuditEvent.success(CATEGORY_RENAMED).by(admin.id(), null).on("CATEGORY", id)
                .with("from", previous).with("to", category.getName()));
        return view(category);
    }

    @Transactional
    public CategoryAdminView setActive(UUID id, boolean active, CurrentUser admin) {
        JobCategory category = load(id);
        if (category.isActive() != active) {
            if (active) {
                category.activate();
            } else {
                category.deactivate();
            }
            audit.record(AuditEvent.success(active ? CATEGORY_ACTIVATED : CATEGORY_DEACTIVATED).by(admin.id(), null)
                    .on("CATEGORY", id));
        }
        return view(category);
    }

    /**
     * RG-30. La vérification donne un message clair ; la clé étrangère {@code fk_job_offer_category}
     * reste le dernier rempart si une offre est créée au même instant.
     */
    @Transactional
    public void delete(UUID id, CurrentUser admin) {
        JobCategory category = load(id);
        long used = usage(List.of(id)).getOrDefault(id, 0L);
        if (used > 0) {
            throw new InvalidStateException(ErrorCode.RESOURCE_IN_USE,
                    "Cette catégorie est utilisée par " + used + " offre(s) : désactivez-la plutôt.",
                    Map.of("usageCount", used));
        }
        categories.delete(category);
        categories.flush();
        audit.record(AuditEvent.success(CATEGORY_DELETED).by(admin.id(), null).on("CATEGORY", id)
                .with("name", category.getName()));
    }

    private CategoryAdminView view(JobCategory category) {
        return new CategoryAdminView(category, usage(List.of(category.getId())).getOrDefault(category.getId(), 0L));
    }

    private JobCategory load(UUID id) {
        return categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Catégorie"));
    }

    private Map<UUID, Long> usage(Collection<UUID> ids) {
        Map<UUID, Long> total = new HashMap<>();
        if (!ids.isEmpty()) {
            usages.orderedStream().forEach(usage -> usage.categoryUsage(ids).forEach((id, count) ->
                    total.merge(id, count, Long::sum)));
        }
        return total;
    }

    public record CategoryAdminView(JobCategory category, long offerCount) {
    }
}
