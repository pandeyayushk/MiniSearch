package com.ayush.model;

import java.util.Objects;
import java.util.Set;

/**
 * An extracted passage and its location in the original document.
 *
 * @param text extracted passage
 * @param startOffset zero-based inclusive character offset
 * @param endOffset zero-based exclusive character offset
 * @param startLine one-based first line number
 * @param endLine one-based last line number
 * @param matchedTerms query terms present in the passage
 */
public record Snippet(String text, int startOffset, int endOffset, int startLine, int endLine,
                      Set<String> matchedTerms) {
    /**
     * Creates a validated snippet with an immutable copy of its matched terms.
     */
    public Snippet {
        Objects.requireNonNull(text, "Snippet text cannot be null");
        Objects.requireNonNull(matchedTerms, "Matched terms cannot be null");
        if (text.isBlank()) {
            throw new IllegalArgumentException("Snippet text cannot be blank");
        }
        if (startOffset < 0 || endOffset <= startOffset) {
            throw new IllegalArgumentException("Snippet offsets must define a non-empty range");
        }
        if (endOffset - startOffset != text.length()) {
            throw new IllegalArgumentException("Offset range must equal snippet text length");
        }
        if (startLine < 1 || endLine < startLine) {
            throw new IllegalArgumentException("Snippet lines must be valid one-based line numbers");
        }
        if (matchedTerms.isEmpty()) {
            throw new IllegalArgumentException("Matched terms cannot be empty");
        }
        matchedTerms = Set.copyOf(matchedTerms);
    }
}
