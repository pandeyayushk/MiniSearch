package com.ayush.cli;

import com.ayush.core.SearchEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchCliTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void indexesAndSearchesUsingTheSameEngineForTheWholeSession() throws IOException {
        Path folder = Files.createDirectory(temporaryDirectory.resolve("docs"));
        Files.writeString(folder.resolve("java.md"), "Java supports thread synchronization.", StandardCharsets.UTF_8);

        String output = run("index " + folder + "\nsearch java thread\nexit\n");

        assertTrue(output.contains("Documents indexed: 1"));
        assertTrue(output.contains("java.md"));
        assertTrue(output.contains("Score:"));
        assertTrue(output.contains("Lines 1-1"));
        assertTrue(output.contains("Java supports thread synchronization."));
        assertTrue(output.contains("Matching terms: java, thread"));
    }

    @Test
    void displaysHelp() {
        String output = run("help\nexit\n");

        assertTrue(output.contains("index <folder>"));
        assertTrue(output.contains("search <terms>"));
        assertTrue(output.contains("help"));
        assertTrue(output.contains("exit"));
    }

    @Test
    void exitsImmediatelyAndDoesNotProcessLaterCommands() {
        String output = run("exit\nunknown\n");

        assertTrue(output.contains("Goodbye."));
        assertTrue(!output.contains("Unknown command"));
    }

    @Test
    void handlesEmptyInputAndInvalidCommands() {
        String output = run("\nnot-a-command\nsearch\nindex\nexit\n");

        assertTrue(output.contains("Please enter a command"));
        assertTrue(output.contains("Unknown command: not-a-command"));
        assertTrue(output.contains("Usage: search <terms>"));
        assertTrue(output.contains("Usage: index <folder>"));
    }

    @Test
    void reportsInvalidDirectoriesAndNoSearchMatches() {
        Path missing = temporaryDirectory.resolve("missing");
        String output = run("index " + missing + "\nsearch absent\nexit\n");

        assertTrue(output.contains("Invalid directory:"));
        assertTrue(output.contains("No results found."));
    }

    @Test
    void reportsIndexIoFailures() {
        SearchEngine failingEngine = new SearchEngine() {
            @Override
            public void indexDirectory(Path directory) throws IOException {
                throw new IOException("simulated read failure");
            }
        };
        String output = run(failingEngine, "index " + temporaryDirectory + "\nexit\n");

        assertTrue(output.contains("Could not index directory: simulated read failure"));
    }

    @Test
    void handlesEndOfInputWithoutManualInteraction() {
        String output = run("");

        assertTrue(output.contains("MiniSearch CLI"));
        assertEquals(1, output.lines().count());
    }

    private String run(String commands) {
        return run(new SearchEngine(), commands);
    }

    private String run(SearchEngine engine, String commands) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            SearchCli cli = new SearchCli(engine,
                    new ByteArrayInputStream(commands.getBytes(StandardCharsets.UTF_8)), output);
            cli.run();
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
