package com.smartrecruit.company.domain;

import com.smartrecruit.shared.persistence.VersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Entreprise cliente : ses recruteurs et ses offres lui sont rattachés (RG-05). */
@Entity
@Table(name = "company")
public class Company extends VersionedEntity {

    public static final ZoneId DEFAULT_TIMEZONE = ZoneId.of("Africa/Casablanca");

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 80)
    private String sector;

    @Column(name = "size_range", length = 16)
    private CompanySize sizeRange;

    @Column(length = 80)
    private String city;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(length = 255)
    private String website;

    /** {@code TEXT} en base : sans longueur, sinon la validation du schéma attendrait un VARCHAR. */
    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 64)
    private String timezone = DEFAULT_TIMEZONE.getId();

    @Column(nullable = false)
    private boolean active = true;

    protected Company() {
        // JPA
    }

    public static Company create(CompanyDetails details) {
        Company company = new Company();
        company.update(details);
        return company;
    }

    public void update(CompanyDetails details) {
        this.name = details.name().strip();
        this.sector = blankToNull(details.sector());
        this.sizeRange = details.sizeRange();
        this.city = blankToNull(details.city());
        this.countryCode = details.countryCode() == null || details.countryCode().isBlank()
                ? null : details.countryCode().strip().toUpperCase(Locale.ROOT);
        this.website = blankToNull(details.website());
        this.description = blankToNull(details.description());
        this.timezone = (details.timezone() == null ? DEFAULT_TIMEZONE : details.timezone()).getId();
    }

    /** RG-31 : la cascade (offres clôturées, recruteurs désactivés) est orchestrée par le service. */
    public void deactivate() {
        this.active = false;
    }

    /** Ne réactive pas les recruteurs : l'admin choisit lesquels réactiver. */
    public void activate() {
        this.active = true;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSector() {
        return sector;
    }

    public CompanySize getSizeRange() {
        return sizeRange;
    }

    public String getCity() {
        return city;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getWebsite() {
        return website;
    }

    public String getDescription() {
        return description;
    }

    public ZoneId getTimezone() {
        return ZoneId.of(timezone);
    }

    public boolean isActive() {
        return active;
    }

    /** Objet valeur des champs modifiables. */
    public record CompanyDetails(String name, String sector, CompanySize sizeRange, String city, String countryCode,
                                 String website, String description, ZoneId timezone) {

        public CompanyDetails {
            Objects.requireNonNull(name, "name");
        }
    }
}
