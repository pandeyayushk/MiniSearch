package com.ayush.cli;

import com.ayush.core.SearchEngine;
import com.ayush.model.SearchResult;
import com.ayush.model.Snippet;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
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

    /**
     * Creates a CLI using injected search and terminal components.
     *
     * @param searchEngine persistent engine for this CLI session
     * @param input command input stream
     * @param output output stream
     */
    public SearchCli(SearchEngine searchEngine, InputStream input, PrintStream output) {
        this.searchEngine = Objects.requireNonNull(searchEngine, "Search engine cannot be null");
        this.input = Objects.requireNonNull(input, "Input stream cannot be null");
        this.output = Objects.requireNonNull(output, "Output stream cannot be null");
    }

    /**
     * Runs commands until the user enters {@code exit} or input ends.
     */
    public void run() {
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

    private void indexDirectory(String directory) {
        if (directory.isBlank()) {
            output.println("Usage: index <folder>");
            return;
        }
        try {
            Path path = Path.of(directory);
            searchEngine.indexDirectory(path);
            output.println("Indexed directory: " + path);
            output.println("Documents indexed: " + searchEngine.getIndexedDocumentCount());
        } catch (IllegalArgumentException exception) {
            output.println("Invalid directory: " + exception.getMessage());
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
