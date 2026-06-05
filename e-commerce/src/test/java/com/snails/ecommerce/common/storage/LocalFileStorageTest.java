package com.snails.ecommerce.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void savesReadableFileAndReturnsTraceablePath() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(tempDir.toString());
        byte[] content = "hello storage".getBytes(StandardCharsets.UTF_8);

        StoredFile storedFile = storage.save(
                "uploads",
                "product.md",
                "text/markdown",
                new ByteArrayInputStream(content));

        assertThat(storedFile.fileKey()).isNotBlank();
        assertThat(storedFile.originalFilename()).isEqualTo("product.md");
        assertThat(storedFile.contentType()).isEqualTo("text/markdown");
        assertThat(storedFile.size()).isEqualTo(content.length);
        assertThat(storage.read(storedFile.fileKey())).hasBinaryContent(content);
        assertThat(storage.resolveUrl(storedFile.fileKey())).contains("uploads");
    }
}
