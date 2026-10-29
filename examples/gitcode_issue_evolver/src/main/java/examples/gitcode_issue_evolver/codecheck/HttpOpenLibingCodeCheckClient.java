/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.codecheck;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import examples.gitcode_issue_evolver.RepositoryCoordinates;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Direct, credential-isolated, fixed-origin adapter for OpenLibing incremental CodeCheck reports.
 *
 * @since 0.1.12
 */
public final class HttpOpenLibingCodeCheckClient implements OpenLibingCodeCheckClient {
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int PAGE_SIZE = 20;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Pattern REPORT_PATH = Pattern.compile(
            "/apps/entryCheckDashCode/([A-Za-z0-9_-]+)/([A-Za-z0-9_-]+)");
    private static final String USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/140.0 Safari/537.36";

    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient client;
    private final HttpUrl baseUrl;
    private final RepositoryCoordinates coordinates;
    private final int maxFindings;

    /** Create a restricted anonymous report reader that never uses GitCode credentials. */
    public HttpOpenLibingCodeCheckClient(URI baseUrl, RepositoryCoordinates coordinates,
                                         int timeoutSeconds, int maxFindings) {
        this(new OkHttpClient.Builder()
                        .proxy(Proxy.NO_PROXY)
                        .followRedirects(false)
                        .callTimeout(Duration.ofSeconds(timeoutSeconds))
                        .build(),
                baseUrl, coordinates, maxFindings);
    }

    HttpOpenLibingCodeCheckClient(OkHttpClient client, URI baseUrl,
                                  RepositoryCoordinates coordinates, int maxFindings) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.baseUrl = requireBase(baseUrl);
        this.coordinates = Objects.requireNonNull(coordinates, "coordinates must not be null");
        if (maxFindings < 1 || maxFindings > 200) {
            throw new IllegalArgumentException("maxFindings must be between 1 and 200");
        }
        this.maxFindings = maxFindings;
    }

    @Override
    public CodeCheckReport read(URI reportUrl, long expectedPullRequest) {
        ReportIdentity identity = validateReportUrl(reportUrl);
        HttpUrl pageUrl = Objects.requireNonNull(HttpUrl.parse(reportUrl.toString()), "report URL");
        JsonNode summary = post(summaryUrl(identity), summaryPayload(identity), pageUrl);
        JsonNode report = result(summary).path("reportVo");
        validateReportOwner(report, expectedPullRequest, identity);
        FindingReadResult findings = readFindings(identity, report, pageUrl);
        return new CodeCheckReport(reportUrl, expectedPullRequest, findings.total(), findings.findings());
    }

    private FindingReadResult readFindings(ReportIdentity identity, JsonNode report,
                                           HttpUrl pageUrl) {
        List<CodeCheckFinding> findings = new ArrayList<>();
        int total = 0;
        for (int page = 1; findings.size() < maxFindings; page++) {
            Map<String, Object> payload = detailPayload(report, page);
            JsonNode body = result(post(detailUrl(identity), payload, pageUrl));
            JsonNode defects = body.path("defects");
            if (!defects.isArray()) {
                throw new CodeCheckAccessException("OpenLibing detail response has no defects array", false);
            }
            total = Math.max(total, body.path("count").asInt(defects.size()));
            for (JsonNode defect : defects) {
                if (findings.size() >= maxFindings) {
                    break;
                }
                findings.add(finding(defect));
            }
            if (defects.size() < PAGE_SIZE || findings.size() >= body.path("count").asInt(0)) {
                break;
            }
        }
        return new FindingReadResult(total, List.copyOf(findings));
    }

    private Map<String, Object> detailPayload(JsonNode report, int page) {
        Map<String, Object> payload = commonFilters();
        payload.put("pageNum", page);
        payload.put("pageSize", PAGE_SIZE);
        payload.put("projectName", bounded(report.path("projectName").asText(""), 200));
        payload.put("projectId", bounded(report.path("projectId").asText(""), 64));
        payload.put("repoUrl", bounded(report.path("repoUrl").asText(""), 500));
        payload.put("repoName", bounded(report.path("repoNameEn").asText(""), 200));
        payload.put("branchName", bounded(report.path("git_branch").asText(""), 200));
        payload.put("ruleName", "");
        return payload;
    }

    private Map<String, Object> summaryPayload(ReportIdentity identity) {
        Map<String, Object> payload = commonFilters();
        payload.put("pageNum", 1);
        payload.put("pageSize", PAGE_SIZE);
        payload.put("flag", "1");
        payload.put("defectCheckName", "");
        payload.put("projectId", identity.projectId());
        return payload;
    }

    private static Map<String, Object> commonFilters() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("date", "");
        payload.put("defectLevel", "");
        payload.put("ruleType", "");
        payload.put("filePath", "");
        payload.put("fileName", "");
        payload.put("defectStatus", "0");
        payload.put("checkType", "");
        payload.put("trigger", "");
        payload.put("shieldType", "");
        payload.put("defectCheckerName", "");
        payload.put("isDelay", "");
        return payload;
    }

    private JsonNode post(HttpUrl url, Map<String, Object> payload, HttpUrl referer) {
        try {
            RequestBody body = RequestBody.create(mapper.writeValueAsBytes(payload), JSON);
            Request.Builder request = new Request.Builder().url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Origin", baseUrl.newBuilder().encodedPath("/").build()
                            .toString().replaceAll("/$", ""))
                    .header("Referer", referer.toString())
                    .header("Sec-Fetch-Site", "same-origin")
                    .post(body);
            try (Response response = client.newCall(request.build()).execute()) {
                if (!response.isSuccessful()) {
                    throw httpFailure("OpenLibing report API", response.code());
                }
                return parseBounded(response);
            }
        } catch (JsonProcessingException ex) {
            throw new CodeCheckAccessException("Unable to encode OpenLibing request", false, ex);
        } catch (IOException ex) {
            throw new CodeCheckAccessException("OpenLibing report transport failed", true, ex);
        }
    }

    private JsonNode parseBounded(Response response) throws IOException {
        if (response.body() == null) {
            throw new IOException("OpenLibing returned an empty response");
        }
        byte[] bytes = response.body().byteStream().readNBytes(MAX_RESPONSE_BYTES + 1);
        if (bytes.length > MAX_RESPONSE_BYTES) {
            throw new CodeCheckAccessException("OpenLibing response exceeded the size limit", false);
        }
        JsonNode parsed = mapper.readTree(bytes);
        if (parsed == null || !parsed.isObject()) {
            throw new IOException("OpenLibing returned invalid JSON");
        }
        return parsed;
    }

    private void validateReportOwner(JsonNode report, long expectedPullRequest,
                                     ReportIdentity identity) {
        long pullRequest = report.path("prId").asLong(-1L);
        String mrUrl = report.path("mrUrl").asText("");
        String repositoryUrl = "https://gitcode.com/" + coordinates.targetRepository();
        String expectedMergeRequest = repositoryUrl + "/merge_requests/" + expectedPullRequest;
        String expectedPullRequestUrl = repositoryUrl + "/pull/" + expectedPullRequest;
        String reportRepository = report.path("repoUrl").asText("");
        boolean identityMatches = identity.uuid().equals(report.path("uuid").asText(""))
                && identity.taskId().equals(report.path("task_id").asText(""))
                && identity.projectId().equals(report.path("projectId").asText(""));
        if (pullRequest != expectedPullRequest
                || !(expectedMergeRequest.equals(mrUrl) || expectedPullRequestUrl.equals(mrUrl))
                || !(repositoryUrl.equals(reportRepository)
                || (repositoryUrl + ".git").equals(reportRepository))
                || !identityMatches) {
            throw new CodeCheckAccessException("OpenLibing report does not belong to the expected PR", false);
        }
    }

    private CodeCheckFinding finding(JsonNode node) {
        List<String> fragment = new ArrayList<>();
        JsonNode lines = node.path("fragment");
        if (lines.isArray()) {
            for (JsonNode line : lines) {
                if (fragment.size() >= 20) {
                    break;
                }
                fragment.add(bounded(line.isTextual() ? line.asText() : line.toString(), 500));
            }
        }
        return new CodeCheckFinding(bounded(node.path("id").asText(""), 100),
                bounded(node.path("fileName").asText(node.path("filePath").asText("")), 500),
                Math.max(0, node.path("lineNumber").asInt(0)), bounded(node.path("ruleId").asText(""), 100),
                bounded(node.path("defectCheckerName").asText(""), 500),
                bounded(node.path("defectContent").asText(node.path("ruleName").asText("")), 1000),
                bounded(node.path("defectLevel").asText(""), 20),
                bounded(node.path("defectStatus").asText(""), 20), fragment);
    }

    private JsonNode result(JsonNode response) {
        int code = response.path("code").asInt(0);
        if (code != 200) {
            if (code == 4001) {
                throw new CodeCheckAccessException(
                        "OpenLibing report is not anonymously readable", false);
            }
            throw new CodeCheckAccessException("OpenLibing report API returned application code " + code,
                    code == 408 || code == 429 || code >= 500);
        }
        JsonNode result = response.path("result");
        if (!result.isObject()) {
            throw new CodeCheckAccessException("OpenLibing response has no result object", false);
        }
        return result;
    }

    private HttpUrl summaryUrl(ReportIdentity identity) {
        return apiUrl("gateway/openlibing-codecheck/ci-portal/v1/codecheck/event/task/issues/report", identity);
    }

    private HttpUrl detailUrl(ReportIdentity identity) {
        return apiUrl("gateway/openlibing-codecheck/ci-portal/v1/event/codecheck/task", identity);
    }

    private HttpUrl apiUrl(String path, ReportIdentity identity) {
        return baseUrl.newBuilder().addPathSegments(path)
                .addQueryParameter("uuid", identity.uuid())
                .addQueryParameter("taskId", identity.taskId()).build();
    }

    private ReportIdentity validateReportUrl(URI reportUrl) {
        if (reportUrl == null || !baseUrl.scheme().equalsIgnoreCase(reportUrl.getScheme())
                || !baseUrl.host().equalsIgnoreCase(reportUrl.getHost())
                || reportUrl.getUserInfo() != null || reportUrl.getFragment() != null
                || (reportUrl.getPort() != -1 && reportUrl.getPort() != baseUrl.port())) {
            throw new CodeCheckAccessException("CodeCheck report URL is outside the configured origin", false);
        }
        Matcher matcher = REPORT_PATH.matcher(reportUrl.getPath());
        if (!matcher.matches()) {
            throw new CodeCheckAccessException("CodeCheck report URL has an unsupported path", false);
        }
        String projectId = queryParameter(reportUrl, "projectId");
        if (!projectId.matches("[0-9]{1,20}")) {
            throw new CodeCheckAccessException("CodeCheck report URL has no valid projectId", false);
        }
        return new ReportIdentity(matcher.group(1), matcher.group(2), projectId);
    }

    private static String queryParameter(URI uri, String name) {
        String query = uri.getRawQuery();
        if (query == null) {
            return "";
        }
        for (String part : query.split("&")) {
            int separator = part.indexOf('=');
            if (separator > 0 && name.equals(part.substring(0, separator))) {
                return part.substring(separator + 1);
            }
        }
        return "";
    }

    private static HttpUrl requireBase(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IllegalArgumentException("OpenLibing base URL must be a plain HTTPS origin");
        }
        String value = uri.toString().endsWith("/") ? uri.toString() : uri + "/";
        return Objects.requireNonNull(HttpUrl.parse(value), "OpenLibing base URL");
    }

    private static CodeCheckAccessException httpFailure(String operation, int code) {
        return new CodeCheckAccessException(operation + " returned HTTP " + code,
                code == 408 || code == 418 || code == 429 || code >= 500);
    }

    private static String bounded(String value, int maxLength) {
        String safe = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').strip();
        return safe.substring(0, Math.min(maxLength, safe.length()));
    }

    private record ReportIdentity(String taskId, String uuid, String projectId) {
    }

    private record FindingReadResult(int total, List<CodeCheckFinding> findings) {
    }
}
