package com.smartrecruit.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;
import java.net.URISyntaxException;

public class HttpsUrlValidator implements ConstraintValidator<HttpsUrl, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;   // laissé à @NotBlank
        }
        try {
            URI uri = new URI(value);
            // Schéma exactement « https » (en minuscules), comme la contrainte SQL ck_candidate_link_https
            return "https".equals(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getHost().contains(".");
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
