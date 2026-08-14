/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.gitcode;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import examples.gitcode_issue_evolver.RepositoryCoordinates;
import examples.gitcode_issue_evolver.gitcode.GitCodeApiException;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Fixed-host GitCode API client for updated-at feature intake and one-PR lifecycle updates.
 *
 * @since 0.1.12
 */
public final class HttpFeatureGitCodeClient implements FeatureGitCodeClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_COMMENT_PAGES = 10;
    private static final int PAGE_SIZE = 100;
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpFeatureGitCodeClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient client;
    private final HttpUrl apiBase;
    private final String token;
    private final RepositoryCoordinates coordinates;

    /**
     * Create a client pinned to the official GitCode API host.
     *
     * @param apiBaseUrl official API v5 base
     * @param token Evolver bot token
     * @param coordinates validated target and publication repositories
     */
    public HttpFeatureGitCodeClient(URI apiBaseUrl, String token,
                                    RepositoryCoordinates coordinates) {
        this(new OkHttpClient.Builder().callTimeout(Duration.ofSeconds(30)).build(),
                requireOfficialBase(apiBaseUrl), token, coordinates);
    }

    HttpFeatureGitCodeClient(OkHttpClient client, URI apiBaseUrl, String token,
                             RepositoryCoordinates coordinates) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.apiBase = requireBase(apiBaseUrl);
        this.token = requireText(token, "GitCode token");
        this.coordinates = Objects.requireNonNull(coordinates, "coordinates must not be null");
    }

    @Override
    public FeatureIssuePage listIssues(FeatureIssueScanRequest request) {
        FeatureIssueScanRequest required = Objects.requireNonNull(request, "request must not be null");
        HttpUrl url = path(targetPath("issues")).newBuilder()
                .addQueryParameter("state", "open")
                .addQueryParameter("labels", required.label())
                .addQueryParameter("sort", "updated")
                .addQueryParameter("direction", "asc")
                .addQueryParameter("updated_after",
                        issueQueryTime(required.window().start().minusMillis(1)))
                .addQueryParameter("updated_before",
                        issueQueryTime(required.window().end().plusMillis(1)))
                .addQueryParameter("page", Integer.toString(required.page()))
                .addQueryParameter("per_page", Integer.toString(required.perPage()))
                .build();
        JsonNode response = get(url);
        if (!response.isArray()) {
            throw new GitCodeApiException("GitCode feature Issue list response was not an array", 0, false);
        }
        List<FeatureIssueSummary> issues = new ArrayList<>();
        response.forEach(node -> issueSummary(node).ifPresent(issues::add));
        return new FeatureIssuePage(issues, response.size());
    }

    @Override
    public FeatureIssue getIssue(long issueIid) {
        requirePositive(issueIid, "issueIid");
        JsonNode issue = get(path(targetPath("issues/" + issueIid)));
        return new FeatureIssue(issueIid, text(issue, "title"), text(issue, "body", "description"),
                text(issue, "state"), text(issue, "html_url", "web_url"));
    }

    @Override
    public List<FeatureComment> listIssueComments(long issueIid) {
        requirePositive(issueIid, "issueIid");
        List<FeatureComment> comments = new ArrayList<>();
        for (int page = 1; page <= MAX_COMMENT_PAGES; page++) {
            HttpUrl url = path(targetPath("issues/" + issueIid + "/comments")).newBuilder()
                    .addQueryParameter("page", Integer.toString(page))
                    .addQueryParameter("per_page", Integer.toString(PAGE_SIZE))
                    .build();
            JsonNode response = get(url);
            JsonNode array = response.isArray() ? response : response.path("data");
            if (!array.isArray()) {
                throw new GitCodeApiException("GitCode comment response was not an array", 0, false);
            }
            array.forEach(node -> comment(node).ifPresent(comments::add));
            if (array.size() < PAGE_SIZE) {
                return List.copyOf(comments);
            }
        }
        LOGGER.warn("Feature Issue {} comments reached the {} page limit",
                issueIid, MAX_COMMENT_PAGES);
        return List.copyOf(comments);
    }

    @Override
    public Optional<FeaturePullRequest> findOpenPullRequest(long issueIid, String headBranch) {
        requirePositive(issueIid, "issueIid");
        String branch = requireText(headBranch, "headBranch");
        HttpUrl issueUrl = path(targetPath("pulls")).newBuilder()
                .addQueryParameter("state", "open")
                .addQueryParameter("issue", Long.toString(issueIid))
                .addQueryParameter("per_page", "100")
                .build();
        Optional<FeaturePullRequest> associated = matchingPullRequest(get(issueUrl), branch);
        if (associated.isPresent()) {
            return associated;
        }
        HttpUrl headUrl = path(targetPath("pulls")).newBuilder()
                .addQueryParameter("state", "open")
                .addQueryParameter("head", requestHead(branch))
                .addQueryParameter("per_page", "100")
                .build();
        return matchingPullRequest(get(headUrl), branch);
    }

    @Override
    public Optional<FeaturePullRequest> findOpenPullRequest(String headBranch) {
        String branch = requireText(headBranch, "headBranch");
        HttpUrl headUrl = path(targetPath("pulls")).newBuilder()
                .addQueryParameter("state", "open")
                .addQueryParameter("head", requestHead(branch))
                .addQueryParameter("per_page", "100")
                .build();
        return matchingPullRequest(get(headUrl), branch);
    }

    @Override
    public FeaturePullRequest createPullRequest(CreateFeaturePullRequest request) {
        CreateFeaturePullRequest required = Objects.requireNonNull(request, "request must not be null");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", required.content().title());
        payload.put("head", requestHead(required.headBranch()));
        payload.put("base", coordinates.baseBranch());
        payload.put("body", required.content().body());
        if (required.issueIid() != null) {
            payload.put("issue", Long.toString(required.issueIid()));
        }
        payload.put("assignees", String.join(",", required.assignees()));
        payload.put("draft", required.draft());
        payload.put("prune_source_branch", false);
        if (!coordinates.sameRepository()) {
            payload.put("fork_path", coordinates.publishRepository());
        }
        return writtenPullRequest(write(path(targetPath("pulls")), payload, WriteMethod.POST));
    }

    @Override
    public FeaturePullRequest updatePullRequest(UpdateFeaturePullRequest request) {
        UpdateFeaturePullRequest required = Objects.requireNonNull(request, "request must not be null");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", required.content().title());
        payload.put("body", required.content().body());
        payload.put("draft", required.draft());
        write(path(targetPath("pulls/" + required.number())), payload, WriteMethod.PATCH);
        return getPullRequest(required.number());
    }

    @Override
    public FeaturePullRequest getPullRequest(long number) {
        requirePositive(number, "pull request number");
        return pullRequest(get(path(targetPath("pulls/" + number))));
    }

    @Override
    public void commentIssue(long issueIid, String body) {
        requirePositive(issueIid, "issueIid");
        JsonNode response = write(path(targetPath("issues/" + issueIid + "/comments")),
                Map.of("body", requireText(body, "comment body")), WriteMethod.POST);
        if (!response.isObject()) {
            throw new GitCodeApiException("GitCode comment response was not an object", 0, true);
        }
    }

    private Optional<FeaturePullRequest> matchingPullRequest(JsonNode response, String branch) {
        if (!response.isArray()) {
            return Optional.empty();
        }
        for (JsonNode node : response) {
            Optional<FeaturePullRequest> parsed = safePullRequest(node);
            if (parsed.isPresent() && parsed.orElseThrow().isOpen()
                    && matchesHead(node, parsed.orElseThrow(), branch)) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    private boolean matchesHead(JsonNode node, FeaturePullRequest pullRequest, String branch) {
        String expected = coordinates.publishOwner() + ":" + branch;
        String label = node.path("head").path("label").asText("");
        return branch.equals(pullRequest.head().ref()) || expected.equals(pullRequest.head().ref())
                || expected.equals(label);
    }

    private String requestHead(String branch) {
        String required = requireText(branch, "headBranch");
        return coordinates.sameRepository() ? required : coordinates.publishOwner() + ":" + required;
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
            retryDelay(attempt);
        }
        throw failure == null ? new GitCodeApiException("GitCode GET failed", 0, false) : failure;
    }

    private JsonNode write(HttpUrl url, Map<String, Object> payload, WriteMethod method) {
        try {
            RequestBody body = RequestBody.create(mapper.writeValueAsBytes(payload), JSON);
            Request.Builder builder = request(url);
            Request requestValue = method == WriteMethod.POST
                    ? builder.post(body).build() : builder.patch(body).build();
            try (Response response = client.newCall(requestValue).execute()) {
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

    private JsonNode parse(Response response) throws IOException {
        String body = response.body() == null ? "{}" : response.body().string();
        JsonNode parsed = mapper.readTree(body.isBlank() ? "{}" : body);
        if (parsed == null || parsed.isNull()) {
            throw new IOException("GitCode API returned an empty JSON value");
        }
        return parsed;
    }

    private static Optional<FeatureIssueSummary> issueSummary(JsonNode node) {
        try {
            long iid = issueIid(node);
            String title = text(node, "title");
            String url = text(node, "html_url", "web_url");
            FeatureIssueSummary.Status status = new FeatureIssueSummary.Status(
                    text(node, "state"), labelNames(node.path("labels")),
                    Instant.parse(text(node, "updated_at")));
            return Optional.of(new FeatureIssueSummary(iid, title, url, status));
        } catch (DateTimeParseException | IllegalArgumentException ex) {
            LOGGER.warn("Skipped a malformed GitCode feature Issue list entry");
            return Optional.empty();
        }
    }

    private static Optional<FeatureComment> comment(JsonNode node) {
        try {
            String id = identifier(node, "id");
            String author = text(node.path("user"), "login", "username", "name");
            if (author.isBlank()) {
                author = text(node.path("author"), "login", "username", "name");
            }
            String body = text(node, "body", "note");
            Instant createdAt = Instant.parse(text(node, "created_at", "createdAt"));
            return Optional.of(new FeatureComment(id, author, body, createdAt));
        } catch (DateTimeParseException | IllegalArgumentException ex) {
            LOGGER.warn("Skipped a malformed GitCode feature comment");
            return Optional.empty();
        }
    }

    private static FeaturePullRequest pullRequest(JsonNode node) {
        return safePullRequest(node).orElseThrow(() ->
                new GitCodeApiException("Malformed GitCode pull-request response", 0, false));
    }

    private FeaturePullRequest writtenPullRequest(JsonNode node) {
        Optional<FeaturePullRequest> parsed = safePullRequest(node, false);
        if (parsed.isPresent()) {
            return parsed.orElseThrow();
        }
        try {
            return getPullRequest(number(node));
        } catch (IllegalArgumentException ex) {
            throw new GitCodeApiException("Malformed GitCode pull-request response", 0, false);
        }
    }

    private static Optional<FeaturePullRequest> safePullRequest(JsonNode node) {
        return safePullRequest(node, true);
    }

    private static Optional<FeaturePullRequest> safePullRequest(JsonNode node,
                                                               boolean warnWhenMalformed) {
        try {
            long number = number(node);
            JsonNode head = node.path("head");
            String headRef = text(head, "ref", "label");
            if (headRef.isBlank()) {
                headRef = text(node, "source_branch");
            }
            String headSha = text(head, "sha");
            if (headSha.isBlank()) {
                headSha = text(node, "head_sha");
            }
            FeaturePullRequest.Head identity = new FeaturePullRequest.Head(
                    requireText(headRef, "pull request head ref"),
                    requireText(headSha, "pull request head SHA"));
            return Optional.of(new FeaturePullRequest(number, text(node, "html_url", "web_url"),
                    text(node, "state"), node.path("draft").asBoolean(
                    node.path("work_in_progress").asBoolean(false)), identity));
        } catch (IllegalArgumentException ex) {
            if (warnWhenMalformed) {
                LOGGER.warn("Skipped a malformed GitCode pull request");
            }
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

    private static long number(JsonNode node) {
        long value = node.path("number").asLong(node.path("iid").asLong(-1L));
        requirePositive(value, "pull request number");
        return value;
    }

    private static String identifier(JsonNode node, String field) {
        JsonNode value = node.path(field);
        String textValue = value.isValueNode() ? value.asText("") : "";
        return requireText(textValue, field);
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

    private static String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isValueNode() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return "";
    }

    private static String issueQueryTime(Instant value) {
        return value.truncatedTo(ChronoUnit.MILLIS).toString();
    }

    private static GitCodeApiException error(Response response, boolean uncertain) {
        return new GitCodeApiException("GitCode API returned HTTP " + response.code(),
                response.code(), uncertain);
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
                || value.getUserInfo() != null || (value.getPort() != -1 && value.getPort() != 443)
                || value.getQuery() != null || value.getFragment() != null) {
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

    private static void retryDelay(int attempt) {
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

    private enum WriteMethod {
        POST,
        PATCH
    }
}
