/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_evolver_common.agent;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.context.ContextUtils;
import com.openjiuwen.core.context.processor.compressor.FullCompactProcessorConfig;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Shared bounded-context model harness for Evolver Agents. */
public final class EvolverAgentHarness {
    /** Fraction of the model context at which full compaction starts. */
    public static final double CONTEXT_PRESSURE_RATIO = 0.8;
    /** Finite timeout applied to every model request. */
    public static final double MODEL_TIMEOUT_SECONDS = 300.0;
    /** Maximum tokens returned by the context compaction request. */
    public static final int COMPACTION_OUTPUT_TOKENS = 4_096;
    /** Recent messages retained verbatim during compaction. */
    public static final int COMPACTION_MESSAGES_TO_KEEP = 10;

    private EvolverAgentHarness() {
    }

    /**
     * Install common model reliability behavior on an Agent.
     *
     * @param agent target Agent
     * @param configuration mutable Agent configuration
     * @return installed reliability rail
     */
    public static EvolverModelReliabilityRail install(ReActAgent agent,
                                                       ReActAgentConfig configuration) {
        return install(agent, configuration, EvolverGuardedModel::new,
                EvolverModelReliabilityRail::new);
    }

    /**
     * Install the shared harness while preserving service-specific compatibility wrappers.
     *
     * @param agent target Agent
     * @param configuration mutable Agent configuration
     * @param modelFactory guarded model factory
     * @param railFactory reliability rail factory
     * @param <R> concrete reliability rail type
     * @return installed service-specific rail
     */
    public static <R extends EvolverModelReliabilityRail> R install(
            ReActAgent agent, ReActAgentConfig configuration,
            BiFunction<ModelClientConfig, ModelRequestConfig, ? extends Model> modelFactory,
            Function<String, R> railFactory) {
        Objects.requireNonNull(agent, "agent must not be null");
        ReActAgentConfig required = Objects.requireNonNull(configuration,
                "configuration must not be null");
        Objects.requireNonNull(modelFactory, "modelFactory must not be null");
        Objects.requireNonNull(railFactory, "railFactory must not be null");
        int contextMaximum = ContextUtils.resolveContextMax(required.getModelName(), null, null);
        ModelClientConfig client = reliableClient(required.getModelClientConfig());
        required.setModelClientConfig(client);
        configureContext(required, client, contextMaximum);
        agent.configure(required);
        agent.setLlm(modelFactory.apply(client, required.getModelConfigObj()));
        R rail = railFactory.apply(required.getModelName());
        agent.registerRail(rail);
        return rail;
    }

    /** Return the context pressure threshold for a model. */
    public static int contextPressureTokens(String modelName) {
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
