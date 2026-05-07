package com.vemo.codereview.review.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewContextRisk {

    private String level = "NORMAL";
    private List<String> hints = new ArrayList<String>();
    private int contextLines = 30;
    private int maxFileChars = 204800;
}
