package com.vemo.codereview.user.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserProjectAssignRequest {

    private List<Long> projectIds;
}
