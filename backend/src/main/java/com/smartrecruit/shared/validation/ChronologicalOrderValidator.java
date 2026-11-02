package com.smartrecruit.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

public class ChronologicalOrderValidator implements ConstraintValidator<ChronologicalOrder, Record> {

    private String start;
    private String end;
    private String message;

    @Override
    public void initialize(ChronologicalOrder annotation) {
        this.start = annotation.start();
        this.end = annotation.end();
        this.message = annotation.message();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean isValid(Record value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        Object startValue = read(value, start);
        Object endValue = read(value, end);
        if (startValue == null || endValue == null) {
            return true;
        }
        if (((Comparable) endValue).compareTo(startValue) >= 0) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addPropertyNode(end).addConstraintViolation();
        return false;
    }

    private static Object read(Record value, String component) {
        RecordComponent found = Arrays.stream(value.getClass().getRecordComponents())
                .filter(c -> c.getName().equals(component))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        value.getClass().getSimpleName() + " n'a pas de composante " + component));
        try {
            // Les DTO sont souvent des records non publics, déclarés dans le paquet de leur contrôleur
            java.lang.reflect.Method accessor = found.getAccessor();
            accessor.setAccessible(true);
            return accessor.invoke(value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Lecture impossible de " + component, e);
        }
    }
}
