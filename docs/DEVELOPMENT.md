# Development guide

## Environment and setup

Use Java 21 and Maven. Clone and enter the project:

```bash
git clone https://github.com/pandeyayushk/MiniSearch.git
cd MiniSearch
```

Build and run tests:

```bash
mvn clean test
mvn package
```

Run the compiled CLI:

```bash
java -cp target/classes com.ayush.cli.Main
```

JUnit Jupiter is the only declared dependency and is test-scoped. The compiler release is set to 21 in `pom.xml`; Maven Compiler and Surefire plugins are configured there.

## Package conventions

- `com.ayush.cli`: terminal command parsing and presentation. `Main` wires standard application components; `SearchCli` owns the command loop and persistence lifecycle.
- `com.ayush.core`: loading, tokenization, indexing, orchestration, scoring, and snippet extraction.
- `com.ayush.model`: immutable `Document`, `SearchResult`, and `Snippet` records.
- `com.ayush.persistence`: the versioned on-disk document format and atomic replacement.

Keep file I/O in the loader and persistence layer, score math in `TfIdfRanker`, token normalization in `Tokenizer`, and terminal output in `SearchCli`.

## Existing tests

All tests use JUnit 5. Tests that need filesystem state use `@TempDir`.

| Test class | Main coverage |
| --- | --- |
| `TokenizerTest` | Locale-stable lowercase, delimiter splitting, duplicates, order, numbers, Unicode, null. |
| `DocumentLoaderTest` | Recursive discovery, extensions, relative IDs, UTF-8 content, sorting, symlinks, invalid roots. |
| `InvertedIndexTest` | Postings, frequencies, replacement/removal, vocabulary cleanup, counts, validation, immutable search results. |
| `TfIdfRankerTest` | TF, smoothed IDF, combined scores, candidate ranking, tie-breaking, immutable results, invalid arguments. |
| `SnippetGeneratorTest` | Whole-token matching, context limits, grouping, non-overlap, offsets/lines, Unicode, immutable output. |
| `SearchEngineTest` | Directory indexing, query handling, ranking, snippet integration, restore/snapshot behavior. |
| `DocumentTest` | Record accessors, equality, and constructor validation. |
| `SearchResultTest` | Score validation, matched-term copying, snippets, and record behavior. |
| `SnippetTest` | Text, offsets, line ranges, matched-term validation and immutability. |
| `SearchCliTest` | Commands, injected streams, terminal output, and error reporting. |
| `SearchCliPersistenceTest` | Save/restart/restore, stale-file replacement, corrupt store behavior, ranking and snippets after restore. |
| `IndexPersistenceTest` | Round trips, UTF-8/Base64 records, malformed data, duplicate IDs, atomic replacement cleanup. |

## Adding a feature

1. Identify the owning layer before editing. For example, a normalization change belongs in `Tokenizer`, not in `SearchCli` or `IndexPersistence`.
2. Keep public APIs narrow and inject collaborators where the surrounding class already uses constructor injection.
3. Preserve immutable result boundaries; use defensive copies for returned collections.
4. Add focused JUnit 5 tests beside the corresponding package. Use real `Document`/`InvertedIndex` objects for algorithmic behavior and temporary directories for filesystem behavior.
5. Run `mvn clean test`. For scoring, compare doubles with a delta; for persistence, test through an injected path rather than the user's home directory.
6. Update README and relevant detailed docs if observable behavior or a public method changes.

## Debugging by layer

- **Discovery or IDs:** inspect `DocumentLoader.load`, extension filtering, and `rootDirectory.relativize` before tracing into indexing.
- **Unexpected tokens:** inspect `Tokenizer.tokenize`; it owns Unicode letter/digit boundaries and `Locale.ROOT` normalization.
- **Missing or stale search hits:** inspect `InvertedIndex` postings and replacement cleanup. Directory indexing builds a replacement index before installing it.
- **Unexpected order or scores:** inspect `TfIdfRanker.calculateTf`, `calculateIdf`, and `rank`; then verify the current document count and posting document frequency.
- **Snippet boundaries:** compare `Snippet.startOffset/endOffset` with the original `String` and check one-based lines against newline positions.
- **Persistence restore or save:** inspect the format header/count and record lines; `IndexPersistence` throws on invalid structure. CLI startup messages distinguish absent and corrupt stores.
- **CLI behavior:** use `SearchCli` with injected `InputStream` and `PrintStream`, as `SearchCliTest` does.

The repository does not define a formatter, static-analysis plugin, or a prescribed branching/release workflow in Maven configuration.
