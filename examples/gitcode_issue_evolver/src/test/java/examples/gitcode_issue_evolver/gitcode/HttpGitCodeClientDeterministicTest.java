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
            ResponseBody body = ResponseBody.create(responseJson(), MediaType.get("application/json"));
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
