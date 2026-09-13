/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.harness.deep_agent.DeepAgent;

import java.util.ArrayList;
import java.util.List;

/**
 * Public class VerificationContractRail used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class VerificationContractRail extends DeepAgentRail {
    /**
     * SECTION_NAME.
     * 
     * @since 0.1.7
     */
    public static final String SECTION_NAME = "verification_contract";

    /**
     * SECTION_PRIORITY.
     * 
     * @since 0.1.7
     */
    public static final int SECTION_PRIORITY = 88;

    private static final String CONTRACT_CONTENT = """
        ## Verification Gate

        After any non-trivial implementation turn, you MUST spawn the verification agent before reporting \
        completion.

        Non-trivial means any of:
        - 3 or more file edits in a single turn
        - Backend, API, or service changes
        - Infrastructure or configuration changes

        Use task_tool with subagent_type="verification_agent" and pass the original request, changed files, \
        approach, and plan file path if one was used.
        On VERDICT: PASS, spot-check 2-3 reported commands before reporting completion.
        On VERDICT: FAIL, fix the issue and re-invoke the same verification session until PASS.
        On VERDICT: PARTIAL, report what was verified and the environmental gap.
        You cannot self-assign any verdict; only the verification agent issues PASS, FAIL, or PARTIAL.
        """;

    private DeepAgent owner;

    /**
     * priority.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int priority() {
        return SECTION_PRIORITY;
    }

    /**
     * init.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void init(Object agent) {
        if (agent instanceof DeepAgent deepAgent) {
            owner = deepAgent;
        }
    }

    /**
     * uninit.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void uninit(Object agent) {
        if (owner != null) {
            owner.getAgent().getPromptBuilder().removeSection(SECTION_NAME);
        }
        owner = null;
    }

    /**
     * beforeModelCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        if (owner != null) {
            owner.getAgent().addPromptBuilderSection(SECTION_NAME, CONTRACT_CONTENT, SECTION_PRIORITY);
        }
        if (ctx.getInputs() instanceof ModelCallInputs inputs) {
            injectContractMessage(inputs);
        }
    }

    /**
     * sectionName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String sectionName() {
        return SECTION_NAME;
    }

    /**
     * requiresVerification.
     * 
     * @param fileEditCount fileEditCount
     * @param isBackendChange isBackendChange
     * @param isInfrastructureChange isInfrastructureChange
     * @return the result
     * @since 0.1.7
     */
    public boolean requiresVerification(int fileEditCount, boolean isBackendChange, boolean isInfrastructureChange) {
        return fileEditCount >= 3 || isBackendChange || isInfrastructureChange;
    }

    /**
     * hasContractPromptSection.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean hasContractPromptSection() {
        return owner != null && owner.getAgent().getPromptBuilder().hasSection(SECTION_NAME);
    }

    /**
     * contractContent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String contractContent() {
        return CONTRACT_CONTENT.trim();
    }

    /**
     * injectContractMessage.
     * 
     * @param inputs inputs
     * @since 0.1.7
     */
    private void injectContractMessage(ModelCallInputs inputs) {
        List<Object> messages =
            inputs.getMessages() != null ? new ArrayList<>(inputs.getMessages()) : new ArrayList<>();
        for (Object message : messages) {
            if (message instanceof BaseMessage baseMessage && "system".equalsIgnoreCase(baseMessage.getRole())
                    && String.valueOf(baseMessage.getContent()).contains("## Verification Gate")) {
                return;
            }
        }
        messages.add(0, new SystemMessage(contractContent()));
        inputs.setMessages(messages);
    }
}
