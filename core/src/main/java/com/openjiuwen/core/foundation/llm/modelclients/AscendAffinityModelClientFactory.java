/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

/**
 * Default factory for the AscendAffinity provider.
 *
 * <p>Mirrors the Python AscendAffinity endpoint profile in
 * {@code openjiuwen/core/foundation/llm/utils/endpoint_profiles.py}: the
 * provider rides the OpenAI-compatible transport and declares affinity
 * capability through {@code extensions.kv_cache.mode=affinity}, which
 * {@link OpenAIModelClient} reads for {@code agent_hint} encoding and the
 * three KVC management actions.</p>
 *
 * @since 0.1.16
 */
public class AscendAffinityModelClientFactory implements Model.ModelClientFactory {
    private final String providerName;

    /**
     * Create a factory bound to one provider name.
     *
     * @param providerName provider registry name
     */
    public AscendAffinityModelClientFactory(String providerName) {
        this.providerName = providerName == null || providerName.isBlank()
                ? "AscendAffinity"
                : providerName;
    }

    @Override
    public String providerName() {
        return providerName;
    }

    @Override
    public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
        return new OpenAiCompatibleModelClient(modelConfig, clientConfig);
    }
}
