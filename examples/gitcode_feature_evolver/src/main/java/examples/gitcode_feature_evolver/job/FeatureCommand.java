/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.job;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Authenticated feature-control comment presented to the durable store.
 *
 * @param identity comment and Issue identity
 * @param actor authenticated GitCode login
 * @param action parsed command action
 * @param reason bounded human reason
 * @param observedAt observation time
 * @since 0.1.12
 */
public record FeatureCommand(Identity identity, String actor, Action action, String reason,
                             Instant observedAt) {
    /** Validate the command. */
    public FeatureCommand {
        identity = Objects.requireNonNull(identity, "identity must not be null");
        actor = requireText(actor, "actor");
        action = Objects.requireNonNull(action, "action must not be null");
        reason = reason == null ? "" : reason.strip();
        observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
    }

    /** Comment and Issue identity. */
    public record Identity(String commentId, String repository, long issueIid) {
        /** Validate identity values. */
        public Identity {
            commentId = requireText(commentId, "commentId");
            repository = requireText(repository, "repository");
            if (issueIid <= 0) {
                throw new IllegalArgumentException("issueIid must be positive");
            }
        }
    }

    /** Supported authenticated controller actions. */
    public enum Action {
        APPROVE_R1,
        APPROVE_R2,
        APPROVE_R3,
        REJECT_R1,
        REJECT_R2,
        REJECT_R3,
        PAUSE,
        RESUME,
        CANCEL,
        STATUS;

        /**
         * Parse an exact command body.
         *
         * @param body untrusted comment body
         * @return parsed action and reason
         */
        public static Parsed parse(String body) {
            String normalized = body == null ? "" : body.strip();
            String lower = normalized.toLowerCase(Locale.ROOT);
            for (CommandPrefix prefix : CommandPrefix.values()) {
                if (lower.equals(prefix.text) || lower.startsWith(prefix.text + " ")) {
                    return new Parsed(prefix.action, normalized.substring(prefix.text.length()).strip());
                }
            }
            throw new IllegalArgumentException("unsupported feature command");
        }
    }

    /** Parsed command action and optional reason. */
    public record Parsed(Action action, String reason) {
        /** Normalize a nullable reason. */
        public Parsed {
            action = Objects.requireNonNull(action, "action must not be null");
            reason = reason == null ? "" : reason;
        }
    }

    private enum CommandPrefix {
        APPROVE_R1("/feature approve r1", Action.APPROVE_R1),
        APPROVE_R2("/feature approve r2", Action.APPROVE_R2),
        APPROVE_R3("/feature approve r3", Action.APPROVE_R3),
        REJECT_R1("/feature reject r1", Action.REJECT_R1),
        REJECT_R2("/feature reject r2", Action.REJECT_R2),
        REJECT_R3("/feature reject r3", Action.REJECT_R3),
        PAUSE("/feature pause", Action.PAUSE),
        RESUME("/feature resume", Action.RESUME),
        CANCEL("/feature cancel", Action.CANCEL),
        STATUS("/feature status", Action.STATUS);

        private final String text;
        private final Action action;

        CommandPrefix(String text, Action action) {
            this.text = text;
            this.action = action;
        }
    }

    private static String requireText(String text, String name) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return text;
    }
}
