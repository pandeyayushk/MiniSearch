package com.ayush.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearchResultTest {

    @Test
    void constructsWithValidFields() {
        Document document = document();

        SearchResult result = new SearchResult(document, 2, Set.of("java", "threads"));

        assertEquals(document, result.document());
        assertEquals(2, result.score());
        assertEquals(Set.of("java", "threads"), result.matchedTerms());
    }

    @Test
    void rejectsNullDocument() {
        assertThrows(NullPointerException.class, () -> new SearchResult(null, 1, Set.of("java")));
    }

    @Test
    void rejectsNullMatchedTerms() {
        assertThrows(NullPointerException.class, () -> new SearchResult(document(), 1, null));
    }

    @Test
    void rejectsNegativeScore() {
        assertThrows(IllegalArgumentException.class,
                () -> new SearchResult(document(), -1, Set.of("java")));
    }

    @Test
    void rejectsEmptyMatchedTerms() {
        assertThrows(IllegalArgumentException.class, () -> new SearchResult(document(), 0, Set.of()));
    }

    @Test
    void rejectsScoreSmallerThanMatchedTermCount() {
        assertThrows(IllegalArgumentException.class,
                () -> new SearchResult(document(), 1, Set.of("java", "threads")));
    }

    @Test
    void rejectsScoreLargerThanMatchedTermCount() {
        assertThrows(IllegalArgumentException.class,
                () -> new SearchResult(document(), 2, Set.of("java")));
    }

    @Test
    void acceptsScoreEqualToMatchedTermCount() {
        SearchResult result = new SearchResult(document(), 2, Set.of("java", "threads"));

        assertEquals(2, result.score());
        assertEquals(2, result.matchedTerms().size());
    }

    @Test
    void defensivelyCopiesMatchedTerms() {
        Set<String> terms = new HashSet<>(Set.of("java"));
        SearchResult result = new SearchResult(document(), 1, terms);

        terms.add("threads");

        assertEquals(Set.of("java"), result.matchedTerms());
    }

    @Test
    void returnedMatchedTermsAreImmutable() {
        SearchResult result = new SearchResult(document(), 1, Set.of("java"));

        assertThrows(UnsupportedOperationException.class, () -> result.matchedTerms().add("threads"));
    }

    @Test
    void hasRecordEqualityAndHashCodeBehavior() {
        SearchResult first = new SearchResult(document(), 1, Set.of("java"));
        SearchResult equal = new SearchResult(document(), 1, Set.of("java"));
        SearchResult different = new SearchResult(document(), 2, Set.of("java", "threads"));

        assertEquals(first, equal);
        assertEquals(first.hashCode(), equal.hashCode());
        assertNotEquals(first, different);
    }

    private static Document document() {
        return new Document("guide.md", Path.of("guide.md"), "Java guide");
    }
}
