package com.ayush.core;

import com.ayush.model.Document;
import com.ayush.model.SearchResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Coordinates document loading, indexing, and ranked search.
 */
public class SearchEngine {

    private final DocumentLoader documentLoader;
    private final Tokenizer tokenizer;
    private final InvertedIndex invertedIndex;
    private final TfIdfRanker ranker;
    private final SnippetGenerator snippetGenerator;
    private static final int DEFAULT_MAX_SNIPPET_LENGTH = 240;

    /**
     * Creates a search engine with the standard components.
     */
    public SearchEngine() {
        this(new DocumentLoader(), new Tokenizer(), new InvertedIndex(), new TfIdfRanker(), new SnippetGenerator());
    }

    /**
     * Creates a search engine with injected components.
     *
     * @param documentLoader component used to load documents
     * @param tokenizer component used to normalize queries
     * @param invertedIndex component used to store and retrieve indexed documents
     * @param ranker component used to calculate TF-IDF scores and rank results
     * @param snippetGenerator component used to extract matching passages
     * @throws NullPointerException if any dependency is {@code null}
     */
    public SearchEngine(DocumentLoader documentLoader, Tokenizer tokenizer, InvertedIndex invertedIndex,
                        TfIdfRanker ranker, SnippetGenerator snippetGenerator) {
        this.documentLoader = Objects.requireNonNull(documentLoader, "Document loader cannot be null");
        this.tokenizer = Objects.requireNonNull(tokenizer, "Tokenizer cannot be null");
        this.invertedIndex = Objects.requireNonNull(invertedIndex, "Inverted index cannot be null");
        this.ranker = Objects.requireNonNull(ranker, "Ranker cannot be null");
        this.snippetGenerator = Objects.requireNonNull(snippetGenerator, "Snippet generator cannot be null");
    }

    /**
     * Creates a search engine using the default snippet generator.
     */
    public SearchEngine(DocumentLoader documentLoader, Tokenizer tokenizer, InvertedIndex invertedIndex,
                        TfIdfRanker ranker) {
        this(documentLoader, tokenizer, invertedIndex, ranker, new SnippetGenerator(tokenizer));
    }

    /**
     * Creates a search engine using the default TF-IDF ranker.
     *
     * @param documentLoader component used to load documents
     * @param tokenizer component used to normalize queries
     * @param invertedIndex component used to store and retrieve indexed documents
     */
    public SearchEngine(DocumentLoader documentLoader, Tokenizer tokenizer, InvertedIndex invertedIndex) {
        this(documentLoader, tokenizer, invertedIndex, new TfIdfRanker(tokenizer));
    }

    /**
     * Loads and indexes all supported documents in the given directory.
     *
     * @param directory directory tree to index
     * @throws IOException if document loading fails
     */
    public void indexDirectory(Path directory) throws IOException {
        List<Document> loadedDocuments = documentLoader.load(directory);
        invertedIndex.replaceDocuments(loadedDocuments);
    }

    /**
     * Searches for any query terms and ranks results by TF-IDF score.
     *
     * @param query search text
     * @return immutable results sorted by descending score, then ascending document ID
     * @throws NullPointerException if {@code query} is {@code null}
     */
    public List<SearchResult> search(String query) {
        Objects.requireNonNull(query, "Query cannot be null");
        Set<String> queryTerms = new LinkedHashSet<>(tokenizer.tokenize(query));
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        Set<String> candidateIds = new LinkedHashSet<>();
        for (String term : queryTerms) {
            candidateIds.addAll(invertedIndex.search(term));
        }

        List<SearchResult> rankedResults = ranker.rank(candidateIds, queryTerms, invertedIndex);
        return rankedResults.stream()
                .map(result -> new SearchResult(result.document(), result.score(), result.matchedTerms(),
                        snippetGenerator.generate(result.document(), queryTerms, DEFAULT_MAX_SNIPPET_LENGTH)))
                .toList();
    }

    /**
     * Returns the number of indexed documents.
     *
     * @return indexed document count
     */
    public int getIndexedDocumentCount() {
        return invertedIndex.getDocumentCount();
    }

    /**
     * Returns an immutable snapshot of the indexed documents sorted by ID.
     *
     * @return immutable document snapshot
     */
    public List<Document> getIndexedDocuments() {
        return invertedIndex.getDocumentsSnapshot();
    }

    /**
     * Replaces the current indexed documents and rebuilds all postings.
     *
     * @param documents documents to restore
     */
    public void restoreDocuments(Collection<Document> documents) {
        List<Document> validatedDocuments = List.copyOf(Objects.requireNonNull(documents, "Documents cannot be null"));
        invertedIndex.replaceDocuments(validatedDocuments);
    }

    /**
     * Removes all indexed documents.
     */
    public void clear() {
        invertedIndex.clear();
    }
}
