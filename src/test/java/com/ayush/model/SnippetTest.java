package com.ayush.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnippetTest {

    @Test
    void constructsValidSnippet() {
        Snippet snippet = new Snippet("Java guide", 4, 14, 1, 1, Set.of("java"));

        assertEquals("Java guide", snippet.text());
        assertEquals(4, snippet.startOffset());
        assertEquals(14, snippet.endOffset());
        assertEquals(1, snippet.startLine());
        assertEquals(1, snippet.endLine());
        assertEquals(Set.of("java"), snippet.matchedTerms());
    }

    @Test
    void validatesOffsetLengthUsingUtf16StringLength() {
        Snippet validUnicode = new Snippet("😀", 3, 5, 1, 1, Set.of("emoji"));
        assertEquals(2, validUnicode.text().length());
        assertThrows(IllegalArgumentException.class,
                () -> new Snippet("Java", 0, 3, 1, 1, Set.of("java")));
        assertThrows(IllegalArgumentException.class,
                () -> new Snippet("Java", 0, 5, 1, 1, Set.of("java")));
    }

    @Test
    void rejectsNullOrBlankTextAndNullMatchedTerms() {
        assertThrows(NullPointerException.class, () -> new Snippet(null, 0, 1, 1, 1, Set.of("java")));
        assertThrows(IllegalArgumentException.class, () -> new Snippet(" \t", 0, 2, 1, 1, Set.of("java")));
        assertThrows(NullPointerException.class, () -> new Snippet("java", 0, 4, 1, 1, null));
    }

    @Test
    void rejectsInvalidOffsets() {
        assertThrows(IllegalArgumentException.class, () -> new Snippet("text", -1, 2, 1, 1, Set.of("java")));
        assertThrows(IllegalArgumentException.class, () -> new Snippet("text", 3, 3, 1, 1, Set.of("java")));
        assertThrows(IllegalArgumentException.class, () -> new Snippet("text", 4, 3, 1, 1, Set.of("java")));
    }

    @Test
    void rejectsInvalidLineNumbersAndEmptyMatches() {
        assertThrows(IllegalArgumentException.class, () -> new Snippet("text", 0, 4, 0, 1, Set.of("java")));
        assertThrows(IllegalArgumentException.class, () -> new Snippet("text", 0, 4, 2, 1, Set.of("java")));
        assertThrows(IllegalArgumentException.class, () -> new Snippet("text", 0, 4, 1, 0, Set.of("java")));
        assertThrows(IllegalArgumentException.class, () -> new Snippet("text", 0, 4, 1, 1, Set.of()));
    }

    @Test
    void defensivelyCopiesMatchedTermsAndReturnsImmutableSet() {
        Set<String> terms = new HashSet<>(Set.of("java"));
        Snippet snippet = new Snippet("Java", 0, 4, 1, 1, terms);
        terms.add("threads");

        assertEquals(Set.of("java"), snippet.matchedTerms());
        assertThrows(UnsupportedOperationException.class, () -> snippet.matchedTerms().add("threads"));
    }
}
