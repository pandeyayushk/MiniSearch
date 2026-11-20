package com.ayush.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenizerTest {

    private final Tokenizer tokenizer = new Tokenizer();

    @Test
    void convertsWordsToLowercase() {
        assertEquals(List.of("java", "is", "powerful"), tokenizer.tokenize("Java is Powerful"));
    }

    @Test
    void usesPunctuationAsTokenDelimiters() {
        assertEquals(List.of("java", "python", "c"), tokenizer.tokenize("Java,Python;C++"));
        assertEquals(List.of("hello", "world"), tokenizer.tokenize("Hello!!!World"));
    }

    @Test
    void handlesMultipleConsecutiveDelimitersWithoutEmptyTokens() {
        assertEquals(List.of("java", "python", "rocks"), tokenizer.tokenize("  Java   & , Python---rocks  "));
    }

    @Test
    void returnsEmptyListForEmptyInput() {
        assertEquals(List.of(), tokenizer.tokenize(""));
    }

    @Test
    void returnsEmptyListForWhitespaceOnlyInput() {
        assertEquals(List.of(), tokenizer.tokenize(" \t\r\n "));
    }

    @Test
    void preservesDuplicateWords() {
        assertEquals(List.of("java", "java", "java"), tokenizer.tokenize("Java Java Java"));
    }

    @Test
    void preservesTokenOrder() {
        assertEquals(List.of("third", "first", "second"), tokenizer.tokenize("Third, first; second"));
    }

    @Test
    void retainsNumericAndAlphanumericTokens() {
        assertEquals(List.of("hello123", "world", "123", "456"),
                tokenizer.tokenize("Hello123 World 123 456"));
    }

    @Test
    void supportsUnicodeLettersAndDigits() {
        assertEquals(List.of("café", "你好", "١٢٣"), tokenizer.tokenize("Café 你好 ١٢٣"));
    }

    @Test
    void rejectsNullInput() {
        assertThrows(NullPointerException.class, () -> tokenizer.tokenize(null));
    }
}
