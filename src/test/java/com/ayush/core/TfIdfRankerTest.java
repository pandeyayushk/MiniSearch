package com.ayush.core;

import com.ayush.model.Document;
import com.ayush.model.SearchResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TfIdfRankerTest {
    private final Tokenizer tokenizer = new Tokenizer();
    private final TfIdfRanker ranker = new TfIdfRanker(tokenizer);

    @Test
    void calculatesNormalizedTermFrequencyIncludingRepeatedTerms() {
        Document document = document("one", "java java concurrency");
        assertEquals(2.0 / 3.0, ranker.calculateTf("JAVA", document), 1e-12);
        assertEquals(1.0 / 3.0, ranker.calculateTf("concurrency", document), 1e-12);
        assertEquals(0.0, ranker.calculateTf("python", document), 1e-12);
    }

    @Test
    void returnsZeroTfForDocumentWithoutTokens() {
        Document punctuationOnly = document("punctuation", "!!!");
        assertEquals(0.0, ranker.calculateTf("java", punctuationOnly), 0.0);
    }

    @Test
    void calculatesSmoothedIdfWithNaturalLogarithm() {
        InvertedIndex index = new InvertedIndex(tokenizer);
        index.addDocument(document("one", "java common"));
        index.addDocument(document("two", "common python"));
        index.addDocument(document("three", "common"));

        assertEquals(Math.log(4.0 / 2.0) + 1.0, ranker.calculateIdf("JAVA", index), 1e-12);
        assertEquals(1.0, ranker.calculateIdf("common", index), 1e-12);
        assertEquals(Math.log(4.0 / 2.0) + 1.0, ranker.calculateIdf("python", index), 1e-12);
    }

    @Test
    void returnsZeroIdfForEmptyIndex() {
        assertEquals(0.0, ranker.calculateIdf("java", new InvertedIndex(tokenizer)), 0.0);
    }

    @Test
    void calculatesCombinedScoreForDistinctTermsAndIgnoresMissingTerms() {
        InvertedIndex index = new InvertedIndex(tokenizer);
        Document document = document("one", "java java python");
        index.addDocument(document);
        index.addDocument(document("two", "java"));

        double javaContribution = (2.0 / 3.0) * (Math.log(3.0 / 3.0) + 1.0);
        double pythonContribution = (1.0 / 3.0) * (Math.log(3.0 / 2.0) + 1.0);
        double score = ranker.calculateScore(document, Set.of("JAVA", "java", "python", "missing"), index);

        assertEquals(javaContribution + pythonContribution, score, 1e-12);
    }

    @Test
    void ranksCandidatesByScoreAndDocumentIdAndIncludesOnlyMatchedTerms() {
        InvertedIndex index = new InvertedIndex(tokenizer);
        index.addDocument(document("z-top", "java concurrency threads"));
        index.addDocument(document("b-tie", "java concurrency"));
        index.addDocument(document("a-tie", "java concurrency"));
        index.addDocument(document("zero", "python"));

        List<SearchResult> results = ranker.rank(Set.of("z-top", "b-tie", "a-tie", "zero"),
                Set.of("java", "concurrency", "threads", "unknown"), index);

        assertEquals(List.of("z-top", "a-tie", "b-tie"),
                results.stream().map(result -> result.document().id()).toList());
        assertTrue(results.get(0).score() > results.get(1).score());
        assertEquals(results.get(1).score(), results.get(2).score(), 1e-12);
        assertEquals(Set.of("java", "concurrency", "threads"), results.get(0).matchedTerms());
        assertEquals(Set.of("java", "concurrency"), results.get(1).matchedTerms());
        assertTrue(results.stream().noneMatch(result -> result.document().id().equals("zero")));
    }

    @Test
    void excludesCandidateIdsNotPresentInIndex() {
        InvertedIndex index = new InvertedIndex(tokenizer);
        index.addDocument(document("one", "java"));

        assertEquals(1, ranker.rank(Set.of("one", "missing"), Set.of("java"), index).size());
    }

    @Test
    void returnsImmutableRanking() {
        InvertedIndex index = new InvertedIndex(tokenizer);
        index.addDocument(document("one", "java"));
        List<SearchResult> results = ranker.rank(Set.of("one"), Set.of("java"), index);

        assertThrows(UnsupportedOperationException.class, results::clear);
    }

    @Test
    void rejectsNullAndInvalidArguments() {
        Document doc = document("one", "java");
        InvertedIndex index = new InvertedIndex(tokenizer);
        index.addDocument(doc);
        assertThrows(NullPointerException.class, () -> new TfIdfRanker(null));
        assertThrows(NullPointerException.class, () -> ranker.calculateTf(null, doc));
        assertThrows(NullPointerException.class, () -> ranker.calculateTf("java", null));
        assertThrows(IllegalArgumentException.class, () -> ranker.calculateTf("java python", doc));
        assertThrows(NullPointerException.class, () -> ranker.calculateIdf("java", null));
        assertThrows(NullPointerException.class, () -> ranker.calculateScore(null, Set.of("java"), index));
        assertThrows(NullPointerException.class, () -> ranker.calculateScore(doc, null, index));
        assertThrows(NullPointerException.class, () -> ranker.calculateScore(doc, Set.of("java"), null));
        assertThrows(NullPointerException.class, () -> ranker.rank(null, Set.of("java"), index));
        assertThrows(NullPointerException.class, () -> ranker.rank(Set.of("one"), null, index));
        assertThrows(NullPointerException.class, () -> ranker.rank(Set.of("one"), Set.of("java"), null));
    }

    private static Document document(String id, String content) {
        return new Document(id, Path.of(id), content);
    }
}
