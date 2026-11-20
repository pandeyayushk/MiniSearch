package com.ayush.model;

import java.util.Objects;
import java.util.Set;

/**
 * Represents a document matching a search query and the distinct query terms it matched.
 *
 * @param document    matching document
 * @param score       number of distinct query terms matched
 * @param matchedTerms query terms found in the document
 */
public record SearchResult(Document document, int score, Set<String> matchedTerms) {

    /**
     * Creates a validated search result with an immutable copy of the matched terms.
     *
     * @throws NullPointerException     if {@code document} or {@code matchedTerms} is {@code null}
     * @throws IllegalArgumentException if the score is negative or no terms matched
     */
    public SearchResult {
        Objects.requireNonNull(document, "Document cannot be null");
        Objects.requireNonNull(matchedTerms, "Matched terms cannot be null");
        if (score < 0) {
            throw new IllegalArgumentException("Score cannot be negative");
        }
        if (matchedTerms.isEmpty()) {
            throw new IllegalArgumentException("Matched terms cannot be empty");
        }
        if (score != matchedTerms.size()) {
            throw new IllegalArgumentException("Score must equal the number of matched terms");
        }
        matchedTerms = Set.copyOf(matchedTerms);
    }
}
