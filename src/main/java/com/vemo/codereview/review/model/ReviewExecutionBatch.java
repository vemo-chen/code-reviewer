package com.vemo.codereview.review.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;

@Getter
public class ReviewExecutionBatch {
    private List<ReviewSemanticUnit> units = new ArrayList<ReviewSemanticUnit>();
    private Set<String> filePaths = new LinkedHashSet<String>();
    private int estimatedChars;
    private int estimatedOutputTokens;

    public boolean canFit(ReviewSemanticUnit unit, int maxFiles, int maxChars,
                          int maxUnitsPerFile, int usableOutputTokens) {
        if (isEmpty()) {
            return true;
        }
        int distinctFiles = filePaths.contains(unit.getFilePath()) ? filePaths.size() : filePaths.size() + 1;
        if (distinctFiles > maxFiles || estimatedChars + chars(unit) > maxChars) {
            return false;
        }
        int sameFile = 0;
        for (ReviewSemanticUnit existing : units) {
            if (equals(existing.getFilePath(), unit.getFilePath())) {
                sameFile++;
            }
        }
        return sameFile < maxUnitsPerFile && estimatedOutputTokens + 600 <= usableOutputTokens - 800;
    }

    public void add(ReviewSemanticUnit unit) {
        units.add(unit);
        filePaths.add(unit.getFilePath());
        estimatedChars += chars(unit);
        estimatedOutputTokens += 600;
    }

    public boolean isEmpty() {
        return units.isEmpty();
    }

    public Split splitHalf() {
        if (units.size() <= 1) {
            throw new IllegalStateException("Cannot split a batch with fewer than two units");
        }
        int middle = units.size() / 2;
        ReviewExecutionBatch left = new ReviewExecutionBatch();
        ReviewExecutionBatch right = new ReviewExecutionBatch();
        for (int index = 0; index < units.size(); index++) {
            (index < middle ? left : right).add(units.get(index));
        }
        return new Split(left, right);
    }

    private int chars(ReviewSemanticUnit unit) {
        int result = length(unit.getDiff()) + length(unit.getExpandedCode());
        if (unit.getRiskHints() != null) {
            for (String hint : unit.getRiskHints()) result += length(hint);
        }
        return result;
    }

    private int length(String value) { return value == null ? 0 : value.length(); }
    private boolean equals(String left, String right) { return left == null ? right == null : left.equals(right); }

    @Getter
    public static class Split {
        private final ReviewExecutionBatch left;
        private final ReviewExecutionBatch right;
        public Split(ReviewExecutionBatch left, ReviewExecutionBatch right) {
            this.left = left;
            this.right = right;
        }
    }
}
