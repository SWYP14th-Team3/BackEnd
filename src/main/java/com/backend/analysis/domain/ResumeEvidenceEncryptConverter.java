package com.backend.analysis.domain;

import com.backend.global.crypto.ResumeContentCrypto;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ResumeEvidenceEncryptConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return ResumeContentCrypto.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return ResumeContentCrypto.decryptIfNeeded(dbData);
    }
}
