# MiniSearch algorithms

This guide follows `Tokenizer`, `InvertedIndex`, `TfIdfRanker`, and `SnippetGenerator` as currently implemented. Overall component flow is in [Architecture](ARCHITECTURE.md).

## 1. Tokenization

`Tokenizer.tokenize(String)` applies `text.toLowerCase(Locale.ROOT)`, then splits on the regular expression `[^\p{L}\p{N}]+`. In other words, any run that is not a Unicode letter or digit is a delimiter. Empty split results are discarded.

```java
tokenize("Java,Python;C++")       // ["java", "python", "c"]
tokenize("Hello!!!World")         // ["hello", "world"]
tokenize("Hello123 123")          // ["hello123", "123"]
tokenize("Java Java")             // ["java", "java"]
tokenize("Café 你好")              // ["café", "你好"]
```

Order and frequency are preserved; duplicates are useful for term-frequency calculation. Empty and delimiter-only strings yield an empty list. Null input throws `NullPointerException`. No stop-word filtering, stemming, or lemmatization occurs. `Locale.ROOT` makes case normalization independent of the machine's locale.

## 2. Inverted index

The index stores postings in this shape:

```text
Map<String, Map<String, Integer>> postings
term → (document ID → term frequency)
```

The second map is a document registry: `Map<String, Document> documents`.

For these documents:

```text
doc-a: java java concurrency
doc-b: java threads
doc-c: concurrency threads threads
```

the postings are:

```text
java        → {doc-a: 2, doc-b: 1}
concurrency → {doc-a: 1, doc-c: 1}
threads     → {doc-b: 1, doc-c: 2}
```

`addDocument` tokenizes content, counts each token, removes old postings for the same ID, stores the document, then inserts the new term frequencies. `replaceDocuments` prepares an entirely new `InvertedIndex` by using `addDocument`, then installs its maps after preparation completes. `removeDocument` removes the ID from postings and deletes vocabulary entries that have no remaining documents. `search(term)` normalizes the term and returns an immutable set of IDs; it rejects terms that normalize to zero or multiple tokens. `getTermFrequency` returns zero when a posting is absent. Vocabulary size is the number of posting keys; document count is the registry size.

## 3. TF-IDF ranking

`TfIdfRanker` implements:

\[
TF(t,d)=\frac{f(t,d)}{\operatorname{TotalTokens}(d)}
\]

\[
IDF(t)=\ln\left(\frac{N+1}{DF(t)+1}\right)+1
\]

\[
Score(d,q)=\sum_{t\in q}TF(t,d)\times IDF(t)
\]

Where:

- `t` is one normalized term and `d` is a document.
- `f(t,d)` is the number of times `t` occurs in the tokenized content of `d`.
- `TotalTokens(d)` is the number of tokens in `d`.
- `N` is `InvertedIndex.getDocumentCount()`.
- `DF(t)` is the number of IDs returned by `InvertedIndex.search(t)`.
- `q` is the distinct normalized query-term set.
- `ln` is the natural logarithm implemented with `Math.log()`.

TF is zero if a document has no tokens. IDF is zero for a valid term when the index has no documents. For a non-empty corpus, smoothing adds one to numerator and denominator and adds one to the logarithm. Thus a term in every document has IDF 1, while a rarer term has a larger IDF. Unknown query terms have zero TF contribution in a document. The ranker de-duplicates and normalizes terms; repeated terms do not contribute more than once.

### Worked example

Corpus:

```text
d1: java java concurrency
d2: java concurrency threads
d3: java
query: java concurrency threads
```

There are `N=3` documents. `java` appears in all three, so `IDF(java)=ln(4/4)+1=1`. `concurrency` appears in two, so `IDF(concurrency)=ln(4/3)+1≈1.287682`. `threads` appears in one, so `IDF(threads)=ln(4/2)+1≈1.693147`.

| Document | TF contributions | Score |
| --- | --- | ---: |
| `d1` | `(2/3 × 1) + (1/3 × 1.287682)` | `≈ 1.095894` |
| `d2` | `(1/3 × 1) + (1/3 × 1.287682) + (1/3 × 1.693147)` | `≈ 1.326943` |
| `d3` | `1/1 × 1` | `1.000000` |

The resulting order is `d2`, `d1`, `d3`. Repeating `java` in the query would not change scores. `TfIdfRanker.rank` excludes zero-score candidates, sorts by descending score, then uses ascending document ID to break exact score ties. The engine supplies OR candidates: a document needs at least one query term to be considered.

## 4. Snippet generation

`SnippetGenerator.generate(document, queryTerms, maxSnippetLength)` normalizes each supplied query term and ignores values that do not normalize to exactly one token. It compiles case-insensitive Unicode-aware regexes with letter/digit lookarounds. Thus `java` matches `Java,` but not `javascript`.

The generator records each whole-token match's original string start and end positions. Nearby match ranges are grouped when their combined span fits the configured maximum. It chooses context windows around groups, merges overlapping windows when the result fits, or cuts overlapping context at a safe boundary between matches. Matches remain covered. Context edges are adjusted to avoid clipping through a word. Results are emitted in document order with immutable matched-term sets and an immutable result list.

The configured maximum must be positive. Returned text is capped at that length in normal cases. If the matching term itself is longer than the maximum, it is retained whole and that snippet exceeds the limit rather than losing the match.

Offsets are zero-based inclusive/exclusive offsets into the original Java `String`, hence UTF-16 code-unit offsets. `Snippet` validates `endOffset - startOffset == text.length()`. Line numbers are one-based and counted from newline (`\n`) characters in the original content.

Example:

```text
Original content:
Intro text. Java supports threads safely. End text.

Query terms: java, threads
Snippet (if the maximum covers the whole passage):
"Intro text. Java supports threads safely. End text."
Matched terms: java, threads
Offsets: [0, 51)   Lines: 1-1
```

Offsets and exact snippet size depend on the requested maximum and surrounding content; use the `Snippet` fields rather than inferring offsets from displayed excerpts. See [Glossary](GLOSSARY.md) for term definitions.
