package com.ayush.core;

import com.ayush.model.Document;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Maintains term frequencies for indexed documents and a registry of those documents.
 */
public class InvertedIndex {

    private final Tokenizer tokenizer;
    private final Map<String, Map<String, Integer>> postings = new HashMap<>();
    private final Map<String, Document> documents = new HashMap<>();

    /**
     * Creates an inverted index using the standard tokenizer.
     */
    public InvertedIndex() {
        this(new Tokenizer());
    }

    /**
     * Creates an inverted index using the provided tokenizer.
     *
     * @param tokenizer tokenizer used to normalize document content and queries
     * @throws NullPointerException if {@code tokenizer} is {@code null}
     */
    public InvertedIndex(Tokenizer tokenizer) {
        this.tokenizer = Objects.requireNonNull(tokenizer, "Tokenizer cannot be null");
    }

    /**
     * Adds a document, replacing any document with the same ID.
     *
     * @param document document to index
     * @throws NullPointerException if {@code document} is {@code null}
     */
    public void addDocument(Document document) {
        Objects.requireNonNull(document, "Document cannot be null");

        Map<String, Integer> termFrequencies = new HashMap<>();
        for (String term : tokenizer.tokenize(document.content())) {
            termFrequencies.merge(term, 1, Integer::sum);
        }

        removeDocument(document.id());
        documents.put(document.id(), document);
        termFrequencies.forEach((term, frequency) ->
                postings.computeIfAbsent(term, ignored -> new HashMap<>())
                        .put(document.id(), frequency));
    }

    /**
     * Removes a document and all of its term postings.
     *
     * @param documentId ID of the document to remove
     * @throws NullPointerException if {@code documentId} is {@code null}
     */
    public void removeDocument(String documentId) {
        Objects.requireNonNull(documentId, "Document ID cannot be null");

        Document removed = documents.remove(documentId);
        if (removed == null) {
            return;
        }

        for (String term : tokenizer.tokenize(removed.content())) {
            Map<String, Integer> termPostings = postings.get(term);
            if (termPostings != null) {
                termPostings.remove(documentId);
                if (termPostings.isEmpty()) {
                    postings.remove(term);
                }
            }
        }
    }

    /**
     * Returns the IDs of documents containing the normalized term.
     *
     * @param term query that must normalize to exactly one token
     * @return an immutable set of matching document IDs
     * @throws NullPointerException     if {@code term} is {@code null}
     * @throws IllegalArgumentException if the query does not normalize to exactly one token
     */
    public Set<String> search(String term) {
        Objects.requireNonNull(term, "Term cannot be null");
        var normalizedTerms = tokenizer.tokenize(term);
        if (normalizedTerms.size() != 1) {
            throw new IllegalArgumentException("Search query must contain exactly one token");
        }
        Map<String, Integer> termPostings = postings.get(normalizedTerms.getFirst());
        return termPostings == null ? Set.of() : Set.copyOf(termPostings.keySet());
    }

    /**
     * Returns the frequency of a term in a document, or zero when either is not indexed.
     * The term is normalized as a single token; the document ID is used as provided.
     *
     * @param term       term to look up
     * @param documentId ID of the document
     * @return the indexed frequency, or zero if no posting exists
     * @throws NullPointerException     if either argument is {@code null}
     * @throws IllegalArgumentException if {@code term} does not normalize to exactly one token
     */
    public int getTermFrequency(String term, String documentId) {
        Objects.requireNonNull(term, "Term cannot be null");
        Objects.requireNonNull(documentId, "Document ID cannot be null");
        var normalizedTerms = tokenizer.tokenize(term);
        if (normalizedTerms.size() != 1) {
            throw new IllegalArgumentException("Term must normalize to exactly one token");
        }
        return postings.getOrDefault(normalizedTerms.getFirst(), Map.of()).getOrDefault(documentId, 0);
    }

    /**
     * Retrieves an indexed document by ID.
     *
     * @param documentId document ID
     * @return the document, or {@code null} when no document has that ID
     * @throws NullPointerException if {@code documentId} is {@code null}
     */
    public Document getDocument(String documentId) {
        Objects.requireNonNull(documentId, "Document ID cannot be null");
        return documents.get(documentId);
    }

    /**
     * Returns the number of indexed documents.
     *
     * @return document count
     */
    public int getDocumentCount() {
        return documents.size();
    }

    /**
     * Returns the number of terms with at least one posting.
     *
     * @return vocabulary size
     */
    public int getVocabularySize() {
        return postings.size();
    }

    /**
     * Removes all documents and postings from this index.
     */
    public void clear() {
        documents.clear();
        postings.clear();
    }
}
