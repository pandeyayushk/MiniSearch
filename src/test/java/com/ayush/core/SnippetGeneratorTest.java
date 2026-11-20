package com.ayush.core;

import com.ayush.model.Document;
import com.ayush.model.Snippet;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnippetGeneratorTest {
    private final SnippetGenerator generator = new SnippetGenerator();

    @Test
    void findsSingleTermAndPreservesSurroundingOriginalContext() {
        String content = "Before, Java works! After.";
        Snippet snippet = generator.generate(document(content), Set.of("java"), 100).getFirst();

        assertEquals(content, snippet.text());
        assertEquals(0, snippet.startOffset());
        assertEquals(content.length(), snippet.endOffset());
        assertEquals(Set.of("java"), snippet.matchedTerms());
    }

    @Test
    void findsMultipleTermsCaseInsensitivelyAndCombinesCloseMatches() {
        String content = "JAVA and thread safety";
        List<Snippet> snippets = generator.generate(document(content), Set.of("java", "THREAD"), 40);

        assertEquals(1, snippets.size());
        assertEquals(Set.of("java", "thread"), snippets.getFirst().matchedTerms());
        assertEquals(content, snippets.getFirst().text());
    }

    @Test
    void doesNotMatchPartialWords() {
        assertTrue(generator.generate(document("javascript typescript"), Set.of("java"), 40).isEmpty());
    }

    @Test
    void returnsBoundedSnippetAroundMiddleMatch() {
        String content = "start alpha beta Java gamma delta omega finish";
        Snippet snippet = generator.generate(document(content), Set.of("java"), 20).getFirst();

        assertTrue(snippet.text().length() <= 20);
        assertTrue(snippet.text().contains("Java"));
        assertTrue(snippet.startOffset() > 0);
        assertTrue(snippet.endOffset() < content.length());
        assertEquals(content.substring(snippet.startOffset(), snippet.endOffset()), snippet.text());
    }

    @Test
    void rejectsNonPositiveMaximumLengths() {
        Document document = document("java");
        assertThrows(IllegalArgumentException.class, () -> generator.generate(document, Set.of("java"), 0));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(document, Set.of("java"), -1));
    }

    @Test
    void handlesTermsAtDocumentBoundaries() {
        String content = "Java is at the beginning and ends with thread";
        List<Snippet> snippets = generator.generate(document(content), Set.of("java", "thread"), 12);

        assertEquals(2, snippets.size());
        assertTrue(snippets.getFirst().text().contains("Java"));
        assertTrue(snippets.getLast().text().contains("thread"));
        assertEquals(0, snippets.getFirst().startOffset());
        assertEquals(content.length(), snippets.getLast().endOffset());
    }

    @Test
    void groupsCloseMatchesButSeparatesDistantMatchesWithoutOverlap() {
        String close = "java and thread then synchronization";
        List<Snippet> combined = generator.generate(document(close), Set.of("java", "thread"), 30);
        assertEquals(1, combined.size());
        assertEquals(Set.of("java", "thread"), combined.getFirst().matchedTerms());

        String distant = "java " + "ordinary words in between ".repeat(10) + "thread";
        List<Snippet> separate = generator.generate(document(distant), Set.of("java", "thread"), 16);
        assertEquals(2, separate.size());
        assertTrue(separate.get(0).endOffset() <= separate.get(1).startOffset());
        assertTrue(separate.get(0).matchedTerms().contains("java"));
        assertTrue(separate.get(1).matchedTerms().contains("thread"));
    }

    @Test
    void resolvesOverlappingWindowsWithoutDroppingAnyMatch() {
        String content = "java " + "x".repeat(11) + " java " + "x".repeat(7) + " java";
        List<Snippet> snippets = generator.generate(document(content), Set.of("java"), 30);

        assertEquals(2, snippets.size());
        assertTrue(snippets.get(0).endOffset() <= snippets.get(1).startOffset());
        assertTrue(snippets.get(0).startOffset() < snippets.get(1).startOffset());
        for (int occurrence = content.indexOf("java"); occurrence >= 0;
             occurrence = content.indexOf("java", occurrence + 1)) {
            int matchStart = occurrence;
            assertTrue(snippets.stream().anyMatch(snippet -> snippet.startOffset() <= matchStart
                    && snippet.endOffset() >= matchStart + "java".length()),
                    "Every occurrence should remain inside an output snippet");
        }
        assertTrue(snippets.stream().allMatch(snippet -> snippet.text().length() <= 30));
    }

    @Test
    void keepsAQueryTermLongerThanTheMaximumIntact() {
        String longTerm = "extraordinarilylongtoken";
        Snippet snippet = generator.generate(document("before " + longTerm + " after"), Set.of(longTerm), 5)
                .getFirst();

        assertEquals(longTerm, snippet.text());
        assertEquals(7, snippet.startOffset());
        assertEquals(7 + longTerm.length(), snippet.endOffset());
        assertTrue(snippet.text().length() > 5,
                "A match longer than the limit is preserved whole rather than silently truncated");
    }

    @Test
    void reportsExactOffsetsAndOneBasedLineNumbers() {
        String content = "first line\nsecond Java line\nthird line";
        Snippet snippet = generator.generate(document(content), Set.of("java"), content.length()).getFirst();

        assertEquals(0, snippet.startOffset());
        assertEquals(content.length(), snippet.endOffset());
        assertEquals(1, snippet.startLine());
        assertEquals(3, snippet.endLine());
        assertEquals(content.substring(snippet.startOffset(), snippet.endOffset()), snippet.text());
    }

    @Test
    void returnsEmptyForMissingTermsAndImmutableListForMatches() {
        assertTrue(generator.generate(document("no match here"), Set.of("java"), 40).isEmpty());
        List<Snippet> snippets = generator.generate(document("java here"), Set.of("java"), 40);
        assertThrows(UnsupportedOperationException.class, snippets::clear);
    }

    @Test
    void handlesUnicodeLettersAndRejectsNullArguments() {
        String content = "Café — 你好世界";
        List<Snippet> snippets = generator.generate(document(content), Set.of("CAFÉ", "你好世界"), 40);
        assertEquals(1, snippets.size());
        assertEquals(Set.of("café", "你好世界"), snippets.getFirst().matchedTerms());
        assertThrows(NullPointerException.class, () -> generator.generate(null, Set.of("java"), 10));
        assertThrows(NullPointerException.class, () -> generator.generate(document("java"), null, 10));
        assertThrows(NullPointerException.class,
                () -> generator.generate(document("java"), java.util.Collections.singleton(null), 10));
    }

    private static Document document(String content) {
        return new Document("document.md", Path.of("document.md"), content);
    }
}
