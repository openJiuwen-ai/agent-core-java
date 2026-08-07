/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.webhook;

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
 * Parses the minimal GitCode Issue, Note, and pull-request Webhook fields.
 *
 * @since 0.1.12
 */
public final class FeatureWebhookParser {
    private static final ObjectReader JSON_READER = new ObjectMapper().reader();

    private FeatureWebhookParser() {
    }

    /** Parse a verified JSON object. */
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

    /** Normalize an Issue hook. */
    public static IssueEvent issue(JsonNode root) {
        JsonNode required = Objects.requireNonNull(root, "root must not be null");
        JsonNode attributes = required.path("object_attributes");
        long iid = attributes.path("iid").asLong(attributes.path("number").asLong(-1L));
        Set<String> currentLabels = labels(firstPresent(
                attributes.path("labels"), required.path("labels")));
        IssueIdentity identity = new IssueIdentity(
                repository(required), iid, text(attributes, "title"));
        IssueState state = new IssueState(text(attributes, "state"),
                text(attributes, "action"), currentLabels, addedLabels(required));
        return new IssueEvent(identity, state, text(attributes, "url", "html_url"));
    }

    /** Normalize an Issue Note hook. */
    public static NoteEvent note(JsonNode root) {
        JsonNode required = Objects.requireNonNull(root, "root must not be null");
        JsonNode attributes = required.path("object_attributes");
        JsonNode issue = required.path("issue");
        long iid = issue.path("iid").asLong(issue.path("number").asLong(
                attributes.path("noteable_iid").asLong(-1L)));
        String author = text(required.path("user"), "username", "login", "name");
        String noteableType = text(attributes, "noteable_type", "target_type");
        NoteIdentity identity = new NoteIdentity(
                repository(required), iid, identifier(attributes.path("id")));
        NoteContent content = new NoteContent(
                author, text(attributes, "note", "body"), noteableType);
        return new NoteEvent(identity, content);
    }

    /** Normalize a pull-request hook. */
    public static PullRequestEvent pullRequest(JsonNode root) {
        JsonNode required = Objects.requireNonNull(root, "root must not be null");
        JsonNode attributes = required.path("object_attributes");
        long number = attributes.path("iid").asLong(attributes.path("number").asLong(-1L));
        String action = text(attributes, "action");
        String state = text(attributes, "state");
        if ("merge".equalsIgnoreCase(action) || "merged".equalsIgnoreCase(action)) {
            state = "merged";
        }
        return new PullRequestEvent(repository(required), number, state);
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
        Optional<JsonNode> before = first(changes, "previous", "before", "old");
        Optional<JsonNode> after = first(changes, "current", "after", "new");
        if (before.isEmpty()) {
            return Set.of();
        }
        Set<String> added = new LinkedHashSet<>(labels(after.orElse(root.path("labels"))));
        added.removeAll(labels(before.orElseThrow()));
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

    private static JsonNode firstPresent(JsonNode first, JsonNode second) {
        return !first.isMissingNode() && !first.isNull() ? first : second;
    }

    private static Set<String> labels(JsonNode node) {
        Set<String> names = new LinkedHashSet<>();
        if (node.isArray()) {
            node.forEach(value -> addLabel(names, value));
        } else {
            addLabel(names, node);
        }
        return Set.copyOf(names);
    }

    private static void addLabel(Set<String> labels, JsonNode node) {
        if (node.isTextual() && !node.asText().isBlank()) {
            labels.add(node.asText());
            return;
        }
        String name = text(node, "name", "title");
        if (!name.isBlank()) {
            labels.add(name);
        }
    }

    private static String identifier(JsonNode node) {
        return node.isValueNode() ? node.asText("") : "";
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

    /** Parsed Issue event grouped below the method parameter limit. */
    public record IssueEvent(IssueIdentity identity, IssueState state, String url) {
        /** Validate grouped Issue fields. */
        public IssueEvent {
            identity = Objects.requireNonNull(identity, "identity must not be null");
            state = Objects.requireNonNull(state, "state must not be null");
            url = url == null ? "" : url;
        }

        /** @return repository owner/name */
        public String repository() {
            return identity.repository();
        }

        /** @return repository-scoped Issue IID */
        public long iid() {
            return identity.iid();
        }

        /** @return Issue title */
        public String title() {
            return identity.title();
        }

        /** @return whether GitCode reports the Issue open */
        public boolean isOpen() {
            return "open".equalsIgnoreCase(state.state())
                    || "opened".equalsIgnoreCase(state.state())
                    || "open".equalsIgnoreCase(state.action())
                    || "reopen".equalsIgnoreCase(state.action());
        }

        /** Report whether this event explicitly activates an exact label. */
        public boolean activatesLabel(String label) {
            boolean creation = "open".equalsIgnoreCase(state.action())
                    || "create".equalsIgnoreCase(state.action())
                    || "reopen".equalsIgnoreCase(state.action());
            return state.addedLabels().contains(label)
                    || creation && state.labels().contains(label);
        }
    }

    /** Stable Issue identity parsed from a Hook. */
    public record IssueIdentity(String repository, long iid, String title) {
    }

    /** Mutable remote Issue state represented by one Hook. */
    public record IssueState(String state, String action, Set<String> labels,
                             Set<String> addedLabels) {
        /** Freeze label sets. */
        public IssueState {
            state = state == null ? "" : state;
            action = action == null ? "" : action;
            labels = labels == null ? Set.of() : Set.copyOf(labels);
            addedLabels = addedLabels == null ? Set.of() : Set.copyOf(addedLabels);
        }
    }

    /** Parsed Issue Note event grouped below the method parameter limit. */
    public record NoteEvent(NoteIdentity identity, NoteContent content) {
        /** Validate grouped Note fields. */
        public NoteEvent {
            identity = Objects.requireNonNull(identity, "identity must not be null");
            content = Objects.requireNonNull(content, "content must not be null");
        }

        /** @return repository owner/name */
        public String repository() {
            return identity.repository();
        }

        /** @return repository-scoped Issue IID */
        public long issueIid() {
            return identity.issueIid();
        }

        /** @return stable GitCode comment ID */
        public String commentId() {
            return identity.commentId();
        }

        /** @return authenticated GitCode author login */
        public String author() {
            return content.author();
        }

        /** @return untrusted comment body */
        public String body() {
            return content.body();
        }

        /** @return whether the note targets an Issue */
        public boolean isIssueNote() {
            return "issue".equalsIgnoreCase(content.noteableType());
        }
    }

    /** Stable Note and Issue identity parsed from a Hook. */
    public record NoteIdentity(String repository, long issueIid, String commentId) {
    }

    /** Note author, body, and target type parsed from a Hook. */
    public record NoteContent(String author, String body, String noteableType) {
    }

    /** Parsed pull-request event. */
    public record PullRequestEvent(String repository, long number, String state) {
        /** @return whether the event reports merged */
        public boolean isMerged() {
            return "merged".equalsIgnoreCase(state);
        }

        /** @return whether the event reports closed without merge */
        public boolean isClosed() {
            return "closed".equalsIgnoreCase(state);
        }
    }
}
