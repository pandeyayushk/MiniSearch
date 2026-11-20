package com.ayush.core;

import com.ayush.model.Document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

/**
 * Loads supported UTF-8 text and Markdown documents from a directory tree.
 */
public class DocumentLoader {

    /**
     * Loads all regular {@code .txt} and {@code .md} files beneath the given root directory.
     * Results are sorted by portable relative ID and returned as an immutable list.
     *
     * @param rootDirectory directory tree to load
     * @return immutable documents sorted by relative ID
     * @throws NullPointerException     if {@code rootDirectory} is {@code null}
     * @throws IllegalArgumentException if the path does not exist or is not a directory
     * @throws IOException              if discovery or reading a supported file fails
     */
    public List<Document> load(Path rootDirectory) throws IOException {
        Objects.requireNonNull(rootDirectory, "Root directory cannot be null");

        if (!Files.exists(rootDirectory)) {
            throw new IllegalArgumentException("Root directory does not exist: " + rootDirectory);
        }
        if (!Files.isDirectory(rootDirectory)) {
            throw new IllegalArgumentException("Root path is not a directory: " + rootDirectory);
        }

        List<Path> supportedFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(rootDirectory, new FileVisitOption[0])) {
            paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(DocumentLoader::isSupported)
                    .forEach(supportedFiles::add);
        } catch (IOException exception) {
            throw new IOException("Failed to discover documents under " + rootDirectory, exception);
        } catch (UncheckedIOException exception) {
            throw new IOException("Failed to discover documents under " + rootDirectory, exception.getCause());
        }

        supportedFiles.sort(Comparator.comparing(path -> toDocumentId(rootDirectory.relativize(path))));
        List<Document> documents = new ArrayList<>(supportedFiles.size());
        for (Path file : supportedFiles) {
            String id = toDocumentId(rootDirectory.relativize(file));
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                documents.add(new Document(id, file, content));
            } catch (IOException exception) {
                throw new IOException("Failed to read supported document: " + file, exception);
            }
        }
        return List.copyOf(documents);
    }

    private static boolean isSupported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".txt") || name.endsWith(".md");
    }

    private static String toDocumentId(Path relativePath) {
        return relativePath.toString().replace('\\', '/');
    }
}
