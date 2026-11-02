package com.smartrecruit.shared.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageTest {

    @TempDir
    Path root;

    private LocalFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorage(new StorageProperties(root.resolve("resumes")));
    }

    @Test
    void storesUnderARandomUuidName() throws IOException {
        String key = storage.store(new byte[]{1, 2, 3}, "pdf");

        assertThat(key).matches("[0-9a-f-]{36}\\.pdf");
        assertThat(Files.readAllBytes(root.resolve("resumes").resolve(key))).containsExactly(1, 2, 3);
        assertThat(storage.load(key).contentLength()).isEqualTo(3);
    }

    @Test
    void leavesNoTemporaryFileBehind() throws IOException {
        storage.store(new byte[]{1}, "pdf");

        try (var files = Files.list(root.resolve("resumes"))) {
            assertThat(files).allSatisfy(file -> assertThat(file.getFileName().toString()).endsWith(".pdf"));
        }
    }

    @Test
    void refusesKeysThatCouldEscapeTheRoot() {
        assertThatThrownBy(() -> storage.load("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete("..")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.store(new byte[]{1}, "../x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingFileIsReported() {
        assertThatThrownBy(() -> storage.load("0192a7c4-5b1e-7c3d-9f20-6a1b2c3d4e5f.pdf"))
                .isInstanceOf(StoredFileNotFoundException.class);
    }

    @Test
    void deleteIsIdempotent() {
        String key = storage.store(new byte[]{1}, "pdf");

        storage.delete(key);
        storage.delete(key);

        assertThat(root.resolve("resumes").resolve(key)).doesNotExist();
    }
}
