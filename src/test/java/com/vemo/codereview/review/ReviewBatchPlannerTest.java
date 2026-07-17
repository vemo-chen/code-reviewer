package com.vemo.codereview.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vemo.codereview.common.config.AppProperties;
import com.vemo.codereview.common.exception.DomainException;
import com.vemo.codereview.review.model.ReviewExecutionBatch;
import com.vemo.codereview.review.model.ReviewSemanticUnit;
import com.vemo.codereview.review.service.ReviewBatchPlanner;
import com.vemo.codereview.review.service.ReviewContextBudgetService;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ReviewBatchPlannerTest {

    @Test
    void shouldKeepAllUnitsInOneInitialBatch() {
        AppProperties properties = new AppProperties();
        properties.getReviewContext().setMaxFiles(20);
        properties.getReviewContext().setMaxTotalChars(60000);
        properties.getReviewContext().setMaxSnippetsPerFile(20);
        ReviewBatchPlanner planner = new ReviewBatchPlanner(new ReviewContextBudgetService(properties));
        List<ReviewSemanticUnit> units = units(9, false);

        List<ReviewExecutionBatch> batches = planner.plan(units, 8192);

        assertEquals(1, batches.size());
        assertEquals(9, batches.get(0).getUnits().size());
        assertEquals(keys(units), batches.stream().flatMap(batch -> batch.getUnits().stream())
            .map(ReviewSemanticUnit::getUnitKey).collect(Collectors.toList()));
    }

    @Test
    void shouldKeepSameFileUnitsInOneInitialBatch() {
        AppProperties properties = new AppProperties();
        properties.getReviewContext().setMaxSnippetsPerFile(3);
        ReviewBatchPlanner planner = new ReviewBatchPlanner(new ReviewContextBudgetService(properties));

        List<ReviewExecutionBatch> batches = planner.plan(units(7, true), 8192);

        assertEquals(1, batches.size());
        assertEquals(7, batches.get(0).getUnits().size());
        assertEquals(7, batches.stream().mapToInt(batch -> batch.getUnits().size()).sum());
    }

    @Test
    void shouldRejectInvalidModelOutputBudget() {
        ReviewBatchPlanner planner = new ReviewBatchPlanner(new ReviewContextBudgetService(new AppProperties()));
        for (Integer value : new Integer[] {null, 0, -1}) {
            DomainException exception = assertThrows(DomainException.class, () -> planner.plan(units(1, false), value));
            assertEquals("LLM_MAX_TOKENS_INVALID", exception.getCode());
        }
    }

    private List<ReviewSemanticUnit> units(int count, boolean sameFile) {
        List<ReviewSemanticUnit> units = new ArrayList<ReviewSemanticUnit>();
        for (int index = 1; index <= count; index++) {
            ReviewSemanticUnit unit = new ReviewSemanticUnit();
            unit.setUnitKey("u" + index);
            unit.setFilePath(sameFile ? "src/A.java" : "src/A" + index + ".java");
            unit.setDiff("+change" + index);
            unit.setExpandedCode("method" + index);
            units.add(unit);
        }
        return units;
    }

    private List<String> keys(List<ReviewSemanticUnit> units) {
        return units.stream().map(ReviewSemanticUnit::getUnitKey).collect(Collectors.toList());
    }
}
