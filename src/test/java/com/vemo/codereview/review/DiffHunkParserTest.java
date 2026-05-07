package com.vemo.codereview.review;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vemo.codereview.review.service.DiffHunkParser;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DiffHunkParserTest {

    @Test
    void shouldParseChangedLinesInNewFile() {
        DiffHunkParser parser = new DiffHunkParser();
        String diff = "@@ -10,3 +10,4 @@\n"
            + " keep\n"
            + "-old\n"
            + "+new\n"
            + "+another\n";

        assertEquals(Arrays.asList(11, 12), parser.parseChangedNewLines(diff));
    }
}
