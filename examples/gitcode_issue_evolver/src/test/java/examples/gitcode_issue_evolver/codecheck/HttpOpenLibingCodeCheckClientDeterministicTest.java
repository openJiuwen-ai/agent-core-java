/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.codecheck;

import examples.gitcode_issue_evolver.RepositoryCoordinates;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Deterministic OpenLibing anonymous report HTTP contract checks. */
public final class HttpOpenLibingCodeCheckClientDeterministicTest {
    private HttpOpenLibingCodeCheckClientDeterministicTest() {
    }

    /** Run the bounded anonymous report contract check. */
    public static void main(String[] args) {
        List<CapturedRequest> requests = new ArrayList<>();
        OkHttpClient httpClient = new OkHttpClient.Builder().addInterceptor(chain -> {
            Request request = chain.request();
            requests.add(new CapturedRequest(request.method(), request.url().encodedPath(),
                    request.url().query(), body(request), request.header("Cookie"),
                    request.header("Csrf-Token-Open-Li-Bing")));
            String response = requests.size() == 1 ? summaryJson() : detailJson();
            return response(request, response);
        }).build();
        RepositoryCoordinates coordinates = RepositoryCoordinates.from(
                "openJiuwen/agent-core-java", "tester/agent-core-java", "730");
        HttpOpenLibingCodeCheckClient client = new HttpOpenLibingCodeCheckClient(
                httpClient, URI.create("https://www.openlibing.com"), coordinates, 100);
        URI reportUrl = URI.create("https://www.openlibing.com/apps/entryCheckDashCode/"
                + "MR_demo_task/demo_uuid?projectId=300075&codeHostingPlatformFlag=gitcode");

        CodeCheckReport report = client.read(reportUrl, 266L);

        require(requests.size() == 2, "client must issue only summary and detail POST requests");
        require(requests.stream().allMatch(request -> "POST".equals(request.method())),
                "OpenLibing report reads must use POST");
        for (CapturedRequest request : requests) {
            require(request.query().contains("uuid=demo_uuid"), "UUID query parameter is reversed");
            require(request.query().contains("taskId=MR_demo_task"),
                    "TASK ID query parameter is reversed");
            require(request.body().contains("\"defectStatus\":\"0\""),
                    "only unresolved findings may enter repair feedback");
            require(request.cookie() == null && request.csrf() == null,
                    "anonymous report reads must not send Cookie or CSRF headers");
        }
        require(requests.get(0).path().endsWith("/codecheck/event/task/issues/report"),
                "summary endpoint is incorrect");
        require(requests.get(1).path().endsWith("/event/codecheck/task"),
                "detail endpoint is incorrect");
        require(report.total() == 1 && report.findings().size() == 1,
                "structured finding was not parsed");
        CodeCheckFinding finding = report.findings().get(0);
        require("G.FMT.10".equals(finding.ruleId()) && finding.lineNumber() == 64,
                "finding identity was not parsed");
        require("src/main/java/example/Demo.java".equals(finding.filePath()),
                "finding path was not parsed");
        System.out.println("HttpOpenLibingCodeCheckClientDeterministicTest: PASS");
    }

    private static String body(Request request) throws java.io.IOException {
        if (request.body() == null) {
            return "";
        }
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return buffer.readUtf8();
    }

    private static Response response(Request request, String json) {
        ResponseBody body = ResponseBody.create(json, MediaType.get("application/json"));
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body)
                .build();
    }

    private static String summaryJson() {
        return """
                {"code":200,"result":{"reportVo":{
                  "prId":"266",
                  "mrUrl":"https://gitcode.com/openJiuwen/agent-core-java/merge_requests/266",
                  "repoUrl":"https://gitcode.com/openJiuwen/agent-core-java.git",
                  "uuid":"demo_uuid","task_id":"MR_demo_task","projectId":300075,
                  "projectName":"openJiuwen","repoNameEn":"agent-core-java","git_branch":"730"
                }}}
                """;
    }

    private static String detailJson() {
        return """
                {"code":200,"result":{"count":1,"defects":[{
                  "id":"finding-1","fileName":"src/main/java/example/Demo.java",
                  "lineNumber":64,"ruleId":"G.FMT.10","defectCheckerName":"Line length",
                  "defectContent":"Each line has a maximum of 120 half-width characters.",
                  "defectLevel":"suggestion","defectStatus":"0","fragment":["return value;"]
                }]}}
                """;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record CapturedRequest(String method, String path, String query, String body,
                                   String cookie, String csrf) {
    }
}
