package com.ayush.core;

import com.ayush.model.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DocumentLoaderTest {

    @TempDir
    Path temporaryDirectory;

    private final DocumentLoader loader = new DocumentLoader();

    @Test
    void loadsTxtFiles() throws IOException {
        Path root = newRoot();
        write(root, "notes.txt", "Text content");

        assertEquals(List.of("notes.txt"), ids(loader.load(root)));
    }

    @Test
    void loadsMarkdownFiles() throws IOException {
        Path root = newRoot();
        write(root, "notes.md", "Markdown content");

        assertEquals(List.of("notes.md"), ids(loader.load(root)));
    }

    @Test
    void discoversSupportedFilesInNestedDirectories() throws IOException {
        Path root = newRoot();
        write(root, "a/b/nested.md", "Nested");

        assertEquals(List.of("a/b/nested.md"), ids(loader.load(root)));
    }

    @Test
    void matchesUppercaseAndMixedCaseExtensions() throws IOException {
        Path root = newRoot();
        write(root, "upper.TXT", "Upper");
        write(root, "mixed.Md", "Mixed");

        assertEquals(List.of("mixed.Md", "upper.TXT"), ids(loader.load(root)));
    }

    @Test
    void ignoresUnsupportedExtensions() throws IOException {
        Path root = newRoot();
        write(root, "document.pdf", "PDF");
        write(root, "document.docx", "DOCX");

        assertTrue(loader.load(root).isEmpty());
    }

    @Test
    void ignoresDirectoriesAndNonRegularFiles() throws IOException {
        Path root = newRoot();
        Files.createDirectory(root.resolve("folder.md"));
        Path target = write(root, "target.txt", "Target");
        try {
            Files.createSymbolicLink(root.resolve("link.md"), target);
        } catch (UnsupportedOperationException | SecurityException | IOException exception) {
            // Symlinks may be unavailable in the current filesystem or process context.
        }

        assertEquals(List.of("target.txt"), ids(loader.load(root)));
    }

    @Test
    void generatesIdsRelativeToRootAndUsesForwardSlashes() throws IOException {
        Path root = newRoot();
        write(root, "nested/deeper/file.md", "Content");

        String id = loader.load(root).getFirst().id();
        assertEquals("nested/deeper/file.md", id);
        assertFalse(id.startsWith(root.toString()));
        assertFalse(id.contains("\\"));
    }

    @Test
    void distinguishesSameFilenameInDifferentDirectories() throws IOException {
        Path root = newRoot();
        write(root, "java/overview.md", "Java");
        write(root, "python/overview.md", "Python");

        assertEquals(List.of("java/overview.md", "python/overview.md"), ids(loader.load(root)));
    }

    @Test
    void readsUtf8AndPreservesOriginalContent() throws IOException {
        Path root = newRoot();
        String content = "  Café — 你好!\r\n\tEnd.  ";
        Files.createDirectories(root.resolve("nested"));
        Files.writeString(root.resolve("nested/text.txt"), content, StandardCharsets.UTF_8);

        assertEquals(content, loader.load(root).getFirst().content());
    }

    @Test
    void returnsEmptyListForEmptyDirectory() throws IOException {
        assertTrue(loader.load(newRoot()).isEmpty());
    }

    @Test
    void returnsImmutableResultList() throws IOException {
        Path root = newRoot();
        write(root, "document.md", "Content");
        List<Document> documents = loader.load(root);

        assertThrows(UnsupportedOperationException.class,
                () -> documents.add(new Document("another.md", Path.of("another.md"), "Other")));
    }

    @Test
    void sortsResultsDeterministicallyByDocumentId() throws IOException {
        Path root = newRoot();
        write(root, "z-last.md", "Z");
        write(root, "a-first.txt", "A");
        write(root, "middle/document.md", "M");

        assertEquals(List.of("a-first.txt", "middle/document.md", "z-last.md"), ids(loader.load(root)));
    }

    @Test
    void doesNotFollowSymbolicLinksWhenSupported() throws IOException {
        Path root = newRoot();
        Path target = write(root, "real.md", "Real");
        Path link = root.resolve("linked.md");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | SecurityException | IOException exception) {
            assumeTrue(false, "Symbolic links are unavailable: " + exception.getMessage());
        }

        assertEquals(List.of("real.md"), ids(loader.load(root)));
    }

    @Test
    void rejectsNullRootDirectory() {
        assertThrows(NullPointerException.class, () -> loader.load(null));
    }

    @Test
    void rejectsNonexistentRootDirectory() {
        assertThrows(IllegalArgumentException.class, () -> loader.load(temporaryDirectory.resolve("missing")));
    }

    @Test
    void rejectsRootPathThatIsARegularFile() throws IOException {
        Path file = Files.createFile(temporaryDirectory.resolve("document.txt"));

        assertThrows(IllegalArgumentException.class, () -> loader.load(file));
    }

    private Path newRoot() throws IOException {
        return Files.createDirectory(temporaryDirectory.resolve("root"));
    }

    private static Path write(Path root, String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static List<String> ids(List<Document> documents) {
        return documents.stream().map(Document::id).toList();
    }
}
