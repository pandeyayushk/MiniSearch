# Index persistence

MiniSearch persists document snapshots, not Java object graphs or internal posting maps. `IndexPersistence` owns the file format and replacement operation; `SearchCli` decides when to restore and save. The search pipeline remains independent of disk storage.

## Location

The default path is resolved at runtime:

```text
<System.getProperty("user.home")>/.minisearch/index-v1.dat
```

For example, on Windows this is usually `%USERPROFILE%\.minisearch\index-v1.dat`; on Unix-like systems it is usually `$HOME/.minisearch/index-v1.dat`. The implementation does not hardcode either form. `IndexPersistence(Path)` allows tests or embedding code to choose another location.

## Version 1 format

The file is UTF-8 text with one field per line:

1. Format identifier: `MINISEARCH_INDEX`
2. Version: `1`
3. Decimal document count
4. For every document, three Base64 lines in this order: ID, original path string, full content

The fields are Base64-encoded from UTF-8 bytes using Java's standard Base64 encoder. Newlines, Unicode, and arbitrary content punctuation therefore remain within one record line.

Illustrative file (the field values are abbreviated for readability; real Base64 payloads are complete):

```text
MINISEARCH_INDEX
1
1
amF2YS9ndWlkZS5tZA==
L3dvcmsvZG9jcy9ndWlkZS5tZA==
SmF2YSBndWlkZQpTZWNvbmQgbGluZQ==
```

The count must be non-negative and the number of following lines must equal three times the count. The loader rejects malformed Base64, malformed UTF-8, invalid records, duplicate IDs, invalid `Document` values, invalid paths, wrong counts, unknown format IDs, and unsupported versions. A missing file loads as an empty list; callers can also check `exists()`.

## Save lifecycle

`save(Collection<Document>)`:

1. Rejects null collections, null documents, and duplicate document IDs before touching storage.
2. Serializes the complete snapshot in memory.
3. Creates the parent directory if necessary.
4. Creates a temporary file in the same directory as the final index.
5. Writes UTF-8 data and closes the write operation.
6. Moves the temp file over the target with `ATOMIC_MOVE` and `REPLACE_EXISTING`.
7. Falls back to `REPLACE_EXISTING` if the filesystem reports that atomic moves are unsupported.
8. Deletes the temporary file after a failed replacement.

The previous index is not written in place. Validation and serialization happen before temp-file creation. Move failures leave the temporary file cleanup to the `finally` path. This reduces exposure to partial output, but the code does not force data to stable storage with `fsync`.

## Load and restore lifecycle

At CLI startup, `SearchCli` checks whether the file exists. When present, it loads and validates every record before calling `SearchEngine.restoreDocuments`. The engine and index reconstruct postings using the same `InvertedIndex.addDocument` tokenization path used during normal indexing. Persisting only source document fields avoids coupling the file format to internal map layouts.

If no file exists, the CLI communicates that it starts empty. If loading fails due to corruption or an unsupported version, it reports the failure, clears the in-memory engine, and states that the saved file is unchanged. It does not silently replace a bad file at startup. A later explicit `index <folder>` operation can save a new snapshot after successful indexing.

After `index <folder>` succeeds, the CLI saves the engine's sorted immutable document snapshot. If the save itself fails, the CLI reports that failure; the in-memory index has already been updated, while replacement is designed to preserve the prior disk file when the move fails.

## Failure cases

| Condition | Behavior |
| --- | --- |
| Missing file | `load()` returns an empty immutable list. |
| Invalid identifier, version, count, or record structure | `load()` throws `IOException`; startup reports it and starts empty without changing the file. |
| Invalid Base64 or UTF-8 field | `load()` throws `IOException`. |
| Invalid document record (for example, blank content) | Record construction fails and is reported as malformed persistence. |
| Duplicate IDs on save | `save()` throws `IllegalArgumentException` before creating/replacing a file. |
| Failed write or move | `IOException` propagates; a failed temp is cleaned up; the direct target is not used as the write destination. |
| Unsupported atomic move | A regular replacement move is attempted. |

The project deliberately uses a small text format instead of Java object serialization: the fields are explicit, the format has an inspectable version, and class implementation details are not serialized. A database is unnecessary for this local, single-user collection and would add an external service or dependency.

Current tradeoffs: the complete file is read into memory, the format has no checksum or encryption, and concurrent processes are not coordinated. Tests pass custom `@TempDir` paths; they never write to the actual home directory.
