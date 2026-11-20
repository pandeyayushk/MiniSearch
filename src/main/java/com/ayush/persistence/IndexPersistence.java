package com.ayush.persistence;

import com.ayush.model.Document;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reads and writes MiniSearch's versioned, text-based document index file.
 */
public class IndexPersistence {

    private static final String FORMAT_ID = "MINISEARCH_INDEX";
    private static final String FORMAT_VERSION = "1";
    private final Path indexPath;

    /**
     * Creates persistence at {@code ~/.minisearch/index-v1.dat}.
     */
    public IndexPersistence() {
        this(defaultPath());
    }

    /**
     * Creates persistence at the supplied path, primarily useful for isolated storage locations.
     *
     * @param indexPath index file path
     */
    public IndexPersistence(Path indexPath) {
        this.indexPath = Objects.requireNonNull(indexPath, "Index path cannot be null").toAbsolutePath();
    }

    /**
     * Resolves the default index path using the configured user home directory.
     *
     * @return default index path
     */
    public static Path defaultPath() {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            throw new IllegalStateException("System property user.home is not set");
        }
        return Path.of(userHome).resolve(".minisearch").resolve("index-v1.dat");
    }

    /**
     * Returns the configured file path.
     *
     * @return index file path
     */
    public Path path() {
        return indexPath;
    }

    /**
     * Returns whether an index file currently exists.
     *
     * @return true when the configured index file exists
     */
    public boolean exists() {
        return Files.exists(indexPath);
    }

    /**
     * Saves documents using a temporary sibling file and replacement move.
     *
     * @param documents documents to persist
     * @throws IOException if writing or replacing the index fails
     */
    public void save(Collection<Document> documents) throws IOException {
        Objects.requireNonNull(documents, "Documents cannot be null");
        List<Document> snapshot;
        try {
            snapshot = List.copyOf(documents);
        } catch (NullPointerException exception) {
            throw new IllegalArgumentException("Document collection cannot contain null", exception);
        }

        Set<String> documentIds = new HashSet<>();
        for (Document document : snapshot) {
            if (!documentIds.add(document.id())) {
                throw new IllegalArgumentException("Duplicate document ID: " + document.id());
            }
        }

        String serialized = serialize(snapshot);
        Path parent = indexPath.getParent();
        if (parent == null) {
            throw new IOException("Index path has no parent directory: " + indexPath);
        }
        Files.createDirectories(parent);
        Path temporaryFile = Files.createTempFile(parent, indexPath.getFileName().toString(), ".tmp");
        boolean replaced = false;
        try {
            Files.writeString(temporaryFile, serialized, StandardCharsets.UTF_8);
            try {
                Files.move(temporaryFile, indexPath, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, indexPath, StandardCopyOption.REPLACE_EXISTING);
            }
            replaced = true;
        } finally {
            if (!replaced) {
                Files.deleteIfExists(temporaryFile);
            }
        }
    }

    /**
     * Loads and validates all documents in the configured file.
     * A missing file represents an empty saved index.
     *
     * @return immutable loaded documents
     * @throws IOException if the file is malformed, incompatible, or unreadable
     */
    public List<Document> load() throws IOException {
        if (!Files.exists(indexPath)) {
            return List.of();
        }
        List<String> lines = Files.readAllLines(indexPath, StandardCharsets.UTF_8);
        if (lines.size() < 3) {
            throw new IOException("Malformed MiniSearch index: incomplete header");
        }
        if (!FORMAT_ID.equals(lines.get(0))) {
            throw new IOException("Invalid MiniSearch index format identifier");
        }
        if (!FORMAT_VERSION.equals(lines.get(1))) {
            throw new IOException("Unsupported MiniSearch index version: " + lines.get(1));
        }

        int documentCount;
        try {
            documentCount = Integer.parseInt(lines.get(2));
        } catch (NumberFormatException exception) {
            throw new IOException("Malformed MiniSearch index document count", exception);
        }
        if (documentCount < 0 || lines.size() != 3L + (long) documentCount * 3L) {
            throw new IOException("Malformed MiniSearch index: document count does not match file structure");
        }

        List<Document> documents = new ArrayList<>(documentCount);
        Set<String> documentIds = new HashSet<>();
        for (int i = 3; i < lines.size(); i += 3) {
            try {
                String id = decode(lines.get(i));
                if (!documentIds.add(id)) {
                    throw new IOException("Duplicate document ID in MiniSearch index: " + id);
                }
                Path originalPath = Path.of(decode(lines.get(i + 1)));
                String content = decode(lines.get(i + 2));
                documents.add(new Document(id, originalPath, content));
            } catch (IOException exception) {
                throw new IOException("Malformed MiniSearch index record " + ((i - 3) / 3 + 1), exception);
            } catch (IllegalArgumentException | NullPointerException exception) {
                throw new IOException("Malformed MiniSearch index record " + ((i - 3) / 3 + 1), exception);
            }
        }
        return List.copyOf(documents);
    }

    private static String serialize(List<Document> documents) {
        StringBuilder data = new StringBuilder();
        data.append(FORMAT_ID).append('\n')
                .append(FORMAT_VERSION).append('\n')
                .append(documents.size()).append('\n');
        for (Document document : documents) {
            data.append(encode(document.id())).append('\n')
                    .append(encode(document.path().toString())).append('\n')
                    .append(encode(document.content())).append('\n');
        }
        return data.toString();
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) throws IOException {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (IllegalArgumentException | CharacterCodingException exception) {
            throw new IOException("Malformed Base64 or UTF-8 field in MiniSearch index", exception);
        }
    }
}
