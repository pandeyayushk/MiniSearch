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
    void acceptsValidFiniteDoubleScore() {
        SearchResult result = new SearchResult(document(), 0.42, Set.of("java"));
        assertEquals(0.42, result.score(), 0.0);
        assertEquals(Set.of("java"), result.matchedTerms());
    }

    @Test
    void rejectsNullFieldsNegativeAndNonFiniteScoresAndEmptyTerms() {
        assertThrows(NullPointerException.class, () -> new SearchResult(null, 1.0, Set.of("java")));
        assertThrows(NullPointerException.class, () -> new SearchResult(document(), 1.0, null));
        assertThrows(IllegalArgumentException.class, () -> new SearchResult(document(), -1.0, Set.of("java")));
        assertThrows(IllegalArgumentException.class, () -> new SearchResult(document(), Double.NaN, Set.of("java")));
        assertThrows(IllegalArgumentException.class, () -> new SearchResult(document(), Double.POSITIVE_INFINITY, Set.of("java")));
        assertThrows(IllegalArgumentException.class, () -> new SearchResult(document(), Double.NEGATIVE_INFINITY, Set.of("java")));
        assertThrows(IllegalArgumentException.class, () -> new SearchResult(document(), 0.0, Set.of()));
    }

    @Test
    void defensivelyCopiesMatchedTermsAndExposesImmutableSet() {
        Set<String> terms = new HashSet<>(Set.of("java"));
        SearchResult result = new SearchResult(document(), 0.7, terms);
        terms.add("threads");
        assertEquals(Set.of("java"), result.matchedTerms());
        assertThrows(UnsupportedOperationException.class, () -> result.matchedTerms().add("threads"));
    }

    @Test
    void retainsRecordEqualityAndHashCodeBehavior() {
        SearchResult result = new SearchResult(document(), 0.5, Set.of("java"));
        SearchResult equal = new SearchResult(document(), 0.5, Set.of("java"));
        SearchResult different = new SearchResult(document(), 0.8, Set.of("java"));
        assertEquals(result, equal);
        assertEquals(result.hashCode(), equal.hashCode());
        assertNotEquals(result, different);
    }

    private static Document document() {
        return new Document("guide.md", Path.of("guide.md"), "Java guide");
    }
}
