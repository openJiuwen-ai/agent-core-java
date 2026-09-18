/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

import com.openjiuwen.agent_teams.constants.TeamConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Top-layer parser for user-facing interact input.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.interaction.router} in
 * {@code openjiuwen/agent_teams/interaction/router.py}.</p>
 */
public final class InteractionRouter {

    public static final Set<String> BROADCAST_TARGETS = Set.of("all", "*");

    private static final Pattern MENTION_PATTERN = Pattern.compile("^@(\\S+)\\s+([\\s\\S]+)$");
    private static final String GOD_VIEW_PREFIX = "# ";
    private static final Pattern HUMAN_AGENT_PREFIX_PATTERN =
            Pattern.compile("^\\$([^\\s@]+)(?:\\s+|(?=@))([\\s\\S]*)$");
    private static final Pattern RECIPIENT_PATTERN = Pattern.compile("^@(\\S+)\\s+");

    private InteractionRouter() {
    }

    /**
     * Parse a single {@code @target body} mention prefix.
     *
     * <p>Mirrors Python's {@code parse_mention} in
     * {@code openjiuwen/agent_teams/interaction/router.py}.</p>
     */
    public static Optional<Mention> parseMention(String content) {
        if (content == null || content.isEmpty()) {
            return Optional.empty();
        }
        Matcher matcher = MENTION_PATTERN.matcher(content);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new Mention(matcher.group(1), matcher.group(2)));
    }

    /**
     * Check whether a name collides with runtime-reserved members.
     *
     * <p>Mirrors Python's {@code is_reserved_name} in
     * {@code openjiuwen/agent_teams/interaction/router.py}.</p>
     */
    public static boolean isReservedName(String name) {
        return TeamConstants.RESERVED_MEMBER_NAMES.contains(name);
    }

    /**
     * Translate a free-form {@code interact(str, ...)} body into typed payloads.
     *
     * <p>Mirrors Python's {@code parse_interact_str} in
     * {@code openjiuwen/agent_teams/interaction/router.py}.</p>
     */
    public static List<InteractPayload> parseInteractStr(String body) {
        if (body == null || body.strip().isEmpty()) {
            return List.of();
        }

        String rest = body;
        String sender = TeamConstants.USER_PSEUDO_MEMBER_NAME;
        boolean humanAgent = false;

        if (rest.startsWith(GOD_VIEW_PREFIX)) {
            rest = rest.substring(GOD_VIEW_PREFIX.length()).stripLeading();
        } else {
            Matcher channelMatcher = HUMAN_AGENT_PREFIX_PATTERN.matcher(rest);
            if (channelMatcher.matches()) {
                sender = channelMatcher.group(1);
                rest = channelMatcher.group(2).stripLeading();
                humanAgent = true;
            }
        }

        List<String> recipients = new ArrayList<>();
        while (true) {
            Matcher recipientMatcher = RECIPIENT_PATTERN.matcher(rest);
            if (!recipientMatcher.find()) {
                break;
            }
            recipients.add(recipientMatcher.group(1));
            rest = rest.substring(recipientMatcher.end());
        }

        String finalBody = rest;
        if (recipients.isEmpty()) {
            if (humanAgent) {
                return List.of(new HumanAgentMessage(finalBody, sender));
            }
            return List.of(new GodViewMessage(finalBody));
        }

        boolean hasBroadcast = recipients.stream().anyMatch(BROADCAST_TARGETS::contains);
        if (hasBroadcast) {
            if (humanAgent) {
                return List.of(new HumanAgentMessage(finalBody, sender, "*"));
            }
            return List.of(new OperatorMessage(finalBody));
        }

        String payloadBody = finalBody;
        String payloadSender = sender;
        if (humanAgent) {
            return recipients.stream()
                    .map(name -> new HumanAgentMessage(payloadBody, payloadSender, name))
                    .collect(Collectors.toList());
        }
        return recipients.stream()
                .map(name -> new OperatorMessage(payloadBody, name))
                .collect(Collectors.toList());
    }

    /**
     * Strict-match named recipients against the live roster.
     *
     * <p>Mirrors Python's {@code resolve_targets} in
     * {@code openjiuwen/agent_teams/interaction/router.py}.</p>
     */
    public static CompletionStage<List<InteractPayload>> resolveTargets(
            List<InteractPayload> payloads,
            MemberExistsCheck memberExists
    ) {
        Objects.requireNonNull(payloads, "payloads");
        Objects.requireNonNull(memberExists, "memberExists");

        List<InteractPayload> kept = new ArrayList<>();
        List<InteractPayload> unknown = new ArrayList<>();
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (InteractPayload payload : payloads) {
            chain = chain.thenCompose(ignored -> {
                String name = namedTarget(payload);
                if (name == null) {
                    kept.add(payload);
                    return CompletableFuture.completedFuture(null);
                }
                return memberExists.exists(name).thenAccept(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        kept.add(payload);
                    } else {
                        unknown.add(payload);
                    }
                });
            });
        }
        return chain.thenApply(ignored -> {
            if (unknown.isEmpty()) {
                return payloads;
            }
            List<InteractPayload> resolved = new ArrayList<>(kept);
            resolved.add(foldUnknownMentions(unknown));
            return resolved;
        });
    }

    /**
     * Validate a direct target and post a point-to-point bus message.
     *
     * <p>Mirrors Python's {@code deliver_direct} in
     * {@code openjiuwen/agent_teams/interaction/router.py}.</p>
     */
    public static CompletionStage<DeliverResult> deliverDirect(
            String body,
            String sender,
            String target,
            MessageManagerView messageManager,
            MemberExistsCheck memberExists
    ) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(messageManager, "messageManager");
        Objects.requireNonNull(memberExists, "memberExists");

        return memberExists.exists(target).thenCompose(exists -> {
            if (!Boolean.TRUE.equals(exists)) {
                return CompletableFuture.completedFuture(DeliverResult.failure("unknown_member:" + target));
            }
            return messageManager.sendMessage(body, target, sender)
                    .thenApply(messageId -> messageId == null
                            ? DeliverResult.failure("send_failed:" + target)
                            : DeliverResult.success(messageId));
        });
    }

    private static String namedTarget(InteractPayload payload) {
        String target = null;
        if (payload instanceof OperatorMessage message) {
            target = message.target();
        } else if (payload instanceof HumanAgentMessage message) {
            target = message.target();
        }
        if (target == null || BROADCAST_TARGETS.contains(target)) {
            return null;
        }
        return target;
    }

    private static InteractPayload foldUnknownMentions(List<InteractPayload> unknown) {
        InteractPayload sample = unknown.get(0);
        String mentions = unknown.stream()
                .map(InteractionRouter::namedTarget)
                .map(name -> "@" + name)
                .collect(Collectors.joining(" "));
        String body = payloadBody(sample);
        String generalBody = body == null || body.isEmpty() ? mentions : mentions + " " + body;
        if (sample instanceof HumanAgentMessage message) {
            return new HumanAgentMessage(generalBody, message.sender());
        }
        return new GodViewMessage(generalBody);
    }

    private static String payloadBody(InteractPayload payload) {
        if (payload instanceof OperatorMessage message) {
            return message.body();
        }
        if (payload instanceof HumanAgentMessage message) {
            return message.body();
        }
        if (payload instanceof GodViewMessage message) {
            return message.body();
        }
        return "";
    }

    /**
     * Parsed mention tuple.
     *
     * <p>Mirrors Python's tuple returned by {@code parse_mention} in
     * {@code openjiuwen/agent_teams/interaction/router.py}.</p>
     */
    public record Mention(String target, String body) {
    }

    /**
     * Async predicate for roster membership.
     *
     * <p>Mirrors Python's {@code MemberExistsCheck} in
     * {@code openjiuwen/agent_teams/interaction/router.py}.</p>
     */
    @FunctionalInterface
    public interface MemberExistsCheck {
        CompletionStage<Boolean> exists(String name);
    }

    /**
     * Message bus surface used by direct delivery.
     *
     * <p>Mirrors Python's {@code TeamMessageManager} calls in
     * {@code openjiuwen/agent_teams/interaction/router.py}.</p>
     */
    @FunctionalInterface
    public interface MessageManagerView {
        CompletionStage<String> sendMessage(String content, String toMemberName, String fromMemberName);
    }
}
