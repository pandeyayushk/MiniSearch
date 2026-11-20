package com.ayush.core;

import com.ayush.model.Document;
import com.ayush.model.Snippet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts bounded passages surrounding query-term matches in a document.
 */
public class SnippetGenerator {

    private final Tokenizer tokenizer;

    /**
     * Creates a generator using the standard tokenizer.
     */
    public SnippetGenerator() {
        this(new Tokenizer());
    }

    /**
     * Creates a generator using the supplied tokenizer.
     *
     * @param tokenizer tokenizer used to normalize query terms
     */
    public SnippetGenerator(Tokenizer tokenizer) {
        this.tokenizer = Objects.requireNonNull(tokenizer, "Tokenizer cannot be null");
    }

    /**
     * Extracts snippets containing one or more query terms.
     *
     * @param document document to search
     * @param queryTerms terms to find
     * @param maxSnippetLength maximum number of UTF-16 characters per snippet
     * @return immutable snippets in document order
     */
    public List<Snippet> generate(Document document, Set<String> queryTerms, int maxSnippetLength) {
        Objects.requireNonNull(document, "Document cannot be null");
        Objects.requireNonNull(queryTerms, "Query terms cannot be null");
        if (maxSnippetLength <= 0) {
            throw new IllegalArgumentException("Maximum snippet length must be positive");
        }

        String content = document.content();
        Set<String> terms = normalizeTerms(queryTerms);
        List<Match> matches = new ArrayList<>();
        for (String term : terms) {
            Matcher matcher = termPattern(term).matcher(content);
            while (matcher.find()) {
                matches.add(new Match(matcher.start(), matcher.end(), term));
            }
        }
        if (matches.isEmpty()) {
            return List.of();
        }
        matches.sort(Comparator.comparingInt(Match::start).thenComparingInt(Match::end));

        List<Range> groups = groupMatches(matches, maxSnippetLength);
        List<Window> windows = new ArrayList<>();
        for (Range group : groups) {
            int windowLimit = Math.max(maxSnippetLength, group.end - group.start);
            Range window = contextWindow(group, content.length(), windowLimit);
            windows.add(new Window(window.start, window.end, group.start, group.end));
        }
        makeWindowsDisjoint(windows, content, maxSnippetLength);

        List<Snippet> snippets = new ArrayList<>();
        for (Window candidate : windows) {
            Range window = trimToWordBoundaries(content, new Range(candidate.start, candidate.end));
            if (window.start >= window.end) {
                continue;
            }
            String text = content.substring(window.start, window.end);
            Set<String> matched = new LinkedHashSet<>();
            for (String term : terms) {
                if (termPattern(term).matcher(text).find()) {
                    matched.add(term);
                }
            }
            if (!matched.isEmpty() && !text.isBlank()) {
                snippets.add(new Snippet(text, window.start, window.end,
                        lineNumberAt(content, window.start), lineNumberAt(content, window.end - 1), matched));
            }
        }
        return List.copyOf(snippets);
    }

    private Set<String> normalizeTerms(Set<String> queryTerms) {
        Set<String> terms = new LinkedHashSet<>();
        for (String queryTerm : queryTerms) {
            List<String> normalized = tokenizer.tokenize(Objects.requireNonNull(queryTerm, "Query term cannot be null"));
            if (normalized.size() == 1) {
                terms.add(normalized.getFirst());
            }
        }
        return terms;
    }

    private static Pattern termPattern(String term) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(term) + "(?![\\p{L}\\p{N}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static List<Range> groupMatches(List<Match> matches, int maxLength) {
        List<Range> groups = new ArrayList<>();
        int start = matches.getFirst().start;
        int end = matches.getFirst().end;
        for (int i = 1; i < matches.size(); i++) {
            Match match = matches.get(i);
            if (match.end - start <= maxLength) {
                end = Math.max(end, match.end);
            } else {
                groups.add(new Range(start, end));
                start = match.start;
                end = match.end;
            }
        }
        groups.add(new Range(start, end));
        return groups;
    }

    private static void makeWindowsDisjoint(List<Window> windows, String content, int maxLength) {
        int i = 0;
        while (i < windows.size() - 1) {
            Window left = windows.get(i);
            Window right = windows.get(i + 1);
            if (left.end <= right.start) {
                i++;
                continue;
            }

            int mergedStart = Math.min(left.start, right.start);
            int mergedEnd = Math.max(left.end, right.end);
            boolean matchRangesOverlap = right.matchStart < left.matchEnd;
            if (mergedEnd - mergedStart <= maxLength || matchRangesOverlap) {
                windows.set(i, new Window(mergedStart, mergedEnd,
                        Math.min(left.matchStart, right.matchStart), Math.max(left.matchEnd, right.matchEnd)));
                windows.remove(i + 1);
                if (i > 0) {
                    i--;
                }
                continue;
            }

            int boundary = chooseBoundary(content, left.matchEnd, right.matchStart);
            windows.set(i, new Window(left.start, Math.min(left.end, boundary), left.matchStart, left.matchEnd));
            windows.set(i + 1, new Window(Math.max(right.start, boundary), right.end,
                    right.matchStart, right.matchEnd));
            i++;
        }
    }

    private static int chooseBoundary(String content, int lowerBound, int upperBound) {
        int midpoint = lowerBound + (upperBound - lowerBound) / 2;
        for (int distance = 0; distance <= upperBound - lowerBound; distance++) {
            int before = midpoint - distance;
            if (isSafeBoundary(content, before, lowerBound, upperBound)) {
                return before;
            }
            int after = midpoint + distance;
            if (isSafeBoundary(content, after, lowerBound, upperBound)) {
                return after;
            }
        }
        return midpoint;
    }

    private static boolean isSafeBoundary(String content, int boundary, int lowerBound, int upperBound) {
        return boundary > lowerBound && boundary < upperBound
                && !isWordCharacter(content.charAt(boundary - 1))
                && !isWordCharacter(content.charAt(boundary));
    }

    private static Range contextWindow(Range matchRange, int contentLength, int maxLength) {
        int length = Math.min(maxLength, contentLength);
        int start = Math.max(0, matchRange.start - (length - (matchRange.end - matchRange.start)) / 2);
        int end = Math.min(contentLength, start + length);
        start = Math.max(0, end - length);
        return new Range(start, end);
    }

    private static Range trimToWordBoundaries(String content, Range range) {
        int start = range.start;
        int end = range.end;
        if (start > 0 && start < content.length() && isWordCharacter(content.charAt(start - 1))
                && isWordCharacter(content.charAt(start))) {
            while (start < end && isWordCharacter(content.charAt(start))) {
                start++;
            }
        }
        if (end < content.length() && end > start && isWordCharacter(content.charAt(end - 1))
                && isWordCharacter(content.charAt(end))) {
            while (end > start && isWordCharacter(content.charAt(end - 1))) {
                end--;
            }
        }
        return new Range(start, end);
    }

    private static boolean isWordCharacter(char character) {
        return Character.isLetterOrDigit(character);
    }

    private static int lineNumberAt(String content, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private record Match(int start, int end, String term) { }

    private record Range(int start, int end) { }

    private record Window(int start, int end, int matchStart, int matchEnd) { }
}
