/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_teams.agent.AgentConfigurator;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.agent.RecoveryManager;
import com.openjiuwen.agent_teams.agent.SessionManager;
import com.openjiuwen.agent_teams.runtime.TeamRuntimeMetadata;
import com.openjiuwen.agent_teams.schema.LeaderSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.models.test_allocator} in
 * {@code tests/unit_tests/agent_teams/models/test_allocator.py}.</p>
 */
class ModelAllocatorPythonParityTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TestFactory
    List<DynamicTest> mirrorsPythonAllocatorTests() {
        return List.of(
                test("test_round_robin_allocator_rotates_through_pool",
                        this::roundRobinAllocatorRotatesThroughPool),
                test("test_round_robin_allocator_returns_none_when_pool_empty",
                        this::roundRobinAllocatorReturnsNoneWhenPoolEmpty),
                test("test_model_pool_entry_assigns_unique_model_id_per_instance",
                        this::modelPoolEntryAssignsUniqueModelIdPerInstance),
                test("test_model_pool_entry_to_team_model_config_carries_credentials",
                        this::modelPoolEntryToTeamModelConfigCarriesCredentials),
                test("test_model_pool_entry_metadata_fills_client_and_request_configs",
                        this::modelPoolEntryMetadataFillsClientAndRequestConfigs),
                test("test_model_pool_entry_explicit_fields_override_metadata",
                        this::modelPoolEntryExplicitFieldsOverrideMetadata),
                test("test_model_pool_entry_metadata_extra_keys_are_ignored_by_materialization",
                        this::modelPoolEntryMetadataExtraKeysAreIgnoredByMaterialization),
                test("test_build_model_allocator_returns_round_robin_when_pool_set",
                        this::buildModelAllocatorReturnsRoundRobinWhenPoolSet),
                test("test_build_model_allocator_returns_none_without_pool",
                        this::buildModelAllocatorReturnsNoneWithoutPool),
                test("test_team_spec_model_pool_round_trips_through_json",
                        this::teamSpecModelPoolRoundTripsThroughJson),
                test("test_team_agent_spec_model_pool_round_trips_through_json",
                        this::teamAgentSpecModelPoolRoundTripsThroughJson),
                test("test_allocation_to_db_ref_is_name_plus_group_index",
                        this::allocationToDbRefIsNamePlusGroupIndex),
                test("test_round_robin_allocation_carries_group_index_within_name",
                        this::roundRobinAllocationCarriesGroupIndexWithinName),
                test("test_by_model_name_allocator_rotates_within_named_group",
                        this::byModelNameAllocatorRotatesWithinNamedGroup),
                test("test_by_model_name_allocator_independent_counters_per_name",
                        this::byModelNameAllocatorIndependentCountersPerName),
                test("test_by_model_name_allocator_returns_none_for_unknown_or_missing_name",
                        this::byModelNameAllocatorReturnsNoneForUnknownOrMissingName),
                test("test_by_model_name_allocator_handles_empty_pool",
                        this::byModelNameAllocatorHandlesEmptyPool),
                test("test_round_robin_allocator_ignores_model_name_argument",
                        this::roundRobinAllocatorIgnoresModelNameArgument),
                test("test_build_model_allocator_dispatches_by_strategy",
                        this::buildModelAllocatorDispatchesByStrategy),
                test("test_build_model_allocator_rejects_unknown_strategy",
                        this::buildModelAllocatorRejectsUnknownStrategy),
                test("test_team_agent_spec_propagates_strategy_into_team_spec",
                        this::teamAgentSpecPropagatesStrategyIntoTeamSpec),
                test("test_round_robin_state_dict_round_trip_resumes_rotation",
                        this::roundRobinStateDictRoundTripResumesRotation),
                test("test_round_robin_state_dict_round_trips_through_json",
                        this::roundRobinStateDictRoundTripsThroughJson),
                test("test_round_robin_load_state_dict_resets_on_pool_digest_change",
                        this::roundRobinLoadStateDictResetsOnPoolDigestChange),
                test("test_round_robin_load_state_dict_tolerates_missing_or_bad_input",
                        this::roundRobinLoadStateDictToleratesMissingOrBadInput),
                test("test_by_model_name_state_dict_resumes_per_group_rotation",
                        this::byModelNameStateDictResumesPerGroupRotation),
                test("test_by_model_name_load_state_dict_resets_on_pool_digest_change",
                        this::byModelNameLoadStateDictResetsOnPoolDigestChange),
                test("test_pool_digest_stable_under_credential_refresh",
                        this::poolDigestStableUnderCredentialRefresh),
                test("test_by_model_name_load_state_dict_tolerates_malformed_input",
                        this::byModelNameLoadStateDictToleratesMalformedInput),
                test("test_by_model_name_load_state_dict_accepts_legacy_dict_format",
                        this::byModelNameLoadStateDictAcceptsLegacyDictFormat),
                test("test_by_model_name_state_dict_round_trips_dotted_model_names",
                        this::byModelNameStateDictRoundTripsDottedModelNames),
                test("test_persist_leader_config_includes_allocator_state",
                        this::persistLeaderConfigIncludesAllocatorState),
                test("test_persist_allocator_state_writes_only_allocator_payload",
                        this::persistAllocatorStateWritesOnlyAllocatorPayload),
                test("test_persist_allocator_state_no_op_without_session_or_allocator",
                        this::persistAllocatorStateNoOpWithoutSessionOrAllocator),
                test("test_persist_leader_config_omits_allocator_state_when_no_pool",
                        this::persistLeaderConfigOmitsAllocatorStateWhenNoPool),
                test("test_leader_spec_carries_model_name_for_pool_allocation",
                        this::leaderSpecCarriesModelNameForPoolAllocation),
                test("test_team_member_spec_carries_model_name_for_pool_allocation",
                        this::teamMemberSpecCarriesModelNameForPoolAllocation),
                test("test_resolve_member_model_returns_entry_at_group_index",
                        this::resolveMemberModelReturnsEntryAtGroupIndex),
                test("test_resolve_member_model_picks_up_refreshed_credentials_from_pool",
                        this::resolveMemberModelPicksUpRefreshedCredentialsFromPool),
                test("test_resolve_member_model_clamps_out_of_range_index_to_zero",
                        this::resolveMemberModelClampsOutOfRangeIndexToZero),
                test("test_resolve_member_model_returns_none_when_name_absent_from_pool",
                        this::resolveMemberModelReturnsNoneWhenNameAbsentFromPool),
                test("test_resolve_member_model_returns_none_without_pool",
                        this::resolveMemberModelReturnsNoneWithoutPool),
                test("test_resolve_member_model_tolerates_missing_index",
                        this::resolveMemberModelToleratesMissingIndex),
                test("test_update_model_pool_replaces_pool_and_resets_allocator",
                        this::updateModelPoolReplacesPoolAndResetsAllocator),
                test("test_inherit_pool_ids_preserves_id_for_bit_exact_entry",
                        this::inheritPoolIdsPreservesIdForBitExactEntry),
                test("test_inherit_pool_ids_breaks_inheritance_on_credential_rotation",
                        this::inheritPoolIdsBreaksInheritanceOnCredentialRotation),
                test("test_inherit_pool_ids_breaks_inheritance_on_base_url_migration",
                        this::inheritPoolIdsBreaksInheritanceOnBaseUrlMigration),
                test("test_inherit_pool_ids_breaks_inheritance_on_metadata_change",
                        this::inheritPoolIdsBreaksInheritanceOnMetadataChange),
                test("test_inherit_pool_ids_keeps_own_id_for_truly_new_endpoint",
                        this::inheritPoolIdsKeepsOwnIdForTrulyNewEndpoint),
                test("test_inherit_pool_ids_signature_match_is_order_independent",
                        this::inheritPoolIdsSignatureMatchIsOrderIndependent),
                test("test_inherit_pool_ids_pairs_one_to_one_when_signatures_collide",
                        this::inheritPoolIdsPairsOneToOneWhenSignaturesCollide),
                test("test_inherit_pool_ids_drops_removed_endpoints",
                        this::inheritPoolIdsDropsRemovedEndpoints),
                test("test_inherit_pool_ids_does_not_mutate_input_lists",
                        this::inheritPoolIdsDoesNotMutateInputLists),
                test("test_inherit_pool_ids_handles_empty_inputs",
                        this::inheritPoolIdsHandlesEmptyInputs),
                test("test_build_rejects_by_model_name_pool_without_leader_model_name",
                        this::buildRejectsByModelNamePoolWithoutLeaderModelName),
                test("test_build_rejects_unknown_leader_model_name",
                        this::buildRejectsUnknownLeaderModelName),
                test("test_build_accepts_pool_when_per_agent_leader_model_supplied",
                        this::buildAcceptsPoolWhenPerAgentLeaderModelSupplied),
                test("test_build_round_robin_strategy_does_not_require_leader_model_name",
                        this::buildRoundRobinStrategyDoesNotRequireLeaderModelName),
                test("test_update_model_pool_preserves_id_only_when_entry_is_unchanged",
                        this::updateModelPoolPreservesIdOnlyWhenEntryIsUnchanged),
                test("test_model_router_config_to_pool_entries_shares_credentials",
                        this::modelRouterConfigToPoolEntriesSharesCredentials),
                test("test_model_router_config_to_pool_entries_carries_metadata",
                        this::modelRouterConfigToPoolEntriesCarriesMetadata),
                test("test_model_router_config_metadata_is_isolated_per_entry",
                        this::modelRouterConfigMetadataIsIsolatedPerEntry),
                test("test_model_router_config_rejects_duplicate_names",
                        this::modelRouterConfigRejectsDuplicateNames),
                test("test_model_router_config_rejects_empty_model_names",
                        this::modelRouterConfigRejectsEmptyModelNames),
                test("test_model_router_config_rejects_blank_model_name",
                        this::modelRouterConfigRejectsBlankModelName),
                test("test_model_router_config_rejects_whitespace_only_model_name",
                        this::modelRouterConfigRejectsWhitespaceOnlyModelName),
                test("test_router_allocator_returns_first_entry_without_hint",
                        this::routerAllocatorReturnsFirstEntryWithoutHint),
                test("test_router_allocator_first_entry_is_deterministic",
                        this::routerAllocatorFirstEntryIsDeterministic),
                test("test_router_allocator_returns_named_entry_when_hint_in_list",
                        this::routerAllocatorReturnsNamedEntryWhenHintInList),
                test("test_router_allocator_returns_none_for_unknown_name",
                        this::routerAllocatorReturnsNoneForUnknownName),
                test("test_router_allocator_rejects_empty_pool",
                        this::routerAllocatorRejectsEmptyPool),
                test("test_router_allocator_rejects_duplicate_names_in_pool",
                        this::routerAllocatorRejectsDuplicateNamesInPool),
                test("test_router_allocator_state_dict_round_trips_through_json",
                        this::routerAllocatorStateDictRoundTripsThroughJson),
                test("test_router_allocator_load_state_dict_is_no_op_on_digest_mismatch",
                        this::routerAllocatorLoadStateDictIsNoOpOnDigestMismatch),
                test("test_build_model_allocator_dispatches_router_strategy",
                        this::buildModelAllocatorDispatchesRouterStrategy),
                test("test_team_agent_spec_rejects_pool_and_router_simultaneously",
                        this::teamAgentSpecRejectsPoolAndRouterSimultaneously),
                test("test_team_agent_spec_model_router_round_trips_through_json",
                        this::teamAgentSpecModelRouterRoundTripsThroughJson),
                test("test_build_expands_router_into_team_spec_model_pool",
                        this::buildExpandsRouterIntoTeamSpecModelPool),
                test("test_build_router_falls_back_to_first_name_when_leader_model_name_unset",
                        this::buildRouterFallsBackToFirstNameWhenLeaderModelNameUnset),
                test("test_build_router_honors_explicit_leader_model_name",
                        this::buildRouterHonorsExplicitLeaderModelName),
                test("test_build_router_rejects_unknown_leader_model_name",
                        this::buildRouterRejectsUnknownLeaderModelName)
        );
    }

    private DynamicTest test(String name, Executable executable) {
        return dynamicTest(name, executable);
    }

    private void roundRobinAllocatorRotatesThroughPool() {
        RoundRobinModelAllocator allocator = new RoundRobinModelAllocator(makePool(3));

        List<String> names = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            names.add(allocator.allocate().toTeamModelConfig().getModelRequestConfig().getModelName());
        }

        assertThat(names).containsExactly("m0", "m1", "m2", "m0", "m1", "m2", "m0");
    }

    private void roundRobinAllocatorReturnsNoneWhenPoolEmpty() {
        RoundRobinModelAllocator allocator = new RoundRobinModelAllocator(List.of());

        assertThat(allocator.allocate()).isNull();
        assertThat(allocator.allocate()).isNull();
    }

    private void modelPoolEntryAssignsUniqueModelIdPerInstance() {
        ModelPoolEntry first = entry("m", "k", "http://x", "OpenAI");
        ModelPoolEntry second = entry("m", "k", "http://x", "OpenAI");

        assertThat(first.getModelId()).isNotEqualTo(second.getModelId());
    }

    private void modelPoolEntryToTeamModelConfigCarriesCredentials() {
        ModelPoolEntry entry = entry("m1", "secret", "http://endpoint", "OpenAI");

        ModelPoolEntry.TeamModelConfig config = entry.toTeamModelConfig();

        assertThat(config.getModelClientConfig().getApiKey()).isEqualTo("secret");
        assertThat(config.getModelClientConfig().getApiBase()).isEqualTo("http://endpoint");
        assertThat(config.getModelClientConfig().getClientId()).isEqualTo(entry.getModelId());
        assertThat(config.getModelRequestConfig().getModelName()).isEqualTo("m1");
    }

    private void modelPoolEntryMetadataFillsClientAndRequestConfigs() {
        ModelPoolEntry entry = entry("m1", "secret", "http://endpoint", "OpenAI", Map.of(
                "client", Map.of("timeout", 30.0, "verify_ssl", false, "max_retries", 5),
                "request", Map.of("temperature", 0.2, "top_p", 0.9, "max_tokens", 1024)
        ));

        ModelPoolEntry.TeamModelConfig config = entry.toTeamModelConfig();

        assertThat(config.getModelClientConfig().getTimeout()).isEqualTo(30.0);
        assertThat(config.getModelClientConfig().isVerifySsl()).isFalse();
        assertThat(config.getModelClientConfig().getMaxRetries()).isEqualTo(5);
        assertThat(config.getModelRequestConfig().getTemperature()).isEqualTo(0.2);
        assertThat(config.getModelRequestConfig().getTopP()).isEqualTo(0.9);
        assertThat(config.getModelRequestConfig().getMaxTokens()).isEqualTo(1024);
    }

    private void modelPoolEntryExplicitFieldsOverrideMetadata() {
        ModelPoolEntry entry = entry("m1", "real-key", "http://real", "OpenAI", Map.of(
                "client", Map.of("api_key", "shadow-key", "api_base", "http://shadow"),
                "request", Map.of("model", "shadow-model")
        ));

        ModelPoolEntry.TeamModelConfig config = entry.toTeamModelConfig();

        assertThat(config.getModelClientConfig().getApiKey()).isEqualTo("real-key");
        assertThat(config.getModelClientConfig().getApiBase()).isEqualTo("http://real");
        assertThat(config.getModelRequestConfig().getModelName()).isEqualTo("m1");
    }

    private void modelPoolEntryMetadataExtraKeysAreIgnoredByMaterialization() {
        ModelPoolEntry entry = entry("m1", "k", "http://x", "OpenAI", Map.of(
                "weight", 5,
                "tags", List.of("fast")
        ));

        ModelPoolEntry.TeamModelConfig config = entry.toTeamModelConfig();

        assertThat(config.getModelClientConfig().getApiKey()).isEqualTo("k");
        assertThat(config.getModelRequestConfig().getModelName()).isEqualTo("m1");
    }

    private void buildModelAllocatorReturnsRoundRobinWhenPoolSet() {
        assertThat(ModelAllocators.buildModelAllocator(baseSpec(), teamSpecWithPool(makePool(2))))
                .isInstanceOf(RoundRobinModelAllocator.class);
    }

    private void buildModelAllocatorReturnsNoneWithoutPool() {
        assertThat(ModelAllocators.buildModelAllocator(baseSpec(), new AgentConfigurator.TeamSpec())).isNull();
    }

    private void teamSpecModelPoolRoundTripsThroughJson() throws Exception {
        com.openjiuwen.agent_teams.schema.TeamSpec teamSpec =
                new com.openjiuwen.agent_teams.schema.TeamSpec("t", "t", "leader");
        teamSpec.setModelPool(makePool(2));

        com.openjiuwen.agent_teams.schema.TeamSpec restored =
                JSON.readValue(JSON.writeValueAsString(teamSpec), com.openjiuwen.agent_teams.schema.TeamSpec.class);

        assertThat(restored.getModelPool()).hasSize(2);
        assertThat(restored.getModelPool().get(0).getModelName()).isEqualTo("m0");
        assertThat(restored.getModelPool().get(1).getApiBaseUrl()).isEqualTo("http://h1");
    }

    private void teamAgentSpecModelPoolRoundTripsThroughJson() throws Exception {
        TeamAgentSpec spec = baseSpec();
        spec.setModelPool(makePool(3));

        TeamAgentSpec restored = JSON.readValue(JSON.writeValueAsString(spec), TeamAgentSpec.class);

        assertThat(restored.getModelPool()).extracting(ModelPoolEntry::getModelName)
                .containsExactly("m0", "m1", "m2");
    }

    private void allocationToDbRefIsNamePlusGroupIndex() {
        Allocation allocation = new Allocation(namedEntry("gpt-4", "a1"), 2);

        assertThat(allocation.toDbRef()).containsEntry("model_name", "gpt-4").containsEntry("model_index", 2);
    }

    private void roundRobinAllocationCarriesGroupIndexWithinName() {
        RoundRobinModelAllocator allocator = new RoundRobinModelAllocator(List.of(
                namedEntry("gpt-4", "a1"),
                namedEntry("claude", "c1"),
                namedEntry("gpt-4", "a2")
        ));

        assertThat(allocator.allocate().toDbRef()).containsEntry("model_name", "gpt-4")
                .containsEntry("model_index", 0);
        assertThat(allocator.allocate().toDbRef()).containsEntry("model_name", "claude")
                .containsEntry("model_index", 0);
        assertThat(allocator.allocate().toDbRef()).containsEntry("model_name", "gpt-4")
                .containsEntry("model_index", 1);
    }

    private void byModelNameAllocatorRotatesWithinNamedGroup() {
        ByModelNameAllocator allocator = new ByModelNameAllocator(List.of(
                namedEntry("gpt-4", "a1"),
                namedEntry("gpt-4", "a2"),
                namedEntry("gpt-4", "a3"),
                namedEntry("claude", "c1")
        ));

        List<String> bases = List.of(
                allocator.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase(),
                allocator.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase(),
                allocator.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase()
        );

        assertThat(bases).containsExactly("http://a1", "http://a2", "http://a3");
        assertThat(allocator.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://a1");
        assertThat(allocator.allocate("claude").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://c1");
        assertThat(allocator.allocate("claude").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://c1");
    }

    private void byModelNameAllocatorIndependentCountersPerName() {
        ByModelNameAllocator allocator = new ByModelNameAllocator(List.of(
                namedEntry("gpt-4", "a1"),
                namedEntry("gpt-4", "a2"),
                namedEntry("claude", "c1"),
                namedEntry("claude", "c2")
        ));

        for (int i = 0; i < 5; i++) {
            allocator.allocate("gpt-4");
        }

        assertThat(allocator.allocate("claude").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://c1");
    }

    private void byModelNameAllocatorReturnsNoneForUnknownOrMissingName() {
        ByModelNameAllocator allocator = new ByModelNameAllocator(List.of(namedEntry("gpt-4", "a1")));

        assertThat(allocator.allocate(null)).isNull();
        assertThat(allocator.allocate()).isNull();
        assertThat(allocator.allocate("")).isNull();
        assertThat(allocator.allocate("gemini")).isNull();
        assertThat(allocator.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://a1");
    }

    private void byModelNameAllocatorHandlesEmptyPool() {
        ByModelNameAllocator allocator = new ByModelNameAllocator(List.of());

        assertThat(allocator.allocate("gpt-4")).isNull();
        assertThat(allocator.allocate()).isNull();
    }

    private void roundRobinAllocatorIgnoresModelNameArgument() {
        RoundRobinModelAllocator allocator = new RoundRobinModelAllocator(List.of(
                namedEntry("gpt-4", "a1"),
                namedEntry("claude", "c1")
        ));

        assertThat(allocator.allocate("claude").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://a1");
        assertThat(allocator.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://c1");
        assertThat(allocator.allocate("claude").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://a1");
    }

    private void buildModelAllocatorDispatchesByStrategy() {
        List<ModelPoolEntry> pool = List.of(namedEntry("gpt-4", "a1"), namedEntry("claude", "c1"));
        AgentConfigurator.TeamSpec roundRobin = teamSpecWithPool(pool);
        roundRobin.setModelPoolStrategy("round_robin");
        AgentConfigurator.TeamSpec byName = teamSpecWithPool(pool);
        byName.setModelPoolStrategy("by_model_name");

        assertThat(ModelAllocators.buildModelAllocator(baseSpec(), roundRobin))
                .isInstanceOf(RoundRobinModelAllocator.class);
        assertThat(ModelAllocators.buildModelAllocator(baseSpec(), byName))
                .isInstanceOf(ByModelNameAllocator.class);
    }

    private void buildModelAllocatorRejectsUnknownStrategy() {
        AgentConfigurator.TeamSpec teamSpec = teamSpecWithPool(List.of(namedEntry("gpt-4", "a1")));
        teamSpec.setModelPoolStrategy("weighted");

        assertThatThrownBy(() -> ModelAllocators.buildModelAllocator(baseSpec(), teamSpec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown model_pool_strategy");
    }

    private void teamAgentSpecPropagatesStrategyIntoTeamSpec() throws Exception {
        TeamAgentSpec spec = baseSpec();
        spec.setModelPool(List.of(namedEntry("gpt-4", "a1")));
        spec.setModelPoolStrategy("by_model_name");

        TeamAgentSpec restored = JSON.readValue(JSON.writeValueAsString(spec), TeamAgentSpec.class);

        assertThat(restored.getModelPoolStrategy()).isEqualTo("by_model_name");
    }

    private void roundRobinStateDictRoundTripResumesRotation() {
        RoundRobinModelAllocator first = new RoundRobinModelAllocator(makePool(3));
        first.allocate();
        first.allocate();
        Map<String, Object> snapshot = first.stateDict();

        RoundRobinModelAllocator restored = new RoundRobinModelAllocator(makePool(3));
        restored.loadStateDict(snapshot);

        assertThat(restored.allocate().toTeamModelConfig().getModelRequestConfig().getModelName()).isEqualTo("m2");
        assertThat(new RoundRobinModelAllocator(makePool(3)).allocate()
                .toTeamModelConfig().getModelRequestConfig().getModelName()).isEqualTo("m0");
    }

    private void roundRobinStateDictRoundTripsThroughJson() throws Exception {
        RoundRobinModelAllocator first = new RoundRobinModelAllocator(makePool(2));
        first.allocate();
        Map<String, Object> decoded = JSON.readValue(
                JSON.writeValueAsString(first.stateDict()),
                new TypeReference<>() {
                }
        );

        RoundRobinModelAllocator restored = new RoundRobinModelAllocator(makePool(2));
        restored.loadStateDict(decoded);

        assertThat(restored.allocate().toTeamModelConfig().getModelRequestConfig().getModelName()).isEqualTo("m1");
    }

    private void roundRobinLoadStateDictResetsOnPoolDigestChange() {
        RoundRobinModelAllocator first = new RoundRobinModelAllocator(makePool(3));
        first.allocate();
        first.allocate();
        Map<String, Object> snapshot = first.stateDict();

        RoundRobinModelAllocator restored = new RoundRobinModelAllocator(makePool(2));
        restored.loadStateDict(snapshot);

        assertThat(restored.allocate().toTeamModelConfig().getModelRequestConfig().getModelName()).isEqualTo("m0");
    }

    private void roundRobinLoadStateDictToleratesMissingOrBadInput() {
        RoundRobinModelAllocator allocator = new RoundRobinModelAllocator(makePool(2));

        allocator.loadStateDict(Map.of());
        assertThat(allocator.allocate().toTeamModelConfig().getModelRequestConfig().getModelName()).isEqualTo("m0");

        allocator.loadStateDict(Map.of("index", "not-an-int", "pool_digest", allocator.stateDict().get("pool_digest")));
        assertThat(allocator.allocate().toTeamModelConfig().getModelRequestConfig().getModelName()).isEqualTo("m0");
    }

    private void byModelNameStateDictResumesPerGroupRotation() {
        List<ModelPoolEntry> pool = List.of(
                namedEntry("gpt-4", "a1"),
                namedEntry("gpt-4", "a2"),
                namedEntry("gpt-4", "a3"),
                namedEntry("claude", "c1"),
                namedEntry("claude", "c2")
        );
        ByModelNameAllocator first = new ByModelNameAllocator(pool);
        first.allocate("gpt-4");
        first.allocate("gpt-4");
        first.allocate("claude");

        ByModelNameAllocator restored = new ByModelNameAllocator(pool);
        restored.loadStateDict(first.stateDict());

        assertThat(restored.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://a3");
        assertThat(restored.allocate("claude").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://c2");
    }

    private void byModelNameLoadStateDictResetsOnPoolDigestChange() {
        ByModelNameAllocator first = new ByModelNameAllocator(List.of(
                namedEntry("gpt-4", "a1"),
                namedEntry("claude", "c1")
        ));
        first.allocate("gpt-4");
        first.allocate("claude");

        ByModelNameAllocator restored = new ByModelNameAllocator(List.of(
                namedEntry("gpt-4", "a1"),
                namedEntry("gpt-4", "a2"),
                namedEntry("gemini", "g1")
        ));
        restored.loadStateDict(first.stateDict());

        assertThat(restored.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://a1");
        assertThat(restored.allocate("gemini").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://g1");
    }

    private void poolDigestStableUnderCredentialRefresh() {
        ModelPoolEntry original = entry("gpt-4", "OLD", "http://x", "OpenAI");
        ByModelNameAllocator first = new ByModelNameAllocator(List.of(original));
        first.allocate("gpt-4");

        ModelPoolEntry refreshed = entry("gpt-4", "NEW", "http://x", "OpenAI");
        ByModelNameAllocator restored = new ByModelNameAllocator(List.of(refreshed));
        restored.loadStateDict(first.stateDict());

        assertThat(restored.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiKey())
                .isEqualTo("NEW");
    }

    private void byModelNameLoadStateDictToleratesMalformedInput() {
        List<ModelPoolEntry> pool = List.of(namedEntry("gpt-4", "a1"), namedEntry("claude", "c1"));
        ByModelNameAllocator allocator = new ByModelNameAllocator(pool);
        Object digest = allocator.stateDict().get("pool_digest");

        allocator.loadStateDict(Map.of("counters", "not-a-list", "pool_digest", digest));
        assertThat(allocator.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://a1");

        allocator.loadStateDict(Map.of("counters", List.of(Map.of("model_name", "gpt-4", "index", "bogus")),
                "pool_digest", digest));
        assertThat(allocator.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://a1");
    }

    private void byModelNameLoadStateDictAcceptsLegacyDictFormat() {
        List<ModelPoolEntry> pool = List.of(
                namedEntry("gpt-4", "a1"),
                namedEntry("gpt-4", "a2"),
                namedEntry("claude", "c1"),
                namedEntry("claude", "c2")
        );
        ByModelNameAllocator allocator = new ByModelNameAllocator(pool);
        Object digest = allocator.stateDict().get("pool_digest");

        allocator.loadStateDict(Map.of("inner_indexes", Map.of("gpt-4", 1, "claude", 1), "pool_digest", digest));

        assertThat(allocator.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://a2");
        assertThat(allocator.allocate("claude").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://c2");
    }

    private void byModelNameStateDictRoundTripsDottedModelNames() {
        List<ModelPoolEntry> pool = List.of(
                namedEntry("glm-5", "g5a"),
                namedEntry("glm-5", "g5b"),
                namedEntry("glm-5.1", "g51a"),
                namedEntry("claude-3.5-sonnet", "c35")
        );
        ByModelNameAllocator first = new ByModelNameAllocator(pool);
        first.allocate("glm-5");
        first.allocate("glm-5.1");
        Map<String, Object> snapshot = first.stateDict();

        assertThat(snapshot.get("counters")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> counters = (List<Map<String, Object>>) snapshot.get("counters");
        assertThat(counters).extracting(record -> record.get("model_name"))
                .containsExactly("glm-5", "glm-5.1", "claude-3.5-sonnet");

        ByModelNameAllocator restored = new ByModelNameAllocator(pool);
        restored.loadStateDict(snapshot);
        assertThat(restored.allocate("glm-5").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://g5b");
        assertThat(restored.allocate("glm-5.1").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://g51a");
    }

    private void persistLeaderConfigIncludesAllocatorState() {
        ByModelNameAllocator allocator = new ByModelNameAllocator(List.of(
                namedEntry("gpt-4", "a1"),
                namedEntry("claude", "c1")
        ));
        AgentConfigurator configurator = configuredConfigurator(allocator);
        allocator.allocate("gpt-4");
        allocator.allocate("claude");
        FakeTeamSession session = new FakeTeamSession("persist-leader");

        new RecoveryManager(configurator, new NoopSpawnManager()).persistLeaderConfig(session);

        Map<String, Object> payload = TeamRuntimeMetadata.readTeamNamespace(session, "t");
        assertThat(payload).containsKey("model_allocator_state");
        assertThat(payload.get("model_allocator_state")).isInstanceOf(Map.class);
    }

    private void persistAllocatorStateWritesOnlyAllocatorPayload() {
        RoundRobinModelAllocator allocator = new RoundRobinModelAllocator(makePool(3));
        AgentConfigurator configurator = configuredConfigurator(allocator);
        allocator.allocate();
        allocator.allocate();
        FakeTeamSession session = new FakeTeamSession("allocator-only");

        new RecoveryManager(configurator, new NoopSpawnManager()).persistAllocatorState(session);

        Map<String, Object> payload = TeamRuntimeMetadata.readTeamNamespace(session, "t");
        assertThat(payload.get("model_allocator_state")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) payload.get("model_allocator_state")).get("index")).isEqualTo(2);
    }

    private void persistAllocatorStateNoOpWithoutSessionOrAllocator() {
        assertThatCode(() -> new RecoveryManager(
                configuredConfigurator(new RoundRobinModelAllocator(makePool(2))),
                new NoopSpawnManager()).persistAllocatorState(null))
                .doesNotThrowAnyException();

        AgentConfigurator configurator = configuredConfigurator(null);
        FakeTeamSession session = new FakeTeamSession("no-allocator");

        assertThatCode(() -> new RecoveryManager(configurator, new NoopSpawnManager()).persistAllocatorState(session))
                .doesNotThrowAnyException();
        assertThat(TeamRuntimeMetadata.readTeamNamespace(session, "t")).isNull();
    }

    private void persistLeaderConfigOmitsAllocatorStateWhenNoPool() {
        AgentConfigurator configurator = configuredConfigurator(null, List.of(), "round_robin");
        FakeTeamSession session = new FakeTeamSession("no-pool");

        new RecoveryManager(configurator, new NoopSpawnManager()).persistLeaderConfig(session);

        Map<String, Object> payload = TeamRuntimeMetadata.readTeamNamespace(session, "t");
        assertThat(payload).doesNotContainKey("model_allocator_state");
        assertThat(payload).containsKey("context");
    }

    private void leaderSpecCarriesModelNameForPoolAllocation() throws Exception {
        LeaderSpec leader = new LeaderSpec();
        leader.setModelName("gpt-4");

        LeaderSpec restored = JSON.readValue(JSON.writeValueAsString(leader), LeaderSpec.class);

        assertThat(restored.getModelName()).isEqualTo("gpt-4");
    }

    private void teamMemberSpecCarriesModelNameForPoolAllocation() throws Exception {
        AgentConfigurator.TeamMemberSpec member = new AgentConfigurator.TeamMemberSpec();
        member.setMemberName("dev1");
        member.setDisplayName("Dev 1");
        member.setPersona("backend");
        member.setModelName("claude");

        AgentConfigurator.TeamMemberSpec restored =
                JSON.readValue(JSON.writeValueAsString(member), AgentConfigurator.TeamMemberSpec.class);

        assertThat(restored.getModelName()).isEqualTo("claude");
    }

    private void resolveMemberModelReturnsEntryAtGroupIndex() {
        AgentConfigurator.TeamSpec teamSpec = teamSpecWithPool(List.of(
                namedEntry("gpt-4", "a1"),
                namedEntry("gpt-4", "a2"),
                namedEntry("claude", "c1")
        ));

        ModelPoolEntry.TeamModelConfig config = ModelAllocators.resolveMemberModel(teamSpec, "gpt-4", 1);

        assertThat(config.getModelClientConfig().getApiBase()).isEqualTo("http://a2");
    }

    private void resolveMemberModelPicksUpRefreshedCredentialsFromPool() {
        AgentConfigurator.TeamSpec teamSpec =
                teamSpecWithPool(List.of(entry("gpt-4", "NEW-KEY", "http://new", "OpenAI")));

        ModelPoolEntry.TeamModelConfig config = ModelAllocators.resolveMemberModel(teamSpec, "gpt-4", 0);

        assertThat(config.getModelClientConfig().getApiKey()).isEqualTo("NEW-KEY");
        assertThat(config.getModelClientConfig().getApiBase()).isEqualTo("http://new");
    }

    private void resolveMemberModelClampsOutOfRangeIndexToZero() {
        AgentConfigurator.TeamSpec teamSpec =
                teamSpecWithPool(List.of(namedEntry("gpt-4", "a1"), namedEntry("gpt-4", "a2")));

        assertThat(ModelAllocators.resolveMemberModel(teamSpec, "gpt-4", 5)
                .getModelClientConfig().getApiBase()).isEqualTo("http://a1");
    }

    private void resolveMemberModelReturnsNoneWhenNameAbsentFromPool() {
        assertThat(ModelAllocators.resolveMemberModel(
                teamSpecWithPool(List.of(namedEntry("gpt-4", "a1"))), "gemini", 0)).isNull();
    }

    private void resolveMemberModelReturnsNoneWithoutPool() {
        assertThat(ModelAllocators.resolveMemberModel(new AgentConfigurator.TeamSpec(), "gpt-4", 0)).isNull();
    }

    private void resolveMemberModelToleratesMissingIndex() {
        assertThat(ModelAllocators.resolveMemberModel(
                teamSpecWithPool(List.of(namedEntry("gpt-4", "a1"))), "gpt-4", null)
                .getModelClientConfig().getApiBase()).isEqualTo("http://a1");
    }

    private void updateModelPoolReplacesPoolAndResetsAllocator() {
        ByModelNameAllocator allocator = new ByModelNameAllocator(List.of(
                namedEntry("gpt-4", "a1"),
                namedEntry("gpt-4", "a2")
        ));
        allocator.allocate("gpt-4");
        allocator.allocate("gpt-4");
        AgentConfigurator configurator = configuredConfigurator(allocator);

        configurator.updateModelPool(List.of(namedEntry("gpt-4", "b1"), namedEntry("claude", "c1")));

        assertThat(configurator.getCtx().getTeamSpec().getModelPool()).hasSize(2);
        ModelAllocator refreshed = (ModelAllocator) configurator.getResources().getModelAllocator();
        assertThat(refreshed.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase())
                .isEqualTo("http://b1");
    }

    private void inheritPoolIdsPreservesIdForBitExactEntry() {
        ModelPoolEntry oldEntry = entry("gpt-4", "K", "http://x", "OpenAI");
        ModelPoolEntry fresh = entry("gpt-4", "K", "http://x", "OpenAI");

        assertThat(fresh.getModelId()).isNotEqualTo(oldEntry.getModelId());
        assertThat(ModelPoolSupport.inheritPoolIds(List.of(oldEntry), List.of(fresh)).get(0).getModelId())
                .isEqualTo(oldEntry.getModelId());
    }

    private void inheritPoolIdsBreaksInheritanceOnCredentialRotation() {
        ModelPoolEntry oldEntry = entry("gpt-4", "OLD", "http://x", "OpenAI");
        ModelPoolEntry rotated = entry("gpt-4", "ROTATED", "http://x", "OpenAI");

        ModelPoolEntry merged = ModelPoolSupport.inheritPoolIds(List.of(oldEntry), List.of(rotated)).get(0);

        assertThat(merged.getModelId()).isEqualTo(rotated.getModelId());
        assertThat(merged.getApiKey()).isEqualTo("ROTATED");
    }

    private void inheritPoolIdsBreaksInheritanceOnBaseUrlMigration() {
        ModelPoolEntry oldEntry = entry("gpt-4", "K", "http://old", "OpenAI");
        ModelPoolEntry migrated = entry("gpt-4", "K", "http://new", "OpenAI");

        assertThat(ModelPoolSupport.inheritPoolIds(List.of(oldEntry), List.of(migrated)).get(0).getModelId())
                .isEqualTo(migrated.getModelId());
    }

    private void inheritPoolIdsBreaksInheritanceOnMetadataChange() {
        ModelPoolEntry oldEntry = entry("gpt-4", "K", "http://x", "OpenAI",
                Map.of("client", Map.of("timeout", 30.0)));
        ModelPoolEntry tuned = entry("gpt-4", "K", "http://x", "OpenAI",
                Map.of("client", Map.of("timeout", 60.0)));

        assertThat(ModelPoolSupport.inheritPoolIds(List.of(oldEntry), List.of(tuned)).get(0).getModelId())
                .isEqualTo(tuned.getModelId());
    }

    private void inheritPoolIdsKeepsOwnIdForTrulyNewEndpoint() {
        ModelPoolEntry oldEntry = entry("gpt-4", "k", "http://a", "OpenAI");
        ModelPoolEntry newEndpoint = entry("claude", "k", "http://b", "OpenAI");

        assertThat(ModelPoolSupport.inheritPoolIds(List.of(oldEntry), List.of(newEndpoint)).get(0).getModelId())
                .isEqualTo(newEndpoint.getModelId());
    }

    private void inheritPoolIdsSignatureMatchIsOrderIndependent() {
        ModelPoolEntry oldFirst = entry("gpt-4", "K1", "http://x", "OpenAI");
        ModelPoolEntry oldSecond = entry("gpt-4", "K2", "http://x", "OpenAI");
        ModelPoolEntry newFirst = entry("gpt-4", "K2", "http://x", "OpenAI");
        ModelPoolEntry newSecond = entry("gpt-4", "K1", "http://x", "OpenAI");

        List<ModelPoolEntry> merged =
                ModelPoolSupport.inheritPoolIds(List.of(oldFirst, oldSecond), List.of(newFirst, newSecond));

        assertThat(merged.get(0).getModelId()).isEqualTo(oldSecond.getModelId());
        assertThat(merged.get(1).getModelId()).isEqualTo(oldFirst.getModelId());
    }

    private void inheritPoolIdsPairsOneToOneWhenSignaturesCollide() {
        ModelPoolEntry oldFirst = entry("gpt-4", "K", "http://x", "OpenAI");
        ModelPoolEntry oldSecond = entry("gpt-4", "K", "http://x", "OpenAI");

        List<ModelPoolEntry> merged = ModelPoolSupport.inheritPoolIds(
                List.of(oldFirst, oldSecond),
                List.of(entry("gpt-4", "K", "http://x", "OpenAI"),
                        entry("gpt-4", "K", "http://x", "OpenAI"))
        );

        assertThat(merged).extracting(ModelPoolEntry::getModelId)
                .containsExactly(oldFirst.getModelId(), oldSecond.getModelId());
    }

    private void inheritPoolIdsDropsRemovedEndpoints() {
        ModelPoolEntry oldFirst = entry("gpt-4", "K", "http://a", "OpenAI");
        ModelPoolEntry oldSecond = entry("claude", "K", "http://b", "OpenAI");

        List<ModelPoolEntry> merged = ModelPoolSupport.inheritPoolIds(
                List.of(oldFirst, oldSecond),
                List.of(entry("gpt-4", "K", "http://a", "OpenAI"))
        );

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).getModelId()).isEqualTo(oldFirst.getModelId());
    }

    private void inheritPoolIdsDoesNotMutateInputLists() {
        ModelPoolEntry oldEntry = entry("gpt-4", "K", "http://x", "OpenAI");
        ModelPoolEntry fresh = entry("gpt-4", "K", "http://x", "OpenAI");
        String freshIdBefore = fresh.getModelId();

        ModelPoolSupport.inheritPoolIds(List.of(oldEntry), List.of(fresh));

        assertThat(fresh.getModelId()).isEqualTo(freshIdBefore);
    }

    private void inheritPoolIdsHandlesEmptyInputs() {
        assertThat(ModelPoolSupport.inheritPoolIds(List.of(), List.of())).isEmpty();
        assertThat(ModelPoolSupport.inheritPoolIds(List.of(), List.of(namedEntry("gpt-4", "a1")))).isNotEmpty();
    }

    private void buildRejectsByModelNamePoolWithoutLeaderModelName() {
        TeamAgentSpec spec = baseSpec();
        spec.setModelPool(List.of(namedEntry("gpt-4", "a1")));
        spec.setModelPoolStrategy("by_model_name");

        assertThatThrownBy(spec::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires leader.model_name");
    }

    private void buildRejectsUnknownLeaderModelName() {
        TeamAgentSpec spec = baseSpec();
        spec.getLeader().setModelName("claude");
        spec.setModelPool(List.of(namedEntry("gpt-4", "a1")));
        spec.setModelPoolStrategy("by_model_name");

        assertThatThrownBy(spec::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not present in the pool");
    }

    private void buildAcceptsPoolWhenPerAgentLeaderModelSupplied() {
        TeamAgentSpec spec = baseSpec();
        spec.getAgents().get("leader").setModel(explicitModel("explicit-model"));
        AgentConfigurator.TeamSpec teamSpec = teamSpecWithPool(List.of(namedEntry("gpt-4", "a1")));
        teamSpec.setModelPoolStrategy("by_model_name");

        assertThat(ModelAllocators.buildModelAllocator(spec, teamSpec)).isInstanceOf(ByModelNameAllocator.class);
        assertThat(spec.getAgents().get("leader").getModel()).isNotNull();
    }

    private void buildRoundRobinStrategyDoesNotRequireLeaderModelName() {
        AgentConfigurator.TeamSpec teamSpec = teamSpecWithPool(List.of(entry("gpt-4", "k", "http://x", "OpenAI",
                Map.of("client", Map.of("verify_ssl", false)))));
        teamSpec.setModelPoolStrategy("round_robin");
        ModelAllocator allocator = ModelAllocators.buildModelAllocator(baseSpec(), teamSpec);

        assertThat(allocator.allocate(null)).isNotNull();
    }

    private void updateModelPoolPreservesIdOnlyWhenEntryIsUnchanged() {
        ModelPoolEntry initial = entry("gpt-4", "K", "http://x", "OpenAI");
        AgentConfigurator configurator = configuredConfigurator(new ByModelNameAllocator(List.of(initial)));
        configurator.getCtx().getTeamSpec().setModelPool(List.of(initial));
        String oldId = initial.getModelId();

        configurator.updateModelPool(List.of(entry("gpt-4", "K", "http://x", "OpenAI")));
        assertThat(poolEntry(configurator, 0).getModelId()).isEqualTo(oldId);

        configurator.updateModelPool(List.of(entry("gpt-4", "ROTATED", "http://x", "OpenAI")));
        assertThat(poolEntry(configurator, 0).getModelId()).isNotEqualTo(oldId);
        assertThat(poolEntry(configurator, 0).getApiKey()).isEqualTo("ROTATED");
    }

    private void modelRouterConfigToPoolEntriesSharesCredentials() {
        List<ModelPoolEntry> entries = routerConfig().toPoolEntries();

        assertThat(entries).extracting(ModelPoolEntry::getModelName)
                .containsExactly("gpt-4o", "claude-opus", "gemini-pro");
        assertThat(entries).extracting(ModelPoolEntry::getApiKey).containsOnly("sk-shared");
        assertThat(entries).extracting(ModelPoolEntry::getApiBaseUrl).containsOnly("https://router.test/v1");
        assertThat(entries).extracting(ModelPoolEntry::getApiProvider).containsOnly("OpenAI");
    }

    private void modelRouterConfigToPoolEntriesCarriesMetadata() {
        List<ModelPoolEntry> entries = routerConfig(null, Map.of("client", Map.of("timeout", 45.0))).toPoolEntries();

        assertThat(entries).allSatisfy(entry ->
                assertThat(entry.toTeamModelConfig().getModelClientConfig().getTimeout()).isEqualTo(45.0));
    }

    private void modelRouterConfigMetadataIsIsolatedPerEntry() {
        ModelRouterConfig router = routerConfig(null, Map.of("client", Map.of("timeout", 30.0)));
        List<ModelPoolEntry> entries = router.toPoolEntries();

        entries.get(0).getMetadata().put("client", Map.of("timeout", 99.0));

        assertThat(entries.get(1).getMetadata()).isEqualTo(Map.of("client", Map.of("timeout", 30.0)));
        assertThat(router.getMetadata()).isEqualTo(Map.of("client", Map.of("timeout", 30.0)));
    }

    private void modelRouterConfigRejectsDuplicateNames() {
        assertThatThrownBy(() -> routerConfig(List.of("gpt-4o", "gpt-4o", "claude-opus"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicates");
    }

    private void modelRouterConfigRejectsEmptyModelNames() {
        assertThatThrownBy(() -> routerConfig(List.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void modelRouterConfigRejectsBlankModelName() {
        assertThatThrownBy(() -> routerConfig(List.of("gpt-4o", ""), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-empty strings");
    }

    private void modelRouterConfigRejectsWhitespaceOnlyModelName() {
        assertThatThrownBy(() -> routerConfig(List.of("   ", "claude-opus"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-empty strings");
    }

    private void routerAllocatorReturnsFirstEntryWithoutHint() {
        RouterAllocator allocator = new RouterAllocator(routerConfig().toPoolEntries());

        Allocation allocation = allocator.allocate();

        assertThat(allocation.getEntry().getModelName()).isEqualTo("gpt-4o");
        assertThat(allocation.getGroupIndex()).isZero();
    }

    private void routerAllocatorFirstEntryIsDeterministic() {
        RouterAllocator allocator = new RouterAllocator(routerConfig().toPoolEntries());

        List<String> names = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            names.add(allocator.allocate().getEntry().getModelName());
        }

        assertThat(names).containsExactly("gpt-4o", "gpt-4o", "gpt-4o", "gpt-4o", "gpt-4o");
    }

    private void routerAllocatorReturnsNamedEntryWhenHintInList() {
        RouterAllocator allocator = new RouterAllocator(routerConfig().toPoolEntries());

        ModelPoolEntry.TeamModelConfig config = allocator.allocate("claude-opus").toTeamModelConfig();

        assertThat(config.getModelRequestConfig().getModelName()).isEqualTo("claude-opus");
        assertThat(config.getModelClientConfig().getApiBase()).isEqualTo("https://router.test/v1");
        assertThat(config.getModelClientConfig().getApiKey()).isEqualTo("sk-shared");
    }

    private void routerAllocatorReturnsNoneForUnknownName() {
        assertThat(new RouterAllocator(routerConfig().toPoolEntries()).allocate("missing-model")).isNull();
    }

    private void routerAllocatorRejectsEmptyPool() {
        assertThatThrownBy(() -> new RouterAllocator(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-empty pool");
    }

    private void routerAllocatorRejectsDuplicateNamesInPool() {
        assertThatThrownBy(() -> new RouterAllocator(List.of(namedEntry("gpt-4o", "a1"), namedEntry("gpt-4o", "a2"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique model_names");
    }

    private void routerAllocatorStateDictRoundTripsThroughJson() throws Exception {
        RouterAllocator allocator = new RouterAllocator(routerConfig().toPoolEntries());
        allocator.allocate("claude-opus");
        Map<String, Object> encoded = JSON.readValue(
                JSON.writeValueAsString(allocator.stateDict()),
                new TypeReference<>() {
                }
        );

        RouterAllocator restored = new RouterAllocator(routerConfig().toPoolEntries());
        restored.loadStateDict(encoded);

        assertThat(encoded).containsKey("pool_digest");
        assertThat(restored.allocate().getEntry().getModelName()).isEqualTo("gpt-4o");
    }

    private void routerAllocatorLoadStateDictIsNoOpOnDigestMismatch() {
        Map<String, Object> snapshot = new RouterAllocator(
                routerConfig(List.of("a", "b"), Map.of()).toPoolEntries()).stateDict();
        RouterAllocator allocator = new RouterAllocator(routerConfig(List.of("c", "d"), Map.of()).toPoolEntries());

        allocator.loadStateDict(snapshot);

        assertThat(allocator.allocate().getEntry().getModelName()).isEqualTo("c");
    }

    private void buildModelAllocatorDispatchesRouterStrategy() {
        AgentConfigurator.TeamSpec teamSpec = teamSpecWithPool(routerConfig().toPoolEntries());
        teamSpec.setModelPoolStrategy("router");

        assertThat(ModelAllocators.buildModelAllocator(baseSpec(), teamSpec)).isInstanceOf(RouterAllocator.class);
    }

    private void teamAgentSpecRejectsPoolAndRouterSimultaneously() {
        TeamAgentSpec spec = baseSpec();
        spec.setModelPool(makePool(2));
        spec.setModelRouter(routerConfig());

        assertThatThrownBy(spec::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
    }

    private void teamAgentSpecModelRouterRoundTripsThroughJson() throws Exception {
        TeamAgentSpec spec = baseSpec();
        spec.setModelRouter(routerConfig());

        TeamAgentSpec restored = JSON.readValue(JSON.writeValueAsString(spec), TeamAgentSpec.class);

        assertThat(restored.getModelRouter()).isNotNull();
        assertThat(restored.getModelRouter().getModelNames())
                .containsExactly("gpt-4o", "claude-opus", "gemini-pro");
        assertThat(restored.getModelRouter().getApiKey()).isEqualTo("sk-shared");
    }

    private void buildExpandsRouterIntoTeamSpecModelPool() {
        AgentConfigurator.TeamSpec teamSpec =
                teamSpecWithPool(routerConfig(List.of("m1", "m2"), Map.of("client", Map.of("verify_ssl", false)))
                        .toPoolEntries());
        teamSpec.setModelPoolStrategy("router");

        assertThat(teamSpec.getModelPoolStrategy()).isEqualTo("router");
        assertThat(teamSpec.getModelPool()).extracting(value -> ((ModelPoolEntry) value).getModelName())
                .containsExactly("m1", "m2");
        assertThat(ModelAllocators.buildModelAllocator(baseSpec(), teamSpec)).isInstanceOf(RouterAllocator.class);
    }

    private void buildRouterFallsBackToFirstNameWhenLeaderModelNameUnset() {
        RouterAllocator allocator = new RouterAllocator(
                routerConfig(List.of("primary", "secondary"), Map.of("client", Map.of("verify_ssl", false)))
                        .toPoolEntries());
        ModelPoolEntry.TeamModelConfig leaderModel = allocator.allocate(null).toTeamModelConfig();

        assertThat(leaderModel.getModelRequestConfig().getModelName()).isEqualTo("primary");
    }

    private void buildRouterHonorsExplicitLeaderModelName() {
        RouterAllocator allocator = new RouterAllocator(
                routerConfig(List.of("primary", "secondary"), Map.of("client", Map.of("verify_ssl", false)))
                        .toPoolEntries());
        ModelPoolEntry.TeamModelConfig leaderModel = allocator.allocate("secondary").toTeamModelConfig();

        assertThat(leaderModel.getModelRequestConfig().getModelName()).isEqualTo("secondary");
    }

    private void buildRouterRejectsUnknownLeaderModelName() {
        TeamAgentSpec spec = baseSpec();
        spec.setSpawnMode("inprocess");
        spec.setModelRouter(routerConfig(List.of("primary", "secondary"), Map.of("client", Map.of("verify_ssl", false))));
        spec.getLeader().setModelName("missing");

        assertThatThrownBy(spec::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not present in the router");
    }

    private static List<ModelPoolEntry> makePool(int count) {
        List<ModelPoolEntry> pool = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pool.add(entry("m" + i, "k" + i, "http://h" + i, "OpenAI"));
        }
        return pool;
    }

    private static ModelPoolEntry namedEntry(String name, String suffix) {
        return entry(name, "k-" + suffix, "http://" + suffix, "OpenAI");
    }

    private static ModelPoolEntry entry(String modelName, String apiKey, String apiBaseUrl, String provider) {
        return entry(modelName, apiKey, apiBaseUrl, provider, Map.of());
    }

    private static ModelPoolEntry entry(
            String modelName,
            String apiKey,
            String apiBaseUrl,
            String provider,
            Map<String, Object> metadata
    ) {
        return new ModelPoolEntry(modelName, apiKey, apiBaseUrl, provider, metadata);
    }

    private static TeamAgentSpec baseSpec() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName("t");
        spec.getAgents().put("leader", new DeepAgentSpec());
        LeaderSpec leader = new LeaderSpec();
        leader.setMemberName("leader");
        spec.setLeader(leader);
        return spec;
    }

    private static AgentConfigurator.TeamSpec teamSpecWithPool(List<ModelPoolEntry> pool) {
        AgentConfigurator.TeamSpec teamSpec = new AgentConfigurator.TeamSpec("t", "t", "leader");
        teamSpec.setModelPool(pool);
        return teamSpec;
    }

    private static AgentConfigurator configuredConfigurator(ModelAllocator allocator) {
        return configuredConfigurator(
                allocator,
                List.of(namedEntry("gpt-4", "a1"), namedEntry("gpt-4", "a2"), namedEntry("claude", "c1")),
                "by_model_name"
        );
    }

    private static AgentConfigurator configuredConfigurator(
            ModelAllocator allocator,
            List<ModelPoolEntry> pool,
            String strategy
    ) {
        TeamAgentSpec spec = baseSpec();
        AgentConfigurator.TeamSpec teamSpec = teamSpecWithPool(pool);
        teamSpec.setModelPoolStrategy(strategy);
        AgentConfigurator.TeamRuntimeContext ctx = new AgentConfigurator.TeamRuntimeContext();
        ctx.setRole(AgentConfigurator.TeamRole.LEADER);
        ctx.setMemberName("leader");
        ctx.setTeamSpec(teamSpec);
        AgentConfigurator configurator = new AgentConfigurator(
                new AgentConfigurator.AgentCard("card", "Card", "description"));
        configurator.setupInfra(spec, ctx);
        configurator.attachModelAllocator(allocator, null);
        return configurator;
    }

    private static ModelPoolEntry.TeamModelConfig explicitModel(String modelName) {
        ModelClientConfig clientConfig = new ModelClientConfig();
        clientConfig.setClientProvider("OpenAI");
        clientConfig.setApiKey("explicit");
        clientConfig.setApiBase("http://explicit");
        clientConfig.setVerifySsl(false);

        ModelRequestConfig requestConfig = new ModelRequestConfig();
        requestConfig.setModelName(modelName);
        return new ModelPoolEntry.TeamModelConfig(clientConfig, requestConfig);
    }

    private static ModelRouterConfig routerConfig() {
        return routerConfig(null, Map.of("client", Map.of("verify_ssl", false)));
    }

    private static ModelRouterConfig routerConfig(List<String> modelNames, Map<String, Object> metadata) {
        return new ModelRouterConfig(
                "https://router.test/v1",
                "sk-shared",
                "OpenAI",
                modelNames == null ? List.of("gpt-4o", "claude-opus", "gemini-pro") : modelNames,
                metadata == null ? Map.of("client", Map.of("verify_ssl", false)) : metadata
        );
    }

    private static ModelPoolEntry poolEntry(AgentConfigurator configurator, int index) {
        return (ModelPoolEntry) configurator.getCtx().getTeamSpec().getModelPool().get(index);
    }

    private static final class FakeTeamSession implements SessionManager.AgentTeamSessionView {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();

        private FakeTeamSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> update) {
            state.putAll(update);
        }
    }

    private static final class NoopSpawnManager implements RecoveryManager.SpawnManagerPort {

        @Override
        public CompletionStage<Boolean> restartTeammate(String memberName) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletionStage<Void> cleanupTeammate(String memberName) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Map<String, Object> spawnedHandles() {
            return Map.of();
        }
    }
}
