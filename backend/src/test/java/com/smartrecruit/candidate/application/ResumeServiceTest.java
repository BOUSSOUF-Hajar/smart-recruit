package com.smartrecruit.candidate.application;

import com.smartrecruit.candidate.api.ResumeUsage;
import com.smartrecruit.shared.security.CurrentUser;
import com.smartrecruit.shared.security.Role;
import com.smartrecruit.shared.storage.FileStorage;
import com.smartrecruit.support.TestPdfs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US-17 : le fichier écrit est supprimé si l'enregistrement en base échoue (compensation). */
class ResumeServiceTest {

    private final FileStorage storage = mock(FileStorage.class);
    private final ResumeRegistry registry = mock(ResumeRegistry.class);
    private final ResumeUsage usage = mock(ResumeUsage.class);
    private final ResumeService service = new ResumeService(new ResumeInspector(), registry, storage,
            new StaticListableBeanFactory(Map.of("usage", usage)).getBeanProvider(ResumeUsage.class));
    private final CurrentUser candidate = new CurrentUser(UUID.randomUUID(), Role.CANDIDATE, null);

    @Test
    void storedFileIsDeletedWhenTheDatabaseFails() {
        when(storage.store(any(), eq("pdf"))).thenReturn("0192a7c4-5b1e-7c3d-9f20-6a1b2c3d4e5f.pdf");
        when(registry.activate(any(), anyString(), anyString(), anyInt(), anyString()))
                .thenThrow(new DataIntegrityViolationException("uk_resume_active_candidate"));

        assertThatThrownBy(() -> service.upload(candidate, new UploadedFile("cv.pdf", "application/pdf", TestPdfs.valid())))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(storage).delete("0192a7c4-5b1e-7c3d-9f20-6a1b2c3d4e5f.pdf");
    }

    @Test
    void invalidFileIsNeverStored() {
        assertThatThrownBy(() -> service.upload(candidate,
                new UploadedFile("cv.pdf", "application/pdf", TestPdfs.textRenamedAsPdf())));

        verify(storage, never()).store(any(), anyString());
    }

    @Test
    void deletedResumeFileIsRemovedAfterTheDatabase() {
        when(registry.remove(eq(candidate.id()), any())).thenReturn(Optional.of("cle.pdf"));

        service.delete(candidate);

        verify(storage).delete("cle.pdf");
    }

    /** US-19 : le registre ne renvoie pas de clé pour un CV joint (désactivé) ; le fichier reste. */
    @Test
    @SuppressWarnings("unchecked")
    void attachedResumeIsOnlyDeactivatedAndItsFileKept() {
        UUID resumeId = UUID.randomUUID();
        when(usage.isAttached(resumeId)).thenReturn(true);
        when(registry.remove(eq(candidate.id()), any())).thenAnswer(invocation -> {
            Predicate<UUID> attached = invocation.getArgument(1, Predicate.class);
            return attached.test(resumeId) ? Optional.empty() : Optional.of("cle.pdf");
        });

        service.delete(candidate);

        verify(storage, never()).delete(anyString());
    }

    @Test
    void sha256IsHexEncoded() {
        org.assertj.core.api.Assertions.assertThat(ResumeService.sha256("abc".getBytes()))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
