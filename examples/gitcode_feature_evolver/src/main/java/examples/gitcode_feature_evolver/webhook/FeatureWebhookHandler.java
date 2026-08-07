/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import examples.gitcode_feature_evolver.FeatureEvolvingConfig;
import examples.gitcode_feature_evolver.FeatureNaming;
import examples.gitcode_feature_evolver.gitcode.FeatureGitCodeClient;
import examples.gitcode_feature_evolver.job.AdmissionResult;
import examples.gitcode_feature_evolver.job.CommandResult;
import examples.gitcode_feature_evolver.job.FeatureCommand;
import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_feature_evolver.job.FeatureJobMutation;
import examples.gitcode_feature_evolver.job.FeatureJobRequest;
import examples.gitcode_feature_evolver.job.FeatureJobStore;
import examples.gitcode_feature_evolver.job.FeatureStage;
import examples.gitcode_feature_evolver.publish.FeatureStatusComment;
import examples.gitcode_issue_evolver.gitcode.GitCodeApiException;
import examples.gitcode_issue_evolver.webhook.GitCodeWebhookVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Authenticated enqueue-and-command GitCode Webhook endpoint for the feature service.
 *
 * @since 0.1.12
 */
public final class FeatureWebhookHandler implements HttpHandler {
    /** Maximum accepted uncompressed body size. */
    public static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final Pattern DELIVERY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{1,200}");
    private static final Pattern EVENT_PATTERN = Pattern.compile("[A-Za-z0-9 _.:-]{1,100}");
    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureWebhookHandler.class);
    private final FeatureEvolvingConfig config;
    private final FeatureJobStore store;
    private final FeatureGitCodeClient gitCode;
    private final Clock clock;

    /**
     * Create a handler using the system UTC clock.
     *
     * @param config validated feature configuration
     * @param store durable feature store
     * @param gitCode configured GitCode API for acknowledgements
     */
    public FeatureWebhookHandler(FeatureEvolvingConfig config, FeatureJobStore store,
                                 FeatureGitCodeClient gitCode) {
        this(config, store, gitCode, Clock.systemUTC());
    }

    FeatureWebhookHandler(FeatureEvolvingConfig config, FeatureJobStore store,
                          FeatureGitCodeClient gitCode, Clock clock) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.gitCode = Objects.requireNonNull(gitCode, "gitCode must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "method_not_allowed");
            return;
        }
        if (!isJson(exchange.getRequestHeaders().getFirst("Content-Type"))) {
            respond(exchange, 415, "unsupported_media_type");
            return;
        }
        byte[] body = boundedBody(exchange);
        if (body == null) {
            respond(exchange, 413, "payload_too_large");
            return;
        }
        if (!GitCodeWebhookVerifier.verify(body, header(exchange, "X-GitCode-Signature-256"),
                config.webhookSecret())) {
            respond(exchange, 401, "invalid_signature");
            return;
        }
        DeliveryHeaders headers = deliveryHeaders(exchange);
        if (!headers.valid()) {
            respond(exchange, 400, "missing_delivery_headers");
            return;
        }
        dispatchSafely(exchange, body, headers);
    }

    private void dispatchSafely(HttpExchange exchange, byte[] body, DeliveryHeaders headers)
            throws IOException {
        JsonNode payload;
        try {
            payload = FeatureWebhookParser.parse(body);
        } catch (IOException ex) {
            LOGGER.warn("Rejected malformed feature Webhook JSON");
            respond(exchange, 400, "invalid_json");
            return;
        }
        try {
            dispatch(exchange, payload, body, headers);
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("Rejected invalid feature Webhook payload", ex);
            respond(exchange, 400, "invalid_payload");
        } catch (IllegalStateException ex) {
            LOGGER.error("Feature Webhook processing failed", ex);
            respond(exchange, 500, "internal_error");
        }
    }

    private void dispatch(HttpExchange exchange, JsonNode payload, byte[] body,
                          DeliveryHeaders headers) throws IOException {
        String type = headers.eventType().toLowerCase(Locale.ROOT);
        FeatureJobRequest.Delivery delivery = new FeatureJobRequest.Delivery(
                headers.deliveryId(), headers.eventType(), FeatureNaming.sha256(body));
        if (type.contains("note")) {
            handleNote(exchange, payload, delivery);
        } else if (type.contains("issue")) {
            handleIssue(exchange, payload, delivery);
        } else if (type.contains("pull") || type.contains("merge")) {
            handlePullRequest(exchange, payload, delivery);
        } else {
            store.acceptDelivery(delivery, clock.instant(), "ignored event type");
            respond(exchange, 204, "");
        }
    }

    private void handleIssue(HttpExchange exchange, JsonNode payload,
                             FeatureJobRequest.Delivery delivery) throws IOException {
        FeatureWebhookParser.IssueEvent event = FeatureWebhookParser.issue(payload);
        if (!targetRepository(event.repository()) || event.iid() <= 0) {
            respond(exchange, 400, "repository_mismatch");
            return;
        }
        if (!event.isOpen() || !event.activatesLabel(config.triggerLabel())) {
            store.acceptDelivery(delivery, clock.instant(), "Issue did not activate trigger label");
            respond(exchange, 204, "");
            return;
        }
        FeatureJob.IssueReference issue = new FeatureJob.IssueReference(
                event.iid(), event.title(), issueUrl(event));
        FeatureJobRequest.Settings settings = new FeatureJobRequest.Settings(
                config.defaultWorkflowMode(), FeatureNaming.artifactRoot(
                config.componentRoot(), event.iid(), event.title()), clock.instant());
        FeatureJobRequest request = new FeatureJobRequest(delivery, event.repository(), issue,
                FeatureNaming.branch(event.iid(), event.title()), settings);
        AdmissionResult result = store.admit(request);
        respond(exchange, 202, result.status().name().toLowerCase(Locale.ROOT));
    }

    private void handleNote(HttpExchange exchange, JsonNode payload,
                            FeatureJobRequest.Delivery delivery) throws IOException {
        FeatureWebhookParser.NoteEvent event = FeatureWebhookParser.note(payload);
        if (!targetRepository(event.repository()) || event.issueIid() <= 0 || !event.isIssueNote()) {
            respond(exchange, 400, "repository_mismatch");
            return;
        }
        if (!store.acceptDelivery(delivery, clock.instant(), "Note Hook")) {
            respond(exchange, 202, "duplicate_delivery");
            return;
        }
        if (!config.approverLogins().contains(event.author())) {
            respond(exchange, 204, "");
            return;
        }
        FeatureCommand.Parsed parsed;
        try {
            parsed = FeatureCommand.Action.parse(event.body());
        } catch (IllegalArgumentException ex) {
            respond(exchange, 204, "");
            return;
        }
        FeatureCommand.Identity identity = new FeatureCommand.Identity(
                event.commentId(), event.repository(), event.issueIid());
        CommandResult result = store.applyCommand(new FeatureCommand(identity, event.author(),
                parsed.action(), parsed.reason(), clock.instant()));
        acknowledge(event.issueIid(), result);
        respond(exchange, 202, result.status().name().toLowerCase(Locale.ROOT));
    }

    private void handlePullRequest(HttpExchange exchange, JsonNode payload,
                                   FeatureJobRequest.Delivery delivery) throws IOException {
        FeatureWebhookParser.PullRequestEvent event = FeatureWebhookParser.pullRequest(payload);
        if (!targetRepository(event.repository()) || event.number() <= 0) {
            respond(exchange, 400, "repository_mismatch");
            return;
        }
        if (!store.acceptDelivery(delivery, clock.instant(), "pull request hook")) {
            respond(exchange, 202, "duplicate_delivery");
            return;
        }
        Optional<FeatureJob> job = store.findByPullRequest(event.repository(), event.number());
        if (job.isPresent() && (event.isMerged() || event.isClosed())) {
            transitionTerminal(job.orElseThrow(), event.isMerged() ? FeatureStage.MERGED : FeatureStage.CLOSED);
        }
        respond(exchange, 202, job.isPresent() ? "updated" : "unknown_pr");
    }

    private void transitionTerminal(FeatureJob job, FeatureStage terminal) {
        FeatureJob current = job;
        for (int attempt = 0; attempt < 2; attempt++) {
            if (current.progress().stage().isTerminal()) {
                return;
            }
            try {
                store.transition(current.identity().id(), current.record().version(),
                        FeatureJobMutation.transition(
                                current, terminal, "PR webhook reconciliation"));
                return;
            } catch (IllegalStateException ex) {
                Optional<FeatureJob> latest = store.findById(current.identity().id());
                if (latest.isEmpty()) {
                    throw ex;
                }
                current = latest.orElseThrow();
            }
        }
        if (!current.progress().stage().isTerminal()) {
            throw new IllegalStateException("Feature PR Webhook changed concurrently");
        }
    }

    private void acknowledge(long issueIid, CommandResult result) {
        if (result.status() == CommandResult.Status.ALREADY_SEEN) {
            return;
        }
        try {
            gitCode.commentIssue(issueIid, FeatureStatusComment.format(result));
        } catch (GitCodeApiException ex) {
            LOGGER.warn("Feature command was persisted but acknowledgement failed");
        }
    }

    private String issueUrl(FeatureWebhookParser.IssueEvent event) {
        if (event.url() != null && !event.url().isBlank()) {
            return event.url();
        }
        return "https://gitcode.com/" + event.repository() + "/issues/" + event.iid();
    }

    private boolean targetRepository(String repository) {
        return config.coordinates().targetRepository().equals(repository);
    }

    private static byte[] boundedBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_BODY_BYTES) {
                    return null;
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static DeliveryHeaders deliveryHeaders(HttpExchange exchange) {
        String delivery = header(exchange, "X-GitCode-Delivery");
        String event = header(exchange, "X-GitCode-Event");
        boolean valid = DELIVERY_PATTERN.matcher(delivery).matches()
                && EVENT_PATTERN.matcher(event).matches();
        return new DeliveryHeaders(delivery, event, valid);
    }

    private static String header(HttpExchange exchange, String name) {
        String value = exchange.getRequestHeaders().getFirst(name);
        return value == null ? "" : value;
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        int separator = contentType.indexOf(';');
        String mediaType = separator < 0 ? contentType : contentType.substring(0, separator);
        return "application/json".equalsIgnoreCase(mediaType.trim());
    }

    private static void respond(HttpExchange exchange, int status, String result) throws IOException {
        byte[] response = result.isBlank() ? new byte[0]
                : ("{\"result\":\"" + result + "\"}").getBytes(StandardCharsets.UTF_8);
        if (response.length > 0) {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        }
        exchange.sendResponseHeaders(status, status == 204 ? -1 : response.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(response);
        }
    }

    private record DeliveryHeaders(String deliveryId, String eventType, boolean valid) {
    }
}
