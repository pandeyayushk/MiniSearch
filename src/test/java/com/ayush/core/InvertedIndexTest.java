package com.ayush.core;

import com.ayush.model.Document;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvertedIndexTest {

    @Test
    void addsDocumentAndIndexesItsTerms() {
        InvertedIndex index = new InvertedIndex();
        Document document = document("doc1", "Java java concurrency");

        index.addDocument(document);

        assertEquals(Set.of("doc1"), index.search("java"));
        assertEquals(Set.of("doc1"), index.search("concurrency"));
        assertEquals(2, index.getVocabularySize());
    }

    @Test
    void searchesTermsAndReturnsEmptySetForUnknownTerms() {
        InvertedIndex index = new InvertedIndex();
        index.addDocument(document("doc1", "Java concurrency"));
        index.addDocument(document("doc2", "Python"));

        assertEquals(Set.of("doc1"), index.search("java"));
        assertEquals(Set.of(), index.search("missing"));
    }

    @Test
    void returnsCorrectTermFrequenciesAndZeroForAbsentCombinations() {
        InvertedIndex index = new InvertedIndex();
        index.addDocument(document("doc1", "java java concurrency"));
        index.addDocument(document("doc2", "python"));

        assertEquals(2, index.getTermFrequency("java", "doc1"));
        assertEquals(0, index.getTermFrequency("java", "doc2"));
        assertEquals(0, index.getTermFrequency("unknown", "doc1"));
        assertEquals(0, index.getTermFrequency("java", "missing"));
    }

    @Test
    void rejectsMultiTokenTermFrequencyQueries() {
        InvertedIndex index = new InvertedIndex();

        assertThrows(IllegalArgumentException.class,
                () -> index.getTermFrequency("java python", "doc1"));
    }

    @Test
    void removingUnknownDocumentIdLeavesIndexUnchanged() {
        InvertedIndex index = new InvertedIndex();
        index.addDocument(document("doc1", "java java"));

        index.removeDocument("missing");

        assertEquals(1, index.getDocumentCount());
        assertEquals(1, index.getVocabularySize());
        assertEquals(Set.of("doc1"), index.search("java"));
        assertEquals(2, index.getTermFrequency("java", "doc1"));
    }

    @Test
    void removingSameDocumentTwiceLeavesIndexConsistent() {
        InvertedIndex index = new InvertedIndex();
        index.addDocument(document("doc1", "java concurrency"));
        index.addDocument(document("doc2", "java python"));

        index.removeDocument("doc1");
        index.removeDocument("doc1");

        assertEquals(1, index.getDocumentCount());
        assertEquals(2, index.getVocabularySize());
        assertEquals(Set.of("doc2"), index.search("java"));
        assertEquals(Set.of(), index.search("concurrency"));
        assertEquals(Set.of("doc2"), index.search("python"));
    }

    @Test
    void termFrequencyLookupNormalizesCaseAndPunctuation() {
        InvertedIndex index = new InvertedIndex();
        index.addDocument(document("doc1", "java JAVA Java"));

        assertEquals(3, index.getTermFrequency("JAVA", "doc1"));
        assertEquals(3, index.getTermFrequency("JaVa!!!", "doc1"));
    }

    @Test
    void retrievesDocumentsAndReturnsNullForUnknownIds() {
        InvertedIndex index = new InvertedIndex();
        Document document = document("doc1", "Java");
        index.addDocument(document);

        assertSame(document, index.getDocument("doc1"));
        assertNull(index.getDocument("missing"));
    }

    @Test
    void countsDocumentsAndUniqueVocabularyTerms() {
        InvertedIndex index = new InvertedIndex();
        index.addDocument(document("doc1", "java java"));
        index.addDocument(document("doc2", "java python"));

        assertEquals(2, index.getDocumentCount());
        assertEquals(2, index.getVocabularySize());
    }

    @Test
    void removesDocumentPostingsAndTermsThatBecomeUnreferenced() {
        InvertedIndex index = new InvertedIndex();
        index.addDocument(document("doc1", "java concurrency"));
        index.addDocument(document("doc2", "java python"));

        index.removeDocument("doc1");

        assertNull(index.getDocument("doc1"));
        assertEquals(Set.of("doc2"), index.search("java"));
        assertEquals(Set.of(), index.search("concurrency"));
        assertEquals(2, index.getVocabularySize());
        assertEquals(1, index.getDocumentCount());
    }

    @Test
    void replacementRemovesOldTermsUpdatesFrequencyAndPreservesOtherDocuments() {
        InvertedIndex index = new InvertedIndex();
        index.addDocument(document("doc1", "java java concurrency"));
        index.addDocument(document("doc2", "java python"));
        Document replacement = document("doc1", "python concurrency concurrency");

        index.addDocument(replacement);

        assertSame(replacement, index.getDocument("doc1"));
        assertEquals(2, index.getDocumentCount());
        assertEquals(1, index.getTermFrequency("java", "doc2"));
        assertEquals(0, index.getTermFrequency("java", "doc1"));
        assertEquals(2, index.getTermFrequency("concurrency", "doc1"));
        assertEquals(1, index.getTermFrequency("python", "doc1"));
        assertEquals(Set.of("doc2"), index.search("java"));
        assertEquals(Set.of("doc1", "doc2"), index.search("python"));
        assertEquals(3, index.getVocabularySize());
    }

    @Test
    void replacingDocumentWithOnlyDelimitersLeavesNoStalePostings() {
        InvertedIndex index = new InvertedIndex();
        index.addDocument(document("doc1", "java concurrency"));
        index.addDocument(document("doc1", "!!!"));

        assertEquals(1, index.getDocumentCount());
        assertEquals(0, index.getVocabularySize());
        assertEquals(Set.of(), index.search("java"));
    }

    @Test
    void removalLeavesUnrelatedDocumentsUnchanged() {
        InvertedIndex index = new InvertedIndex();
        Document remaining = document("doc2", "python python");
        index.addDocument(document("doc1", "java"));
        index.addDocument(remaining);

        index.removeDocument("doc1");

        assertSame(remaining, index.getDocument("doc2"));
        assertEquals(2, index.getTermFrequency("python", "doc2"));
        assertEquals(Set.of("doc2"), index.search("python"));
    }

    @Test
    void clearRemovesAllDocumentsAndPostings() {
        InvertedIndex index = new InvertedIndex();
        index.addDocument(document("doc1", "java concurrency"));
        index.addDocument(document("doc2", "python"));

        index.clear();

        assertEquals(0, index.getDocumentCount());
        assertEquals(0, index.getVocabularySize());
        assertEquals(Set.of(), index.search("java"));
        assertNull(index.getDocument("doc1"));
    }

    @Test
    void rejectsNullDocumentAndNullLookupArguments() {
        InvertedIndex index = new InvertedIndex();

        assertThrows(NullPointerException.class, () -> index.addDocument(null));
        assertThrows(NullPointerException.class, () -> index.removeDocument(null));
        assertThrows(NullPointerException.class, () -> index.search(null));
        assertThrows(NullPointerException.class, () -> index.getTermFrequency(null, "doc1"));
        assertThrows(NullPointerException.class, () -> index.getTermFrequency("java", null));
        assertThrows(NullPointerException.class, () -> index.getDocument(null));
        assertThrows(NullPointerException.class, () -> new InvertedIndex(null));
    }

    @Test
    void emptyContentIsRejectedByDocumentContract() {
        assertThrows(IllegalArgumentException.class, () -> document("doc1", ""));
        assertThrows(IllegalArgumentException.class, () -> document("doc1", " \t\n"));
    }

    @Test
    void rejectsEmptyWhitespaceOnlyAndMultiTokenSearchQueries() {
        InvertedIndex index = new InvertedIndex();

        assertThrows(IllegalArgumentException.class, () -> index.search(""));
        assertThrows(IllegalArgumentException.class, () -> index.search(" \t\n"));
        assertThrows(IllegalArgumentException.class, () -> index.search("java python"));
    }

    @Test
    void normalizesCaseInSearchQueries() {
        InvertedIndex index = new InvertedIndex();
        index.addDocument(document("doc1", "java"));

        assertEquals(Set.of("doc1"), index.search("JAVA"));
        assertEquals(Set.of("doc1"), index.search("JaVa!!!"));
    }

    @Test
    void repeatedAddReplaceAndRemoveOperationsKeepIndexConsistent() {
        InvertedIndex index = new InvertedIndex();
        index.addDocument(document("doc1", "alpha beta beta"));
        index.addDocument(document("doc1", "gamma beta"));
        index.addDocument(document("doc2", "gamma delta"));

        index.removeDocument("doc1");
        index.addDocument(document("doc1", "delta delta"));

        assertEquals(2, index.getDocumentCount());
        assertEquals(2, index.getVocabularySize());
        assertEquals(Set.of(), index.search("alpha"));
        assertEquals(Set.of("doc2"), index.search("gamma"));
        assertEquals(Set.of("doc1", "doc2"), index.search("delta"));
        assertEquals(2, index.getTermFrequency("delta", "doc1"));
        assertEquals(0, index.getTermFrequency("beta", "doc1"));
    }

    @Test
    void searchResultsCannotMutateIndexState() {
        InvertedIndex index = new InvertedIndex();
        index.addDocument(document("doc1", "java"));
        Set<String> results = index.search("java");

        assertThrows(UnsupportedOperationException.class, () -> results.add("doc2"));
        assertEquals(Set.of("doc1"), index.search("java"));
    }

    private static Document document(String id, String content) {
        return new Document(id, Path.of(id + ".txt"), content);
    }
}
