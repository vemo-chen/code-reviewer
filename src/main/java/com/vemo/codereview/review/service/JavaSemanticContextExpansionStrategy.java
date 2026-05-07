package com.vemo.codereview.review.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.vemo.codereview.platform.gitlab.model.GitLabChangesPayload;
import com.vemo.codereview.review.model.ReviewCodeSnippet;
import com.vemo.codereview.review.model.ReviewContextRisk;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class JavaSemanticContextExpansionStrategy extends AbstractTextContextExpansionStrategy implements ContextExpansionStrategy {

    @Override
    public boolean supports(String filePath) {
        return filePath != null && filePath.toLowerCase().endsWith(".java");
    }

    @Override
    public List<ReviewCodeSnippet> expand(
        GitLabChangesPayload.Change change,
        String fileContent,
        List<Integer> changedLines,
        ReviewContextRisk risk) {
        if (!StringUtils.hasText(fileContent) || changedLines == null || changedLines.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(fileContent);
            List<String> lines = lines(fileContent);
            List<ReviewCodeSnippet> snippets = new ArrayList<ReviewCodeSnippet>();
            for (Integer changedLine : changedLines) {
                Node node = findSmallestSemanticNode(compilationUnit, changedLine);
                if (node == null || !node.getRange().isPresent()) {
                    continue;
                }
                int start = node.getRange().get().begin.line;
                int end = node.getRange().get().end.line;
                snippets.add(snippet(title(node), lines, start, end));
            }
            return unique(snippets);
        } catch (RuntimeException ex) {
            return Collections.emptyList();
        }
    }

    private Node findSmallestSemanticNode(CompilationUnit compilationUnit, Integer changedLine) {
        List<Node> candidates = new ArrayList<Node>();
        candidates.addAll(compilationUnit.findAll(MethodDeclaration.class));
        candidates.addAll(compilationUnit.findAll(ConstructorDeclaration.class));
        candidates.addAll(compilationUnit.findAll(FieldDeclaration.class));
        candidates.addAll(compilationUnit.findAll(BodyDeclaration.class));
        candidates.sort(Comparator.comparingInt(this::nodeLength));
        for (Node node : candidates) {
            if (node.getRange().isPresent()
                && changedLine >= node.getRange().get().begin.line
                && changedLine <= node.getRange().get().end.line) {
                return node;
            }
        }
        return null;
    }

    private int nodeLength(Node node) {
        return node.getRange().map(range -> range.end.line - range.begin.line).orElse(Integer.MAX_VALUE);
    }

    private String title(Node node) {
        if (node instanceof MethodDeclaration) {
            return "method " + ((MethodDeclaration) node).getNameAsString();
        }
        if (node instanceof ConstructorDeclaration) {
            return "constructor " + ((ConstructorDeclaration) node).getNameAsString();
        }
        if (node instanceof FieldDeclaration) {
            return "field";
        }
        return "java semantic block";
    }
}
