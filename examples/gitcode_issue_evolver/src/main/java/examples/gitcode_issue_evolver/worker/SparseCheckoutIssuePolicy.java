/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.worker;

import examples.gitcode_issue_evolver.agent.CodeCheckRepairDirective;
import examples.gitcode_issue_evolver.gitcode.GitCodeIssue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects explicit Issue requests that cannot be completed in the first-version sparse checkout.
 *
 * @since 0.1.12
 */
final class SparseCheckoutIssuePolicy {
    private static final int INTENT_CONTEXT_CHARS = 96;
    private static final Pattern EXCLUDED_PATH = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_./-])(?:(?:documents|(?:src/main/java/)?examples|logs|target|resources)"
                    + "/[^\\s`'\"),;:]*)");
    private static final Pattern CHANGE_INTENT = Pattern.compile(
            "(?i)(?:\\b(?:modify|update|edit|change|add|create|delete|remove|rename|write|implement|fix)\\b"
                    + "|\\u4fee\\u6539|\\u66f4\\u65b0|\\u7f16\\u8f91|\\u65b0\\u589e"
                    + "|\\u65b0\\u5efa|\\u5220\\u9664|\\u79fb\\u9664|\\u7f16\\u5199|\\u8c03\\u6574)");
    private static final Pattern NEGATED_INTENT = Pattern.compile(
            "(?i)(?:\\b(?:do\\s+not|don't|must\\s+not|should\\s+not|never)\\b"
                    + "|\\u4e0d\\u8981|\\u7981\\u6b62|\\u65e0\\u9700|\\u4e0d\\u9700\\u8981)");
    private static final Pattern TRAILING_SENTENCE_PUNCTUATION = Pattern.compile(
            "[.!?;\\u3002\\uff01\\uff1f\\uff1b]+$");

    private SparseCheckoutIssuePolicy() {
    }

    /**
     * Validate Issue text before creating a Worktree or starting an Agent.
     *
     * @param issue untrusted Issue content
     * @return immutable validation result
     */
    static Validation validate(GitCodeIssue issue) {
        GitCodeIssue requiredIssue = Objects.requireNonNull(issue, "issue must not be null");
        Set<String> excludedPaths = new LinkedHashSet<>();
        boolean codeCheck = CodeCheckRepairDirective.from(requiredIssue).isCodeCheck();
        List<String> sections = new ArrayList<>();
        sections.add(requiredIssue.title());
        sections.add(requiredIssue.description());
        sections.addAll(requiredIssue.comments());
        for (String section : sections) {
            detectExcludedRequests(section, excludedPaths, codeCheck);
        }
        return new Validation(excludedPaths.isEmpty(), List.copyOf(excludedPaths));
    }

    private static void detectExcludedRequests(String text, Set<String> excludedPaths,
                                               boolean codeCheck) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String line : text.replace('\\', '/').split("\\R", -1)) {
            Matcher pathMatcher = EXCLUDED_PATH.matcher(line);
            while (pathMatcher.find()) {
                int contextStart = Math.max(clauseStart(line, pathMatcher.start()),
                        pathMatcher.start() - INTENT_CONTEXT_CHARS);
                int contextEnd = Math.min(clauseEnd(line, pathMatcher.end()),
                        pathMatcher.end() + INTENT_CONTEXT_CHARS);
                String context = line.substring(contextStart, contextEnd);
                boolean hasChangeIntent = CHANGE_INTENT.matcher(context).find();
                if ((codeCheck || hasChangeIntent) && !NEGATED_INTENT.matcher(context).find()) {
                    excludedPaths.add(TRAILING_SENTENCE_PUNCTUATION
                            .matcher(pathMatcher.group())
                            .replaceAll(""));
                }
            }
        }
    }

    private static int clauseStart(String line, int position) {
        for (int index = position - 1; index >= 0; index--) {
            if (isClauseBoundary(line, index)) {
                return index + 1;
            }
        }
        return 0;
    }

    private static int clauseEnd(String line, int position) {
        for (int index = position; index < line.length(); index++) {
            if (isClauseBoundary(line, index)) {
                return index;
            }
        }
        return line.length();
    }

    private static boolean isClauseBoundary(String line, int index) {
        char value = line.charAt(index);
        boolean sentencePeriod = value == '.'
                && (index + 1 == line.length() || Character.isWhitespace(line.charAt(index + 1)));
        return sentencePeriod || value == '!' || value == '?' || value == ';'
                || value == '\u3002' || value == '\uff01' || value == '\uff1f' || value == '\uff1b';
    }

    /**
     * Sparse checkout admission result.
     *
     * @param allowed whether execution can continue
     * @param excludedPaths explicitly requested paths outside the checkout
     */
    record Validation(boolean allowed, List<String> excludedPaths) {
        Validation {
            excludedPaths = List.copyOf(excludedPaths);
        }
    }
}
