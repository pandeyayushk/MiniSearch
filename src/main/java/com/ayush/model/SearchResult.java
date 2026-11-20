package com.ayush.model;

import java.util.Objects;
import java.util.Set;

/**
 * Represents a document matching a search query and the distinct query terms it matched.
 *
 * @param document    matching document
 * @param score       relevance score calculated by the ranker
 * @param matchedTerms query terms found in the document
 */
public record SearchResult(Document document, double score, Set<String> matchedTerms) {

    /**
     * Creates a validated search result with an immutable copy of the matched terms.
     *
     * @throws NullPointerException     if {@code document} or {@code matchedTerms} is {@code null}
     * @throws IllegalArgumentException if the score is not finite, is negative, or no terms matched
     */
    public SearchResult {
        Objects.requireNonNull(document, "Document cannot be null");
        Objects.requireNonNull(matchedTerms, "Matched terms cannot be null");
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("Score must be finite");
        }
        if (score < 0.0) {
            throw new IllegalArgumentException("Score cannot be negative");
        }
        if (matchedTerms.isEmpty()) {
            throw new IllegalArgumentException("Matched terms cannot be empty");
        }
        matchedTerms = Set.copyOf(matchedTerms);
    }
}
