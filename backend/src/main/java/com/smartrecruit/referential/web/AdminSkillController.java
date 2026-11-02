package com.smartrecruit.referential.web;

import com.smartrecruit.referential.application.AdminSkillService;
import com.smartrecruit.referential.application.AdminSkillService.SkillAdminView;
import com.smartrecruit.shared.security.CurrentUser;
import com.smartrecruit.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** {@code /admin/skills} (US-51) : recherche, renommage, fusion, (dés)activation. */
@RestController
@RequestMapping("/api/v1/admin/skills")
class AdminSkillController {

    private final AdminSkillService skills;

    AdminSkillController(AdminSkillService skills) {
        this.skills = skills;
    }

    @GetMapping
    PageResponse<SkillAdminResponse> list(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) Boolean active,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(skills.search(q, active, PageResponse.page(page), PageResponse.size(size)),
                SkillAdminResponse::of);
    }

    @PutMapping("/{id}")
    SkillAdminResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameRequest request, CurrentUser admin) {
        return SkillAdminResponse.of(skills.rename(id, request.name(), admin));
    }

    /** La source disparaît : la réponse décrit la cible, avec ses nouveaux alias et utilisations. */
    @PostMapping("/{id}/merge")
    MergeResponse merge(@PathVariable UUID id, @Valid @RequestBody MergeRequest request, CurrentUser admin) {
        AdminSkillService.MergeResult result = skills.merge(id, request.targetSkillId(), admin);
        return new MergeResponse(SkillAdminResponse.of(result.target()), result.references());
    }

    @PostMapping("/{id}/deactivate")
    SkillAdminResponse deactivate(@PathVariable UUID id, CurrentUser admin) {
        return SkillAdminResponse.of(skills.setActive(id, false, admin));
    }

    @PostMapping("/{id}/activate")
    SkillAdminResponse activate(@PathVariable UUID id, CurrentUser admin) {
        return SkillAdminResponse.of(skills.setActive(id, true, admin));
    }

    record RenameRequest(@NotBlank @Size(max = 80) String name) {
    }

    record MergeRequest(@NotNull UUID targetSkillId) {
    }

    record SkillAdminResponse(UUID id, String name, boolean active, long usageCount, List<String> aliases) {

        static SkillAdminResponse of(SkillAdminView view) {
            return new SkillAdminResponse(view.skill().getId(), view.skill().getName(), view.skill().isActive(),
                    view.usageCount(), view.aliases());
        }
    }

    record MergeResponse(SkillAdminResponse target, int references) {
    }
}
