/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import examples.gitcode_issue_evolver.job.EnqueueResult;
import examples.gitcode_issue_evolver.job.EvolutionJob;
import examples.gitcode_issue_evolver.job.EvolutionJobState;
import examples.gitcode_issue_evolver.job.EvolutionJobStore;
import examples.gitcode_issue_evolver.job.IssueJobRequest;
import examples.gitcode_issue_evolver.profile.RepositoryProfile;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Authenticated, size-bounded, enqueue-only GitCode webhook endpoint.
 *
 * @since 0.1.12
 */
public final class GitCodeWebhookHandler implements HttpHandler {
    /** Maximum accepted uncompressed webhook request size. */
    public static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final Pattern DELIVERY_ID_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{1,200}");
    private static final Pattern EVENT_TYPE_PATTERN = Pattern.compile("[A-Za-z0-9 _.:-]{1,100}");
    private static final Logger LOGGER = LoggerFactory.getLogger(GitCodeWebhookHandler.class);
    private final String secret;
    private final EvolutionJobStore store;
    private final RepositoryProfile profile;
    private final WebhookAdmission admission;
    private final boolean requireCiSuccess;

    /**
     * Create a webhook handler for one fixed repository profile.
     *
     * @param secret HMAC webhook secret
     * @param store durable delivery and job store
     * @param profile fixed repository policy
     */
    public GitCodeWebhookHandler(String secret, EvolutionJobStore store, RepositoryProfile profile) {
        this(secret, store, profile, WebhookAdmission.disabled(), false);
    }

    /**
     * Create a webhook handler with an explicit demo admission policy.
     *
     * @param secret HMAC webhook secret
     * @param store durable delivery and job store
     * @param profile fixed repository policy
     * @param admission fail-closed event admission policy
     */
    public GitCodeWebhookHandler(String secret, EvolutionJobStore store, RepositoryProfile profile,
                                 WebhookAdmission admission) {
        this(secret, store, profile, admission, false);
    }

    /** Create a handler that defers merged completion to CI-aware polling. */
    public GitCodeWebhookHandler(String secret, EvolutionJobStore store, RepositoryProfile profile,
                                 WebhookAdmission admission, boolean requireCiSuccess) {
        this.secret = secret == null ? "" : secret;
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.profile = Objects.requireNonNull(profile, "profile must not be null");
        this.admission = Objects.requireNonNull(admission, "admission must not be null");
        this.requireCiSuccess = requireCiSuccess;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "method_not_allowed");
            return;
        }
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (!isJson(contentType)) {
            respond(exchange, 415, "unsupported_media_type");
            return;
        }
        byte[] body;
        try {
            body = readBody(exchange);
        } catch (PayloadTooLargeException ex) {
            respond(exchange, 413, "payload_too_large");
            return;
        }
        if (!GitCodeWebhookVerifier.verify(body,
                exchange.getRequestHeaders().getFirst("X-GitCode-Signature-256"), secret)) {
            respond(exchange, 401, "invalid_signature");
            return;
        }
        String deliveryId = header(exchange, "X-GitCode-Delivery");
        String eventType = header(exchange, "X-GitCode-Event");
        if (!DELIVERY_ID_PATTERN.matcher(deliveryId).matches()
                || !EVENT_TYPE_PATTERN.matcher(eventType).matches()) {
            respond(exchange, 400, "missing_delivery_headers");
            return;
        }
        JsonNode payload;
        try {
            payload = GitCodeWebhookParser.parse(body);
        } catch (IOException ex) {
            respond(exchange, 400, "invalid_json");
            return;
        }
        try {
            dispatch(exchange, payload, body, deliveryId, eventType);
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("Rejected invalid GitCode webhook payload", ex);
            respond(exchange, 400, "invalid_payload");
        } catch (IllegalStateException ex) {
            LOGGER.error("GitCode webhook processing failed", ex);
            respond(exchange, 500, "internal_error");
        }
    }

    private void dispatch(HttpExchange exchange, JsonNode payload, byte[] body,
                          String deliveryId, String eventType) throws IOException {
        String hash = GitCodeWebhookVerifier.sha256(body);
        String normalizedEvent = eventType.toLowerCase(Locale.ROOT);
        if (normalizedEvent.contains("issue")) {
            handleIssue(exchange, payload, deliveryId, eventType, hash);
        } else if (normalizedEvent.contains("pull") || normalizedEvent.contains("merge")) {
            handlePullRequest(exchange, payload, deliveryId, eventType, hash);
        } else {
            recordIgnoredDelivery(deliveryId, eventType, hash);
            respond(exchange, 204, "");
        }
    }

    private void handleIssue(HttpExchange exchange, JsonNode payload, String deliveryId,
                             String eventType, String hash) throws IOException {
        GitCodeIssueEvent event = GitCodeWebhookParser.issue(payload);
        if (!profile.repository().equals(event.repository()) || !admission.allowsRepository(event.repository())
                || event.issueIid() <= 0) {
            respond(exchange, 400, "repository_mismatch");
            return;
        }
        if (!admission.allowsIssue(event)) {
            recordIgnoredDelivery(deliveryId, eventType, hash);
            respond(exchange, 204, "");
            return;
        }
        EnqueueResult result = store.enqueueIssue(new IssueJobRequest(
                deliveryId, eventType, hash, event.repository(), event.issueIid(), event.title(),
                event.url(), profile.branchName(event.issueIid(), event.title())));
        respond(exchange, 202, result.status().name().toLowerCase(Locale.ROOT));
    }

    private void handlePullRequest(HttpExchange exchange, JsonNode payload, String deliveryId,
                                   String eventType, String hash) throws IOException {
        GitCodePullRequestEvent event = GitCodeWebhookParser.pullRequest(payload);
        if (!profile.repository().equals(event.repository()) || !admission.allowsPullRequest(event)
                || event.number() <= 0) {
            respond(exchange, 400, "repository_mismatch");
            return;
        }
        if (!store.acceptDelivery(deliveryId, eventType, hash)) {
            respond(exchange, 202, "duplicate_delivery");
            return;
        }
        Optional<EvolutionJobState> terminalState = event.terminalState();
        Optional<EvolutionJob> job = store.findByPullRequest(event.repository(), event.number());
        if (terminalState.isPresent() && job.isPresent()
                && shouldApplyTerminal(terminalState.get())) {
            transitionTerminal(job.get(), terminalState.get());
        }
        respond(exchange, 202, job.isPresent() ? "updated" : "unknown_pr");
    }

    private boolean shouldApplyTerminal(EvolutionJobState state) {
        return state != EvolutionJobState.MERGED || !requireCiSuccess;
    }

    private void transitionTerminal(EvolutionJob job, EvolutionJobState terminalState) {
        try {
            EvolutionJob updated = store.transition(
                    job.id(), job.version(), terminalState, "PR webhook");
            LOGGER.debug("Updated evolution job {} from PR webhook", updated.id());
        } catch (IllegalStateException ex) {
            Optional<EvolutionJob> latest = store.findById(job.id());
            if (latest.isPresent() && !latest.get().state().isActive()) {
                LOGGER.debug("PR webhook observed a concurrent terminal transition for job {}", job.id());
                return;
            }
            throw ex;
        }
    }

    private void recordIgnoredDelivery(String deliveryId, String eventType, String hash) {
        if (!store.acceptDelivery(deliveryId, eventType, hash)) {
            LOGGER.debug("Ignored duplicate GitCode delivery {}", deliveryId);
        }
    }

    private static byte[] readBody(HttpExchange exchange) throws IOException, PayloadTooLargeException {
        try (InputStream input = exchange.getRequestBody();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_BODY_BYTES) {
                    throw new PayloadTooLargeException();
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
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

    private static final class PayloadTooLargeException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
