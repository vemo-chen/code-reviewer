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
public class FrontendSemanticContextExpansionStrategy extends AbstractTextContextExpansionStrategy implements ContextExpansionStrategy {

    private static final Pattern FUNCTION_START = Pattern.compile("^\\s*(export\\s+)?(async\\s+)?function\\s+\\w+\\s*\\(.*");
    private static final Pattern ARROW_START = Pattern.compile("^\\s*(export\\s+)?(const|let|var)\\s+\\w+\\s*=\\s*(async\\s*)?(\\([^)]*\\)|\\w+)\\s*=>.*");
    private static final Pattern METHOD_START = Pattern.compile("^\\s*(async\\s+)?\\w+\\s*\\([^)]*\\)\\s*\\{\\s*$");
    private static final Pattern CLASS_START = Pattern.compile("^\\s*(export\\s+default\\s+)?class\\s+\\w+.*");

    @Override
    public boolean supports(String filePath) {
        if (filePath == null) {
            return false;
        }
        String path = filePath.toLowerCase(Locale.ROOT);
        return path.endsWith(".js") || path.endsWith(".jsx") || path.endsWith(".ts")
            || path.endsWith(".tsx") || path.endsWith(".vue");
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
            ReviewCodeSnippet snippet = extractScriptLikeBlock(lines, changedLine);
            if (snippet == null && isVue(change)) {
                snippet = extractVueBlock(lines, changedLine);
            }
            if (snippet != null) {
                snippets.add(snippet);
            }
        }
        return unique(snippets);
    }

    private boolean isVue(GitLabChangesPayload.Change change) {
        String path = change == null ? null : (change.getNewPath() == null ? change.getOldPath() : change.getNewPath());
        return path != null && path.toLowerCase(Locale.ROOT).endsWith(".vue");
    }

    private ReviewCodeSnippet extractScriptLikeBlock(List<String> lines, int changedLine) {
        int start = findBraceStart(lines, changedLine);
        for (int i = start; i >= Math.max(1, start - 6); i--) {
            String line = lines.get(i - 1);
            if (isSemanticStart(line)) {
                int end = findMatchingBraceEnd(lines, i);
                return snippet("frontend block", lines, i, Math.max(changedLine, end));
            }
        }
        return null;
    }

    private boolean isSemanticStart(String line) {
        return FUNCTION_START.matcher(line).find()
            || ARROW_START.matcher(line).find()
            || METHOD_START.matcher(line).find()
            || CLASS_START.matcher(line).find()
            || line.trim().startsWith("export default {");
    }

    private ReviewCodeSnippet extractVueBlock(List<String> lines, int changedLine) {
        int start = changedLine;
        while (start > 1 && !lines.get(start - 1).trim().startsWith("<script")
            && !lines.get(start - 1).trim().startsWith("<template")) {
            start--;
        }
        int end = changedLine;
        while (end < lines.size() && !lines.get(end - 1).trim().startsWith("</script>")
            && !lines.get(end - 1).trim().startsWith("</template>")) {
            end++;
        }
        if (start <= changedLine && end >= changedLine) {
            return snippet("vue block", lines, start, end);
        }
        return null;
    }
}
