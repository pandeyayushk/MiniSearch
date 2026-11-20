# MiniSearch project walkthrough

This trace follows a normal CLI session from process startup through saving, restart, and a ranked search. See [Architecture](ARCHITECTURE.md) for component diagrams and [Algorithms](ALGORITHMS.md) for scoring details.

## Example collection

Assume `./notes` contains:

```text
notes/
├── java/concurrency.md   # "Java manages threads. Thread synchronization protects shared state."
└── python/basics.txt     # "Python has readable syntax."
```

## Execution, step by step

1. **Launch.** `com.ayush.cli.Main.main` creates a `SearchEngine`, default `IndexPersistence`, and `SearchCli` using `System.in` and `System.out`.
2. **Check saved state.** At the start of `SearchCli.run`, `IndexPersistence.exists()` checks `~/.minisearch/index-v1.dat` (with `~` resolved through `user.home`).
3. **Restore if present.** The persistence layer validates the format, version, count, Base64, UTF-8, IDs, paths, and document values. It returns documents only after the complete file parses. `SearchEngine.restoreDocuments` sends them to `InvertedIndex.replaceDocuments`, which prepares postings with the existing tokenizer and installs them. The CLI reports the restored count.
4. **Accept the index command.** The user types `index ./notes`. The CLI resolves the rest of the command line as a path and checks that it exists and is a directory.
5. **Discover files.** `SearchEngine.indexDirectory` calls `DocumentLoader.load`. The loader recursively walks regular files without following links, accepts `.txt` and `.md` case-insensitively, reads UTF-8, and ignores unsupported extensions.
6. **Create documents.** Each loaded file gets an ID relative to `./notes`, such as `java/concurrency.md`; separators in IDs are `/`. The `Document` contains the original path and unchanged content.
7. **Build postings.** The loader returns the complete immutable document list. The engine calls `InvertedIndex.replaceDocuments`. The index creates a replacement instance and invokes its ordinary `addDocument` method for each item. `addDocument` calls `Tokenizer.tokenize` and counts each token. The active maps are replaced only after preparation completes.
8. **Save.** The CLI asks the engine for a sorted immutable document snapshot and passes it to `IndexPersistence.save`. The persistence layer writes a versioned UTF-8 text representation to a temporary sibling and moves it into place. It does not persist posting lists.
9. **Accept the search command.** The user types `search java thread synchronization`.
10. **Normalize query.** `SearchEngine.search` tokenizes the query with `Locale.ROOT`, removes duplicate terms with a `LinkedHashSet`, and returns immediately with no results when no tokens remain.
11. **Retrieve candidates.** For each query term, the engine calls `InvertedIndex.search`. It unions these IDs, so matching one or more terms qualifies (OR semantics).
12. **Calculate TF-IDF.** `TfIdfRanker.rank` gets each candidate's `Document` and posting frequencies. It calculates normalized TF over that document's token list, smoothed IDF from total document count and posting document frequency, and sums contributions from distinct matched query terms.
13. **Order results.** The ranker sorts by score descending and document ID ascending for equal scores. Candidates with a zero score are excluded.
14. **Generate snippets.** The engine calls `SnippetGenerator.generate` on each ranked document with the full normalized query-term set and a 240-character target. The generator finds case-insensitive whole-token matches, chooses context ranges, avoids overlapping snippets, and returns original-content offsets and one-based line ranges. A token longer than 240 characters can produce a longer snippet so the match is preserved.
15. **Display.** `SearchCli` prints each document ID, score, snippet line numbers and text, and the result's matched query terms.

Example interaction (output values are illustrative):

```text
index ./notes
Indexed directory: ./notes
Documents indexed: 2
Saved index to C:\Users\you\.minisearch\index-v1.dat
search java thread synchronization
Search results:

1. java/concurrency.md
   Score: 0.8031
   Lines 1-1
   Java manages threads. Thread synchronization protects shared state.
   Matching terms: java, synchronization, thread
```

## Restart behavior

On the next launch, the CLI parses the saved document records and reconstructs postings; it does not load an object snapshot of internal maps. A missing index is an explicitly reported empty start. A corrupt or incompatible index is reported and left unchanged while the CLI starts with an empty in-memory index.

Indexing the directory again replaces the entire indexed collection for that root. If a file was deleted, its ID disappears from the new postings and the new persisted snapshot. If loading or replacement preparation fails, the old in-memory index remains in use. A persistence-save failure is reported separately; the successfully indexed in-memory state remains for the current session.

## Short presentation summary

MiniSearch is a local Java 21 search CLI. It reads supported files, normalizes their words, builds an inverted index with frequencies, retrieves OR-based candidates, ranks them with smoothed TF-IDF, and adds original-text snippets. It persists source document fields in a versioned, atomically replaced file and reconstructs the index through the same indexing path at restart.
