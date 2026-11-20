package com.ayush.cli;

import com.ayush.core.SearchEngine;
import com.ayush.model.SearchResult;
import com.ayush.model.Snippet;
import com.ayush.persistence.IndexPersistence;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;

/**
 * Reads commands and presents MiniSearch results in a terminal session.
 */
public class SearchCli {

    private final SearchEngine searchEngine;
    private final InputStream input;
    private final PrintStream output;
    private final IndexPersistence indexPersistence;
    private boolean persistenceInitialized;

    /**
     * Creates a CLI using injected search and terminal components.
     *
     * @param searchEngine persistent engine for this CLI session
     * @param input command input stream
     * @param output output stream
     */
    public SearchCli(SearchEngine searchEngine, InputStream input, PrintStream output) {
        this(searchEngine, input, output, null);
    }

    /**
     * Creates a CLI with persistent index storage.
     *
     * @param searchEngine persistent engine for this CLI session
     * @param input command input stream
     * @param output output stream
     * @param indexPersistence persistence store to restore and save
     */
    public SearchCli(SearchEngine searchEngine, InputStream input, PrintStream output,
                     IndexPersistence indexPersistence) {
        this.searchEngine = Objects.requireNonNull(searchEngine, "Search engine cannot be null");
        this.input = Objects.requireNonNull(input, "Input stream cannot be null");
        this.output = Objects.requireNonNull(output, "Output stream cannot be null");
        this.indexPersistence = indexPersistence;
    }

    /**
     * Runs commands until the user enters {@code exit} or input ends.
     */
    public void run() {
        initializePersistence();
        Scanner scanner = new Scanner(input, java.nio.charset.StandardCharsets.UTF_8);
        output.println("MiniSearch CLI. Type 'help' for available commands.");
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                output.println("Please enter a command. Type 'help' for available commands.");
                continue;
            }

            int separator = firstWhitespace(line);
            String command = (separator < 0 ? line : line.substring(0, separator)).toLowerCase(Locale.ROOT);
            String argument = separator < 0 ? "" : line.substring(separator).trim();
            switch (command) {
                case "index" -> indexDirectory(argument);
                case "search" -> search(argument);
                case "help" -> showHelp();
                case "exit" -> {
                    output.println("Goodbye.");
                    return;
                }
                default -> output.println("Unknown command: " + command + ". Type 'help' for available commands.");
            }
        }
    }

    private void initializePersistence() {
        if (persistenceInitialized || indexPersistence == null) {
            return;
        }
        persistenceInitialized = true;
        if (!indexPersistence.exists()) {
            output.println("No saved index found. Starting with an empty index.");
            return;
        }
        try {
            var documents = indexPersistence.load();
            searchEngine.restoreDocuments(documents);
            output.println("Restored " + documents.size() + " document(s) from the saved index.");
        } catch (IOException | RuntimeException exception) {
            searchEngine.clear();
            output.println("Could not load saved index: " + exception.getMessage());
            output.println("Starting with an empty index. The saved file was left unchanged.");
        }
    }

    private void indexDirectory(String directory) {
        if (directory.isBlank()) {
            output.println("Usage: index <folder>");
            return;
        }
        Path path;
        try {
            path = Path.of(directory);
        } catch (IllegalArgumentException exception) {
            output.println("Invalid directory: " + exception.getMessage());
            return;
        }
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            output.println("Invalid directory: path does not exist or is not a directory: " + path);
            return;
        }

        try {
            searchEngine.indexDirectory(path);
            output.println("Indexed directory: " + path);
            output.println("Documents indexed: " + searchEngine.getIndexedDocumentCount());
            if (indexPersistence != null) {
                try {
                    indexPersistence.save(searchEngine.getIndexedDocuments());
                    output.println("Saved index to " + indexPersistence.path());
                } catch (IOException exception) {
                    output.println("Could not save index: " + exception.getMessage());
                }
            }
        } catch (IllegalArgumentException exception) {
            output.println("Could not load documents: " + exception.getMessage());
        } catch (IOException exception) {
            output.println("Could not index directory: " + exception.getMessage());
        }
    }

    private void search(String query) {
        if (query.isBlank()) {
            output.println("Usage: search <terms>");
            return;
        }
        try {
            var results = searchEngine.search(query);
            if (results.isEmpty()) {
                output.println("No results found.");
                return;
            }
            output.println("Search results:");
            for (int i = 0; i < results.size(); i++) {
                printResult(i + 1, results.get(i));
            }
        } catch (IllegalArgumentException exception) {
            output.println("Invalid search query: " + exception.getMessage());
        }
    }

    private void printResult(int number, SearchResult result) {
        output.printf(Locale.ROOT, "%n%d. %s%n   Score: %.4f%n", number,
                result.document().id(), result.score());
        if (!result.snippets().isEmpty()) {
            for (Snippet snippet : result.snippets()) {
                output.printf("   Lines %d-%d%n   %s%n", snippet.startLine(), snippet.endLine(), snippet.text());
            }
        } else {
            output.println("   (No snippet available)");
        }
        output.println("   Matching terms: " + String.join(", ", result.matchedTerms().stream().sorted().toList()));
    }

    private void showHelp() {
        output.println("Commands:");
        output.println("  index <folder>  Load supported documents from a folder");
        output.println("  search <terms>  Search indexed documents");
        output.println("  help            Show this help message");
        output.println("  exit            End the CLI session");
    }

    private static int firstWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
