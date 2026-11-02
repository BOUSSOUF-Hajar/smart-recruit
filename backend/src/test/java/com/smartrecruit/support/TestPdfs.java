package com.smartrecruit.support;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Fichiers de test construits à la volée : aucun binaire dans le dépôt. */
public final class TestPdfs {

    private TestPdfs() {
    }

    /** PDF valide d'une page. */
    public static byte[] valid() {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            return save(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** PDF d'une page contenant ces lignes de texte (caractères Latin-1 seulement). */
    public static byte[] withText(String... lines) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                content.setLeading(14);
                content.newLineAtOffset(40, 750);
                for (String line : lines) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            return save(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** PDF qui exige un mot de passe pour être ouvert. */
    public static byte[] passwordProtected() {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-secret", "user-secret",
                    new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            return save(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Texte ordinaire, tel qu'un fichier .txt renommé en .pdf. */
    public static byte[] textRenamedAsPdf() {
        return "Ceci n'est pas un PDF, seulement du texte renommé.".getBytes(StandardCharsets.UTF_8);
    }

    /** Commence par la signature PDF mais ne contient rien de lisible. */
    public static byte[] corrupted() {
        return "%PDF-1.7\n%garbage without objects".getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] save(PDDocument document) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }
}
