package com.smartrecruit.identity.web;

import com.smartrecruit.identity.api.RecruiterAffiliations.Affiliation;
import com.smartrecruit.identity.application.AdminUserService;
import com.smartrecruit.identity.application.AdminUserService.AdminUserView;
import com.smartrecruit.identity.application.StaffAccountService;
import com.smartrecruit.identity.domain.User;
import com.smartrecruit.shared.web.PageResponse;
import com.smartrecruit.identity.application.StaffAccountService.CreatedStaffAccount;
import com.smartrecruit.shared.security.CurrentUser;
import com.smartrecruit.shared.security.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/** {@code /admin/users} (réservé ADMIN par la configuration de sécurité) : US-47 et US-48. */
@RestController
@RequestMapping("/api/v1/admin/users")
class AdminUserController {

    private final StaffAccountService staffAccounts;
    private final AdminUserService adminUsers;

    AdminUserController(StaffAccountService staffAccounts, AdminUserService adminUsers) {
        this.staffAccounts = staffAccounts;
        this.adminUsers = adminUsers;
    }

    /** US-48 : filtres combinables, tri par nom. */
    @GetMapping
    PageResponse<AdminUserResponse> list(@RequestParam(required = false) Role role,
                                         @RequestParam(required = false) UUID companyId,
                                         @RequestParam(required = false) Boolean enabled,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(adminUsers.search(new AdminUserService.SearchCriteria(role, companyId, enabled, q),
                PageResponse.page(page), PageResponse.size(size)), AdminUserResponse::of);
    }

    @GetMapping("/{id}")
    AdminUserResponse get(@PathVariable UUID id) {
        return AdminUserResponse.of(adminUsers.get(id));
    }

    /** Nom, entreprise et poste ; le rôle n'est pas modifiable (G-3) : il n'est même pas lu. */
    @PutMapping("/{id}")
    AdminUserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request, CurrentUser admin) {
        return AdminUserResponse.of(adminUsers.update(id, new AdminUserService.UpdateCommand(request.firstName(),
                request.lastName(), request.companyId(), request.jobTitle(), request.version()), admin));
    }

    @PostMapping("/{id}/disable")
    AdminUserResponse disable(@PathVariable UUID id, CurrentUser admin) {
        return AdminUserResponse.of(adminUsers.disable(id, admin));
    }

    @PostMapping("/{id}/enable")
    AdminUserResponse enable(@PathVariable UUID id, CurrentUser admin) {
        return AdminUserResponse.of(adminUsers.enable(id, admin));
    }

    /** US-47. 201 avec le mot de passe temporaire, jamais mis en cache ni renvoyé une seconde fois. */
    @PostMapping
    ResponseEntity<CreatedStaffResponse> create(@Valid @RequestBody CreateStaffRequest request, CurrentUser admin) {
        CreatedStaffAccount created = staffAccounts.create(new StaffAccountService.CreateStaffCommand(
                request.email(), request.firstName(), request.lastName(), request.role(), request.companyId(),
                request.jobTitle()), admin);
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + created.user().getId()))
                .cacheControl(CacheControl.noStore())
                .body(new CreatedStaffResponse(StaffUserResponse.of(created), created.temporaryPassword()));
    }

    record CreateStaffRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotNull Role role,
            UUID companyId,
            @Size(max = 120) String jobTitle) {
    }

    record StaffUserResponse(UUID id, String email, String firstName, String lastName, Role role, UUID companyId,
                             boolean enabled, boolean mustChangePassword) {

        static StaffUserResponse of(CreatedStaffAccount created) {
            var user = created.user();
            return new StaffUserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                    user.getRole(), created.companyId(), user.isEnabled(), user.isMustChangePassword());
        }
    }

    record CreatedStaffResponse(StaffUserResponse user, String temporaryPassword) {
    }

    record UpdateUserRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            UUID companyId,
            @Size(max = 120) String jobTitle,
            @NotNull Long version) {
    }

    /** Vue admin d'un compte : jamais le hash du mot de passe ni la version de session. */
    record AdminUserResponse(UUID id, String email, String firstName, String lastName, Role role, boolean enabled,
                             boolean mustChangePassword, Instant lastLoginAt, Instant createdAt, UUID companyId,
                             String companyName, String jobTitle, long version) {

        static AdminUserResponse of(AdminUserView view) {
            User user = view.user();
            Affiliation affiliation = view.affiliation();
            return new AdminUserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                    user.getRole(), user.isEnabled(), user.isMustChangePassword(), user.getLastLoginAt(),
                    user.getCreatedAt(), affiliation == null ? null : affiliation.companyId(),
                    affiliation == null ? null : affiliation.companyName(),
                    affiliation == null ? null : affiliation.jobTitle(),
                    user.getVersion() == null ? 0 : user.getVersion());
        }
    }
}
