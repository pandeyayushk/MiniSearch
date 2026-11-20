package com.ayush.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentTest {

    @Test
    void constructsDocumentWithValidFields() {
        Document document = new Document("docs/readme.md", Path.of("docs/readme.md"), "Original content.\n");

        assertNotNull(document);
    }

    @Test
    void accessorsReturnOriginalFieldValues() {
        Path path = Path.of("docs/readme.md");
        String content = " Original content!\n";
        Document document = new Document("docs/readme.md", path, content);

        assertEquals("docs/readme.md", document.id());
        assertEquals(path, document.path());
        assertEquals(content, document.content());
    }

    @Test
    void equalDocumentsHaveEqualHashCodes() {
        Document first = new Document("guide.md", Path.of("guide.md"), "Guide");
        Document second = new Document("guide.md", Path.of("guide.md"), "Guide");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void documentsWithDifferentFieldsAreNotEqual() {
        Document original = new Document("guide.md", Path.of("guide.md"), "Guide");

        assertNotEquals(original, new Document("other.md", Path.of("guide.md"), "Guide"));
        assertNotEquals(original, new Document("guide.md", Path.of("other.md"), "Guide"));
        assertNotEquals(original, new Document("guide.md", Path.of("guide.md"), "Different guide"));
    }

    @Test
    void rejectsNullId() {
        assertThrows(NullPointerException.class,
                () -> new Document(null, Path.of("guide.md"), "Guide"));
    }

    @Test
    void rejectsNullPath() {
        assertThrows(NullPointerException.class,
                () -> new Document("guide.md", null, "Guide"));
    }

    @Test
    void rejectsNullContent() {
        assertThrows(NullPointerException.class,
                () -> new Document("guide.md", Path.of("guide.md"), null));
    }

    @Test
    void rejectsBlankId() {
        assertThrows(IllegalArgumentException.class,
                () -> new Document(" \t\n", Path.of("guide.md"), "Guide"));
    }

    @Test
    void rejectsBlankContent() {
        assertThrows(IllegalArgumentException.class,
                () -> new Document("guide.md", Path.of("guide.md"), " \t\n"));
    }

    @Test
    void rejectsEmptyPath() {
        assertThrows(IllegalArgumentException.class,
                () -> new Document("guide.md", Path.of(""), "Guide"));
    }
}
