package com.smartrecruit.referential.application;

import com.smartrecruit.referential.api.CategoryCatalog;
import com.smartrecruit.referential.api.CategorySummary;
import com.smartrecruit.referential.infrastructure.JobCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Catégories d'offres proposées dans les formulaires et les filtres (lecture). */
@Service
public class CategoryService implements CategoryCatalog {

    private final JobCategoryRepository categories;

    public CategoryService(JobCategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<CategoryView> listActive() {
        return categories.findByActiveTrueOrderByNameAsc().stream()
                .map(category -> new CategoryView(category.getId(), category.getName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CategorySummary> findCategory(UUID categoryId) {
        return categories.findById(categoryId)
                .map(category -> new CategorySummary(category.getId(), category.getName(), category.isActive()));
    }

    public record CategoryView(UUID id, String name) {
    }
}
