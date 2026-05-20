package com.openjiuwen.agentteams.agent;

import com.openjiuwen.agentteams.TeamConstants;
import com.openjiuwen.agentteams.factory.TeamFactory;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.ModelPoolEntries;
import com.openjiuwen.agentteams.schema.team.ModelPoolEntry;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamModelConfig;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelAllocatorCompatibilityTest {

    @Test
    void roundRobinAllocatorShouldRotateThroughPoolAndPersistGroupIndex() {
        ModelAllocator allocator = new ModelAllocators.RoundRobinModelAllocator(List.of(
                poolEntry("gpt-4", "http://a1"),
                poolEntry("claude", "http://c1"),
                poolEntry("gpt-4", "http://a2")
        ));

        Allocation alloc1 = allocator.allocate();
        Allocation alloc2 = allocator.allocate("ignored");
        Allocation alloc3 = allocator.allocate();

        assertThat(alloc1.toDbRef()).containsEntry("model_name", "gpt-4").containsEntry("model_index", 0);
        assertThat(alloc2.toDbRef()).containsEntry("model_name", "claude").containsEntry("model_index", 0);
        assertThat(alloc3.toDbRef()).containsEntry("model_name", "gpt-4").containsEntry("model_index", 1);
    }

    @Test
    void byModelNameAllocatorShouldRotateWithinNamedGroupAndIgnoreUnknownName() {
        ModelAllocator allocator = new ModelAllocators.ByModelNameAllocator(List.of(
                poolEntry("gpt-4", "http://a1"),
                poolEntry("gpt-4", "http://a2"),
                poolEntry("claude", "http://c1")
        ));

        assertThat(allocator.allocate(null)).isNull();
        assertThat(allocator.allocate("")).isNull();
        assertThat(allocator.allocate("gemini")).isNull();

        assertThat(allocator.allocate("gpt-4").toTeamModelConfig().modelClientConfig().getApiBase()).isEqualTo("http://a1");
        assertThat(allocator.allocate("gpt-4").toTeamModelConfig().modelClientConfig().getApiBase()).isEqualTo("http://a2");
        assertThat(allocator.allocate("gpt-4").toTeamModelConfig().modelClientConfig().getApiBase()).isEqualTo("http://a1");
        assertThat(allocator.allocate("claude").toTeamModelConfig().modelClientConfig().getApiBase()).isEqualTo("http://c1");
    }

    @Test
    void allocatorStateShouldResumeAndResetOnPoolDigestChange() {
        ModelAllocator allocator = new ModelAllocators.RoundRobinModelAllocator(List.of(
                poolEntry("m0", "http://0"),
                poolEntry("m1", "http://1"),
                poolEntry("m2", "http://2")
        ));
        allocator.allocate();
        allocator.allocate();
        Map<String, Object> snapshot = allocator.stateDict();

        ModelAllocator resumed = new ModelAllocators.RoundRobinModelAllocator(List.of(
                poolEntry("m0", "http://0"),
                poolEntry("m1", "http://1"),
                poolEntry("m2", "http://2")
        ));
        resumed.loadStateDict(snapshot);
        assertThat(resumed.allocate().toTeamModelConfig().modelRequestConfig().getModelName()).isEqualTo("m2");

        ModelAllocator reset = new ModelAllocators.RoundRobinModelAllocator(List.of(
                poolEntry("m0", "http://0"),
                poolEntry("m1", "http://1")
        ));
        reset.loadStateDict(snapshot);
        assertThat(reset.allocate().toTeamModelConfig().modelRequestConfig().getModelName()).isEqualTo("m0");
    }

    @Test
    void modelPoolEntryShouldMaterializeModelConfigWithMetadataAndExplicitOverrides() {
        ModelPoolEntry entry = ModelPoolEntry.builder()
                .modelName("gpt-4")
                .provider("OpenAI")
                .apiKey("real-key")
                .apiBaseUrl("http://real")
                .metadata(Map.of(
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
                                "max_tokens", 1024,
                                "tag", "fast"
                        )
                ))
                .build();

        TeamModelConfig config = entry.toTeamModelConfig();

        assertThat(config.modelClientConfig().getClientId()).isEqualTo(entry.getModelId());
        assertThat(config.modelClientConfig().getClientProvider()).isEqualTo("OpenAI");
        assertThat(config.modelClientConfig().getApiKey()).isEqualTo("real-key");
        assertThat(config.modelClientConfig().getApiBase()).isEqualTo("http://real");
        assertThat(config.modelClientConfig().getTimeout()).isEqualTo(30.0);
        assertThat(config.modelClientConfig().isVerifySsl()).isFalse();
        assertThat(config.modelClientConfig().getMaxRetries()).isEqualTo(5);
        assertThat(config.modelRequestConfig().getModelName()).isEqualTo("gpt-4");
        assertThat(config.modelRequestConfig().getTemperature()).isEqualTo(0.2);
        assertThat(config.modelRequestConfig().getTopP()).isEqualTo(0.9);
        assertThat(config.modelRequestConfig().getMaxTokens()).isEqualTo(1024);
        assertThat(config.modelRequestConfig().getExtraFields()).containsEntry("tag", "fast");
    }

    @Test
    void resolveMemberModelShouldUseLivePoolAndClampOutOfRangeIndex() {
        TeamAgentSpec spec = TeamAgentSpec.builder()
                .name("team")
                .modelPool(List.of(
                        poolEntry("gpt-4", "http://a1"),
                        poolEntry("gpt-4", "http://a2"),
                        poolEntry("claude", "http://c1")
                ))
                .build();

        TeamModelConfig config = ModelAllocators.resolveMemberModel(spec, "gpt-4", 1);
        TeamModelConfig clamped = ModelAllocators.resolveMemberModel(spec, "gpt-4", 5);

        assertThat(config.modelClientConfig().getApiBase()).isEqualTo("http://a2");
        assertThat(clamped.modelClientConfig().getApiBase()).isEqualTo("http://a1");
        assertThat(ModelAllocators.resolveMemberModel(spec, "gemini", 0)).isNull();
    }

    @Test
    void buildModelAllocatorShouldDispatchByStrategyAndRejectUnknownStrategy() {
        TeamAgentSpec roundRobin = TeamAgentSpec.builder()
                .name("team")
                .modelPool(List.of(poolEntry("gpt-4", "http://a1")))
                .modelPoolStrategy("round_robin")
                .build();
        TeamAgentSpec byName = TeamAgentSpec.builder()
                .name("team")
                .modelPool(List.of(poolEntry("gpt-4", "http://a1")))
                .modelPoolStrategy("by_model_name")
                .build();
        TeamAgentSpec unknown = TeamAgentSpec.builder()
                .name("team")
                .modelPool(List.of(poolEntry("gpt-4", "http://a1")))
                .modelPoolStrategy("weighted")
                .build();

        assertThat(ModelAllocators.buildModelAllocator(roundRobin)).isInstanceOf(ModelAllocators.RoundRobinModelAllocator.class);
        assertThat(ModelAllocators.buildModelAllocator(byName)).isInstanceOf(ModelAllocators.ByModelNameAllocator.class);
        assertThatThrownBy(() -> ModelAllocators.buildModelAllocator(unknown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown model_pool_strategy");
    }

    @Test
    void inheritPoolIdsShouldPreserveIdsOnlyForBitExactEntries() {
        ModelPoolEntry oldStable = poolEntry("gpt-4", "http://a1");
        ModelPoolEntry oldDuplicate = poolEntry("gpt-4", "http://a1");
        ModelPoolEntry unchanged = poolEntry("gpt-4", "http://a1");
        ModelPoolEntry rotated = poolEntry("gpt-4", "http://a1").toBuilder().apiKey("rotated").build();

        List<ModelPoolEntry> merged = ModelPoolEntries.inheritPoolIds(
                List.of(oldStable, oldDuplicate),
                List.of(unchanged, rotated)
        );

        assertThat(merged.get(0).getModelId()).isEqualTo(oldStable.getModelId());
        assertThat(merged.get(1).getModelId()).isNotEqualTo(oldDuplicate.getModelId());
        assertThat(rotated.getModelId()).isEqualTo(merged.get(1).getModelId());
    }

    @Test
    void teamAgentShouldPersistAllocatorStateAcrossSnapshotAndResetAfterPoolRefresh() {
        TeamAgentSpec spec = TeamAgentSpec.builder()
                .name("alloc-team")
                .members(List.of(
                        TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(TeamRole.LEADER).modelName("gpt-4").build()
                ))
                .modelPool(List.of(
                        poolEntry("gpt-4", "http://a1"),
                        poolEntry("gpt-4", "http://a2"),
                        poolEntry("claude", "http://c1")
                ))
                .modelPoolStrategy("by_model_name")
                .build();

        var agent = TeamFactory.createAgentTeam(spec);
        assertThat(agent.allocateModel("gpt-4").toTeamModelConfig().modelClientConfig().getApiBase()).isEqualTo("http://a2");
        assertThat(agent.allocateModel("gpt-4").toTeamModelConfig().modelClientConfig().getApiBase()).isEqualTo("http://a1");

        Map<String, Object> snapshot = agent.snapshot();
        var recovered = TeamFactory.recoverAgentTeam(snapshot);
        assertThat(recovered.allocateModel("gpt-4").toTeamModelConfig().modelClientConfig().getApiBase()).isEqualTo("http://a2");

        String beforeRefreshModelId = recovered.getSpec().getModelPool().get(0).getModelId();
        recovered.updateModelPool(List.of(
                poolEntry("gpt-4", "http://b1"),
                poolEntry("claude", "http://c1")
        ));

        assertThat(recovered.getSpec().getModelPool().get(0).getModelId()).isNotEqualTo(beforeRefreshModelId);
        assertThat(recovered.allocateModel("gpt-4").toTeamModelConfig().modelClientConfig().getApiBase()).isEqualTo("http://b1");
        assertThat(recovered.getContext().getMetadata()).containsKey("model_allocator_state");
    }

    @Test
    void teamFactoryShouldPreallocateLeaderFromSharedModelPoolLikePythonBlueprintBuild() {
        TeamAgentSpec spec = TeamAgentSpec.builder()
                .name("leader-model-team")
                .members(List.of(
                        TeamMemberSpec.builder()
                                .name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                                .role(TeamRole.LEADER)
                                .modelName("gpt-4")
                                .build()
                ))
                .modelPool(List.of(
                        poolEntry("gpt-4", "http://a1"),
                        poolEntry("gpt-4", "http://a2"),
                        poolEntry("claude", "http://c1")
                ))
                .modelPoolStrategy("by_model_name")
                .build();

        TeamAgent agent = TeamFactory.createAgentTeam(spec);

        assertThat(agent.getContext().getMetadata().get("member_model")).isInstanceOf(TeamModelConfig.class);
        TeamModelConfig leaderModel = (TeamModelConfig) agent.getContext().getMetadata().get("member_model");
        assertThat(leaderModel.modelClientConfig().getApiBase()).isEqualTo("http://a1");
        Map<?, ?> leaderModelRef = (Map<?, ?>) agent.getContext().getMetadata().get("leader_model_ref");
        assertThat(leaderModelRef.get("model_name")).isEqualTo("gpt-4");
        assertThat(leaderModelRef.get("model_index")).isEqualTo(0);
        assertThat(agent.allocateModel("gpt-4").toTeamModelConfig().modelClientConfig().getApiBase())
                .isEqualTo("http://a2");
    }

    @Test
    void teamAgentShouldInjectAllocatedMemberModelIntoDeepAgentConfigLikePythonConfigurator() {
        TeamAgentSpec spec = TeamAgentSpec.builder()
                .name("deep-model-team")
                .members(List.of(
                        TeamMemberSpec.builder()
                                .name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                                .role(TeamRole.LEADER)
                                .modelName("gpt-4")
                                .build()
                ))
                .modelPool(List.of(
                        poolEntry("gpt-4", "http://a1"),
                        poolEntry("gpt-4", "http://a2")
                ))
                .modelPoolStrategy("by_model_name")
                .build();

        TeamAgent agent = TeamFactory.createAgentTeam(spec);

        assertThat(agent.getDeepAgent().getConfig().getModel()).isInstanceOf(ModelRequestConfig.class);
        assertThat(agent.getDeepAgent().getConfig().getBackend()).isInstanceOf(ModelClientConfig.class);
        ModelRequestConfig request = (ModelRequestConfig) agent.getDeepAgent().getConfig().getModel();
        ModelClientConfig backend = (ModelClientConfig) agent.getDeepAgent().getConfig().getBackend();
        assertThat(request.getModelName()).isEqualTo("gpt-4");
        assertThat(backend.getApiBase()).isEqualTo("http://a1");
        assertThat(backend.getClientProvider()).isEqualTo("OpenAI");
    }

    @Test
    void teamFactoryShouldRejectByNamePoolWithoutLeaderModelName() {
        TeamAgentSpec spec = TeamAgentSpec.builder()
                .name("leader-model-required")
                .members(List.of(
                        TeamMemberSpec.builder()
                                .name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                                .role(TeamRole.LEADER)
                                .build()
                ))
                .modelPool(List.of(poolEntry("gpt-4", "http://a1")))
                .modelPoolStrategy("by_model_name")
                .build();

        assertThatThrownBy(() -> TeamFactory.createAgentTeam(spec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leader.model_name");
    }

    private static ModelPoolEntry poolEntry(String modelName, String apiBaseUrl) {
        Map<String, Object> clientMetadata = new LinkedHashMap<>();
        clientMetadata.put("verify_ssl", false);
        return ModelPoolEntry.builder()
                .modelName(modelName)
                .provider("OpenAI")
                .apiKey("key-" + modelName + "-" + apiBaseUrl)
                .apiBaseUrl(apiBaseUrl)
                .metadata(Map.of("client", clientMetadata))
                .build();
    }
}
