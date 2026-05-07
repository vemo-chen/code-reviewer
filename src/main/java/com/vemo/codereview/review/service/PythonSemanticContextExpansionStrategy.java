package com.vemo.codereview.review.service;

import com.vemo.codereview.platform.gitlab.model.GitLabChangesPayload;
import com.vemo.codereview.review.model.ReviewCodeSnippet;
import com.vemo.codereview.review.model.ReviewContextRisk;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PythonSemanticContextExpansionStrategy extends AbstractTextContextExpansionStrategy implements ContextExpansionStrategy {

    @Override
    public boolean supports(String filePath) {
        return filePath != null && filePath.toLowerCase(Locale.ROOT).endsWith(".py");
    }

    @Override
    public List<ReviewCodeSnippet> expand(
        GitLabChangesPayload.Change change,
        String fileContent,
        List<Integer> changedLines,
        ReviewContextRisk risk) {
        if (!StringUtils.hasText(fileContent) || changedLines == null || changedLines.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> lines = lines(fileContent);
        List<ReviewCodeSnippet> snippets = new ArrayList<ReviewCodeSnippet>();
        for (Integer changedLine : changedLines) {
            ReviewCodeSnippet snippet = extractPythonBlock(lines, changedLine);
            if (snippet != null) {
                snippets.add(snippet);
            }
        }
        return unique(snippets);
    }

    private ReviewCodeSnippet extractPythonBlock(List<String> lines, int changedLine) {
        int start = findPythonStart(lines, changedLine);
        if (start < 1) {
            return null;
        }
        int baseIndent = indent(lines.get(start - 1));
        int decoratedStart = includeDecorators(lines, start);
        int end = lines.size();
        for (int i = start + 1; i <= lines.size(); i++) {
            String line = lines.get(i - 1);
            if (StringUtils.hasText(line) && indent(line) <= baseIndent && isPythonStart(line)) {
                end = i - 1;
                break;
            }
        }
        return snippet("python block", lines, decoratedStart, Math.max(changedLine, end));
    }

    private int findPythonStart(List<String> lines, int changedLine) {
        for (int i = changedLine; i >= 1; i--) {
            if (isPythonStart(lines.get(i - 1))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isPythonStart(String line) {
        String trimmed = line == null ? "" : line.trim();
        return trimmed.startsWith("def ") || trimmed.startsWith("async def ") || trimmed.startsWith("class ");
    }

    private int includeDecorators(List<String> lines, int start) {
        int current = start;
        while (current > 1 && lines.get(current - 2).trim().startsWith("@")) {
            current--;
        }
        return current;
    }

    private int indent(String line) {
        int count = 0;
        while (count < line.length() && Character.isWhitespace(line.charAt(count))) {
            count++;
        }
        return count;
    }
}
