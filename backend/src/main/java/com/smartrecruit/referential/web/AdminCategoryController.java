package com.smartrecruit.referential.web;

import com.smartrecruit.referential.application.AdminCategoryService;
import com.smartrecruit.referential.application.AdminCategoryService.CategoryAdminView;
import com.smartrecruit.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** {@code /admin/categories} (US-50) : liste complète, création, renommage, (dés)activation, suppression. */
@RestController
@RequestMapping("/api/v1/admin/categories")
class AdminCategoryController {

    private final AdminCategoryService categories;

    AdminCategoryController(AdminCategoryService categories) {
        this.categories = categories;
    }

    @GetMapping
    List<CategoryAdminResponse> list() {
        return categories.list().stream().map(CategoryAdminResponse::of).toList();
    }

    @PostMapping
    ResponseEntity<CategoryAdminResponse> create(@Valid @RequestBody CategoryRequest request, CurrentUser admin) {
        CategoryAdminResponse created = CategoryAdminResponse.of(categories.create(request.name(), admin));
        return ResponseEntity.created(URI.create("/api/v1/admin/categories/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    CategoryAdminResponse rename(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request,
                                 CurrentUser admin) {
        return CategoryAdminResponse.of(categories.rename(id, request.name(), admin));
    }

    @PostMapping("/{id}/deactivate")
    CategoryAdminResponse deactivate(@PathVariable UUID id, CurrentUser admin) {
        return CategoryAdminResponse.of(categories.setActive(id, false, admin));
    }

    @PostMapping("/{id}/activate")
    CategoryAdminResponse activate(@PathVariable UUID id, CurrentUser admin) {
        return CategoryAdminResponse.of(categories.setActive(id, true, admin));
    }

    /** 409 RESOURCE_IN_USE si une offre l'utilise : le frontend propose alors la désactivation. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, CurrentUser admin) {
        categories.delete(id, admin);
    }

    record CategoryRequest(@NotBlank @Size(min = 2, max = 80) String name) {
    }

    record CategoryAdminResponse(UUID id, String name, boolean active, long offerCount) {

        static CategoryAdminResponse of(CategoryAdminView view) {
            return new CategoryAdminResponse(view.category().getId(), view.category().getName(),
                    view.category().isActive(), view.offerCount());
        }
    }
}
