/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.classification.ModelCallClassification;
import com.openjiuwen.core.foundation.llm.classification.ModelErrorCategory;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgent;

import java.util.Map;

/**
 * Main loop guardrail that disposes model call failures by their unified
 * classification. Rate limited and temporary failures are retried once more
 * after a wait (the wait is clamped to the remaining run budget by the budget
 * rail downstream); permanent categories (quota, auth, context length) fail
 * fast with their classified surface; cancellation and human interruptions
 * never reach this hook by construction. The guardrail yields to
 * {@link ModelBackupRail} whenever one is present (backup switching takes over
 * recovery), fails fast at registration time when a backup rail is already
 * registered, and performs no retry at all when the current model client is
 * configured with {@code maxRetries == 0} (zero disables automatic retry on
 * both the client layer and this guardrail layer).
 *
 * @since 2026-10-19
 */
public class ClassifiedModelFailureRail extends AgentRail {
    private static final String ATTEMPTS_KEY = "classified_failure_guardrail_attempts";
    private static final int DEFAULT_MAX_GUARDRAIL_RETRIES = 1;
    private static final double DEFAULT_MAX_DELAY_SECONDS = 60.0D;
    private static final double RATE_LIMIT_DEFAULT_DELAY_SECONDS = 5.0D;
    private static final double TEMPORARY_DEFAULT_DELAY_SECONDS = 2.0D;

    private final int maxGuardrailRetries;
    private final double maxDelaySeconds;
    private boolean hasYieldWarned;

    /**
     * Creates the guardrail with default policy (one guarded retry, delay capped at 60s).
     */
    public ClassifiedModelFailureRail() {
        this(DEFAULT_MAX_GUARDRAIL_RETRIES, DEFAULT_MAX_DELAY_SECONDS);
    }

    /**
     * Creates the guardrail with an explicit policy.
     *
     * @param maxGuardrailRetries max guarded retries per invoke, must be >= 0
     * @param maxDelaySeconds upper bound of the guarded retry delay in seconds
     */
    public ClassifiedModelFailureRail(int maxGuardrailRetries, double maxDelaySeconds) {
        this.maxGuardrailRetries = Math.max(0, maxGuardrailRetries);
        this.maxDelaySeconds = Math.max(0.0D, maxDelaySeconds);
    }

    @Override
    public void init(BaseAgent agent) {
        if (agent != null
            && agent.getAgentCallbackManager().isRailTypeRegistered(ModelBackupRail.class)) {
            throw new IllegalStateException(
                "ClassifiedModelFailureRail and ModelBackupRail must not be registered together");
        }
    }

    @Override
    public void onModelException(AgentCallbackContext context) {
        if (context == null || context.getException() == null) {
            return;
        }
        if (yieldsToBackupRail(context)) {
            return;
        }
        ModelCallClassification classification = ModelCallClassification.ofOrClassify(context.getException());
        ModelErrorCategory category = classification.category();
        if (category != ModelErrorCategory.RATE_LIMITED && category != ModelErrorCategory.TEMPORARY) {
            return;
        }
        if (!classification.isRetryable() || !currentModelAllowsRetry(context)) {
            return;
        }
        int attempts = guardrailAttempts(context);
        if (attempts >= maxGuardrailRetries) {
            return;
        }
        context.getExtra().put(ATTEMPTS_KEY, attempts + 1);
        context.requestRetry(resolveDelaySeconds(classification));
    }

    private boolean yieldsToBackupRail(AgentCallbackContext context) {
        boolean hasBackupRail = context.getAgent() != null && context.getAgent().getAgentCallbackManager()
            .isRailTypeRegistered(ModelBackupRail.class);
        if (hasBackupRail && !hasYieldWarned) {
            hasYieldWarned = true;
            Loggers.AGENT.warning("ClassifiedModelFailureRail yields to ModelBackupRail; "
                + "backup switching takes over model failure recovery. {}", Map.of());
        }
        return hasBackupRail;
    }

    private static boolean currentModelAllowsRetry(AgentCallbackContext context) {
        if (!(context.getAgent() instanceof ReActAgent reactAgent)) {
            return true;
        }
        Model llm = reactAgent.getLlm();
        if (llm == null || llm.getModelClientConfig() == null) {
            return true;
        }
        return llm.getModelClientConfig().getMaxRetries() > 0;
    }

    private static int guardrailAttempts(AgentCallbackContext context) {
        Object attempts = context.getExtra().get(ATTEMPTS_KEY);
        return attempts instanceof Number number ? number.intValue() : 0;
    }

    private double resolveDelaySeconds(ModelCallClassification classification) {
        double categoryDefault = classification.category() == ModelErrorCategory.RATE_LIMITED
            ? RATE_LIMIT_DEFAULT_DELAY_SECONDS
            : TEMPORARY_DEFAULT_DELAY_SECONDS;
        double suggested = classification.suggestedDelaySeconds() != null
            ? classification.suggestedDelaySeconds()
            : 0.0D;
        return Math.min(Math.max(suggested, categoryDefault), maxDelaySeconds);
    }
}
