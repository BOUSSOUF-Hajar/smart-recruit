package com.smartrecruit.company.web;

import com.smartrecruit.company.application.CompanyService;
import com.smartrecruit.company.application.CompanyService.CompanyView;
import com.smartrecruit.company.application.CompanyService.Deactivation;
import com.smartrecruit.company.domain.Company;
import com.smartrecruit.company.domain.CompanySize;
import com.smartrecruit.shared.security.CurrentUser;
import com.smartrecruit.shared.validation.HttpsUrl;
import com.smartrecruit.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;

/** {@code /admin/companies} : création, consultation, modification, désactivation en cascade (US-49). */
@RestController
@RequestMapping("/api/v1/admin/companies")
class AdminCompanyController {

    private final CompanyService companies;

    AdminCompanyController(CompanyService companies) {
        this.companies = companies;
    }

    @GetMapping
    PageResponse<CompanyResponse> list(@RequestParam(required = false) Boolean active,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(companies.search(active, q, PageResponse.page(page), PageResponse.size(size)),
                CompanyResponse::of);
    }

    @GetMapping("/{id}")
    CompanyResponse get(@PathVariable UUID id) {
        return CompanyResponse.of(companies.get(id));
    }

    @PostMapping
    ResponseEntity<CompanyResponse> create(@Valid @RequestBody CompanyRequest request, CurrentUser admin) {
        CompanyResponse created = CompanyResponse.of(companies.create(request.toDetails(), admin));
        return ResponseEntity.created(URI.create("/api/v1/admin/companies/" + created.id())).body(created);
    }

    /** Mêmes champs que la création, plus la version lue (verrouillage optimiste), obligatoire ici. */
    @PutMapping("/{id}")
    CompanyResponse update(@PathVariable UUID id, @Valid @RequestBody CompanyRequest request, CurrentUser admin) {
        if (request.version() == null) {
            throw new com.smartrecruit.shared.domain.FieldValidationException("version", "Obligatoire.");
        }
        return CompanyResponse.of(companies.update(id, request.toDetails(), request.version(), admin));
    }

    /** RG-31 : la réponse dit ce que la cascade a fait. Rejouer l'appel ne change plus rien. */
    @PostMapping("/{id}/deactivate")
    DeactivationResponse deactivate(@PathVariable UUID id, CurrentUser admin) {
        return DeactivationResponse.of(companies.deactivate(id, admin));
    }

    /** Les recruteurs restent désactivés : l'admin réactive ceux qu'il veut (US-48). */
    @PostMapping("/{id}/activate")
    CompanyResponse activate(@PathVariable UUID id, CurrentUser admin) {
        return CompanyResponse.of(companies.activate(id, admin));
    }

    record DeactivationResponse(CompanyResponse company, int closedJobs, int disabledRecruiters) {

        static DeactivationResponse of(Deactivation deactivation) {
            return new DeactivationResponse(CompanyResponse.of(deactivation.company()), deactivation.closedJobs(),
                    deactivation.disabledRecruiters());
        }
    }

    record CompanyRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 80) String sector,
            @Pattern(regexp = "1-10|11-50|51-200|201-1000|1000\\+", message = "tranche inconnue") String sizeRange,
            @Size(max = 80) String city,
            @Pattern(regexp = "[A-Za-z]{2}", message = "doit être un code pays ISO à 2 lettres") String countryCode,
            @Size(max = 255) @HttpsUrl String website,
            @Size(max = 5000) String description,
            @Size(max = 64) String timezone,
            Long version) {

        Company.CompanyDetails toDetails() {
            return new Company.CompanyDetails(name, sector, sizeRange == null ? null : CompanySize.fromLabel(sizeRange),
                    city, countryCode, website, description, zone(timezone));
        }

        private static ZoneId zone(String timezone) {
            if (timezone == null || timezone.isBlank()) {
                return null;
            }
            try {
                return ZoneId.of(timezone);
            } catch (DateTimeException e) {
                throw new com.smartrecruit.shared.domain.FieldValidationException("timezone", "Fuseau horaire inconnu.");
            }
        }
    }

    record CompanyResponse(UUID id, String name, String sector, String sizeRange, String city, String countryCode,
                           String website, String description, String timezone, boolean active, long recruiterCount,
                           long jobCount, long publishedJobCount, long version) {

        static CompanyResponse of(CompanyView view) {
            Company company = view.company();
            return new CompanyResponse(company.getId(), company.getName(), company.getSector(),
                    company.getSizeRange() == null ? null : company.getSizeRange().label(), company.getCity(),
                    company.getCountryCode(), company.getWebsite(), company.getDescription(),
                    company.getTimezone().getId(), company.isActive(), view.recruiterCount(),
                    view.offerCounts().total(), view.offerCounts().published(),
                    company.getVersion() == null ? 0 : company.getVersion());
        }
    }
}
