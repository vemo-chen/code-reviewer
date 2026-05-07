package com.vemo.codereview.review.service;

import com.vemo.codereview.review.model.ReviewCodeSnippet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

abstract class AbstractTextContextExpansionStrategy {

    protected List<String> lines(String content) {
        if (content == null) {
            return Collections.emptyList();
        }
        String[] items = content.split("\\r?\\n", -1);
        List<String> lines = new ArrayList<String>();
        Collections.addAll(lines, items);
        return lines;
    }

    protected ReviewCodeSnippet snippet(String title, List<String> lines, int startLine, int endLine) {
        int safeStart = Math.max(1, startLine);
        int safeEnd = Math.min(lines.size(), Math.max(safeStart, endLine));
        StringBuilder content = new StringBuilder();
        for (int i = safeStart; i <= safeEnd; i++) {
            content.append(lines.get(i - 1));
            if (i < safeEnd) {
                content.append('\n');
            }
        }
        ReviewCodeSnippet snippet = new ReviewCodeSnippet();
        snippet.setTitle(title);
        snippet.setStartLine(safeStart);
        snippet.setEndLine(safeEnd);
        snippet.setContent(content.toString());
        return snippet;
    }

    protected boolean covers(ReviewCodeSnippet snippet, int line) {
        return snippet != null
            && snippet.getStartLine() != null
            && snippet.getEndLine() != null
            && line >= snippet.getStartLine()
            && line <= snippet.getEndLine();
    }

    protected List<ReviewCodeSnippet> unique(List<ReviewCodeSnippet> snippets) {
        if (CollectionUtils.isEmpty(snippets)) {
            return Collections.emptyList();
        }
        List<ReviewCodeSnippet> result = new ArrayList<ReviewCodeSnippet>();
        for (ReviewCodeSnippet candidate : snippets) {
            boolean duplicate = false;
            for (ReviewCodeSnippet existing : result) {
                if (sameRange(existing, candidate)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate && StringUtils.hasText(candidate.getContent())) {
                result.add(candidate);
            }
        }
        result.sort(Comparator.comparing(ReviewCodeSnippet::getStartLine));
        return result;
    }

    private boolean sameRange(ReviewCodeSnippet left, ReviewCodeSnippet right) {
        return left != null && right != null
            && left.getStartLine() != null
            && left.getStartLine().equals(right.getStartLine())
            && left.getEndLine() != null
            && left.getEndLine().equals(right.getEndLine());
    }

    protected int findMatchingBraceEnd(List<String> lines, int startLine) {
        int depth = 0;
        boolean seenOpen = false;
        for (int i = startLine; i <= lines.size(); i++) {
            String line = stripStrings(lines.get(i - 1));
            for (int j = 0; j < line.length(); j++) {
                char item = line.charAt(j);
                if (item == '{') {
                    depth++;
                    seenOpen = true;
                } else if (item == '}') {
                    depth--;
                    if (seenOpen && depth <= 0) {
                        return i;
                    }
                }
            }
        }
        return startLine;
    }

    protected int findBraceStart(List<String> lines, int changedLine) {
        int depth = 0;
        for (int i = changedLine; i >= 1; i--) {
            String line = stripStrings(lines.get(i - 1));
            for (int j = line.length() - 1; j >= 0; j--) {
                char item = line.charAt(j);
                if (item == '}') {
                    depth++;
                } else if (item == '{') {
                    if (depth == 0) {
                        return i;
                    }
                    depth--;
                }
            }
        }
        return changedLine;
    }

    private String stripStrings(String line) {
        return line == null ? "" : line.replaceAll("\"([^\"\\\\]|\\\\.)*\"", "\"\"")
            .replaceAll("'([^'\\\\]|\\\\.)*'", "''");
    }
}
