  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.foundation.llm.Model;

/**
 * Registers the built-in OpenAI-compatible model client factories.
 */
public final class DefaultModelClientFactories {

    private static volatile boolean registered;

    private DefaultModelClientFactories() {
    }

    public static synchronized void ensureRegistered() {
        if (registered) {
            return;
        }
        Model.registerFactory(new OpenAiModelClientFactory());
        Model.registerFactory(new OpenRouterModelClientFactory());
        Model.registerFactory(new SiliconFlowModelClientFactory());
        Model.registerFactory(new DashScopeModelClientFactory());
        Model.registerFactory(new InferenceAffinityModelClientFactory("InferenceAffinity"));
        Model.registerFactory(new InferenceAffinityModelClientFactory("inference_affinity"));
        registered = true;
    }
}
