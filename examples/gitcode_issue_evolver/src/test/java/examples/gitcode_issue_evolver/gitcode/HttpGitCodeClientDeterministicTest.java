/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.gitcode;

import examples.gitcode_issue_evolver.RepositoryCoordinates;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Deterministic GitCode Issue-list HTTP contract checks. */
public final class HttpGitCodeClientDeterministicTest {
    private HttpGitCodeClientDeterministicTest() {
    }

    /** Run the local HTTP contract check. */
    public static void main(String[] args) {
        AtomicReference<String> query = new AtomicReference<>("");
        runRequest(query);
        System.out.println("HttpGitCodeClientDeterministicTest: PASS");
    }

    private static void runRequest(AtomicReference<String> query) {
        URI base = URI.create("http://127.0.0.1/api/v5/");
        RepositoryCoordinates coordinates = RepositoryCoordinates.from(
                "openJiuwen/agent-core-java", "tester/agent-core-java", "730");
        OkHttpClient httpClient = new OkHttpClient.Builder().addInterceptor(chain -> {
            query.set(chain.request().url().query());
            String response = responseJson();
            String path = chain.request().url().encodedPath();
            if (path.endsWith("/pulls/9")) {
                response = pullRequestJson();
            }
            if (path.endsWith("/pulls/9/comments")) {
                response = pullRequestCommentsJson();
            }
            if (path.endsWith("/issues/90")) {
                response = issueJson();
            }
            if (path.endsWith("/issues/90/comments")) {
                response = "[]";
            }
            ResponseBody body = ResponseBody.create(response, MediaType.get("application/json"));
            return new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build();
        }).build();
        HttpGitCodeClient client = new HttpGitCodeClient(
                httpClient, base, "test-token", coordinates);
        Instant lower = Instant.parse("2026-08-03T12:00:00Z");
        Instant upper = Instant.parse("2026-08-04T12:00:00Z");
        GitCodeIssuePage page = client.listIssues(new IssueScanRequest(lower, upper, "bug", 2, 100));
        require(page.receivedCount() == 3, "raw response count must preserve malformed entries");
        require(page.issues().size() == 2, "malformed timestamp must be skipped");
        require(page.issues().get(0).createdAt().equals(Instant.parse("2026-08-04T02:00:00Z")),
                "offset timestamp was not normalized");
        require(page.issues().get(0).hasLabel("bug"), "string label was not parsed");
        require(page.issues().get(1).hasLabel("bug"), "object label was not parsed");
        String captured = query.get();
        require(captured.contains("state=open"), "open state filter is missing");
        require(captured.contains("labels=bug"), "label filter is missing");
        require(captured.contains("sort=created") && captured.contains("direction=asc"),
                "creation ordering is missing");
        require(captured.contains("page=2") && captured.contains("per_page=100"),
                "pagination parameters are missing");
        require(captured.contains("created_after=") && captured.contains("created_before="),
                "frozen window parameters are missing");

        client.listOpenIssuesByLabel(new IssueLabelScanRequest("bug/codecheck", 7, 100));
        String fullScanQuery = query.get();
        require(fullScanQuery.contains("state=open"), "full scan open state filter is missing");
        require(fullScanQuery.contains("labels=bug/codecheck"),
                "full scan label filter is missing");
        require(fullScanQuery.contains("page=7") && fullScanQuery.contains("per_page=100"),
                "full scan pagination parameters are missing");
        require(!fullScanQuery.contains("created_after=") && !fullScanQuery.contains("created_before="),
                "full scan must not send rolling window parameters");
        GitCodeIssue issue = client.getIssue(90L);
        require(issue.labels().equals(List.of("bug/codecheck")),
                "Issue detail labels were not retained for worker policy selection");
        GitCodePullRequest pullRequest = client.getPullRequest(9L);
        require(pullRequest.hasLabel("ci-successful"), "PR label was not parsed");
        List<GitCodePullRequestComment> comments = client.listPullRequestComments(9L);
        require(comments.size() == 1 && "openJiuwen-bot".equals(comments.get(0).authorLogin()),
                "trusted PR comment metadata was not parsed");
    }

    private static String pullRequestJson() {
        return """
                {"number":9,"state":"open","html_url":"https://gitcode/pr/9",
                 "head":{"ref":"branch","sha":"0123456789012345678901234567890123456789"},
                 "labels":[{"name":"ci-successful"}],"draft":false}
                """;
    }

    private static String issueJson() {
        return """
                {"number":90,"title":"CodeCheck G.OTH.01","body":"target:64","state":"open",
                 "html_url":"https://gitcode/issues/90","labels":[{"name":"bug/codecheck"}]}
                """;
    }

    private static String pullRequestCommentsJson() {
        return """
                [{"id":12,"body":"CodeCheck FAILED","comment_type":"Note",
                  "user":{"login":"openJiuwen-bot"},
                  "created_at":"2026-08-24T01:00:00Z","updated_at":"2026-08-24T02:00:00Z"}]
                """;
    }

    private static String responseJson() {
        return """
                [
                  {"number":"1","title":"offset","state":"open","html_url":"https://issue/1",
                   "labels":["bug"],"created_at":"2026-08-04T10:00:00+08:00"},
                  {"number":2,"title":"invalid","state":"open","html_url":"https://issue/2",
                   "labels":["bug"],"created_at":"not-a-time"},
                  {"iid":3,"title":"object-label","state":"opened","web_url":"https://issue/3",
                   "labels":[{"name":"bug"}],"created_at":"2026-08-04T12:00:01Z"}
                ]
                """;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
