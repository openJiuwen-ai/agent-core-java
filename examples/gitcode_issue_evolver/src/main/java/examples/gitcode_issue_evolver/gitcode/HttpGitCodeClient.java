/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.gitcode;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * OkHttp GitCode client pinned to one validated target repository and the official API host.
 *
 * @since 0.1.12
 */
public final class HttpGitCodeClient implements GitCodeClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpGitCodeClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient client;
    private final HttpUrl apiBase;
    private final String token;
    private final RepositoryCoordinates coordinates;

    /**
     * Create a client restricted to the official GitCode HTTPS API host.
     *
     * @param apiBaseUrl official GitCode API root
     * @param token robot access token sent only in the Authorization header
     * @param coordinates validated target, publication, and baseline coordinates
     */
    public HttpGitCodeClient(URI apiBaseUrl, String token, RepositoryCoordinates coordinates) {
        this(new OkHttpClient.Builder().callTimeout(Duration.ofSeconds(30)).build(),
                requireOfficialBase(apiBaseUrl), token, coordinates);
    }

    HttpGitCodeClient(OkHttpClient client, URI apiBaseUrl, String token,
                      RepositoryCoordinates coordinates) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.apiBase = requireBase(apiBaseUrl);
        this.token = requireText(token, "GitCode token");
        this.coordinates = Objects.requireNonNull(coordinates, "coordinates must not be null");
    }

    @Override
    public GitCodeIssuePage listIssues(IssueScanRequest request) {
        IssueScanRequest requiredRequest = Objects.requireNonNull(request, "request must not be null");
        HttpUrl url = path(targetPath("issues")).newBuilder()
                .addQueryParameter("state", "open")
                .addQueryParameter("labels", requiredRequest.label())
                .addQueryParameter("sort", "created")
                .addQueryParameter("direction", "asc")
                .addQueryParameter("created_after", requiredRequest.createdAfter().minusMillis(1).toString())
                .addQueryParameter("created_before", requiredRequest.createdBefore().plusMillis(1).toString())
                .addQueryParameter("page", Integer.toString(requiredRequest.page()))
                .addQueryParameter("per_page", Integer.toString(requiredRequest.perPage()))
                .build();
        return issuePage(get(url));
    }

    @Override
    public GitCodeIssuePage listOpenIssuesByLabel(IssueLabelScanRequest request) {
        IssueLabelScanRequest requiredRequest = Objects.requireNonNull(request, "request must not be null");
        HttpUrl url = path(targetPath("issues")).newBuilder()
                .addQueryParameter("state", "open")
                .addQueryParameter("labels", requiredRequest.label())
                .addQueryParameter("sort", "created")
                .addQueryParameter("direction", "asc")
                .addQueryParameter("page", Integer.toString(requiredRequest.page()))
                .addQueryParameter("per_page", Integer.toString(requiredRequest.perPage()))
                .build();
        return issuePage(get(url));
    }

    private GitCodeIssuePage issuePage(JsonNode response) {
        if (!response.isArray()) {
            throw new GitCodeApiException("GitCode Issue list response was not an array", 0, false);
        }
        List<GitCodeIssueSummary> issues = new ArrayList<>();
        response.forEach(node -> issueSummary(node).ifPresent(issues::add));
        return new GitCodeIssuePage(issues, response.size());
    }

    @Override
    public GitCodeIssue getIssue(long issueIid) {
        requirePositive(issueIid, "issueIid");
        JsonNode issue = get(path(targetPath("issues/" + issueIid)));
        return new GitCodeIssue(issueIid, text(issue, "title"), text(issue, "body", "description"),
                text(issue, "state"), text(issue, "html_url", "web_url"), listIssueComments(issueIid),
                labelNames(issue.path("labels")));
    }

    @Override
    public List<String> listIssueComments(long issueIid) {
        requirePositive(issueIid, "issueIid");
        JsonNode response = get(path(targetPath("issues/" + issueIid + "/comments")));
        List<String> comments = new ArrayList<>();
        if (response.isArray()) {
            response.forEach(node -> comments.add(text(node, "body", "note")));
        }
        return List.copyOf(comments);
    }

    @Override
    public Optional<GitCodePullRequest> findOpenPullRequest(long issueIid, String headBranch) {
        requirePositive(issueIid, "issueIid");
        String requiredBranch = requireText(headBranch, "head branch");
        HttpUrl url = path(targetPath("pulls")).newBuilder()
                .addQueryParameter("state", "open")
                .addQueryParameter("issue", Long.toString(issueIid))
                .addQueryParameter("per_page", "100")
                .build();
        Optional<GitCodePullRequest> associated = matchingPullRequest(get(url), requiredBranch);
        if (associated.isPresent()) {
            return associated;
        }
        HttpUrl headUrl = path(targetPath("pulls")).newBuilder()
                .addQueryParameter("state", "open")
                .addQueryParameter("head", requestHead(requiredBranch))
                .addQueryParameter("per_page", "100")
                .build();
        return matchingPullRequest(get(headUrl), requiredBranch);
    }

    private Optional<GitCodePullRequest> matchingPullRequest(JsonNode response, String requiredBranch) {
        if (response.isArray()) {
            for (JsonNode node : response) {
                GitCodePullRequest pullRequest = pullRequest(node);
                if (pullRequest.isOpen() && matchesHead(node, pullRequest, requiredBranch)) {
                    return Optional.of(pullRequest);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public GitCodePullRequest createPullRequest(CreatePullRequestRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", request.title());
        payload.put("head", requestHead(request.headBranch()));
        payload.put("base", coordinates.baseBranch());
        payload.put("body", request.body());
        payload.put("issue", String.valueOf(request.issueIid()));
        payload.put("assignees", String.join(",", request.assignees()));
        payload.put("draft", request.draft());
        payload.put("prune_source_branch", false);
        if (!coordinates.sameRepository()) {
            payload.put("fork_path", coordinates.publishRepository());
        }
        return pullRequest(post(path(targetPath("pulls")), payload));
    }

    @Override
    public void commentIssue(long issueIid, String body) {
        requirePositive(issueIid, "issueIid");
        JsonNode response = post(path(targetPath("issues/" + issueIid + "/comments")),
                Map.of("body", requireText(body, "comment body")));
        if (!response.isObject()) {
            throw new GitCodeApiException("GitCode comment response was not an object", 0, true);
        }
    }

    @Override
    public GitCodePullRequest getPullRequest(long number) {
        requirePositive(number, "pull request number");
        return pullRequest(get(path(targetPath("pulls/" + number))));
    }

    @Override
    public List<GitCodePullRequestComment> listPullRequestComments(long number) {
        requirePositive(number, "pull request number");
        List<GitCodePullRequestComment> comments = new ArrayList<>();
        for (int page = 1; page <= 10; page++) {
            HttpUrl url = path(targetPath("pulls/" + number + "/comments")).newBuilder()
                    .addQueryParameter("page", Integer.toString(page))
                    .addQueryParameter("per_page", "100")
                    .addQueryParameter("direction", "asc")
                    .build();
            JsonNode response = get(url);
            if (!response.isArray()) {
                throw new GitCodeApiException("GitCode PR comments response was not an array", 0, false);
            }
            response.forEach(node -> pullRequestComment(node).ifPresent(comments::add));
            if (response.size() < 100) {
                break;
            }
        }
        return List.copyOf(comments);
    }

    private boolean matchesHead(JsonNode node, GitCodePullRequest pullRequest, String branch) {
        String label = node.path("head").path("label").asText("");
        String expectedLabel = coordinates.publishOwner() + ":" + branch;
        return branch.equals(pullRequest.headRef())
                || expectedLabel.equals(pullRequest.headRef())
                || expectedLabel.equals(label);
    }

    private String requestHead(String branch) {
        String requiredBranch = requireText(branch, "head branch");
        return coordinates.sameRepository()
                ? requiredBranch : coordinates.publishOwner() + ":" + requiredBranch;
    }

    private String targetPath(String suffix) {
        return coordinates.targetApiPath() + "/" + suffix;
    }

    private JsonNode get(HttpUrl url) {
        GitCodeApiException failure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try (Response response = client.newCall(request(url).get().build()).execute()) {
                if (response.isSuccessful()) {
                    return parse(response);
                }
                failure = error(response, false);
                if (response.code() != 429 && response.code() < 500) {
                    throw failure;
                }
            } catch (IOException ex) {
                failure = new GitCodeApiException("GitCode GET transport failed", 0, false, ex);
            }
            sleep(attempt);
        }
        throw failure == null ? new GitCodeApiException("GitCode GET failed", 0, false) : failure;
    }

    private JsonNode post(HttpUrl url, Map<String, Object> payload) {
        try {
            RequestBody body = RequestBody.create(mapper.writeValueAsBytes(payload), JSON);
            try (Response response = client.newCall(request(url).post(body).build()).execute()) {
                if (!response.isSuccessful()) {
                    throw error(response, false);
                }
                return parse(response);
            }
        } catch (JsonProcessingException ex) {
            throw new GitCodeApiException("Unable to encode GitCode request", 0, false, ex);
        } catch (IOException ex) {
            throw new GitCodeApiException("GitCode write result is uncertain", 0, true, ex);
        }
    }

    private Request.Builder request(HttpUrl url) {
        return new Request.Builder().url(url)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json");
    }

    private HttpUrl path(String relativePath) {
        HttpUrl resolved = apiBase.resolve(relativePath);
        if (resolved == null) {
            throw new IllegalArgumentException("Invalid GitCode API path");
        }
        return resolved;
    }

    private static GitCodePullRequest pullRequest(JsonNode node) {
        JsonNode head = node.path("head");
        return new GitCodePullRequest(node.path("number").asLong(node.path("iid").asLong(-1)),
                text(node, "html_url", "web_url"), text(node, "state"),
                text(head, "ref", "label"), text(head, "sha"),
                node.path("draft").asBoolean(node.path("work_in_progress").asBoolean(false)),
                labelNames(node.path("labels")));
    }

    private static Optional<GitCodePullRequestComment> pullRequestComment(JsonNode node) {
        try {
            String id = text(node, "id");
            String body = text(node, "body", "note");
            String author = text(node.path("user"), "login", "username", "name");
            Instant createdAt = Instant.parse(text(node, "created_at"));
            String updated = text(node, "updated_at");
            Instant updatedAt = updated.isBlank() ? createdAt : Instant.parse(updated);
            if (id.isBlank() || body.isBlank() || author.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new GitCodePullRequestComment(id, body, author,
                    text(node, "comment_type", "type"), createdAt, updatedAt));
        } catch (DateTimeParseException ex) {
            LOGGER.warn("Skipped a GitCode PR comment with an invalid timestamp");
            return Optional.empty();
        }
    }

    private static Optional<GitCodeIssueSummary> issueSummary(JsonNode node) {
        try {
            long iid = issueIid(node);
            String title = text(node, "title");
            String url = text(node, "html_url", "web_url");
            Instant createdAt = Instant.parse(text(node, "created_at"));
            if (iid <= 0 || title.isBlank() || url.isBlank()) {
                LOGGER.warn("Skipped a malformed GitCode Issue list entry");
                return Optional.empty();
            }
            return Optional.of(new GitCodeIssueSummary(iid, title, text(node, "state"), url,
                    labelNames(node.path("labels")), createdAt));
        } catch (DateTimeParseException | NumberFormatException ex) {
            LOGGER.warn("Skipped a GitCode Issue list entry with invalid identifier or timestamp");
            return Optional.empty();
        }
    }

    private static long issueIid(JsonNode node) {
        JsonNode number = node.path("number");
        if (number.canConvertToLong()) {
            return number.asLong();
        }
        String value = number.asText("");
        return value.isBlank() ? node.path("iid").asLong(-1L) : Long.parseLong(value);
    }

    private static List<String> labelNames(JsonNode labels) {
        if (!labels.isArray()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        labels.forEach(label -> {
            String name = label.isTextual() ? label.asText() : text(label, "name", "title");
            if (!name.isBlank()) {
                names.add(name);
            }
        });
        return List.copyOf(names);
    }

    private JsonNode parse(Response response) throws IOException {
        String body = response.body() == null ? "{}" : response.body().string();
        JsonNode parsed = mapper.readTree(body.isBlank() ? "{}" : body);
        if (parsed == null || parsed.isNull()) {
            throw new IOException("GitCode API returned an empty JSON value");
        }
        return parsed;
    }

    private static GitCodeApiException error(Response response, boolean uncertain) {
        return new GitCodeApiException("GitCode API returned HTTP " + response.code(),
                response.code(), uncertain);
    }

    private static String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isValueNode() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return "";
    }

    private static HttpUrl requireBase(URI value) {
        String url = value.toString().endsWith("/") ? value.toString() : value + "/";
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid GitCode API base URL");
        }
        return parsed;
    }

    private static URI requireOfficialBase(URI value) {
        if (value == null || !"https".equalsIgnoreCase(value.getScheme())
                || !"api.gitcode.com".equalsIgnoreCase(value.getHost())
                || value.getUserInfo() != null
                || (value.getPort() != -1 && value.getPort() != 443)
                || value.getQuery() != null
                || value.getFragment() != null) {
            throw new IllegalArgumentException("GitCode API base must be https://api.gitcode.com");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void sleep(int attempt) {
        if (attempt >= 3) {
            return;
        }
        try {
            Thread.sleep(Math.min(2000L, 250L * attempt));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GitCodeApiException("GitCode retry interrupted", 0, false);
        }
    }
}
