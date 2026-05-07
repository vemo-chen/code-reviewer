package com.vemo.codereview.review.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DiffHunkParser {

    private static final Pattern HUNK_HEADER = Pattern.compile("^@@\\s+-\\d+(?:,\\d+)?\\s+\\+(\\d+)(?:,\\d+)?\\s+@@.*$");

    public List<Integer> parseChangedNewLines(String diff) {
        List<Integer> changedLines = new ArrayList<Integer>();
        if (!StringUtils.hasText(diff)) {
            return changedLines;
        }

        Integer newLine = null;
        String[] lines = diff.split("\\r?\\n", -1);
        for (String line : lines) {
            Matcher matcher = HUNK_HEADER.matcher(line);
            if (matcher.matches()) {
                newLine = Integer.parseInt(matcher.group(1));
                continue;
            }
            if (newLine == null) {
                continue;
            }
            if (line.startsWith("+++") || line.startsWith("---")) {
                continue;
            }
            if (line.startsWith("+")) {
                changedLines.add(newLine);
                newLine++;
                continue;
            }
            if (line.startsWith("-")) {
                continue;
            }
            newLine++;
        }
        return changedLines;
    }
}
