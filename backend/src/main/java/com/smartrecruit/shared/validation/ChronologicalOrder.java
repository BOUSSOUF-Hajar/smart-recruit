package com.smartrecruit.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sur un record : la composante {@code end} ne précède pas la composante {@code start}
 * (dates ou années). L'erreur est rattachée au champ {@code end}, pour s'afficher sous lui.
 * Les valeurs nulles sont acceptées (date de fin vide = en cours).
 */
@Documented
@Constraint(validatedBy = ChronologicalOrderValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ChronologicalOrder {

    String start();

    String end();

    String message() default "ne peut pas précéder le début";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
