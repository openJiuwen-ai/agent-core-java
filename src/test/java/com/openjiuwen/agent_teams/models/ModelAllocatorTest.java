/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_teams.agent.AgentConfigurator;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for model pool allocation.
 *
 * <p>Mirrors Python's allocator tests for
 * {@code openjiuwen/agent_teams/models/allocator.py} and
 * {@code openjiuwen/agent_teams/models/pool.py}.</p>
 */
class ModelAllocatorTest {

    @Test
    void roundRobinRotatesThroughPoolAndCarriesGroupIndex() {
        List<ModelPoolEntry> pool = List.of(
                namedEntry("gpt-4", "a1"),
                namedEntry("claude", "c1"),
                namedEntry("gpt-4", "a2")
        );
        RoundRobinModelAllocator allocator = new RoundRobinModelAllocator(pool);

        Allocation first = allocator.allocate("ignored");
        Allocation second = allocator.allocate();
        Allocation third = allocator.allocate();
        Allocation wrap = allocator.allocate();

        assertThat(first.toDbRef()).containsEntry("model_name", "gpt-4").containsEntry("model_index", 0);
        assertThat(second.toDbRef()).containsEntry("model_name", "claude").containsEntry("model_index", 0);
        assertThat(third.toDbRef()).containsEntry("model_name", "gpt-4").containsEntry("model_index", 1);
        assertThat(wrap.getEntry().getApiBaseUrl()).isEqualTo("http://a1");
    }

    @Test
    void roundRobinReturnsNullWhenPoolEmpty() {
        RoundRobinModelAllocator allocator = new RoundRobinModelAllocator(List.of());

        assertThat(allocator.allocate()).isNull();
        assertThat(allocator.allocate("gpt-4")).isNull();
    }

    @Test
    void byModelNameRotatesWithinIndependentGroupsAndIgnoresUnknownNames() {
        ByModelNameAllocator allocator = new ByModelNameAllocator(List.of(
                namedEntry("gpt-4", "a1"),
                namedEntry("gpt-4", "a2"),
                namedEntry("claude", "c1"),
                namedEntry("claude", "c2")
        ));

        assertThat(allocator.allocate()).isNull();
        assertThat(allocator.allocate("missing")).isNull();
        assertThat(allocator.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://a1");
        assertThat(allocator.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://a2");
        assertThat(allocator.allocate("claude").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://c1");
        assertThat(allocator.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://a1");
    }

    @Test
    void byModelNameStateDictUsesOpaqueModelNameRecordsAndLoadsLegacyDicts() {
        List<ModelPoolEntry> pool = List.of(
                namedEntry("glm-5", "g5a"),
                namedEntry("glm-5", "g5b"),
                namedEntry("glm-5.1", "g51a"),
                namedEntry("claude-3.5-sonnet", "c35")
        );
        ByModelNameAllocator allocator = new ByModelNameAllocator(pool);
        allocator.allocate("glm-5");
        allocator.allocate("glm-5.1");
        Map<String, Object> snapshot = allocator.stateDict();

        assertThat(snapshot.get("counters")).isInstanceOf(List.class);
        ByModelNameAllocator restored = new ByModelNameAllocator(pool);
        restored.loadStateDict(snapshot);

        assertThat(restored.allocate("glm-5").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://g5b");
        assertThat(restored.allocate("glm-5.1").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://g51a");

        ByModelNameAllocator legacy = new ByModelNameAllocator(pool);
        legacy.loadStateDict(Map.of(
                "pool_digest", snapshot.get("pool_digest"),
                "inner_indexes", Map.of("glm-5", 1, "claude-3.5-sonnet", 0)
        ));
        assertThat(legacy.allocate("glm-5").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://g5b");
    }

    @Test
    void allocatorStateResetsOnPoolDigestChangeButCredentialRefreshPreservesDigest() {
        List<ModelPoolEntry> original = List.of(namedEntry("gpt-4", "a1"));
        ByModelNameAllocator allocator = new ByModelNameAllocator(original);
        allocator.allocate("gpt-4");
        Map<String, Object> snapshot = allocator.stateDict();

        ModelPoolEntry refreshed = new ModelPoolEntry(
                "gpt-4",
                "NEW",
                "http://a1",
                "OpenAI"
        );
        ByModelNameAllocator sameDigest = new ByModelNameAllocator(List.of(refreshed));
        sameDigest.loadStateDict(snapshot);
        assertThat(sameDigest.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiKey())
                .isEqualTo("NEW");

        ByModelNameAllocator changedDigest = new ByModelNameAllocator(List.of(
                namedEntry("gpt-4", "a1"),
                namedEntry("gpt-4", "a2")
        ));
        changedDigest.loadStateDict(snapshot);
        assertThat(changedDigest.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://a1");
    }

    @Test
    void routerAllocatorSelectsDefaultAndNamedEntries() {
        List<ModelPoolEntry> pool = routerConfig("gpt-4o", "claude-opus", "gemini-pro").toPoolEntries();
        RouterAllocator allocator = new RouterAllocator(pool);

        assertThat(allocator.allocate().getEntry().getModelName()).isEqualTo("gpt-4o");
        assertThat(allocator.allocate().getEntry().getModelName()).isEqualTo("gpt-4o");
        assertThat(allocator.allocate("claude-opus").toTeamModelConfig().getModelRequestConfig().getModelName())
                .isEqualTo("claude-opus");
        assertThat(allocator.allocate("missing")).isNull();
        assertThat(allocator.stateDict()).containsKey("pool_digest");
    }

    @Test
    void routerAllocatorRejectsEmptyOrDuplicatePools() {
        assertThatThrownBy(() -> new RouterAllocator(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-empty pool");
        assertThatThrownBy(() -> new RouterAllocator(List.of(namedEntry("gpt-4o", "a1"), namedEntry("gpt-4o", "a2"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique model_names");
    }

    @Test
    void modelPoolEntryMaterializesModelConfigWithMetadataMergeAndExplicitOverride() {
        ModelPoolEntry entry = new ModelPoolEntry(
                "m1",
                "real-key",
                "http://real",
                "OpenAI",
                Map.of(
                        "client", Map.of(
                                "api_key", "shadow-key",
                                "api_base", "http://shadow",
                                "timeout", 30.0,
                                "verify_ssl", false,
                                "max_retries", 5
                        ),
                        "request", Map.of(
                                "model", "shadow-model",
                                "temperature", 0.2,
                                "top_p", 0.9,
                                "max_tokens", 1024
                        ),
                        "weight", 5
                )
        );

        ModelPoolEntry.TeamModelConfig config = entry.toTeamModelConfig();
        assertThat(config.getModelClientConfig().getClientId()).isEqualTo(entry.getModelId());
        assertThat(config.getModelClientConfig().getApiKey()).isEqualTo("real-key");
        assertThat(config.getModelClientConfig().getApiBase()).isEqualTo("http://real");
        assertThat(config.getModelClientConfig().getTimeout()).isEqualTo(30.0);
        assertThat(config.getModelClientConfig().isVerifySsl()).isFalse();
        assertThat(config.getModelClientConfig().getMaxRetries()).isEqualTo(5);
        assertThat(config.getModelRequestConfig().getModelName()).isEqualTo("m1");
        assertThat(config.getModelRequestConfig().getTemperature()).isEqualTo(0.2);
        assertThat(config.getModelRequestConfig().getTopP()).isEqualTo(0.9);
        assertThat(config.getModelRequestConfig().getMaxTokens()).isEqualTo(1024);
    }

    @Test
    void modelPoolEntryAssignsUniqueIdsAndRoundTripsThroughJson() throws Exception {
        ModelPoolEntry first = namedEntry("m", "one");
        ModelPoolEntry second = namedEntry("m", "two");
        assertThat(first.getModelId()).isNotEqualTo(second.getModelId());

        ObjectMapper mapper = new ObjectMapper();
        ModelPoolEntry restored = mapper.readValue(mapper.writeValueAsString(first), ModelPoolEntry.class);
        assertThat(restored.getModelName()).isEqualTo("m");
        assertThat(restored.getApiBaseUrl()).isEqualTo("http://one");
        assertThat(restored.getModelId()).isEqualTo(first.getModelId());
    }

    @Test
    void routerConfigExpandsEntriesAndRejectsInvalidNames() {
        ModelRouterConfig router = routerConfig("gpt-4o", "claude-opus");
        List<ModelPoolEntry> entries = router.toPoolEntries();
        assertThat(entries).extracting(ModelPoolEntry::getModelName).containsExactly("gpt-4o", "claude-opus");
        assertThat(entries).extracting(ModelPoolEntry::getApiKey).containsOnly("sk-shared");

        entries.get(0).getMetadata().put("client", Map.of("timeout", 99.0));
        assertThat(entries.get(0).getMetadata()).isEqualTo(Map.of("client", Map.of("timeout", 99.0)));
        assertThat(entries.get(1).getMetadata()).isEqualTo(Map.of("client", Map.of("verify_ssl", false)));
        assertThat(router.getMetadata()).isEqualTo(Map.of("client", Map.of("verify_ssl", false)));

        assertThatThrownBy(() -> new ModelRouterConfig("u", "k", "OpenAI", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> routerConfig("gpt-4o", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-empty strings");
        assertThatThrownBy(() -> routerConfig("gpt-4o", "gpt-4o"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicates");
    }

    @Test
    void inheritPoolIdsOnlyPreservesBitExactEntries() {
        ModelPoolEntry old = new ModelPoolEntry("gpt-4", "K", "http://x", "OpenAI");
        ModelPoolEntry exact = new ModelPoolEntry("gpt-4", "K", "http://x", "OpenAI");
        ModelPoolEntry rotated = new ModelPoolEntry("gpt-4", "ROTATED", "http://x", "OpenAI");

        List<ModelPoolEntry> inherited = ModelPoolSupport.inheritPoolIds(List.of(old), List.of(exact));
        assertThat(inherited.get(0).getModelId()).isEqualTo(old.getModelId());
        assertThat(exact.getModelId()).isNotEqualTo(old.getModelId());

        List<ModelPoolEntry> notInherited = ModelPoolSupport.inheritPoolIds(List.of(old), List.of(rotated));
        assertThat(notInherited.get(0).getModelId()).isEqualTo(rotated.getModelId());
    }

    @Test
    void buildModelAllocatorDispatchesByStrategyAndResolveMemberModelUsesStoredReference() {
        AgentConfigurator.TeamAgentSpec spec = new AgentConfigurator.TeamAgentSpec();
        AgentConfigurator.TeamSpec teamSpec = new AgentConfigurator.TeamSpec("team", "Team", "leader");
        List<ModelPoolEntry> pool = List.of(namedEntry("gpt-4", "a1"), namedEntry("gpt-4", "a2"));
        teamSpec.setModelPool(pool);

        assertThat(ModelAllocators.buildModelAllocator(spec, teamSpec)).isInstanceOf(RoundRobinModelAllocator.class);
        teamSpec.setModelPoolStrategy("by_model_name");
        assertThat(ModelAllocators.buildModelAllocator(spec, teamSpec)).isInstanceOf(ByModelNameAllocator.class);
        teamSpec.setModelPoolStrategy("router");
        assertThatThrownBy(() -> ModelAllocators.buildModelAllocator(spec, teamSpec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique model_names");

        ModelPoolEntry.TeamModelConfig resolved = ModelAllocators.resolveMemberModel(pool, "gpt-4", 1);
        assertThat(resolved.getModelClientConfig().getApiBase()).isEqualTo("http://a2");
        assertThat(ModelAllocators.resolveMemberModel(pool, "missing", 0)).isNull();
        assertThat(ModelAllocators.resolveMemberModel(pool, "gpt-4", 99).getModelClientConfig().getApiBase())
                .isEqualTo("http://a1");
    }

    @Test
    void modelAllocatorsImplementAgentConfiguratorAndRecoveryContracts() {
        ModelAllocator allocator = new RoundRobinModelAllocator(List.of(namedEntry("gpt-4", "a1")));

        assertThat(allocator).isInstanceOf(AgentConfigurator.ModelAllocator.class);
        assertThat(allocator).isInstanceOf(com.openjiuwen.agent_teams.agent.RecoveryManager.StatefulAllocator.class);
        assertThat(allocator.allocate("any")).isInstanceOf(Allocation.class);
    }

    private static ModelPoolEntry namedEntry(String name, String suffix) {
        return new ModelPoolEntry(name, "k-" + suffix, "http://" + suffix, "OpenAI");
    }

    private static ModelRouterConfig routerConfig(String... names) {
        return new ModelRouterConfig(
                "https://router.test/v1",
                "sk-shared",
                "OpenAI",
                List.of(names),
                Map.of("client", Map.of("verify_ssl", false))
        );
    }
}
