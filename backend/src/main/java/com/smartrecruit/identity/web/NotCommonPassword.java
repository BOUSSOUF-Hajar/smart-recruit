package com.smartrecruit.identity.web;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Refuse les mots de passe de la liste des mots de passe courants (ENF-SEC-02). */
@Documented
@Constraint(validatedBy = NotCommonPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotCommonPassword {

    String message() default "est trop courant, choisissez-en un autre";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
