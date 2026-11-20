package com.ayush.persistence;

import com.ayush.model.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexPersistenceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void savesAndLoadsMultipleDocumentsWithMultilineUnicodeAndSpecialContent() throws IOException {
        IndexPersistence persistence = persistence();
        List<Document> documents = List.of(
                new Document("folder/guide.md", temporaryDirectory.resolve("folder/guide.md"),
                        "First line\nSecond line\n  punctuation: !@#$%^&*()\n"),
                new Document("unicode.txt", temporaryDirectory.resolve("unicode.txt"), "Café — 你好 😀\r\n"));

        persistence.save(documents);

        assertEquals(documents, persistence.load());
    }

    @Test
    void savesAndLoadsAnEmptyIndex() throws IOException {
        IndexPersistence persistence = persistence();

        persistence.save(List.of());

        assertTrue(persistence.exists());
        assertTrue(persistence.load().isEmpty());
    }

    @Test
    void missingIndexLoadsAsEmpty() throws IOException {
        assertFalse(persistence().exists());
        assertTrue(persistence().load().isEmpty());
    }

    @Test
    void rejectsInvalidHeaderAndUnsupportedVersion() throws IOException {
        IndexPersistence persistence = persistence();
        Files.writeString(persistence.path(), "NOT_MINISEARCH\n1\n0\n", StandardCharsets.UTF_8);
        assertThrows(IOException.class, persistence::load);

        Files.writeString(persistence.path(), "MINISEARCH_INDEX\n2\n0\n", StandardCharsets.UTF_8);
        assertThrows(IOException.class, persistence::load);
    }

    @Test
    void rejectsMalformedBase64IncompleteRecordsAndWrongCounts() throws IOException {
        IndexPersistence persistence = persistence();
        Files.writeString(persistence.path(), "MINISEARCH_INDEX\n1\n1\n%%%\n", StandardCharsets.UTF_8);
        assertThrows(IOException.class, persistence::load);

        Files.writeString(persistence.path(), "MINISEARCH_INDEX\n1\n1\n", StandardCharsets.UTF_8);
        assertThrows(IOException.class, persistence::load);

        Files.writeString(persistence.path(), "MINISEARCH_INDEX\n1\n0\nextra\n", StandardCharsets.UTF_8);
        assertThrows(IOException.class, persistence::load);
    }

    @Test
    void failedSaveLeavesPreviousValidIndexAndCleansTemporaryArtifacts() throws IOException {
        IndexPersistence persistence = persistence();
        List<Document> original = List.of(new Document("old.md", Path.of("old.md"), "Old content"));
        persistence.save(original);

        assertThrows(IllegalArgumentException.class,
                () -> persistence.save(Arrays.asList(new Document("new.md", Path.of("new.md"), "New"), null)));

        assertEquals(original, persistence.load());
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(List.of(persistence.path()), files.toList());
        }
    }

    @Test
    void rejectsDuplicateIdsBeforeChangingPreviousIndex() throws IOException {
        IndexPersistence persistence = persistence();
        List<Document> original = List.of(new Document("kept.md", Path.of("kept.md"), "Kept content"));
        persistence.save(original);
        List<Document> duplicateIds = List.of(
                new Document("duplicate.md", Path.of("one/duplicate.md"), "First"),
                new Document("duplicate.md", Path.of("two/duplicate.md"), "Second"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> persistence.save(duplicateIds));

        assertTrue(exception.getMessage().contains("Duplicate document ID: duplicate.md"));
        assertEquals(original, persistence.load());
    }

    @Test
    void cleansTemporaryFileWhenReplacementMoveFails() throws IOException {
        Path targetDirectory = Files.createDirectory(temporaryDirectory.resolve("blocked-index.dat"));
        Path marker = Files.writeString(targetDirectory.resolve("keep.txt"), "keep", StandardCharsets.UTF_8);
        IndexPersistence persistence = new IndexPersistence(targetDirectory);

        assertThrows(IOException.class, () -> persistence.save(
                List.of(new Document("new.md", Path.of("new.md"), "New content"))));

        assertEquals("keep", Files.readString(marker, StandardCharsets.UTF_8));
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(List.of(targetDirectory), files.toList());
        }
    }

    @Test
    void rejectsDuplicateDocumentIdsAsMalformedRecords() throws IOException {
        IndexPersistence persistence = persistence();
        String record = encode("same") + "\n" + encode("one.md") + "\n" + encode("One") + "\n";
        Files.writeString(persistence.path(), "MINISEARCH_INDEX\n1\n2\n" + record + record,
                StandardCharsets.UTF_8);

        assertThrows(IOException.class, persistence::load);
    }

    @Test
    void rejectsMalformedDocumentRecords() throws IOException {
        IndexPersistence persistence = persistence();
        String record = encode("id") + "\n" + encode("path.md") + "\n" + encode(" \t\n") + "\n";
        Files.writeString(persistence.path(), "MINISEARCH_INDEX\n1\n1\n" + record, StandardCharsets.UTF_8);

        assertThrows(IOException.class, persistence::load);
    }

    private IndexPersistence persistence() {
        return new IndexPersistence(temporaryDirectory.resolve("index-v1.dat"));
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
