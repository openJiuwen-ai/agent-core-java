/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.agent;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.context.ContextUtils;
import com.openjiuwen.core.context.processor.compressor.FullCompactProcessorConfig;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;

import java.util.List;
import java.util.Objects;

/**
 * Installs bounded-context and reliable model-call behavior for a feature stage Agent.
 *
 * @since 0.1.13
 */
final class FeatureAgentHarness {
    static final double CONTEXT_PRESSURE_RATIO = 0.8;
    static final double MODEL_TIMEOUT_SECONDS = 300.0;
    static final int COMPACTION_OUTPUT_TOKENS = 4_096;
    static final int COMPACTION_MESSAGES_TO_KEEP = 10;

    private FeatureAgentHarness() {
    }

    static FeatureModelReliabilityRail install(ReActAgent agent,
                                                ReActAgentConfig configuration) {
        Objects.requireNonNull(agent, "agent must not be null");
        ReActAgentConfig required = Objects.requireNonNull(configuration,
                "configuration must not be null");
        int contextMaximum = ContextUtils.resolveContextMax(
                required.getModelName(), null, null);
        ModelClientConfig client = reliableClient(required.getModelClientConfig());
        required.setModelClientConfig(client);
        configureContext(required, client, contextMaximum);
        agent.configure(required);
        agent.setLlm(new FeatureGuardedModel(client, required.getModelConfigObj()));
        FeatureModelReliabilityRail rail = new FeatureModelReliabilityRail(required.getModelName());
        agent.registerRail(rail);
        return rail;
    }

    static int contextPressureTokens(String modelName) {
        int maximum = ContextUtils.resolveContextMax(modelName, null, null);
        return Math.max(1, (int) Math.floor(maximum * CONTEXT_PRESSURE_RATIO));
    }

    private static void configureContext(ReActAgentConfig configuration,
                                         ModelClientConfig client, int contextMaximum) {
        ContextEngineConfig context = configuration.getContextEngineConfig();
        context.setDefaultWindowRoundNum(null);
        context.setContextWindowTokens(contextMaximum);
        context.setModelName(configuration.getModelName());
        FullCompactProcessorConfig compact = FullCompactProcessorConfig.builder()
                .triggerTotalTokens(contextPressureTokens(configuration.getModelName()))
                .compressionCallMaxTokens(contextMaximum)
                .messagesToKeep(COMPACTION_MESSAGES_TO_KEEP)
                .sessionMemoryEnabled(false)
                .model(compactionRequest(configuration.getModelName()))
                .modelClient(client)
                .build();
        configuration.configureContextProcessors(List.of(
                new ContextEngine.ProcessorSpec("FullCompactProcessor", compact)));
    }

    private static ModelRequestConfig compactionRequest(String modelName) {
        ModelRequestConfig request = ModelRequestConfig.builder().modelName(modelName).build();
        request.setTemperature(0.1);
        request.setMaxTokens(COMPACTION_OUTPUT_TOKENS);
        return request;
    }

    private static ModelClientConfig reliableClient(ModelClientConfig source) {
        ModelClientConfig required = Objects.requireNonNull(source,
                "model client configuration must not be null");
        return ModelClientConfig.builder()
                .clientId(required.getClientId())
                .clientProvider(required.getClientProvider())
                .apiKey(required.getApiKey())
                .apiBase(required.getApiBase())
                .timeout(MODEL_TIMEOUT_SECONDS)
                .maxRetries(0)
                .verifySsl(required.isVerifySsl())
                .sslCert(required.getSslCert())
                .headers(required.getHeaders())
                .build();
    }
}
