/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.gitcode;

import examples.gitcode_feature_evolver.job.FeatureScanCheckpoint;
import examples.gitcode_issue_evolver.RepositoryCoordinates;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Deterministic updated-at, structured-comment, and PR PATCH HTTP contract checks. */
public final class HttpFeatureGitCodeClientDeterministicTest {
    private HttpFeatureGitCodeClientDeterministicTest() {
    }

    /** Run all local HTTP client checks. */
    public static void main(String[] args) {
        AtomicReference<String> issueQuery = new AtomicReference<>("");
        AtomicReference<String> patchBody = new AtomicReference<>("");
        AtomicReference<String> patchMethod = new AtomicReference<>("");
        HttpFeatureGitCodeClient client = client(issueQuery, patchBody, patchMethod);
        testUpdatedIssueList(client, issueQuery);
        testStructuredComments(client);
        testPullRequestPatch(client, patchBody, patchMethod);
        System.out.println("HttpFeatureGitCodeClientDeterministicTest: PASS");
    }

    private static HttpFeatureGitCodeClient client(AtomicReference<String> issueQuery,
                                                   AtomicReference<String> patchBody,
                                                   AtomicReference<String> patchMethod) {
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            String path = chain.request().url().encodedPath();
            String method = chain.request().method();
            String body;
            if (path.endsWith("/issues") && "GET".equals(method)) {
                issueQuery.set(chain.request().url().query());
                body = issueResponse();
            } else if (path.endsWith("/issues/77/comments")) {
                body = commentResponse();
            } else if (path.endsWith("/pulls/9") && "PATCH".equals(method)) {
                patchMethod.set(method);
                patchBody.set(requestBody(chain.request().body()));
                body = pullRequestResponse(false);
            } else {
                body = "{}";
            }
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .body(ResponseBody.create(body, MediaType.get("application/json"))).build();
        }).build();
        RepositoryCoordinates coordinates = RepositoryCoordinates.from(
                "openJiuwen/agent-core-java", "tester/agent-core-java", "730");
        return new HttpFeatureGitCodeClient(http, URI.create("http://127.0.0.1/api/v5/"),
                "test-token", coordinates);
    }

    private static void testUpdatedIssueList(HttpFeatureGitCodeClient client,
                                             AtomicReference<String> issueQuery) {
        FeatureScanCheckpoint.Window window = new FeatureScanCheckpoint.Window(
                Instant.parse("2026-08-05T06:00:00Z"), Instant.parse("2026-08-06T06:00:00Z"));
        FeatureIssuePage page = client.listIssues(new FeatureIssueScanRequest(
                window, "feature", 2, 100));
        require(page.receivedCount() == 3 && page.issues().size() == 2,
                "malformed updated_at entry was not skipped");
        require(page.issues().get(0).status().updatedAt().equals(
                Instant.parse("2026-08-06T02:00:00Z")), "offset updated_at was not normalized");
        String query = issueQuery.get();
        require(query.contains("sort=updated") && query.contains("direction=asc"),
                "updated-at ordering parameters are missing");
        require(query.contains("updated_after=") && query.contains("updated_before="),
                "frozen updated-at bounds are missing");
        require(query.contains("labels=feature") && query.contains("state=open"),
                "feature label or open state filter is missing");
        require(query.contains("page=2") && query.contains("per_page=100"),
                "Issue pagination parameters are missing");
    }

    private static void testStructuredComments(HttpFeatureGitCodeClient client) {
        List<FeatureComment> comments = client.listIssueComments(77);
        require(comments.size() == 1, "structured comment was not parsed");
        FeatureComment comment = comments.get(0);
        require(comment.id().equals("501") && comment.authorLogin().equals("approver"),
                "comment ID or author login was lost");
        require(comment.body().equals("/feature approve r1"), "comment body was lost");
    }

    private static void testPullRequestPatch(HttpFeatureGitCodeClient client,
                                             AtomicReference<String> patchBody,
                                             AtomicReference<String> patchMethod) {
        CreateFeaturePullRequest.Content content = new CreateFeaturePullRequest.Content(
                "Feature title", "Standardized body");
        FeaturePullRequest pullRequest = client.updatePullRequest(
                new UpdateFeaturePullRequest(9, content, false));
        require(patchMethod.get().equals("PATCH"), "PR update did not use PATCH");
        require(patchBody.get().contains("\"draft\":false"), "PR Draft transition was not encoded");
        require(patchBody.get().contains("Standardized body"), "standardized PR body was not encoded");
        require(!pullRequest.draft() && pullRequest.number() == 9,
                "updated PR response was not parsed");
    }

    private static String requestBody(okhttp3.RequestBody body) {
        try {
            Buffer buffer = new Buffer();
            body.writeTo(buffer);
            return buffer.readUtf8();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Unable to inspect deterministic request body", ex);
        }
    }

    private static String issueResponse() {
        return """
                [
                  {"number":"1","title":"offset","state":"open","html_url":"https://issue/1",
                   "labels":["feature"],"updated_at":"2026-08-06T10:00:00+08:00"},
                  {"number":2,"title":"invalid","state":"open","html_url":"https://issue/2",
                   "labels":["feature"],"updated_at":"not-a-time"},
                  {"iid":3,"title":"object-label","state":"opened","web_url":"https://issue/3",
                   "labels":[{"name":"feature"}],"updated_at":"2026-08-06T06:00:00Z"}
                ]
                """;
    }

    private static String commentResponse() {
        return """
                [{"id":501,"body":"/feature approve r1","created_at":"2026-08-06T06:00:00Z",
                  "user":{"login":"approver"}}]
                """;
    }

    private static String pullRequestResponse(boolean draft) {
        return """
                {"number":9,"html_url":"https://gitcode/pr/9","state":"open","draft":%s,
                 "head":{"ref":"feature-evolving/issue-77-feature",
                 "sha":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}}
                """.formatted(draft);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
