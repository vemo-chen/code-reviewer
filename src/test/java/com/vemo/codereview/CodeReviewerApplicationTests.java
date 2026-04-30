package com.vemo.codereview;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CodeReviewerApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void shouldConfigureDefaultTimeZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

            CodeReviewerApplication.configureDefaultTimeZone();

            assertEquals("Asia/Shanghai", TimeZone.getDefault().getID());
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
