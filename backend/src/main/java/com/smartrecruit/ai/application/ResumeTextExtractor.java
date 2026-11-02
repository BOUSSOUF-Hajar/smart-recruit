package com.smartrecruit.ai.application;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * F-AI-01 : texte du PDF, étape déterministe sans IA. Un PDF sans texte exploitable (scan) donne
 * {@code FILE_UNREADABLE} : « CV non analysable automatiquement », la candidature reste traitable.
 */
@Component
public class ResumeTextExtractor {

    /** En dessous, le PDF est probablement une image scannée. */
    static final int MIN_LETTERS = 50;

    public String extract(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            long letters = text.chars().filter(Character::isLetter).count();
            if (letters < MIN_LETTERS) {
                throw AiException.unreadable();
            }
            return text.strip();
        } catch (IOException e) {
            throw AiException.unreadable();
        }
    }
}
