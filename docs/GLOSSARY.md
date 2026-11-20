# Glossary

| Term | Meaning in MiniSearch |
| --- | --- |
| **Document** | One immutable source file value: ID, original path, and complete original content. |
| **Token** | A lowercase run of Unicode letters and digits produced by `Tokenizer`. |
| **Tokenization** | Lowercasing with `Locale.ROOT` and splitting on non-letter/non-digit characters. |
| **Term frequency (TF)** | In the index, the number of token occurrences in a document. In ranking, normalized frequency: occurrences divided by total document tokens. |
| **Document frequency (DF)** | Number of indexed documents containing a term. |
| **Inverted index** | A lookup structure from a term to documents containing it and their term frequencies. |
| **Posting list** | The document-ID-to-frequency map belonging to one term. |
| **Vocabulary** | The set of terms with at least one posting; vocabulary size is the number of posting-map keys. |
| **Candidate document** | A document ID retrieved for at least one query term before ranking. MiniSearch uses OR matching. |
| **TF-IDF** | Ranking score formed by multiplying normalized TF by inverse document frequency for each distinct query term, then summing. |
| **IDF smoothing** | The `+1` adjustments in `ln((N+1)/(DF+1))+1`, which avoid a zero logarithm for terms present in every document and keep weights positive. |
| **Ranking** | Sorting candidates by descending TF-IDF score, with document ID ascending as the tie-breaker. |
| **Snippet** | A bounded passage around one or more query-term matches, with match terms, offsets, and line numbers. |
| **Character offset** | A zero-based position in the original Java string. Java indexes strings in UTF-16 code units; snippet end offsets are exclusive. |
| **Serialization** | Converting document fields to the versioned text file representation. MiniSearch Base64-encodes each UTF-8 field; it does not use Java object serialization. |
| **Deserialization** | Validating the persisted fields and reconstructing immutable `Document` values. |
| **Persistence** | Retaining document records on disk across CLI sessions. |
| **Atomic move** | A filesystem replacement operation intended to make the final move indivisible. MiniSearch falls back to a regular replacement move when unsupported. |
| **UTF-8** | The character encoding used to read source files and encode persistence fields. |
| **Base64** | A textual encoding that makes arbitrary UTF-8 field bytes safe to store on one line; it is not encryption. |

See [Algorithms](ALGORITHMS.md) for the formulas and [Persistence](PERSISTENCE.md) for the file lifecycle.
