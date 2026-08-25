/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.codecheck;

import examples.gitcode_issue_evolver.gitcode.GitCodePullRequestComment;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses only the fixed CodeCheck signal and report link from a trusted robot comment. */
public final class CodeCheckCommentParser {
    private static final int MAX_COMMENT_LENGTH = 65_536;
    private static final Pattern FAILED_PATTERN = Pattern.compile(
            "(?is)CodeCheck.{0,200}?(FAILED|FAIL|未通过)");
    private static final Pattern REPORT_PATTERN = Pattern.compile(
            "https://[A-Za-z0-9.-]+/apps/entryCheckDashCode/"
                    + "[A-Za-z0-9_-]+/[A-Za-z0-9_-]+\\?[A-Za-z0-9._~%=&;-]+",
            Pattern.CASE_INSENSITIVE);

    /** Parse one already-authorized trusted comment. */
    public Optional<FailedCodeCheckComment> parseFailed(GitCodePullRequestComment comment) {
        if (comment == null || comment.body() == null || comment.body().length() > MAX_COMMENT_LENGTH
                || !FAILED_PATTERN.matcher(comment.body()).find()) {
            return Optional.empty();
        }
        Matcher matcher = REPORT_PATTERN.matcher(comment.body());
        if (!matcher.find()) {
            return Optional.empty();
        }
        String url = matcher.group().replace("&amp;", "&");
        try {
            return Optional.of(new FailedCodeCheckComment(comment.id(), comment.updatedAt(), URI.create(url)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
