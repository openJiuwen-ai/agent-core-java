/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Parses a minimal, validated subset of GitCode webhook payloads.
 *
 * @since 0.1.12
 */
public final class GitCodeWebhookParser {
    private static final ObjectReader JSON_READER = new ObjectMapper().reader();

    private GitCodeWebhookParser() {
    }

    /**
     * Parse one JSON object from the verified request body.
     *
     * @param body verified webhook bytes
     * @return parsed JSON object
     * @throws IOException when the payload is invalid or is not a JSON object
     */
    public static JsonNode parse(byte[] body) throws IOException {
        if (body == null) {
            throw new IOException("GitCode webhook body must not be null");
        }
        JsonNode root = JSON_READER.readTree(body);
        if (root == null || !root.isObject()) {
            throw new IOException("GitCode webhook payload must be a JSON object");
        }
        return root;
    }

    /**
     * Normalize an Issue event.
     *
     * @param root parsed webhook object
     * @return normalized Issue event
     */
    public static GitCodeIssueEvent issue(JsonNode root) {
        JsonNode requiredRoot = Objects.requireNonNull(root, "root must not be null");
        JsonNode attributes = requiredRoot.path("object_attributes");
        return new GitCodeIssueEvent(
                repository(requiredRoot),
                attributes.path("iid").asLong(-1),
                attributes.path("title").asText(""),
                attributes.path("description").asText(""),
                attributes.path("state").asText(""),
                attributes.path("action").asText(""),
                attributes.path("url").asText(""),
                addedLabels(requiredRoot));
    }

    /**
     * Normalize a pull-request event.
     *
     * @param root parsed webhook object
     * @return normalized pull-request event
     */
    public static GitCodePullRequestEvent pullRequest(JsonNode root) {
        JsonNode requiredRoot = Objects.requireNonNull(root, "root must not be null");
        JsonNode attributes = requiredRoot.path("object_attributes");
        long number = attributes.path("iid").asLong(attributes.path("number").asLong(-1));
        return new GitCodePullRequestEvent(repository(requiredRoot), number,
                attributes.path("state").asText(""), attributes.path("action").asText(""));
    }

    private static String repository(JsonNode root) {
        for (JsonNode node : List.of(
                root.path("project").path("path_with_namespace"),
                root.path("repository").path("full_name"),
                root.path("object_attributes").path("target").path("path_with_namespace"))) {
            if (node.isTextual() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        return "";
    }

    private static Set<String> addedLabels(JsonNode root) {
        JsonNode changes = root.path("changes").path("labels");
        if (changes.isMissingNode() || changes.isNull()) {
            return Set.of();
        }
        Optional<JsonNode> beforeNode = first(changes, "previous", "before", "old");
        Optional<JsonNode> afterNode = first(changes, "current", "after", "new");
        if (beforeNode.isEmpty()) {
            return Set.of();
        }
        JsonNode effectiveAfter = afterNode.orElse(root.path("labels"));
        Set<String> before = labels(beforeNode.get());
        Set<String> after = labels(effectiveAfter);
        Set<String> added = new LinkedHashSet<>(after);
        added.removeAll(before);
        return Set.copyOf(added);
    }

    private static Optional<JsonNode> first(JsonNode object, String... fields) {
        for (String field : fields) {
            JsonNode value = object.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private static Set<String> labels(JsonNode node) {
        Set<String> labels = new LinkedHashSet<>();
        if (node.isArray()) {
            node.forEach(item -> addLabel(labels, item));
        } else {
            addLabel(labels, node);
        }
        return labels;
    }

    private static void addLabel(Set<String> labels, JsonNode node) {
        if (node.isTextual()) {
            labels.add(node.asText());
            return;
        }
        for (String field : List.of("name", "title")) {
            JsonNode value = node.path(field);
            if (value.isTextual() && !value.asText().isBlank()) {
                labels.add(value.asText());
                return;
            }
        }
    }
}
