package com.vemo.codereview.review;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vemo.codereview.platform.gitlab.model.GitLabChangesPayload;
import com.vemo.codereview.review.model.ReviewCodeSnippet;
import com.vemo.codereview.review.model.ReviewContextRisk;
import com.vemo.codereview.review.service.CppSemanticContextExpansionStrategy;
import com.vemo.codereview.review.service.FrontendSemanticContextExpansionStrategy;
import com.vemo.codereview.review.service.JavaSemanticContextExpansionStrategy;
import com.vemo.codereview.review.service.PythonSemanticContextExpansionStrategy;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SemanticContextExpansionStrategyTest {

    @Test
    void shouldExtractJavaMethodContainingChangedLine() {
        String content = "class UserService {\n"
            + "  public String name(String first, String last) {\n"
            + "    String value = first + last;\n"
            + "    return value;\n"
            + "  }\n"
            + "}\n";

        List<ReviewCodeSnippet> snippets = new JavaSemanticContextExpansionStrategy()
            .expand(change("src/UserService.java"), content, Arrays.asList(3), new ReviewContextRisk());

        assertFalse(snippets.isEmpty());
        assertTrue(snippets.get(0).getContent().contains("public String name"));
        assertTrue(snippets.get(0).getContent().contains("return value"));
    }

    @Test
    void shouldExtractFrontendArrowFunctionContainingChangedLine() {
        String content = "const submitForm = async () => {\n"
            + "  const result = await api.save(form);\n"
            + "  return result;\n"
            + "};\n";

        List<ReviewCodeSnippet> snippets = new FrontendSemanticContextExpansionStrategy()
            .expand(change("src/Form.ts"), content, Arrays.asList(2), new ReviewContextRisk());

        assertFalse(snippets.isEmpty());
        assertTrue(snippets.get(0).getContent().contains("const submitForm"));
        assertTrue(snippets.get(0).getContent().contains("await api.save"));
    }

    @Test
    void shouldExtractPythonFunctionContainingChangedLine() {
        String content = "@transactional\n"
            + "def update_user(user):\n"
            + "    user.name = user.name.strip()\n"
            + "    return user\n"
            + "\n"
            + "def other():\n"
            + "    pass\n";

        List<ReviewCodeSnippet> snippets = new PythonSemanticContextExpansionStrategy()
            .expand(change("service.py"), content, Arrays.asList(3), new ReviewContextRisk());

        assertFalse(snippets.isEmpty());
        assertTrue(snippets.get(0).getContent().contains("@transactional"));
        assertTrue(snippets.get(0).getContent().contains("def update_user"));
    }

    @Test
    void shouldExtractCppMemberFunctionContainingChangedLine() {
        String content = "int UserService::updateUser(User user) {\n"
            + "    int result = save(user);\n"
            + "    return result;\n"
            + "}\n";

        List<ReviewCodeSnippet> snippets = new CppSemanticContextExpansionStrategy()
            .expand(change("user_service.cpp"), content, Arrays.asList(2), new ReviewContextRisk());

        assertFalse(snippets.isEmpty());
        assertTrue(snippets.get(0).getContent().contains("UserService::updateUser"));
        assertTrue(snippets.get(0).getContent().contains("return result"));
    }

    private GitLabChangesPayload.Change change(String path) {
        GitLabChangesPayload.Change change = new GitLabChangesPayload.Change();
        change.setNewPath(path);
        change.setOldPath(path);
        return change;
    }
}
