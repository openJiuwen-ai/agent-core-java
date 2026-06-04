package com.openjiuwen.agent_teams.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.LeaderSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamModelConfig;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.TeamSpec;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.session.AgentSessionApi;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_teams.test_model_allocator}.
 */
class ModelAllocatorTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void testRoundRobinAllocatorRotatesThroughPool() {
        RoundRobinModelAllocator allocator = new RoundRobinModelAllocator(makePool(3));
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            names.add(allocator.allocate().toTeamModelConfig().getModelRequestConfig().getModelName());
        }
        assertEquals(List.of("m0", "m1", "m2", "m0", "m1", "m2", "m0"), names);
    }

    @Test
    void testRoundRobinAllocatorReturnsNoneWhenPoolEmpty() {
        RoundRobinModelAllocator allocator = new RoundRobinModelAllocator(List.of());
        assertNull(allocator.allocate());
        assertNull(allocator.allocate());
    }

    @Test
    void testModelPoolEntryAssignsUniqueModelIdPerInstance() {
        ModelPoolEntry a = namedEntry("m", "x");
        ModelPoolEntry b = namedEntry("m", "x");
        assertNotEquals(a.getModelId(), b.getModelId());
    }

    @Test
    void testModelPoolEntryToTeamModelConfigCarriesCredentials() {
        ModelPoolEntry entry = entry("m1", "secret", "http://endpoint", "OpenAI");
        TeamModelConfig cfg = entry.toTeamModelConfig();
        assertEquals("secret", cfg.getModelClientConfig().getApiKey());
        assertEquals("http://endpoint", cfg.getModelClientConfig().getApiBase());
        assertEquals(entry.getModelId(), cfg.getModelClientConfig().getClientId());
        assertEquals("m1", cfg.getModelRequestConfig().getModelName());
    }

    @Test
    void testModelPoolEntryMetadataFillsClientAndRequestConfigs() {
        ModelPoolEntry entry = entry("m1", "secret", "http://endpoint", "OpenAI", Map.of(
                "client", Map.of("timeout", 30.0, "verify_ssl", false, "max_retries", 5),
                "request", Map.of("temperature", 0.2, "top_p", 0.9, "max_tokens", 1024)
        ));
        TeamModelConfig cfg = entry.toTeamModelConfig();
        assertEquals(30.0, cfg.getModelClientConfig().getTimeout());
        assertFalse(cfg.getModelClientConfig().isVerifySsl());
        assertEquals(5, cfg.getModelClientConfig().getMaxRetries());
        assertEquals(0.2, cfg.getModelRequestConfig().getTemperature());
        assertEquals(0.9, cfg.getModelRequestConfig().getTopP());
        assertEquals(1024, cfg.getModelRequestConfig().getMaxTokens());
    }

    @Test
    void testModelPoolEntryExplicitFieldsOverrideMetadata() {
        ModelPoolEntry entry = entry("m1", "real-key", "http://real", "OpenAI", Map.of(
                "client", Map.of("api_key", "shadow-key", "api_base", "http://shadow"),
                "request", Map.of("model", "shadow-model")
        ));
        TeamModelConfig cfg = entry.toTeamModelConfig();
        assertEquals("real-key", cfg.getModelClientConfig().getApiKey());
        assertEquals("http://real", cfg.getModelClientConfig().getApiBase());
        assertEquals("m1", cfg.getModelRequestConfig().getModelName());
    }

    @Test
    void testModelPoolEntryMetadataExtraKeysAreIgnoredByMaterialization() {
        ModelPoolEntry entry = entry("m1", "k", "http://x", "OpenAI", Map.of(
                "weight", 5,
                "tags", List.of("fast")
        ));
        TeamModelConfig cfg = entry.toTeamModelConfig();
        assertEquals("k", cfg.getModelClientConfig().getApiKey());
        assertEquals("m1", cfg.getModelRequestConfig().getModelName());
        assertFalse(cfg.getModelClientConfig().getExtraFields().containsKey("weight"));
        assertTrue(cfg.getModelRequestConfig().getExtraFields().isEmpty());
    }

    @Test
    void testBuildModelAllocatorReturnsRoundRobinWhenPoolSet() {
        ModelAllocator allocator = ModelAllocators.buildModelAllocator(spec(), teamSpecWithPool(makePool(2)));
        assertInstanceOf(RoundRobinModelAllocator.class, allocator);
    }

    @Test
    void testBuildModelAllocatorReturnsNoneWithoutPool() {
        assertNull(ModelAllocators.buildModelAllocator(spec(), new TeamSpec()));
    }

    @Test
    void testTeamSpecModelPoolRoundTripsThroughJson() throws Exception {
        TeamSpec teamSpec = teamSpecWithPool(makePool(2));
        String json = JSON.writeValueAsString(teamSpec);
        TeamSpec restored = JSON.readValue(json, TeamSpec.class);
        assertEquals(2, restored.getModelPool().size());
        assertEquals("m0", restored.getModelPool().get(0).getModelName());
        assertEquals("http://h1", restored.getModelPool().get(1).getApiBaseUrl());
    }

    @Test
    void testTeamAgentSpecModelPoolRoundTripsThroughJson() throws Exception {
        TeamAgentSpec spec = spec();
        spec.setModelPool(makePool(3));
        String json = JSON.writeValueAsString(spec);
        TeamAgentSpec restored = JSON.readValue(json, TeamAgentSpec.class);
        assertEquals(List.of("m0", "m1", "m2"), restored.getModelPool().stream().map(ModelPoolEntry::getModelName).toList());
    }

    @Test
    void testAllocationToDbRefIsNamePlusGroupIndex() {
        Allocation allocation = new Allocation(namedEntry("gpt-4", "a1"), 2);
        assertEquals(Map.of("model_name", "gpt-4", "model_index", 2), allocation.toDbRef());
    }

    @Test
    void testRoundRobinAllocationCarriesGroupIndexWithinName() {
        List<ModelPoolEntry> pool = List.of(
                namedEntry("gpt-4", "a1"),
                namedEntry("claude", "c1"),
                namedEntry("gpt-4", "a2")
        );
        RoundRobinModelAllocator allocator = new RoundRobinModelAllocator(pool);
        assertEquals(Map.of("model_name", "gpt-4", "model_index", 0), allocator.allocate().toDbRef());
        assertEquals(Map.of("model_name", "claude", "model_index", 0), allocator.allocate().toDbRef());
        assertEquals(Map.of("model_name", "gpt-4", "model_index", 1), allocator.allocate().toDbRef());
    }

    @Test
    void testByModelNameAllocatorRotatesWithinNamedGroup() {
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
        assertEquals(List.of("http://a1", "http://a2", "http://a3"), bases);
        assertEquals("http://a1", allocator.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase());
        assertEquals("http://c1", allocator.allocate("claude").toTeamModelConfig().getModelClientConfig().getApiBase());
        assertEquals("http://c1", allocator.allocate("claude").toTeamModelConfig().getModelClientConfig().getApiBase());
    }

    @Test
    void testByModelNameAllocatorIndependentCountersPerName() {
        ByModelNameAllocator allocator = new ByModelNameAllocator(List.of(
                namedEntry("gpt-4", "a1"),
                namedEntry("gpt-4", "a2"),
                namedEntry("claude", "c1"),
                namedEntry("claude", "c2")
        ));
        for (int i = 0; i < 5; i++) {
            allocator.allocate("gpt-4");
        }
        assertEquals("http://c1", allocator.allocate("claude").toTeamModelConfig().getModelClientConfig().getApiBase());
    }

    @Test
    void testByModelNameAllocatorReturnsNoneForUnknownOrMissingName() {
        ByModelNameAllocator allocator = new ByModelNameAllocator(List.of(namedEntry("gpt-4", "a1")));
        assertNull(allocator.allocate(null));
        assertNull(allocator.allocate());
        assertNull(allocator.allocate(""));
        assertNull(allocator.allocate("gemini"));
        assertEquals("http://a1", allocator.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase());
    }

    @Test
    void testByModelNameAllocatorHandlesEmptyPool() {
        ByModelNameAllocator allocator = new ByModelNameAllocator(List.of());
        assertNull(allocator.allocate("gpt-4"));
        assertNull(allocator.allocate());
    }

    @Test
    void testRoundRobinAllocatorIgnoresModelNameArgument() {
        RoundRobinModelAllocator allocator = new RoundRobinModelAllocator(List.of(
                namedEntry("gpt-4", "a1"),
                namedEntry("claude", "c1")
        ));
        assertEquals("http://a1", allocator.allocate("claude").toTeamModelConfig().getModelClientConfig().getApiBase());
        assertEquals("http://c1", allocator.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase());
        assertEquals("http://a1", allocator.allocate("claude").toTeamModelConfig().getModelClientConfig().getApiBase());
    }

    @Test
    void testBuildModelAllocatorDispatchesByStrategy() {
        List<ModelPoolEntry> pool = List.of(namedEntry("gpt-4", "a1"), namedEntry("claude", "c1"));
        TeamSpec rr = teamSpecWithPool(pool);
        rr.setModelPoolStrategy("round_robin");
        assertInstanceOf(RoundRobinModelAllocator.class, ModelAllocators.buildModelAllocator(spec(), rr));

        TeamSpec byName = teamSpecWithPool(pool);
        byName.setModelPoolStrategy("by_model_name");
        assertInstanceOf(ByModelNameAllocator.class, ModelAllocators.buildModelAllocator(spec(), byName));
    }

    @Test
    void testBuildModelAllocatorRejectsUnknownStrategy() {
        TeamSpec teamSpec = teamSpecWithPool(List.of(namedEntry("gpt-4", "a1")));
        teamSpec.setModelPoolStrategy("weighted");
        assertThrows(IllegalArgumentException.class, () -> ModelAllocators.buildModelAllocator(spec(), teamSpec));
    }

    @Test
    void testTeamAgentSpecPropagatesStrategyIntoTeamSpec() throws Exception {
        TeamAgentSpec spec = spec();
        spec.setModelPool(List.of(namedEntry("gpt-4", "a1")));
        spec.setModelPoolStrategy("by_model_name");
        TeamAgentSpec restored = JSON.readValue(JSON.writeValueAsString(spec), TeamAgentSpec.class);
        assertEquals("by_model_name", restored.getModelPoolStrategy());
    }

    @Test
    void testRoundRobinStateDictRoundTripResumesRotation() {
        RoundRobinModelAllocator a = new RoundRobinModelAllocator(makePool(3));
        a.allocate();
        a.allocate();
        Map<String, Object> snapshot = a.stateDict();

        RoundRobinModelAllocator b = new RoundRobinModelAllocator(makePool(3));
        b.loadStateDict(snapshot);
        assertEquals("m2", b.allocate().toTeamModelConfig().getModelRequestConfig().getModelName());
        assertEquals("m0", new RoundRobinModelAllocator(makePool(3)).allocate().toTeamModelConfig().getModelRequestConfig().getModelName());
    }

    @Test
    void testRoundRobinStateDictRoundTripsThroughJson() throws Exception {
        RoundRobinModelAllocator a = new RoundRobinModelAllocator(makePool(2));
        a.allocate();
        @SuppressWarnings("unchecked")
        Map<String, Object> decoded = JSON.readValue(JSON.writeValueAsString(a.stateDict()), Map.class);
        RoundRobinModelAllocator b = new RoundRobinModelAllocator(makePool(2));
        b.loadStateDict(decoded);
        assertEquals("m1", b.allocate().toTeamModelConfig().getModelRequestConfig().getModelName());
    }

    @Test
    void testRoundRobinLoadStateDictResetsOnPoolDigestChange() {
        RoundRobinModelAllocator a = new RoundRobinModelAllocator(makePool(3));
        a.allocate();
        a.allocate();
        Map<String, Object> snapshot = a.stateDict();

        RoundRobinModelAllocator b = new RoundRobinModelAllocator(makePool(2));
        b.loadStateDict(snapshot);
        assertEquals("m0", b.allocate().toTeamModelConfig().getModelRequestConfig().getModelName());
    }

    @Test
    void testRoundRobinLoadStateDictToleratesMissingOrBadInput() {
        RoundRobinModelAllocator a = new RoundRobinModelAllocator(makePool(2));
        a.loadStateDict(Map.of());
        assertEquals("m0", a.allocate().toTeamModelConfig().getModelRequestConfig().getModelName());

        a.loadStateDict(Map.of("index", "not-an-int", "pool_digest", a.stateDict().get("pool_digest")));
        assertEquals("m0", a.allocate().toTeamModelConfig().getModelRequestConfig().getModelName());
    }

    @Test
    void testByModelNameStateDictResumesPerGroupRotation() {
        List<ModelPoolEntry> pool = List.of(
                namedEntry("gpt-4", "a1"),
                namedEntry("gpt-4", "a2"),
                namedEntry("gpt-4", "a3"),
                namedEntry("claude", "c1"),
                namedEntry("claude", "c2")
        );
        ByModelNameAllocator a = new ByModelNameAllocator(pool);
        a.allocate("gpt-4");
        a.allocate("gpt-4");
        a.allocate("claude");
        Map<String, Object> snapshot = a.stateDict();

        ByModelNameAllocator b = new ByModelNameAllocator(pool);
        b.loadStateDict(snapshot);
        assertEquals("http://a3", b.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase());
        assertEquals("http://c2", b.allocate("claude").toTeamModelConfig().getModelClientConfig().getApiBase());
    }

    @Test
    void testByModelNameLoadStateDictResetsOnPoolDigestChange() {
        ByModelNameAllocator a = new ByModelNameAllocator(List.of(namedEntry("gpt-4", "a1"), namedEntry("claude", "c1")));
        a.allocate("gpt-4");
        a.allocate("claude");
        Map<String, Object> snapshot = a.stateDict();

        ByModelNameAllocator b = new ByModelNameAllocator(List.of(
                namedEntry("gpt-4", "a1"),
                namedEntry("gpt-4", "a2"),
                namedEntry("gemini", "g1")
        ));
        b.loadStateDict(snapshot);
        assertEquals("http://a1", b.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase());
        assertEquals("http://g1", b.allocate("gemini").toTeamModelConfig().getModelClientConfig().getApiBase());
    }

    @Test
    void testPoolDigestStableUnderCredentialRefresh() {
        ModelPoolEntry original = entry("gpt-4", "OLD", "http://x", "OpenAI");
        ByModelNameAllocator a = new ByModelNameAllocator(List.of(original));
        a.allocate("gpt-4");
        Map<String, Object> snapshot = a.stateDict();

        ModelPoolEntry refreshed = entry("gpt-4", "NEW", "http://x", "OpenAI");
        ByModelNameAllocator b = new ByModelNameAllocator(List.of(refreshed));
        b.loadStateDict(snapshot);
        assertEquals("NEW", b.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiKey());
    }

    @Test
    void testByModelNameLoadStateDictToleratesMalformedInput() {
        List<ModelPoolEntry> pool = List.of(namedEntry("gpt-4", "a1"), namedEntry("claude", "c1"));
        ByModelNameAllocator a = new ByModelNameAllocator(pool);
        Object digest = a.stateDict().get("pool_digest");
        a.loadStateDict(Map.of("inner_indexes", "not-a-dict", "pool_digest", digest));
        assertEquals("http://a1", a.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase());

        a.loadStateDict(Map.of("inner_indexes", Map.of("gpt-4", "bogus"), "pool_digest", digest));
        assertEquals("http://a1", a.allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase());
    }

    @Test
    void testPersistLeaderConfigIncludesAllocatorState() {
        TeamAgent agent = bareTeamAgent(new ByModelNameAllocator(List.of(namedEntry("gpt-4", "a1"), namedEntry("claude", "c1"))));
        agent.getModelAllocator().allocate("gpt-4");
        agent.getModelAllocator().allocate("claude");

        AgentSessionApi session = AgentSessionApi.create("persist-leader", Map.of(), null);
        agent.getRecoveryManager().persistLeaderConfig(session);

        Map<?, ?> payload = assertInstanceOf(Map.class, session.getState(RecoveryManager.LEADER_STATE_KEY));
        Map<?, ?> snapshot = assertInstanceOf(Map.class, payload.get("model_allocator_state"));
        Map<?, ?> indexes = assertInstanceOf(Map.class, snapshot.get("inner_indexes"));
        assertEquals(Map.of("gpt-4", 1, "claude", 1), indexes);
        assertTrue(snapshot.containsKey("pool_digest"));
    }

    @Test
    void testPersistAllocatorStateWritesOnlyAllocatorPayload() {
        TeamAgent agent = bareTeamAgent(new RoundRobinModelAllocator(makePool(3)));
        agent.getModelAllocator().allocate();
        agent.getModelAllocator().allocate();

        AgentSessionApi session = AgentSessionApi.create("allocator-only", Map.of(), null);
        agent.registerCurrentSession(session);
        agent.persistAllocatorState();

        Map<?, ?> snapshot = assertInstanceOf(Map.class, session.getState("model_allocator_state"));
        assertEquals(2, snapshot.get("index"));
        assertTrue(snapshot.containsKey("pool_digest"));
    }

    @Test
    void testPersistAllocatorStateNoOpWithoutSessionOrAllocator() {
        TeamAgent noSession = bareTeamAgent(new RoundRobinModelAllocator(makePool(2)));
        assertDoesNotThrow(noSession::persistAllocatorState);

        TeamAgent noAllocator = bareTeamAgent(null);
        AgentSessionApi session = AgentSessionApi.create("no-allocator", Map.of(), null);
        noAllocator.registerCurrentSession(session);
        noAllocator.persistAllocatorState();
        assertNull(session.getState("model_allocator_state"));
    }

    @Test
    void testPersistLeaderConfigOmitsAllocatorStateWhenNoPool() {
        TeamAgent agent = TeamAgent.fromSpec(spec());
        AgentSessionApi session = AgentSessionApi.create("no-pool", Map.of(), null);
        agent.getRecoveryManager().persistLeaderConfig(session);
        Map<?, ?> payload = assertInstanceOf(Map.class, session.getState(RecoveryManager.LEADER_STATE_KEY));
        assertFalse(payload.containsKey("model_allocator_state"));
        assertTrue(payload.containsKey("context"));
    }

    @Test
    void testLeaderSpecCarriesModelNameForPoolAllocation() throws Exception {
        LeaderSpec leader = new LeaderSpec();
        leader.setModelName("gpt-4");
        LeaderSpec restored = JSON.readValue(JSON.writeValueAsString(leader), LeaderSpec.class);
        assertEquals("gpt-4", restored.getModelName());
    }

    @Test
    void testTeamMemberSpecCarriesModelNameForPoolAllocation() throws Exception {
        TeamMemberSpec member = new TeamMemberSpec();
        member.setMemberName("dev1");
        member.setDisplayName("Dev 1");
        member.setPersona("backend");
        member.setModelName("claude");
        TeamMemberSpec restored = JSON.readValue(JSON.writeValueAsString(member), TeamMemberSpec.class);
        assertEquals("claude", restored.getModelName());
    }

    @Test
    void testResolveMemberModelReturnsEntryAtGroupIndex() {
        TeamSpec teamSpec = teamSpecWithPool(List.of(namedEntry("gpt-4", "a1"), namedEntry("gpt-4", "a2"), namedEntry("claude", "c1")));
        TeamModelConfig cfg = ModelAllocators.resolveMemberModel(teamSpec, "gpt-4", 1);
        assertNotNull(cfg);
        assertEquals("http://a2", cfg.getModelClientConfig().getApiBase());
    }

    @Test
    void testResolveMemberModelPicksUpRefreshedCredentialsFromPool() {
        TeamSpec teamSpec = teamSpecWithPool(List.of(entry("gpt-4", "NEW-KEY", "http://new", "OpenAI")));
        TeamModelConfig cfg = ModelAllocators.resolveMemberModel(teamSpec, "gpt-4", 0);
        assertEquals("NEW-KEY", cfg.getModelClientConfig().getApiKey());
        assertEquals("http://new", cfg.getModelClientConfig().getApiBase());
    }

    @Test
    void testResolveMemberModelClampsOutOfRangeIndexToZero() {
        TeamSpec teamSpec = teamSpecWithPool(List.of(namedEntry("gpt-4", "a1"), namedEntry("gpt-4", "a2")));
        TeamModelConfig cfg = ModelAllocators.resolveMemberModel(teamSpec, "gpt-4", 5);
        assertEquals("http://a1", cfg.getModelClientConfig().getApiBase());
    }

    @Test
    void testResolveMemberModelReturnsNoneWhenNameAbsentFromPool() {
        assertNull(ModelAllocators.resolveMemberModel(teamSpecWithPool(List.of(namedEntry("gpt-4", "a1"))), "gemini", 0));
    }

    @Test
    void testResolveMemberModelReturnsNoneWithoutPool() {
        assertNull(ModelAllocators.resolveMemberModel(new TeamSpec(), "gpt-4", 0));
    }

    @Test
    void testResolveMemberModelToleratesMissingIndex() {
        TeamModelConfig cfg = ModelAllocators.resolveMemberModel(teamSpecWithPool(List.of(namedEntry("gpt-4", "a1"))), "gpt-4", null);
        assertEquals("http://a1", cfg.getModelClientConfig().getApiBase());
    }

    @Test
    void testUpdateModelPoolReplacesPoolAndResetsAllocator() {
        ByModelNameAllocator allocator = new ByModelNameAllocator(List.of(namedEntry("gpt-4", "a1"), namedEntry("gpt-4", "a2")));
        allocator.allocate("gpt-4");
        allocator.allocate("gpt-4");
        TeamAgent agent = bareTeamAgent(allocator);

        agent.updateModelPool(List.of(namedEntry("gpt-4", "b1"), namedEntry("claude", "c1")));

        assertEquals(2, agent.getRuntimeContext().getTeamSpec().getModelPool().size());
        assertEquals("http://b1", agent.getModelAllocator().allocate("gpt-4").toTeamModelConfig().getModelClientConfig().getApiBase());
    }

    @Test
    void testInheritPoolIdsPreservesIdForBitExactEntry() {
        ModelPoolEntry old = entry("gpt-4", "K", "http://x", "OpenAI");
        ModelPoolEntry fresh = entry("gpt-4", "K", "http://x", "OpenAI");
        assertNotEquals(old.getModelId(), fresh.getModelId());

        List<ModelPoolEntry> merged = ModelPoolEntry.inheritPoolIds(List.of(old), List.of(fresh));
        assertEquals(old.getModelId(), merged.get(0).getModelId());
    }

    @Test
    void testInheritPoolIdsBreaksInheritanceOnCredentialRotation() {
        ModelPoolEntry old = entry("gpt-4", "OLD", "http://x", "OpenAI");
        ModelPoolEntry rotated = entry("gpt-4", "ROTATED", "http://x", "OpenAI");
        List<ModelPoolEntry> merged = ModelPoolEntry.inheritPoolIds(List.of(old), List.of(rotated));
        assertEquals(rotated.getModelId(), merged.get(0).getModelId());
        assertEquals("ROTATED", merged.get(0).getApiKey());
    }

    @Test
    void testInheritPoolIdsBreaksInheritanceOnBaseUrlMigration() {
        ModelPoolEntry old = entry("gpt-4", "K", "http://old", "OpenAI");
        ModelPoolEntry migrated = entry("gpt-4", "K", "http://new", "OpenAI");
        assertEquals(migrated.getModelId(), ModelPoolEntry.inheritPoolIds(List.of(old), List.of(migrated)).get(0).getModelId());
    }

    @Test
    void testInheritPoolIdsBreaksInheritanceOnMetadataChange() {
        ModelPoolEntry old = entry("gpt-4", "K", "http://x", "OpenAI", Map.of("client", Map.of("timeout", 30.0)));
        ModelPoolEntry tuned = entry("gpt-4", "K", "http://x", "OpenAI", Map.of("client", Map.of("timeout", 60.0)));
        assertEquals(tuned.getModelId(), ModelPoolEntry.inheritPoolIds(List.of(old), List.of(tuned)).get(0).getModelId());
    }

    @Test
    void testInheritPoolIdsKeepsOwnIdForTrulyNewEndpoint() {
        ModelPoolEntry old = entry("gpt-4", "k", "http://a", "OpenAI");
        ModelPoolEntry newEndpoint = entry("claude", "k", "http://b", "OpenAI");
        assertEquals(newEndpoint.getModelId(), ModelPoolEntry.inheritPoolIds(List.of(old), List.of(newEndpoint)).get(0).getModelId());
    }

    @Test
    void testInheritPoolIdsSignatureMatchIsOrderIndependent() {
        ModelPoolEntry old1 = entry("gpt-4", "K1", "http://x", "OpenAI");
        ModelPoolEntry old2 = entry("gpt-4", "K2", "http://x", "OpenAI");
        ModelPoolEntry new1 = entry("gpt-4", "K2", "http://x", "OpenAI");
        ModelPoolEntry new2 = entry("gpt-4", "K1", "http://x", "OpenAI");

        List<ModelPoolEntry> merged = ModelPoolEntry.inheritPoolIds(List.of(old1, old2), List.of(new1, new2));
        assertEquals(old2.getModelId(), merged.get(0).getModelId());
        assertEquals(old1.getModelId(), merged.get(1).getModelId());
    }

    @Test
    void testInheritPoolIdsPairsOneToOneWhenSignaturesCollide() {
        ModelPoolEntry old1 = entry("gpt-4", "K", "http://x", "OpenAI");
        ModelPoolEntry old2 = entry("gpt-4", "K", "http://x", "OpenAI");
        List<ModelPoolEntry> merged = ModelPoolEntry.inheritPoolIds(
                List.of(old1, old2),
                List.of(entry("gpt-4", "K", "http://x", "OpenAI"), entry("gpt-4", "K", "http://x", "OpenAI"))
        );
        assertEquals(List.of(old1.getModelId(), old2.getModelId()), merged.stream().map(ModelPoolEntry::getModelId).toList());
    }

    @Test
    void testInheritPoolIdsDropsRemovedEndpoints() {
        ModelPoolEntry old1 = entry("gpt-4", "K", "http://a", "OpenAI");
        ModelPoolEntry old2 = entry("claude", "K", "http://b", "OpenAI");
        List<ModelPoolEntry> merged = ModelPoolEntry.inheritPoolIds(List.of(old1, old2), List.of(entry("gpt-4", "K", "http://a", "OpenAI")));
        assertEquals(1, merged.size());
        assertEquals(old1.getModelId(), merged.get(0).getModelId());
    }

    @Test
    void testInheritPoolIdsDoesNotMutateInputLists() {
        ModelPoolEntry old = entry("gpt-4", "K", "http://x", "OpenAI");
        ModelPoolEntry fresh = entry("gpt-4", "K", "http://x", "OpenAI");
        String freshIdBefore = fresh.getModelId();
        ModelPoolEntry.inheritPoolIds(List.of(old), List.of(fresh));
        assertEquals(freshIdBefore, fresh.getModelId());
    }

    @Test
    void testInheritPoolIdsHandlesEmptyInputs() {
        assertTrue(ModelPoolEntry.inheritPoolIds(List.of(), List.of()).isEmpty());
        assertFalse(ModelPoolEntry.inheritPoolIds(List.of(), List.of(namedEntry("gpt-4", "a1"))).isEmpty());
    }

    @Test
    void testBuildRejectsByModelNamePoolWithoutLeaderModelName() {
        TeamAgentSpec spec = spec();
        spec.setModelPool(List.of(namedEntry("gpt-4", "a1")));
        spec.setModelPoolStrategy("by_model_name");
        assertThrows(IllegalArgumentException.class, spec::build);
    }

    @Test
    void testBuildRejectsUnknownLeaderModelName() {
        TeamAgentSpec spec = spec();
        LeaderSpec leader = new LeaderSpec();
        leader.setMemberName("leader");
        leader.setModelName("claude");
        spec.setLeader(leader);
        spec.setModelPool(List.of(namedEntry("gpt-4", "a1")));
        spec.setModelPoolStrategy("by_model_name");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, spec::build);
        assertTrue(error.getMessage().contains("not present in the pool"));
    }

    @Test
    void testBuildAcceptsPoolWhenPerAgentLeaderModelSupplied() {
        TeamAgentSpec spec = spec();
        spec.getAgents().get("leader").setModel(explicitModel("explicit-model"));
        spec.setModelPool(List.of(namedEntry("gpt-4", "a1")));
        spec.setModelPoolStrategy("by_model_name");
        assertDoesNotThrow(spec::build);
    }

    @Test
    void testBuildRoundRobinStrategyDoesNotRequireLeaderModelName() {
        TeamAgentSpec spec = spec();
        spec.setSpawnMode("inprocess");
        spec.setModelPool(List.of(entry("gpt-4", "k", "http://x", "OpenAI", Map.of("client", Map.of("verify_ssl", false)))));
        spec.setModelPoolStrategy("round_robin");
        assertDoesNotThrow(spec::build);
    }

    @Test
    void testUpdateModelPoolPreservesIdOnlyWhenEntryIsUnchanged() {
        ModelPoolEntry initial = entry("gpt-4", "K", "http://x", "OpenAI");
        TeamAgent agent = bareTeamAgent(new ByModelNameAllocator(List.of(initial)));
        agent.getRuntimeContext().getTeamSpec().setModelPool(List.of(initial));
        String oldId = initial.getModelId();

        agent.updateModelPool(List.of(entry("gpt-4", "K", "http://x", "OpenAI")));
        assertEquals(oldId, agent.getRuntimeContext().getTeamSpec().getModelPool().get(0).getModelId());

        agent.updateModelPool(List.of(entry("gpt-4", "ROTATED", "http://x", "OpenAI")));
        ModelPoolEntry stored = agent.getRuntimeContext().getTeamSpec().getModelPool().get(0);
        assertNotEquals(oldId, stored.getModelId());
        assertEquals("ROTATED", stored.getApiKey());
    }

    private static List<ModelPoolEntry> makePool(int n) {
        List<ModelPoolEntry> pool = new ArrayList<>();
        for (int i = 0; i < n; i++) {
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
        ModelPoolEntry entry = new ModelPoolEntry();
        entry.setModelName(modelName);
        entry.setApiKey(apiKey);
        entry.setApiBaseUrl(apiBaseUrl);
        entry.setApiProvider(provider);
        entry.setMetadata(new LinkedHashMap<>(metadata));
        return entry;
    }

    private static TeamAgentSpec spec() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName("t");
        LeaderSpec leader = new LeaderSpec();
        leader.setMemberName("leader");
        spec.setLeader(leader);
        spec.getAgents().put("leader", new DeepAgentSpec());
        return spec;
    }

    private static TeamSpec teamSpecWithPool(List<ModelPoolEntry> pool) {
        TeamSpec teamSpec = new TeamSpec();
        teamSpec.setTeamName("t");
        teamSpec.setDisplayName("t");
        teamSpec.setModelPool(pool);
        return teamSpec;
    }

    private static TeamAgent bareTeamAgent(ModelAllocator allocator) {
        List<ModelPoolEntry> pool = List.of(namedEntry("gpt-4", "a1"), namedEntry("gpt-4", "a2"), namedEntry("claude", "c1"));
        TeamAgentSpec spec = spec();
        spec.setModelPool(pool);
        spec.setModelPoolStrategy("by_model_name");
        LeaderSpec leader = spec.getLeader();
        leader.setModelName("gpt-4");
        TeamAgent agent = TeamAgent.fromSpec(spec);
        agent.attachModelAllocator(allocator, null);
        return agent;
    }

    private static TeamModelConfig explicitModel(String modelName) {
        return new TeamModelConfig(
                ModelClientConfig.builder()
                        .clientProvider("OpenAI")
                        .apiKey("explicit")
                        .apiBase("http://explicit")
                        .verifySsl(false)
                        .build(),
                ModelRequestConfig.builder().modelName(modelName).build()
        );
    }
}
