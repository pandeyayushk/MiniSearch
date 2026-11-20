# MiniSearch

MiniSearch is a lightweight, local document search engine written in Java 21. It indexes `.txt` and `.md` files beneath a directory, searches their contents with an inverted index, ranks matches with TF-IDF, and shows short contextual snippets in a terminal CLI.

It is intended to make a small collection of local documents searchable without a remote service or database. Unlike a filename/content grep, MiniSearch keeps a reusable in-memory index, calculates term frequencies, ranks multi-term matches, and retains the index between CLI sessions.

## Features

- Recursively discovers regular `.txt` and `.md` files, with case-insensitive extensions. Symbolic links are not followed.
- Reads file contents as UTF-8 and generates portable document IDs relative to the selected root directory.
- Lowercases with `Locale.ROOT`; splits on characters other than Unicode letters and digits; preserves duplicate tokens and order.
- Builds an inverted index of `term → document ID → frequency`.
- Searches with OR semantics: a document matching any query term can be returned.
- Ranks candidates using normalized term frequency and smoothed inverse document frequency.
- Extracts bounded context snippets with matched terms, original-content UTF-16 offsets, and one-based line numbers.
- Provides `index`, `search`, `help`, and `exit` terminal commands.
- Persists documents in a versioned UTF-8 text file with Base64-encoded fields and restores the postings at startup.
- Re-indexing a directory replaces the indexed document collection, including removing documents that disappeared from that directory.

## Technology

- **Java 21**: records, modern collection APIs, and the application runtime.
- **Maven**: compilation, dependency management, and test execution.
- **JUnit 5**: unit and integration tests.
- **Java Collections Framework**: token lists, posting maps, candidate sets, and immutable results.
- **Java NIO**: recursive file discovery, UTF-8 file operations, temporary files, and atomic moves.
- **Java I/O**: CLI streams and scanner-based input.
- **Regular expressions**: Unicode token splitting and whole-token snippet matching.

## Build and run

Clone the repository and enter it:

```bash
git clone https://github.com/pandeyayushk/MiniSearch.git
cd MiniSearch
```

Build and run the tests:

```bash
mvn clean package
mvn test
```

Start the CLI after compilation:

```bash
java -cp target/classes com.ayush.cli.Main
```

Java 21 and Maven must be installed. The production application has no third-party runtime dependencies; JUnit 5 is test-scoped.

## CLI usage

```text
MiniSearch CLI. Type 'help' for available commands.
index ./documents
Indexed directory: ./documents
Documents indexed: 4
Saved index to C:\Users\you\.minisearch\index-v1.dat
search java concurrency
Search results:

1. java/concurrency.md
   Score: 0.8421
   Lines 3-5
   Java provides mechanisms for managing multiple threads safely.
   Matching terms: concurrency, java
help
Commands:
  index <folder>  Load supported documents from a folder
  search <terms>  Search indexed documents
  help            Show this help message
  exit            End the CLI session
exit
Goodbye.
```

The sample output is illustrative; scores, snippets, paths, and line numbers depend on the indexed files and corpus.

| Command | Behavior |
| --- | --- |
| `index <folder>` | Recursively load supported files and replace the current indexed collection. The updated document collection is saved when the CLI is using persistence. |
| `search <terms>` | Search one or more terms. Terms are normalized and deduplicated; any term can match. Results are ordered by descending TF-IDF score, then ascending document ID. |
| `help` | Print command usage. |
| `exit` | End the session. |

An empty or punctuation-only query returns no results. A missing or non-directory index path is reported as invalid. Loading errors, such as a blank supported file, are reported separately.

## Persistent storage

The CLI stores data at `~/.minisearch/index-v1.dat`, with the home directory resolved from `System.getProperty("user.home")`. The file contains a format identifier, version, document count, and each document's ID, original path, and full content as separate Base64-encoded UTF-8 fields.

MiniSearch stores documents rather than serialized posting maps. At startup it validates and loads the documents, then indexes them through the normal `InvertedIndex` tokenization path. Saves go to a sibling temporary file before a replacement move. If atomic moves are unsupported, a regular replacement move is used. See [Persistence](docs/PERSISTENCE.md) for the format and failure behavior.

If the file is missing, the CLI starts empty. If it is corrupted or has an unsupported version, the CLI reports the error, starts empty, and leaves the saved file untouched.

## Project structure

```text
.
├── .gitignore
├── pom.xml
├── README.md
├── docs/
│   ├── ALGORITHMS.md
│   ├── ARCHITECTURE.md
│   ├── DEVELOPMENT.md
│   ├── GLOSSARY.md
│   ├── PERSISTENCE.md
│   └── PROJECT_WALKTHROUGH.md
└── src/
    ├── main/java/com/ayush/
    │   ├── cli/
    │   │   ├── Main.java
    │   │   └── SearchCli.java
    │   ├── core/
    │   │   ├── DocumentLoader.java
    │   │   ├── InvertedIndex.java
    │   │   ├── SearchEngine.java
    │   │   ├── SnippetGenerator.java
    │   │   ├── TfIdfRanker.java
    │   │   └── Tokenizer.java
    │   ├── model/
    │   │   ├── Document.java
    │   │   ├── SearchResult.java
    │   │   └── Snippet.java
    │   └── persistence/
    │       └── IndexPersistence.java
    └── test/java/com/ayush/
        ├── cli/
        │   ├── SearchCliPersistenceTest.java
        │   └── SearchCliTest.java
        ├── core/
        │   ├── DocumentLoaderTest.java
        │   ├── InvertedIndexTest.java
        │   ├── SearchEngineTest.java
        │   ├── SnippetGeneratorTest.java
        │   ├── TfIdfRankerTest.java
        │   └── TokenizerTest.java
        ├── model/
        │   ├── DocumentTest.java
        │   ├── SearchResultTest.java
        │   └── SnippetTest.java
        └── persistence/
            └── IndexPersistenceTest.java
```

Class responsibilities are described in [Architecture](docs/ARCHITECTURE.md), with a complete execution trace in the [project walkthrough](docs/PROJECT_WALKTHROUGH.md).

## Current limitations

- Only UTF-8 `.txt` and `.md` regular files are supported; PDF and DOCX are ignored.
- Empty or whitespace-only supported files are rejected by `Document` validation, so a directory containing one fails to index as a whole.
- The index and search candidates are held in memory. The persistence file has no checksum, encryption, inter-process lock, or configurable size limit.
- Indexing replaces the whole collection for a root; there is no file watcher or incremental synchronization.
- Tokenization does not remove stop words, stem, or lemmatize. Search uses OR matching.
- A snippet may exceed its configured length when necessary to preserve a matching token longer than that limit.
- The CLI is interactive and local; there is no web interface or database backend.

Further technical details: [Algorithms](docs/ALGORITHMS.md), [Development](docs/DEVELOPMENT.md), and [Glossary](docs/GLOSSARY.md).
