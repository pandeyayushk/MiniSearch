package com.ayush.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents an immutable text or Markdown document loaded by MiniSearch.
 *
 * @param id      unique document identifier, eventually relative to the indexed directory
 * @param path    location of the original file
 * @param content complete original file content, preserved without modification
 */
public record Document(String id,
                       Path path,
                       String content) {
    /**
     * Creates a document after validating its identifier, location, and content.
     *
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the identifier or content is blank, or the path is empty
     */
    public Document {
        Objects.requireNonNull(id, "Document id cannot be null");
        Objects.requireNonNull(path, "Document path cannot be null");
        Objects.requireNonNull(content, "Document content cannot be null");

        if (id.isBlank()) {
            throw new IllegalArgumentException("Document id cannot be blank");
        }
        if (content.isBlank()) {
            throw new IllegalArgumentException("Document content cannot be blank");
        }
        if (path.toString().isBlank()) {
            throw new IllegalArgumentException("Document path cannot be empty");
        }
    }
}
