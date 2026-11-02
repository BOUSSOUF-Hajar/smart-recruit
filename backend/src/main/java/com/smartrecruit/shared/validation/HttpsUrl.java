package com.smartrecruit.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Adresse web absolue en {@code https}, avec un nom d'hôte. Refuse {@code http:}, {@code javascript:},
 * {@code data:} et les adresses relatives : le lien est affiché aux recruteurs, il ne doit rien exécuter.
 */
@Documented
@Constraint(validatedBy = HttpsUrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpsUrl {

    String message() default "doit être une adresse https valide";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
