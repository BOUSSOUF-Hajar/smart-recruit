package com.smartrecruit.company.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CompanySizeConverter implements AttributeConverter<CompanySize, String> {

    @Override
    public String convertToDatabaseColumn(CompanySize size) {
        return size == null ? null : size.label();
    }

    @Override
    public CompanySize convertToEntityAttribute(String label) {
        return label == null ? null : CompanySize.fromLabel(label);
    }
}
