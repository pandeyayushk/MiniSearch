package com.ayush.core;

import com.ayush.model.Document;
import com.ayush.model.SearchResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Coordinates document loading, indexing, and ranked search.
 */
public class SearchEngine {

    private final DocumentLoader documentLoader;
    private final Tokenizer tokenizer;
    private final InvertedIndex invertedIndex;
    private final TfIdfRanker ranker;

    /**
     * Creates a search engine with the standard components.
     */
    public SearchEngine() {
        this(new DocumentLoader(), new Tokenizer(), new InvertedIndex(), new TfIdfRanker());
    }

    /**
     * Creates a search engine with injected components.
     *
     * @param documentLoader component used to load documents
     * @param tokenizer component used to normalize queries
     * @param invertedIndex component used to store and retrieve indexed documents
     * @param ranker component used to calculate TF-IDF scores and rank results
     * @throws NullPointerException if any dependency is {@code null}
     */
    public SearchEngine(DocumentLoader documentLoader, Tokenizer tokenizer, InvertedIndex invertedIndex,
                        TfIdfRanker ranker) {
        this.documentLoader = Objects.requireNonNull(documentLoader, "Document loader cannot be null");
        this.tokenizer = Objects.requireNonNull(tokenizer, "Tokenizer cannot be null");
        this.invertedIndex = Objects.requireNonNull(invertedIndex, "Inverted index cannot be null");
        this.ranker = Objects.requireNonNull(ranker, "Ranker cannot be null");
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
        for (Document document : documentLoader.load(directory)) {
            invertedIndex.addDocument(document);
        }
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

        return ranker.rank(candidateIds, queryTerms, invertedIndex);
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
