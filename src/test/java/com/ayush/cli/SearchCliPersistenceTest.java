package com.ayush.cli;

import com.ayush.core.SearchEngine;
import com.ayush.persistence.IndexPersistence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchCliPersistenceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void indexesSavesRestoresAndSearchesWithRankingAndSnippets() throws Exception {
        Path documents = Files.createDirectory(temporaryDirectory.resolve("docs"));
        Files.writeString(documents.resolve("java.md"), "Java Java thread synchronization", StandardCharsets.UTF_8);
        Files.writeString(documents.resolve("thread.txt"), "Thread synchronization", StandardCharsets.UTF_8);
        IndexPersistence persistence = new IndexPersistence(temporaryDirectory.resolve("store/index-v1.dat"));

        String firstSession = run(new SearchEngine(), persistence,
                "index " + documents + "\nexit\n");
        assertTrue(firstSession.contains("Saved index to"));
        assertEquals(2, persistence.load().size());

        SearchEngine restartedEngine = new SearchEngine();
        String secondSession = run(restartedEngine, persistence, "search java thread\nexit\n");

        assertTrue(secondSession.contains("Restored 2 document(s)"));
        assertTrue(secondSession.contains("java.md"));
        assertTrue(secondSession.contains("thread.txt"));
        assertTrue(secondSession.contains("Lines 1-1"));
        assertEquals(2, restartedEngine.getIndexedDocumentCount());
        var restoredResults = restartedEngine.search("java thread");
        assertEquals(java.util.List.of("java.md", "thread.txt"), restoredResults.stream()
                .map(result -> result.document().id()).toList());
        assertTrue(restoredResults.getFirst().score() > restoredResults.getLast().score());
        assertTrue(!restoredResults.getFirst().snippets().isEmpty());
    }

    @Test
    void reindexingReplacesSavedDocumentContent() throws Exception {
        Path documents = Files.createDirectory(temporaryDirectory.resolve("docs"));
        Path file = documents.resolve("guide.md");
        Files.writeString(file, "obsolete java", StandardCharsets.UTF_8);
        IndexPersistence persistence = new IndexPersistence(temporaryDirectory.resolve("index.dat"));
        SearchEngine first = new SearchEngine();
        first.indexDirectory(documents);
        persistence.save(first.getIndexedDocuments());

        Files.writeString(file, "updated python", StandardCharsets.UTF_8);
        String output = run(first, persistence, "index " + documents + "\nexit\n");

        assertTrue(output.contains("Saved index to"));
        assertEquals("updated python", persistence.load().getFirst().content());
        SearchEngine restored = new SearchEngine();
        restored.restoreDocuments(persistence.load());
        assertTrue(restored.search("java").isEmpty());
        assertEquals("guide.md", restored.search("python").getFirst().document().id());
    }

    @Test
    void reindexingDirectoryRemovesDeletedDocumentsFromMemoryAndPersistence() throws Exception {
        Path documents = Files.createDirectory(temporaryDirectory.resolve("docs"));
        Path keepFile = documents.resolve("keep.md");
        Path deleteFile = documents.resolve("delete.txt");
        Files.writeString(keepFile, "shared retainedword", StandardCharsets.UTF_8);
        Files.writeString(deleteFile, "shared deletedword", StandardCharsets.UTF_8);
        IndexPersistence persistence = new IndexPersistence(temporaryDirectory.resolve("saved/index.dat"));
        SearchEngine engine = new SearchEngine();

        run(engine, persistence, "index " + documents + "\nexit\n");
        assertEquals(2, engine.getIndexedDocumentCount());
        Files.delete(deleteFile);

        String output = run(engine, persistence, "index " + documents + "\nexit\n");

        assertTrue(output.contains("Documents indexed: 1"));
        assertEquals(1, engine.getIndexedDocumentCount());
        assertEquals("keep.md", engine.search("shared").getFirst().document().id());
        assertTrue(engine.search("deletedword").isEmpty());
        assertEquals(java.util.List.of("keep.md"), persistence.load().stream().map(doc -> doc.id()).toList());
    }

    @Test
    void reportsCorruptSavedIndexAndLeavesItUntouched() throws Exception {
        IndexPersistence persistence = new IndexPersistence(temporaryDirectory.resolve("bad-index.dat"));
        Files.writeString(persistence.path(), "corrupt bytes", StandardCharsets.UTF_8);
        byte[] original = Files.readAllBytes(persistence.path());
        SearchEngine engine = new SearchEngine();

        String output = run(engine, persistence, "search anything\nexit\n");

        assertTrue(output.contains("Could not load saved index:"));
        assertTrue(output.contains("Starting with an empty index"));
        assertTrue(output.contains("No results found."));
        assertEquals(0, engine.getIndexedDocumentCount());
        assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(persistence.path())));
    }

    @Test
    void startsEmptyWhenNoSavedIndexExists() {
        IndexPersistence persistence = new IndexPersistence(temporaryDirectory.resolve("missing/index.dat"));

        String output = run(new SearchEngine(), persistence, "search anything\nexit\n");

        assertTrue(output.contains("No saved index found. Starting with an empty index."));
    }

    private String run(SearchEngine engine, IndexPersistence persistence, String commands) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            new SearchCli(engine, new ByteArrayInputStream(commands.getBytes(StandardCharsets.UTF_8)),
                    output, persistence).run();
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
