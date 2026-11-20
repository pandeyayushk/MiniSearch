package com.ayush.core;

import com.ayush.model.Document;
import com.ayush.model.SearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchEngineTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultConstructorCreatesUsableEngine() {
        SearchEngine engine = new SearchEngine();

        assertEquals(0, engine.getIndexedDocumentCount());
        assertTrue(engine.search("anything").isEmpty());
    }

    @Test
    void constructorAcceptsInjectedDependencies() {
        FixedDocumentLoader loader = new FixedDocumentLoader(List.of(document("doc.md", "java")));
        Tokenizer tokenizer = new Tokenizer();
        InvertedIndex index = new InvertedIndex(tokenizer);
        SearchEngine engine = new SearchEngine(loader, tokenizer, index, new TfIdfRanker(tokenizer));

        assertNotNull(engine);
    }

    @Test
    void constructorRejectsNullDependencies() {
        Tokenizer tokenizer = new Tokenizer();
        InvertedIndex index = new InvertedIndex(tokenizer);

        assertThrows(NullPointerException.class, () -> new SearchEngine(null, tokenizer, index));
        assertThrows(NullPointerException.class, () -> new SearchEngine(new DocumentLoader(), null, index));
        assertThrows(NullPointerException.class,
                () -> new SearchEngine(new DocumentLoader(), tokenizer, null));
        assertThrows(NullPointerException.class,
                () -> new SearchEngine(new DocumentLoader(), tokenizer, index, null));
    }

    @Test
    void indexesTxtAndMarkdownFilesAndReportsCount() throws IOException {
        Path root = newRoot();
        write(root, "java.txt", "Java language");
        write(root, "guide.md", "Markdown guide");
        write(root, "skip.pdf", "not indexed");
        SearchEngine engine = new SearchEngine();

        engine.indexDirectory(root);

        assertEquals(2, engine.getIndexedDocumentCount());
        assertEquals(List.of("guide.md", "java.txt"), ids(engine.search("guide markdown java")));
    }

    @Test
    void searchesForOneTermAndNormalizesCase() throws IOException {
        SearchEngine engine = indexFiles("java.txt", "Java language");

        assertEquals(List.of("java.txt"), ids(engine.search("JAVA")));
    }

    @Test
    void searchesMultipleTermsAndCountsDuplicateQueryTermsOnce() throws IOException {
        SearchEngine engine = indexFiles("guide.txt", "java java concurrency");

        List<SearchResult> results = engine.search("java java JAVA concurrency");

        assertEquals(1, results.size());
        assertEquals(1.0, results.getFirst().score(), 1e-12);
        assertEquals(java.util.Set.of("java", "concurrency"), results.getFirst().matchedTerms());
    }

    @Test
    void normalizesPunctuationAndUsesOrMatching() throws IOException {
        Path root = newRoot();
        write(root, "java.md", "Java only");
        write(root, "python.md", "Python only");
        SearchEngine engine = new SearchEngine();
        engine.indexDirectory(root);

        assertEquals(List.of("java.md", "python.md"), ids(engine.search("JAVA, PYTHON")));
    }

    @Test
    void unknownTermsDoNotPreventKnownTermMatches() throws IOException {
        SearchEngine engine = indexFiles("java.md", "Java guide");

        assertEquals(List.of("java.md"), ids(engine.search("unknown java absent")));
        assertTrue(engine.search("unknown absent").isEmpty());
    }

    @Test
    void emptyAndPunctuationOnlyQueriesReturnNoResults() throws IOException {
        SearchEngine engine = indexFiles("java.md", "Java");

        assertTrue(engine.search("").isEmpty());
        assertTrue(engine.search(" \t\n ").isEmpty());
        assertTrue(engine.search("!!! ...").isEmpty());
    }

    @Test
    void rejectsNullQuery() {
        assertThrows(NullPointerException.class, () -> new SearchEngine().search(null));
    }

    @Test
    void sortsByDescendingScoreThenAscendingDocumentId() throws IOException {
        Path root = newRoot();
        write(root, "z-top.md", "java concurrency threads");
        write(root, "b-tie.md", "java concurrency");
        write(root, "a-tie.md", "java concurrency");
        write(root, "c-one.md", "java");
        SearchEngine engine = new SearchEngine();
        engine.indexDirectory(root);

        List<SearchResult> results = engine.search("java concurrency threads");

        assertEquals(List.of("z-top.md", "a-tie.md", "b-tie.md", "c-one.md"), ids(results));
        double expectedTopScore = (1.0 + (Math.log(5.0 / 4.0) + 1.0)
                + (Math.log(5.0 / 2.0) + 1.0)) / 3.0;
        assertEquals(expectedTopScore, results.getFirst().score(), 1e-12);
        assertTrue(results.get(0).score() > results.get(1).score());
        assertEquals(results.get(1).score(), results.get(2).score(), 1e-12);
        assertTrue(results.get(2).score() > results.get(3).score());
    }

    @Test
    void matchedTermsContainOnlyQueryTermsPresentInEachDocument() throws IOException {
        Path root = newRoot();
        write(root, "one.md", "java concurrency");
        write(root, "two.md", "threads");
        SearchEngine engine = new SearchEngine();
        engine.indexDirectory(root);

        List<SearchResult> results = engine.search("java concurrency threads unknown");

        assertEquals(java.util.Set.of("java", "concurrency"), results.get(0).matchedTerms());
        assertEquals(java.util.Set.of("threads"), results.get(1).matchedTerms());
    }

    @Test
    void reindexingReplacesDocumentsAndRemovesObsoleteTerms() throws IOException {
        Path root = newRoot();
        Path file = write(root, "guide.md", "java concurrency");
        SearchEngine engine = new SearchEngine();
        engine.indexDirectory(root);
        Files.writeString(file, "python concurrency", StandardCharsets.UTF_8);

        engine.indexDirectory(root);

        assertEquals(1, engine.getIndexedDocumentCount());
        assertTrue(engine.search("java").isEmpty());
        assertEquals(List.of("guide.md"), ids(engine.search("python")));
        assertEquals(List.of("guide.md"), ids(engine.search("concurrency")));
    }

    @Test
    void clearRemovesAllIndexedDocumentsAndResults() throws IOException {
        SearchEngine engine = indexFiles("guide.md", "java concurrency");

        engine.clear();

        assertEquals(0, engine.getIndexedDocumentCount());
        assertTrue(engine.search("java concurrency").isEmpty());
    }

    @Test
    void returnedResultsAreImmutable() throws IOException {
        SearchEngine engine = indexFiles("guide.md", "java");
        List<SearchResult> results = engine.search("java");

        assertThrows(UnsupportedOperationException.class, () -> results.clear());
        assertEquals(1, engine.search("java").size());
    }

    @Test
    void resultsIncludeSnippetsForTheirDocumentWithOnlyLocalMatchingTerms() throws IOException {
        Path root = newRoot();
        write(root, "first.md", "Java Java appears here.");
        write(root, "second.md", "Thread appears here.");
        SearchEngine engine = new SearchEngine();
        engine.indexDirectory(root);

        List<SearchResult> results = engine.search("java thread absent");

        assertEquals(2, results.size());
        SearchResult first = results.stream().filter(result -> result.document().id().equals("first.md")).findFirst().orElseThrow();
        SearchResult second = results.stream().filter(result -> result.document().id().equals("second.md")).findFirst().orElseThrow();
        double idf = Math.log(3.0 / 2.0) + 1.0;
        assertEquals(idf / 2.0, first.score(), 1e-12);
        assertEquals(idf / 3.0, second.score(), 1e-12);
        assertEquals(1, first.snippets().size());
        assertEquals(1, second.snippets().size());
        assertEquals(Set.of("java"), first.snippets().getFirst().matchedTerms());
        assertEquals(Set.of("thread"), second.snippets().getFirst().matchedTerms());
        assertEquals(first.document().content(), first.snippets().getFirst().text());
        assertEquals(second.document().content(), second.snippets().getFirst().text());
    }

    @Test
    void snippetsDoNotChangeTfIdfScoresOrExistingResultOrder() throws IOException {
        Path root = newRoot();
        write(root, "b.md", "java concurrency");
        write(root, "a.md", "java java");
        write(root, "c.md", "concurrency");
        SearchEngine engine = new SearchEngine();
        engine.indexDirectory(root);

        List<SearchResult> results = engine.search("java concurrency");

        assertEquals(List.of("a.md", "b.md", "c.md"), ids(results));
        double idf = Math.log(4.0 / 3.0) + 1.0;
        assertEquals(idf, results.get(0).score(), 1e-12);
        assertEquals(idf, results.get(1).score(), 1e-12);
        assertEquals(idf, results.get(2).score(), 1e-12);
        assertTrue(results.stream().allMatch(result -> !result.snippets().isEmpty()));
    }

    @Test
    void emptySearchResultsRemainEmpty() {
        assertTrue(new SearchEngine().search("no matching terms").isEmpty());
    }

    private SearchEngine indexFiles(String relativePath, String content) throws IOException {
        Path root = newRoot();
        write(root, relativePath, content);
        SearchEngine engine = new SearchEngine();
        engine.indexDirectory(root);
        return engine;
    }

    private Path newRoot() throws IOException {
        return Files.createDirectory(temporaryDirectory.resolve("root"));
    }

    private static Path write(Path root, String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static List<String> ids(List<SearchResult> results) {
        return results.stream().map(result -> result.document().id()).toList();
    }

    private static Document document(String id, String content) {
        return new Document(id, Path.of(id), content);
    }

    private static final class FixedDocumentLoader extends DocumentLoader {
        private final List<Document> documents;

        private FixedDocumentLoader(List<Document> documents) {
            this.documents = documents;
        }

        @Override
        public List<Document> load(Path ignored) {
            return documents;
        }
    }
}
