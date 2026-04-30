package com.vemo.codereview.review.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DiffChunkService {

    private static final int DEFAULT_CHUNK_SIZE = 1200;

    public List<String> chunk(String diff) {
        List<String> chunks = new ArrayList<String>();
        if (diff == null || diff.isEmpty()) {
            return chunks;
        }

        if (diff.length() <= DEFAULT_CHUNK_SIZE) {
            chunks.add(diff);
            return chunks;
        }

        int start = 0;
        while (start < diff.length()) {
            int end = Math.min(start + DEFAULT_CHUNK_SIZE, diff.length());
            chunks.add(diff.substring(start, end));
            start = end;
        }
        return chunks;
    }
}
