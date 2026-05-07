package com.vemo.codereview.review.service;

import com.vemo.codereview.platform.gitlab.model.GitLabChangesPayload;
import com.vemo.codereview.review.model.ReviewCodeSnippet;
import com.vemo.codereview.review.model.ReviewContextRisk;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
public class FallbackLineWindowExpansionStrategy extends AbstractTextContextExpansionStrategy implements ContextExpansionStrategy {

    @Override
    public boolean supports(String filePath) {
        return true;
    }

    @Override
    public List<ReviewCodeSnippet> expand(
        GitLabChangesPayload.Change change,
        String fileContent,
        List<Integer> changedLines,
        ReviewContextRisk risk) {
        if (!StringUtils.hasText(fileContent)) {
            return Collections.emptyList();
        }
        List<String> lines = lines(fileContent);
        if (lines.isEmpty()) {
            return Collections.emptyList();
        }
        int contextLines = risk == null ? 30 : risk.getContextLines();
        if (CollectionUtils.isEmpty(changedLines)) {
            return Collections.singletonList(snippet("file head", lines, 1, Math.min(lines.size(), contextLines * 2 + 1)));
        }
        List<Range> ranges = new ArrayList<Range>();
        for (Integer changedLine : changedLines) {
            if (changedLine == null) {
                continue;
            }
            ranges.add(new Range(Math.max(1, changedLine - contextLines), Math.min(lines.size(), changedLine + contextLines)));
        }
        return toSnippets(lines, merge(ranges));
    }

    private List<Range> merge(List<Range> ranges) {
        if (ranges.isEmpty()) {
            return ranges;
        }
        Collections.sort(ranges);
        List<Range> result = new ArrayList<Range>();
        for (Range range : ranges) {
            if (result.isEmpty() || range.start > result.get(result.size() - 1).end + 1) {
                result.add(range);
            } else {
                result.get(result.size() - 1).end = Math.max(result.get(result.size() - 1).end, range.end);
            }
        }
        return result;
    }

    private List<ReviewCodeSnippet> toSnippets(List<String> lines, List<Range> ranges) {
        List<ReviewCodeSnippet> snippets = new ArrayList<ReviewCodeSnippet>();
        for (Range range : ranges) {
            snippets.add(snippet("lines " + range.start + "-" + range.end, lines, range.start, range.end));
        }
        return snippets;
    }

    private static class Range implements Comparable<Range> {
        private final int start;
        private int end;

        private Range(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public int compareTo(Range other) {
            return Integer.compare(this.start, other.start);
        }
    }
}
