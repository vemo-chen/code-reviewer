package com.vemo.codereview.review.service;

import com.vemo.codereview.platform.gitlab.model.GitLabChangesPayload;
import com.vemo.codereview.review.model.ReviewCodeSnippet;
import com.vemo.codereview.review.model.ReviewContextRisk;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CppSemanticContextExpansionStrategy extends AbstractTextContextExpansionStrategy implements ContextExpansionStrategy {

    private static final Pattern FUNCTION_OR_TYPE_START = Pattern.compile(".*(\\w+::\\w+|\\w+)\\s*\\([^;]*\\)\\s*(const\\s*)?(override\\s*)?(\\{|$)|^\\s*(class|struct)\\s+\\w+.*");

    @Override
    public boolean supports(String filePath) {
        if (filePath == null) {
            return false;
        }
        String path = filePath.toLowerCase(Locale.ROOT);
        return path.endsWith(".c") || path.endsWith(".cc") || path.endsWith(".cpp") || path.endsWith(".cxx")
            || path.endsWith(".h") || path.endsWith(".hpp") || path.endsWith(".hh") || path.endsWith(".hxx");
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
            ReviewCodeSnippet snippet = extractCppBlock(lines, changedLine);
            if (snippet != null) {
                snippets.add(snippet);
            }
        }
        return unique(snippets);
    }

    private ReviewCodeSnippet extractCppBlock(List<String> lines, int changedLine) {
        int start = findCppStart(lines, changedLine);
        if (start < 1) {
            return null;
        }
        while (start > 1 && lines.get(start - 2).trim().startsWith("template")) {
            start--;
        }
        int end = findMatchingBraceEnd(lines, start);
        if (end <= start) {
            end = Math.min(lines.size(), changedLine + 5);
        }
        return snippet("cpp block", lines, start, Math.max(changedLine, end));
    }

    private int findCppStart(List<String> lines, int changedLine) {
        int braceStart = findBraceStart(lines, changedLine);
        for (int i = braceStart; i >= Math.max(1, braceStart - 8); i--) {
            String candidate = lines.get(i - 1).trim();
            if (!candidate.startsWith("//") && FUNCTION_OR_TYPE_START.matcher(candidate).find()) {
                return i;
            }
        }
        return -1;
    }
}
