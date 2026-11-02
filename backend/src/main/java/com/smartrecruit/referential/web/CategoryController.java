package com.smartrecruit.referential.web;

import com.smartrecruit.referential.application.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catégories actives, triées par nom ; public (filtres de la recherche d'offres). */
@RestController
class CategoryController {

    private final CategoryService categories;

    CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    @GetMapping("/api/v1/categories")
    List<CategoryService.CategoryView> list() {
        return categories.listActive();
    }
}
