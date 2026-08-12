/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.context_evolver;

/**
 * Entry point for the MicroCompactProcessor + KV cache release benchmark.
 * <p>
 * Runs multiple concurrent worker sessions, each with several rounds of
 * tool-calling dialogue. Each round produces a large (~8 KB) tool result;
 * before the next round, {@code MicroCompactProcessor} clears the previous
 * tool result so the framework detects a prefix diff and triggers
 * {@code KVCacheManager.release} when the model supports it.
 * <p>
 * Provider selection is driven by {@code apiconfig.json}:
 * <ul>
 *   <li>{@code InferenceAffinity} — KV cache release ON (sends
 *       {@code /release_kv_cache} to vLLM).</li>
 *   <li>{@code OpenAI} — KV cache release OFF (no release HTTP; control
 *       group for A/B comparison).</li>
 * </ul>
 * <p>
 * Metrics printed at the end: per-round TTFT, total wall time, and number
 * of {@code [RELEASE REASON]} log lines observed in the
 * {@code context_engine} logger.
 *
 * @since 2026-08-08
 */
public final class MicroCompactProcessorKvCacheExample {

    private MicroCompactProcessorKvCacheExample() {
    }

    /**
     * Entry point.
     *
     * @param args optional first-round query; defaults to "北京天气如何"
     * @throws Exception if the agent run fails
     */
    public static void main(String[] args) throws Exception {
        MicroCompactProcessorKvCacheExampleSupport.run(args);
    }
}
