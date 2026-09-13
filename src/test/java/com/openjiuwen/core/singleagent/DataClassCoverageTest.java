// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.

package com.openjiuwen.core.singleagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.common.schema.Part;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.singleagent.rail.*;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.schema.AgentResult;
import com.openjiuwen.core.singleagent.schema.Artifact;
import com.openjiuwen.core.singleagent.skills.GitHubTree;
import com.openjiuwen.core.singleagent.skills.Skill;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Supplementary tests for Lombok-generated code in data classes.
 * Covers toString, equals, hashCode, getters/setters for coverage.
 */
class DataClassCoverageTest {
    // ========== AgentCallbackContext getters/setters ==========
    @Test
    void testAgentCallbackContextGettersSetters() {
        AgentCallbackContext ctx = AgentCallbackContext.builder().build();
        ctx.setAgent("agentObj");
        ctx.setEvent(AgentCallbackEvent.BEFORE_INVOKE);
        ctx.setRetryAttempt(3);
        ctx.setException(new RuntimeException("err"));
        ctx.setRetryRequest(RetryRequest.builder().delaySeconds(1.5).build());

        assertThat(ctx.getAgent()).isEqualTo("agentObj");
        assertThat(ctx.getEvent()).isEqualTo(AgentCallbackEvent.BEFORE_INVOKE);
        assertThat(ctx.getRetryAttempt()).isEqualTo(3);
        assertThat(ctx.getException()).isNotNull();
        assertThat(ctx.getRetryRequest().getDelaySeconds()).isEqualTo(1.5);
    }

    @Test
    void testAgentCallbackContextToString() {
        AgentCallbackContext ctx = AgentCallbackContext.builder().build();
        assertThat(ctx.toString()).contains("AgentCallbackContext");
    }

    @Test
    void testAgentCallbackContextEqualsAndHashCode() {
        AgentCallbackContext ctx1 = AgentCallbackContext.builder().retryAttempt(1).build();
        AgentCallbackContext ctx2 = AgentCallbackContext.builder().retryAttempt(1).build();

        assertThat(ctx1).isEqualTo(ctx2);
        assertThat(ctx1.hashCode()).isEqualTo(ctx2.hashCode());
    }

    @Test
    void testAgentCallbackContextNotEqual() {
        AgentCallbackContext ctx1 = AgentCallbackContext.builder().retryAttempt(1).build();
        AgentCallbackContext ctx2 = AgentCallbackContext.builder().retryAttempt(2).build();
        assertThat(ctx1).isNotEqualTo(ctx2);
    }

    // ========== RetryRequest ==========

    @Test
    void testRetryRequestToString() {
        RetryRequest rr = RetryRequest.builder().delaySeconds(5.0).build();
        assertThat(rr.toString()).contains("RetryRequest");
    }

    @Test
    void testRetryRequestEqualsHashCode() {
        RetryRequest r1 = RetryRequest.builder().delaySeconds(2.0).build();
        RetryRequest r2 = RetryRequest.builder().delaySeconds(2.0).build();

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    void testRetryRequestSetDelay() {
        RetryRequest rr = RetryRequest.builder().build();
        rr.setDelaySeconds(3.0);
        assertThat(rr.getDelaySeconds()).isEqualTo(3.0);
    }

    // ========== InvokeInputs ==========

    @Test
    void testInvokeInputsGettersSetters() {
        InvokeInputs ii = InvokeInputs.builder().query("test query").conversationId("conv-1").build();

        assertThat(ii.getQuery()).isEqualTo("test query");
        assertThat(ii.getConversationId()).isEqualTo("conv-1");

        ii.setResult(Map.of("output", "val"));
        assertThat(ii.getResult()).isNotNull();
    }

    @Test
    void testInvokeInputsToString() {
        InvokeInputs ii = InvokeInputs.builder().query("q").build();
        assertThat(ii.toString()).contains("InvokeInputs");
    }

    @Test
    void testInvokeInputsEqualsHashCode() {
        InvokeInputs i1 = InvokeInputs.builder().query("q").build();
        InvokeInputs i2 = InvokeInputs.builder().query("q").build();

        assertThat(i1).isEqualTo(i2);
        assertThat(i1.hashCode()).isEqualTo(i2.hashCode());
    }

    // ========== ModelCallInputs ==========

    @Test
    void testModelCallInputsGettersSetters() {
        ModelCallInputs mci = ModelCallInputs.builder().messages(List.of("msg")).tools(List.of()).build();

        assertThat(mci.getMessages()).hasSize(1);
        assertThat(mci.getTools()).isEmpty();

        mci.setResponse(null);
        assertThat(mci.getResponse()).isNull();

        mci.setMessages(List.of("a", "b"));
        assertThat(mci.getMessages()).hasSize(2);
    }

    @Test
    void testModelCallInputsToString() {
        ModelCallInputs mci = ModelCallInputs.builder().build();
        assertThat(mci.toString()).contains("ModelCallInputs");
    }

    @Test
    void testModelCallInputsEqualsHashCode() {
        ModelCallInputs m1 = ModelCallInputs.builder().messages(List.of("a")).build();
        ModelCallInputs m2 = ModelCallInputs.builder().messages(List.of("a")).build();

        assertThat(m1).isEqualTo(m2);
        assertThat(m1.hashCode()).isEqualTo(m2.hashCode());
    }

    // ========== ToolCallInputs ==========

    @Test
    void testToolCallInputsGettersSetters() {
        ToolCallInputs tci = ToolCallInputs.builder().toolName("tool1").toolArgs("{}").build();

        assertThat(tci.getToolName()).isEqualTo("tool1");
        assertThat(tci.getToolArgs()).isEqualTo("{}");

        tci.setToolResult("result");
        assertThat(tci.getToolResult()).isEqualTo("result");

        tci.setToolMsg(null);
        assertThat(tci.getToolMsg()).isNull();
    }

    @Test
    void testToolCallInputsToString() {
        ToolCallInputs tci = ToolCallInputs.builder().toolName("t").build();
        assertThat(tci.toString()).contains("ToolCallInputs");
    }

    @Test
    void testToolCallInputsEqualsHashCode() {
        ToolCallInputs t1 = ToolCallInputs.builder().toolName("t").build();
        ToolCallInputs t2 = ToolCallInputs.builder().toolName("t").build();

        assertThat(t1).isEqualTo(t2);
        assertThat(t1.hashCode()).isEqualTo(t2.hashCode());
    }

    // ========== AgentCard ==========

    @Test
    void testAgentCardToString() {
        AgentCard ac = AgentCard.builder().name("agent1").build();
        assertThat(ac.toString()).isNotNull();
    }

    @Test
    void testAgentCardGettersSetters() {
        AgentCard ac =
            AgentCard.builder().name("agent").description("desc").inputParams(Map.of("q", Map.of("type", "string")))
                    .outputParams(Map.of("o", Map.of("type", "string"))).build();

        assertThat(ac.getName()).isEqualTo("agent");
        assertThat(ac.getDescription()).isEqualTo("desc");
        assertThat(ac.getInputParamsAsMap()).containsKey("q");
        assertThat(ac.getOutputParamsAsMap()).containsKey("o");
    }

    @Test
    void testAgentCardEqualsHashCode() {
        AgentCard a1 = AgentCard.builder().name("a").description("d").build();
        // Use same id to ensure equals works (id is auto-generated)
        AgentCard a2 = AgentCard.builder().name("a").description("d").id(a1.getId()).build();

        assertThat(a1).isEqualTo(a2);
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());
    }

    // ========== AgentResult ==========

    @Test
    void testAgentResultToString() {
        AgentResult ar = AgentResult.builder().taskId("t-1").build();
        assertThat(ar.toString()).contains("AgentResult");
    }

    @Test
    void testAgentResultGettersSetters() {
        AgentResult ar = AgentResult.builder().taskId("t-1").sessionId("s-1").status(TaskStatus.COMPLETED)
                .metadata(Map.of("key", "value")).build();

        assertThat(ar.getTaskId()).isEqualTo("t-1");
        assertThat(ar.getSessionId()).isEqualTo("s-1");
        assertThat(ar.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(ar.getMetadata()).containsKey("key");

        ar.setStatus(TaskStatus.FAILED);
        assertThat(ar.getStatus()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    void testAgentResultEqualsHashCode() {
        AgentResult r1 = AgentResult.builder().taskId("t").sessionId("s").build();
        AgentResult r2 = AgentResult.builder().taskId("t").sessionId("s").build();

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    // ========== Artifact ==========

    @Test
    void testArtifactToString() {
        Artifact a = Artifact.builder().name("art1").build();
        assertThat(a.toString()).contains("Artifact");
    }

    @Test
    void testArtifactGettersSetters() {
        Part part = Part.builder().type("text").content("hello").build();
        Artifact a = Artifact.builder().artifactId("aid-1").name("artifact").description("desc").parts(List.of(part))
                .metadata(Map.of("m", "v")).build();

        assertThat(a.getArtifactId()).isEqualTo("aid-1");
        assertThat(a.getName()).isEqualTo("artifact");
        assertThat(a.getDescription()).isEqualTo("desc");
        assertThat(a.getParts()).hasSize(1);
        assertThat(a.getMetadata()).containsKey("m");

        a.setName("updated");
        assertThat(a.getName()).isEqualTo("updated");
    }

    @Test
    void testArtifactEqualsHashCode() {
        Artifact a1 = Artifact.builder().name("a").artifactId("id").build();
        Artifact a2 = Artifact.builder().name("a").artifactId("id").build();

        assertThat(a1).isEqualTo(a2);
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());
    }

    // ========== Skill ==========

    @Test
    void testSkillToString() {
        Skill s = Skill.builder().name("skill1").build();
        assertThat(s.toString()).contains("Skill");
    }

    @Test
    void testSkillGettersSetters() {
        Skill s = Skill.builder().name("my-skill").description("skill desc").directory("/path/to/skill").build();

        assertThat(s.getName()).isEqualTo("my-skill");
        assertThat(s.getDescription()).isEqualTo("skill desc");
        assertThat(s.getDirectory()).isEqualTo("/path/to/skill");

        s.setName("updated-skill");
        assertThat(s.getName()).isEqualTo("updated-skill");
    }

    @Test
    void testSkillEqualsHashCode() {
        Skill s1 = Skill.builder().name("s").description("d").directory("dir").build();
        Skill s2 = Skill.builder().name("s").description("d").directory("dir").build();

        assertThat(s1).isEqualTo(s2);
        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
    }

    @Test
    void testSkillNotEqual() {
        Skill s1 = Skill.builder().name("s1").build();
        Skill s2 = Skill.builder().name("s2").build();
        assertThat(s1).isNotEqualTo(s2);
    }

    // ========== GitHubTree ==========

    @Test
    void testGitHubTreeToString() {
        GitHubTree gt = new GitHubTree("owner", "repo", "main", "dir");
        assertThat(gt.toString()).contains("GitHubTree");
    }

    @Test
    void testGitHubTreeGettersSetters() {
        GitHubTree gt = new GitHubTree("owner", "repo", "main", "dir");
        assertThat(gt.getRepoOwner()).isEqualTo("owner");
        assertThat(gt.getRepoName()).isEqualTo("repo");
        assertThat(gt.getTreeRef()).isEqualTo("main");
        assertThat(gt.getDirectory()).isEqualTo("dir");

        gt.setRepoOwner("new-owner");
        assertThat(gt.getRepoOwner()).isEqualTo("new-owner");
    }

    @Test
    void testGitHubTreeEqualsHashCode() {
        GitHubTree g1 = new GitHubTree("o", "r", "m", "d");
        GitHubTree g2 = new GitHubTree("o", "r", "m", "d");

        assertThat(g1).isEqualTo(g2);
        assertThat(g1.hashCode()).isEqualTo(g2.hashCode());
    }

    @Test
    void testGitHubTreeNotEqual() {
        GitHubTree g1 = new GitHubTree("o", "r", "m", "d1");
        GitHubTree g2 = new GitHubTree("o", "r", "m", "d2");
        assertThat(g1).isNotEqualTo(g2);
    }

    // ========== AbilityExecutionError ==========

    @Test
    void testAbilityExecutionErrorToString() {
        AbilityExecutionError err = new AbilityExecutionError(
                com.openjiuwen.core.common.exception.StatusCode.AGENT_TOOL_EXECUTION_ERROR, "test error", null);
        assertThat(err.toString()).isNotNull();
        assertThat(err.getMessage()).contains("test error");
    }

    @Test
    void testAbilityExecutionErrorGetToolMessage() {
        com.openjiuwen.core.foundation.llm.schema.ToolMessage msg =
            com.openjiuwen.core.foundation.llm.schema.ToolMessage.builder().content("err").toolCallId("tc-1").build();
        AbilityExecutionError err = new AbilityExecutionError(
                com.openjiuwen.core.common.exception.StatusCode.AGENT_TOOL_EXECUTION_ERROR, "err", msg);
        assertThat(err.getToolMessage()).isSameAs(msg);
    }
}
