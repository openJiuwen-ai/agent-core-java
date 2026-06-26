/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.BridgeOutboundWrap.BridgeMailboxInjectMode;
import com.openjiuwen.agent_teams.interaction.BridgeProtocol;
import com.openjiuwen.agent_teams.prompts.BridgeRemoteBrief;
import com.openjiuwen.agent_teams.prompts.MemberSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code test_bridge_wrap_compose} in
 * {@code tests/unit_tests/agent_teams/agent/test_bridge_wrap_compose.py}.</p>
 */
class BridgeWrapComposeTest {

    @Test
    void passthroughMinimalHeaderCn() {
        String text = BridgeOutboundWrap.wrapOutboundToRemote(
                "alice",
                "Alice",
                TeamRole.TEAMMATE,
                "dev",
                "please review pr 123",
                false,
                null,
                BridgeMailboxInjectMode.PASSTHROUGH,
                "cn");

        assertEquals("[来自 Alice] please review pr 123", text);
    }

    @Test
    void passthroughMinimalHeaderEn() {
        String text = BridgeOutboundWrap.wrapOutboundToRemote(
                "alice",
                "Alice",
                TeamRole.TEAMMATE,
                "dev",
                "please review pr 123",
                false,
                null,
                BridgeMailboxInjectMode.PASSTHROUGH,
                "en");

        assertEquals("[from Alice] please review pr 123", text);
    }

    @Test
    void passthroughBroadcastMarker() {
        String text = BridgeOutboundWrap.wrapOutboundToRemote(
                "leader",
                null,
                null,
                null,
                "standup in 5",
                true,
                null,
                BridgeMailboxInjectMode.PASSTHROUGH,
                "en");

        assertTrue(text.startsWith("[from leader (broadcast)]"));
    }

    @Test
    void rephraseIncludesRoleAndPersonaCn() {
        String text = BridgeOutboundWrap.wrapOutboundToRemote(
                "alice",
                "Alice",
                TeamRole.TEAMMATE,
                "senior dev",
                "please refactor metrics module",
                false,
                "任务 #42 重构监控",
                BridgeMailboxInjectMode.REPHRASE,
                "cn");

        assertTrue(text.contains("Alice"));
        assertTrue(text.contains("teammate"));
        assertTrue(text.contains("senior dev"));
        assertTrue(text.contains("please refactor metrics module"));
        assertTrue(text.contains("相关任务：任务 #42 重构监控"));
    }

    @Test
    void rephraseWithoutTaskHint() {
        String text = BridgeOutboundWrap.wrapOutboundToRemote(
                "alice",
                "Alice",
                TeamRole.TEAMMATE,
                "dev",
                "hi",
                false,
                null,
                BridgeMailboxInjectMode.REPHRASE,
                "en");

        assertFalse(text.contains("Re:"));
    }

    @Test
    void rephraseBroadcastKindLabel() {
        String text = BridgeOutboundWrap.wrapOutboundToRemote(
                "leader",
                "Leader",
                TeamRole.LEADER,
                "planner",
                "standup",
                true,
                null,
                BridgeMailboxInjectMode.REPHRASE,
                "en");

        assertTrue(text.contains("broadcast"));
    }

    @Test
    void composeContainsOriginalAndRemoteCn() {
        String text = BridgeInboundCompose.composeBridgeInbound(
                "alice",
                "please review pr 123",
                "diff looks clean. lgtm.",
                "cn",
                null);

        assertTrue(text.contains("please review pr 123"));
        assertTrue(text.contains("diff looks clean. lgtm."));
        assertTrue(text.contains("alice"));
        assertTrue(text.contains("原样"));
    }

    @Test
    void composeForbidsRelayViaToolCn() {
        String text = BridgeInboundCompose.composeBridgeInbound("alice", "x", "y", "cn", null);

        assertTrue(text.contains("已自动转发"));
        assertTrue(text.contains("无需再调用 send_message"));
    }

    @Test
    void composeEnTemplateIncludesSchedulingContract() {
        String text = BridgeInboundCompose.composeBridgeInbound(
                "leader",
                "status update?",
                "working on task 42",
                "en",
                null);

        assertTrue(text.contains("schedule only"));
        assertTrue(text.contains("Do NOT rewrite"));
        assertTrue(text.contains("already been forwarded"));
    }

    @Test
    void composePropagatesSentinelWhenNoAdapter() {
        String text = BridgeInboundCompose.composeBridgeInbound(
                "alice",
                "hi",
                BridgeProtocol.REMOTE_UNAVAILABLE_SENTINEL,
                "en",
                null);

        assertTrue(text.contains(BridgeProtocol.REMOTE_UNAVAILABLE_SENTINEL));
    }

    @Test
    void composeIncludesTimeInfoWhenProvided() {
        String text = BridgeInboundCompose.composeBridgeInbound(
                "alice",
                "ping",
                "pong",
                "en",
                "2026-05-27 14:30:05 +08:00 (3m ago)");
        assertTrue(text.contains("2026-05-27 14:30:05 +08:00 (3m ago)"));

        String plain = BridgeInboundCompose.composeBridgeInbound("alice", "ping", "pong", "en", null);
        assertFalse(plain.contains("·"));
    }

    @Test
    void buildBridgePersonaCnContainsIdentityAndContract() {
        String text = BridgeRemoteBrief.buildBridgePersona("codex", "senior python reviewer");

        assertTrue(text.contains("codex"));
        assertTrue(text.contains("senior python reviewer"));
        assertTrue(text.contains("实际执行者"));
        assertTrue(text.contains("没有工具"));
    }

    @Test
    void buildBridgePersonaEnPassthroughContract() {
        String text = BridgeRemoteBrief.buildBridgePersona("claudecode", "pair-programmer", "en");

        assertTrue(text.contains("claudecode"));
        assertTrue(text.contains("VERBATIM"));
        assertTrue(text.contains("do NOT have tools"));
    }

    @Test
    void buildTeamOverviewListsMembersCn() {
        String text = BridgeRemoteBrief.buildTeamOverview(
                "demo",
                List.of(
                        new MemberSummary("leader", com.openjiuwen.agent_teams.schema.TeamRole.LEADER, "planner"),
                        new MemberSummary("alice", com.openjiuwen.agent_teams.schema.TeamRole.TEAMMATE, "dev"),
                        new MemberSummary("codex", com.openjiuwen.agent_teams.schema.TeamRole.BRIDGE_AGENT, "reviewer")));

        assertTrue(text.contains("团队 demo"));
        assertTrue(text.contains("leader (leader): planner"));
        assertTrue(text.contains("alice (teammate): dev"));
        assertTrue(text.contains("codex (bridge_agent): reviewer"));
    }

    @Test
    void buildTeamOverviewEmptyMembers() {
        String text = BridgeRemoteBrief.buildTeamOverview("empty", List.of(), "en");

        assertTrue(text.contains("Team empty roster:"));
        List<String> bulletLines = text.lines().filter(line -> line.startsWith("- ")).toList();
        assertEquals(List.of(), bulletLines);
    }

    @Test
    void buildTeamOverviewHandlesNoPersona() {
        String text = BridgeRemoteBrief.buildTeamOverview(
                "demo",
                List.of(new MemberSummary("x", com.openjiuwen.agent_teams.schema.TeamRole.TEAMMATE)),
                "en");

        assertTrue(text.contains("x (teammate): (no persona)"));
    }
}
