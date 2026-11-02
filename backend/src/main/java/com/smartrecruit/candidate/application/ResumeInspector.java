package com.smartrecruit.candidate.application;

import com.smartrecruit.candidate.domain.ResumeRules;
import com.smartrecruit.shared.domain.BusinessRuleViolationException;
import com.smartrecruit.shared.domain.ErrorCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Contrôles d'un CV avant stockage, dans l'ordre de l'analyse fonctionnelle §3.2 (RG-07) :
 * taille, type déclaré, signature binaire, lisibilité. Ni l'extension ni le type annoncé par le
 * navigateur ne suffisent : un fichier texte renommé en {@code .pdf} est annoncé {@code application/pdf}.
 */
@Component
public class ResumeInspector {

    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final Set<String> ACCEPTED_TYPES = Set.of(ResumeRules.CONTENT_TYPE, "application/x-pdf");

    public void inspect(UploadedFile file) {
        byte[] content = file.content();
        if (content.length > ResumeRules.MAX_SIZE_BYTES) {
            throw new BusinessRuleViolationException(ErrorCode.FILE_TOO_LARGE,
                    "Le fichier dépasse la taille maximale de 5 Mo.");
        }
        if (content.length == 0) {
            throw new BusinessRuleViolationException(ErrorCode.FILE_UNREADABLE, "Le fichier est vide.");
        }
        if (!ACCEPTED_TYPES.contains(baseType(file.contentType())) || !startsWithPdfSignature(content)) {
            throw new BusinessRuleViolationException(ErrorCode.FILE_INVALID_TYPE,
                    "Le fichier n'est pas un PDF. Seuls les CV au format PDF sont acceptés.");
        }
        checkReadable(content);
    }

    /** Un PDF chiffré par mot de passe ou endommagé ne pourrait être lu ni par un recruteur ni par l'IA. */
    private static void checkReadable(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.getNumberOfPages() == 0) {
                throw unreadable("Le PDF ne contient aucune page.");
            }
        } catch (InvalidPasswordException e) {
            throw unreadable("Le PDF est protégé par un mot de passe. Envoyez une version sans protection.");
        } catch (IOException e) {
            throw unreadable("Le PDF est endommagé et ne peut pas être lu.");
        }
    }

    static boolean startsWithPdfSignature(byte[] content) {
        return content.length >= PDF_SIGNATURE.length
                && Arrays.equals(content, 0, PDF_SIGNATURE.length, PDF_SIGNATURE, 0, PDF_SIGNATURE.length);
    }

    private static String baseType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parameters = contentType.indexOf(';');
        return (parameters >= 0 ? contentType.substring(0, parameters) : contentType).strip().toLowerCase(Locale.ROOT);
    }

    private static BusinessRuleViolationException unreadable(String message) {
        return new BusinessRuleViolationException(ErrorCode.FILE_UNREADABLE, message);
    }
}
