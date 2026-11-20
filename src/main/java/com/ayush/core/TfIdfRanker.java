package com.ayush.core;

import com.ayush.model.Document;
import com.ayush.model.SearchResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Calculates TF-IDF relevance scores and ranks candidate documents.
 */
public class TfIdfRanker {

    private final Tokenizer tokenizer;

    /**
     * Creates a ranker using the standard tokenizer.
     */
    public TfIdfRanker() {
        this(new Tokenizer());
    }

    /**
     * Creates a ranker using the given tokenizer.
     *
     * @param tokenizer tokenizer used to normalize terms and document content
     */
    public TfIdfRanker(Tokenizer tokenizer) {
        this.tokenizer = Objects.requireNonNull(tokenizer, "Tokenizer cannot be null");
    }

    /**
     * Calculates normalized term frequency for a document.
     *
     * @param term term to count
     * @param document document to inspect
     * @return term frequency between zero and one
     */
    public double calculateTf(String term, Document document) {
        Objects.requireNonNull(term, "Term cannot be null");
        Objects.requireNonNull(document, "Document cannot be null");
        List<String> normalizedTerm = tokenizer.tokenize(term);
        if (normalizedTerm.size() != 1) {
            throw new IllegalArgumentException("Term must normalize to exactly one token");
        }

        List<String> documentTokens = tokenizer.tokenize(document.content());
        if (documentTokens.isEmpty()) {
            return 0.0;
        }
        long occurrences = documentTokens.stream().filter(normalizedTerm.getFirst()::equals).count();
        return (double) occurrences / documentTokens.size();
    }

    /**
     * Calculates smoothed inverse document frequency for a term.
     *
     * @param term term to evaluate
     * @param index index containing the corpus
     * @return smoothed inverse document frequency
     */
    public double calculateIdf(String term, InvertedIndex index) {
        Objects.requireNonNull(term, "Term cannot be null");
        Objects.requireNonNull(index, "Index cannot be null");
        int documentCount = index.getDocumentCount();
        if (documentCount == 0) {
            return 0.0;
        }
        List<String> normalizedTerm = tokenizer.tokenize(term);
        if (normalizedTerm.size() != 1) {
            throw new IllegalArgumentException("Term must normalize to exactly one token");
        }
        int documentFrequency = index.search(normalizedTerm.getFirst()).size();
        return Math.log((documentCount + 1.0) / (documentFrequency + 1.0)) + 1.0;
    }

    /**
     * Calculates the sum of TF-IDF contributions for distinct query terms.
     *
     * @param document document to score
     * @param queryTerms terms in the query
     * @param index indexed corpus
     * @return total TF-IDF score
     */
    public double calculateScore(Document document, Set<String> queryTerms, InvertedIndex index) {
        Objects.requireNonNull(document, "Document cannot be null");
        Objects.requireNonNull(queryTerms, "Query terms cannot be null");
        Objects.requireNonNull(index, "Index cannot be null");
        Set<String> distinctTerms = new LinkedHashSet<>();
        for (String term : queryTerms) {
            List<String> normalized = tokenizer.tokenize(Objects.requireNonNull(term, "Query term cannot be null"));
            if (normalized.size() == 1) {
                distinctTerms.add(normalized.getFirst());
            }
        }

        double score = 0.0;
        for (String term : distinctTerms) {
            score += calculateTf(term, document) * calculateIdf(term, index);
        }
        return score;
    }

    /**
     * Scores and ranks candidate documents, excluding candidates with zero score.
     *
     * @param candidateDocumentIds candidate IDs from the index
     * @param queryTerms distinct or repeated query terms
     * @param index index used to retrieve documents and term postings
     * @return immutable results sorted by descending score, then ascending document ID
     */
    public List<SearchResult> rank(Set<String> candidateDocumentIds, Set<String> queryTerms,
                                   InvertedIndex index) {
        Objects.requireNonNull(candidateDocumentIds, "Candidate document IDs cannot be null");
        Objects.requireNonNull(queryTerms, "Query terms cannot be null");
        Objects.requireNonNull(index, "Index cannot be null");

        Set<String> normalizedQueryTerms = new LinkedHashSet<>();
        for (String term : queryTerms) {
            List<String> normalized = tokenizer.tokenize(Objects.requireNonNull(term, "Query term cannot be null"));
            if (normalized.size() == 1) {
                normalizedQueryTerms.add(normalized.getFirst());
            }
        }

        List<SearchResult> results = new ArrayList<>();
        for (String documentId : candidateDocumentIds) {
            Document document = index.getDocument(Objects.requireNonNull(documentId, "Candidate ID cannot be null"));
            if (document == null) {
                continue;
            }
            Set<String> matchedTerms = new LinkedHashSet<>();
            for (String term : normalizedQueryTerms) {
                if (index.getTermFrequency(term, documentId) > 0) {
                    matchedTerms.add(term);
                }
            }
            double score = calculateScore(document, matchedTerms, index);
            if (score > 0.0) {
                results.add(new SearchResult(document, score, matchedTerms));
            }
        }

        results.sort(Comparator.comparingDouble(SearchResult::score).reversed()
                .thenComparing(result -> result.document().id()));
        return List.copyOf(results);
    }
}
