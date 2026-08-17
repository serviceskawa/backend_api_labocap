package com.labo.anapath.common.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    FileStorageServiceImpl service;

    @BeforeEach
    void setup() {
        service = new FileStorageServiceImpl(tempDir.toString(), StoredFilesFixture.enClair());
    }

    @Test
    @DisplayName("store - stocke le fichier et retourne un chemin relatif UUID")
    void store_savesFileAndReturnsRelativePath() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf",
                "application/pdf", "pdf content".getBytes());

        String relativePath = service.store(file, "documents");

        assertThat(relativePath).startsWith("documents/").endsWith(".pdf");
        Path absolute = service.resolve(relativePath);
        assertThat(Files.exists(absolute)).isTrue();
        assertThat(Files.readAllBytes(absolute)).isEqualTo("pdf content".getBytes());
    }

    @Test
    @DisplayName("resolve - retourne le chemin absolu correct")
    void resolve_returnsAbsolutePath() {
        String relative = "documents/somefile.pdf";
        Path absolute = service.resolve(relative);

        assertThat(absolute).isAbsolute();
        assertThat(absolute.toString()).contains("documents");
        assertThat(absolute.toString()).contains("somefile.pdf");
    }

    @Test
    @DisplayName("delete - supprime le fichier sans lever d'exception")
    void delete_removesFile() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "to_delete.png",
                "image/png", "img".getBytes());
        String path = service.store(file, "images");

        assertThat(Files.exists(service.resolve(path))).isTrue();
        service.delete(path);
        assertThat(Files.exists(service.resolve(path))).isFalse();
    }

    @Test
    @DisplayName("delete - fichier inexistant → pas d'exception")
    void delete_nonExistentFile_noException() {
        service.delete("documents/nonexistent-uuid.pdf");
    }
}
