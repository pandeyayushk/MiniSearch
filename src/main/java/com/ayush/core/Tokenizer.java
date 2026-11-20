package com.ayush.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Converts raw text into normalized tokens for indexing and searching.
 * Tokens are lowercase Unicode letter and digit sequences, in their original order.
 */
public class Tokenizer {

    /**
     * Converts the provided text into lowercase tokens separated by non-letter and non-digit characters.
     * Duplicate tokens and their original order are preserved.
     *
     * @param text text to tokenize
     * @return an ordered list of tokens, or an empty list when the input is empty or whitespace-only
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public List<String> tokenize(String text) {
        Objects.requireNonNull(text, "Text cannot be null");

        List<String> tokens =new ArrayList<>();
        String lowercased = text.toLowerCase(Locale.ROOT);
        String[] parts = lowercased.split("[^\\p{L}\\p{N}]+");

        for (String part : parts) {
            if (!part.isEmpty()) {
                tokens.add(part);
            }
        }
        return tokens;
    }
}
