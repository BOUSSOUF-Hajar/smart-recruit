package com.smartrecruit.referential.api;

import java.util.Optional;
import java.util.UUID;

/** Lecture des catégories d'offres par les autres modules. */
public interface CategoryCatalog {

    Optional<CategorySummary> findCategory(UUID categoryId);
}
