package com.smartrecruit.demo;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** CV de démonstration : un vrai PDF texte, que l'extraction PDFBox et l'IA savent lire. */
final class DemoPdf {

    private DemoPdf() {
    }

    /** Coupe les lignes trop longues pour la largeur de la page (environ 100 caractères en corps 10). */
    static List<String> wrap(List<String> lines) {
        List<String> wrapped = new ArrayList<>();
        for (String line : lines) {
            String rest = line;
            while (rest.length() > 100) {
                int cut = rest.lastIndexOf(' ', 100);
                cut = cut <= 0 ? 100 : cut;
                wrapped.add(rest.substring(0, cut));
                rest = rest.substring(cut).strip();
            }
            wrapped.add(rest);
        }
        return wrapped;
    }

    static byte[] resume(List<String> lines) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setLeading(15);
                content.newLineAtOffset(50, 740);
                boolean first = true;
                for (String line : wrap(lines)) {
                    boolean heading = first || line.equals(line.toUpperCase()) && !line.isBlank();
                    content.setFont(heading ? bold : regular, first ? 16 : 10);
                    content.showText(line);
                    content.newLine();
                    first = false;
                }
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
