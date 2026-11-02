package com.smartrecruit.job.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/**
 * Fourchette de salaire, facultative. Montants en {@code NUMERIC(12,2)}, jamais en flottant.
 * {@code visible} faux : le salaire sert au filtre interne mais n'est pas montré au public.
 */
@Embeddable
public class Salary {

    @Column(name = "salary_min", precision = 12, scale = 2)
    private BigDecimal min;

    @Column(name = "salary_max", precision = 12, scale = 2)
    private BigDecimal max;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "salary_currency", length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_period", length = 8)
    private SalaryPeriod period;

    @Column(name = "salary_visible", nullable = false)
    private boolean visible;

    protected Salary() {
        // JPA
    }

    public Salary(BigDecimal min, BigDecimal max, String currency, SalaryPeriod period, boolean visible) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException("Salaire minimum supérieur au maximum");
        }
        this.min = min;
        this.max = max;
        this.currency = currency == null ? "MAD" : currency.toUpperCase(Locale.ROOT);
        this.period = period;
        this.visible = visible;
    }

    /** Aucun salaire renseigné. */
    public static Salary none() {
        return new Salary(null, null, "MAD", null, false);
    }

    public boolean isDefined() {
        return min != null || max != null;
    }

    public BigDecimal getMin() {
        return min;
    }

    public BigDecimal getMax() {
        return max;
    }

    public String getCurrency() {
        return currency;
    }

    public SalaryPeriod getPeriod() {
        return period;
    }

    public boolean isVisible() {
        return visible;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Salary that && Objects.equals(min, that.min) && Objects.equals(max, that.max)
                && Objects.equals(currency, that.currency) && period == that.period && visible == that.visible;
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max, currency, period, visible);
    }
}
