package com.ayush.core;

import com.ayush.model.Document;
import com.ayush.model.SearchResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Coordinates document loading, indexing, and ranked search.
 */
public class SearchEngine {

    private final DocumentLoader documentLoader;
    private final Tokenizer tokenizer;
    private final InvertedIndex invertedIndex;

    /**
     * Creates a search engine with the standard components.
     */
    public SearchEngine() {
        this(new DocumentLoader(), new Tokenizer(), new InvertedIndex());
    }

    /**
     * Creates a search engine with injected components.
     *
     * @param documentLoader component used to load documents
     * @param tokenizer component used to normalize queries
     * @param invertedIndex component used to store and retrieve indexed documents
     * @throws NullPointerException if any dependency is {@code null}
     */
    public SearchEngine(DocumentLoader documentLoader, Tokenizer tokenizer, InvertedIndex invertedIndex) {
        this.documentLoader = Objects.requireNonNull(documentLoader, "Document loader cannot be null");
        this.tokenizer = Objects.requireNonNull(tokenizer, "Tokenizer cannot be null");
        this.invertedIndex = Objects.requireNonNull(invertedIndex, "Inverted index cannot be null");
    }

    /**
     * Loads and indexes all supported documents in the given directory.
     *
     * @param directory directory tree to index
     * @throws IOException if document loading fails
     */
    public void indexDirectory(Path directory) throws IOException {
        for (Document document : documentLoader.load(directory)) {
            invertedIndex.addDocument(document);
        }
    }

    /**
     * Searches for any query terms and ranks results by distinct matched-term count.
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

        List<SearchResult> results = new ArrayList<>();
        for (String documentId : candidateIds) {
            Document document = invertedIndex.getDocument(documentId);
            Set<String> matchedTerms = queryTerms.stream()
                    .filter(term -> invertedIndex.getTermFrequency(term, documentId) > 0)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!matchedTerms.isEmpty()) {
                results.add(new SearchResult(document, matchedTerms.size(), matchedTerms));
            }
        }

        results.sort(Comparator.comparingInt(SearchResult::score).reversed()
                .thenComparing(result -> result.document().id()));
        return List.copyOf(results);
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
     * Removes all indexed documents.
     */
    public void clear() {
        invertedIndex.clear();
    }
}
