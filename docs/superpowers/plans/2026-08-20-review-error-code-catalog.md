# Review Error Code Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a shared review failure code catalog and use it to return readable failure details in review task detail responses.

**Architecture:** Introduce one enum-like catalog for known failure codes, then map task detail responses through that catalog so the API returns both the raw code and a user-facing summary. Keep the raw task error fields intact for diagnostics, and let the front end render the summary only for failed tasks.

**Tech Stack:** Java 8, Spring Boot 2.7, MyBatis-Plus, Vue 3, Vite, TypeScript, Element Plus

## Global Constraints

- Preserve existing `LLM_*`, `GITLAB_*`, `REVIEW_*`, `TASK_*`, and `PROJECT_*` error code strings as stored contract values.
- Do not replace raw `errorMessage` values with user-facing summaries.
- Keep the detail page readable and only show failure copy for `FAILED` tasks.

---

### Task 1: Add a shared failure code catalog

**Files:**
- Create: `src/main/java/com/vemo/codereview/review/model/ReviewFailureCode.java`

**Interfaces:**
- Produces: `ReviewFailureCode.fromCode(String)`, `getCode()`, `getUserMessage()`

- [ ] **Step 1: Write the failing test**

Add an assertion in `src/test/java/com/vemo/codereview/dashboard/DashboardControllerTest.java` that expects `LLM_API_ERROR` to map to a readable failure summary.

- [ ] **Step 2: Run test to verify it fails**

Run: `/Users/vemo/maven/apache-maven-3.9.16/bin/mvn -Dtest=DashboardControllerTest#shouldReturnFailedReviewTaskDetailWithMappedFailureSummary test`
Expected: FAIL because the summary field is missing.

- [ ] **Step 3: Write minimal implementation**

Implement the enum with a lookup table and readable summary strings for known codes.

- [ ] **Step 4: Run test to verify it passes**

Run: `/Users/vemo/maven/apache-maven-3.9.16/bin/mvn -Dtest=DashboardControllerTest#shouldReturnFailedReviewTaskDetailWithMappedFailureSummary test`
Expected: PASS.

### Task 2: Return failure details from task detail queries

**Files:**
- Modify: `src/main/java/com/vemo/codereview/dashboard/model/ReviewTaskDetailResponse.java`
- Modify: `src/main/java/com/vemo/codereview/dashboard/service/DashboardQueryService.java`
- Modify: `src/test/java/com/vemo/codereview/dashboard/DashboardControllerTest.java`
- Modify: `docs/api/dashboard.md`

**Interfaces:**
- Consumes: `ReviewFailureCode.fromCode(String)`
- Produces: `errorCode`, `errorMessage`, `failureSummary` in `GET /api/dashboard/review-tasks/{taskId}`

- [ ] **Step 1: Write the failing test**

Extend the dashboard controller test to assert a failed task returns `errorCode`, `errorMessage`, and `failureSummary`.

- [ ] **Step 2: Run test to verify it fails**

Run the same focused Maven test command.
Expected: FAIL until the detail response is populated.

- [ ] **Step 3: Write minimal implementation**

Populate the extra fields in `DashboardQueryService#getReviewTaskDetail`.

- [ ] **Step 4: Run test to verify it passes**

Run the same focused Maven test command.
Expected: PASS.

### Task 3: Render the failure block in the review detail drawer

**Files:**
- Modify: `web-ui/src/views/reviews/ReviewListView.vue`

**Interfaces:**
- Consumes: `errorCode`, `errorMessage`, `failureSummary`
- Produces: a failure section visible only when the task status is `FAILED`

- [ ] **Step 1: Add the UI state and template bindings**
- [ ] **Step 2: Build the web UI**
- [ ] **Step 3: Verify the drawer shows the new failure block for failed tasks**

Run: `cd web-ui && npm run build`
Expected: PASS.
