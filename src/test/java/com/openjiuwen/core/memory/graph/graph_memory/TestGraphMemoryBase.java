/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.store.base_embedding.Embedding;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.foundation.store.graph.WeightedRankConfig;
import com.openjiuwen.core.memory.config.EpisodeType;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.reranker.Reranker;
import com.openjiuwen.spi.store.query.QueryExpr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GraphMemory.
 * <p>
 * Mirrors Python's test_base.py from
 * <code>tests/unit_tests/core/memory/graph/graph_memory/test_base.py</code>.
 */
@DisplayName("Graph Memory Base Tests")
class TestGraphMemoryBase {

    @Nested
    @DisplayName("GraphMemory Init")
    class TestGraphMemoryInit {
        @Test
        void test_init_sets_public_attributes() {
            FakeGraphStore backend = new FakeGraphStore();
            GraphMemory mem = memory(backend);

            assertSame(backend, mem.getDbBackend());
            assertNotNull(mem.getConfig());
            assertEquals("cn", mem.getLanguage());
            assertNull(mem.getEmbedder());
        }

        @Test
        void test_init_creates_search_strategies() {
            GraphMemory mem = memory(new FakeGraphStore());

            assertTrue(mem.getSearchStrategies().containsKey("default"));
            assertEquals(3, mem.getSearchStrategies().get("default").size());
        }
    }

    @Nested
    @DisplayName("Embedder")
    class TestGraphMemoryEmbedder {
        @Test
        void test_embedder_returns_backend_embedder() {
            FakeGraphStore backend = new FakeGraphStore();
            FakeEmbedding embedder = new FakeEmbedding();
            backend.attachEmbedder(embedder);

            assertSame(embedder, memory(backend).getEmbedder());
        }

        @Test
        void test_attach_embedder_sets_on_backend() {
            FakeGraphStore backend = new FakeGraphStore();
            FakeEmbedding embedder = new FakeEmbedding();

            memory(backend).attachEmbedder(embedder);

            assertSame(embedder, backend.embedder);
            assertEquals(1, backend.attachEmbedderCalls);
        }
    }

    @Nested
    @DisplayName("Reranker")
    class TestGraphMemoryAttachReranker {
        @Test
        void test_attach_reranker_valid_sets_reranker() {
            GraphMemory mem = memory(new FakeGraphStore());
            FakeReranker reranker = new FakeReranker();

            mem.attachReranker(reranker);

            assertSame(reranker, mem.getReranker());
        }

        @Test
        void test_attach_reranker_invalid_raises() {
            GraphMemory mem = memory(new FakeGraphStore());

            BaseError err = assertThrows(BaseError.class, () -> mem.attachReranker("not a reranker"));
            assertTrue(err.getMessage().contains("Reranker"));
        }
    }

    @Nested
    @DisplayName("Search Strategy Registration")
    class TestGraphMemoryRegisterSearchStrategy {
        @Test
        void test_register_search_strategy_new_name() {
            GraphMemory mem = memory(new FakeGraphStore());
            GraphMemory.SearchConfig config = new GraphMemory.SearchConfig(new WeightedRankConfig());

            mem.registerSearchStrategy("custom", config);

            assertSame(config, mem.getSearchStrategies().get("custom").get(0));
        }

        @Test
        void test_register_search_strategy_empty_name_raises() {
            GraphMemory mem = memory(new FakeGraphStore());

            BaseError err = assertThrows(BaseError.class,
                    () -> mem.registerSearchStrategy("", new GraphMemory.SearchConfig()));
            assertTrue(err.getMessage().contains("empty"));
        }

        @Test
        void test_register_search_strategy_duplicate_raises_without_force() {
            GraphMemory mem = memory(new FakeGraphStore());
            mem.registerSearchStrategy("dup", new GraphMemory.SearchConfig());

            BaseError err = assertThrows(BaseError.class,
                    () -> mem.registerSearchStrategy("dup", new GraphMemory.SearchConfig()));
            assertTrue(err.getMessage().contains("already exists"));
        }

        @Test
        void test_register_search_strategy_force_overwrites() {
            GraphMemory mem = memory(new FakeGraphStore());
            GraphMemory.SearchConfig first = new GraphMemory.SearchConfig();
            GraphMemory.SearchConfig second = GraphMemory.SearchConfig.withMinScore(0.5);

            mem.registerSearchStrategy("s", first);
            mem.registerSearchStrategy("s", second, null, null, true);

            assertSame(second, mem.getSearchStrategies().get("s").get(0));
        }

        @Test
        void test_register_search_strategy_invalid_config_raises() {
            GraphMemory mem = memory(new FakeGraphStore());

            BaseError err = assertThrows(BaseError.class,
                    () -> mem.registerSearchStrategy("x", new Object(), null, null, false));
            assertTrue(err.getMessage().contains("Search config"));
        }
    }

    @Nested
    @DisplayName("Thread Lock")
    class TestGraphMemoryEnsureThreadLock {
        @Test
        void test_ensure_thread_lock_creates_per_user_lock() {
            GraphMemory mem = memory(new FakeGraphStore());

            mem.ensureThreadLock("user-1");
            mem.ensureThreadLock("user-1");

            assertTrue(mem.getUserLocks().containsKey("user-1"));
            assertEquals(1, mem.getUserLocks().size());
        }
    }

    @Nested
    @DisplayName("Add Memory")
    class TestAddMemory {
        @Test
        void test_add_memory_without_embedder_raises() {
            GraphMemory mem = memory(new FakeGraphStore());

            BaseError err = futureError(mem.addMemory(EpisodeType.DOCUMENT, "user1", "Some content."));

            assertTrue(err.getMessage().contains("attach"));
        }

        @Test
        void test_add_memory_success_returns_graph_mem_update() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.attachEmbedder(new FakeEmbedding());
            GraphMemory mem = memory(backend);

            GraphMemoryStates.GraphMemUpdate result =
                    mem.addMemory(EpisodeType.DOCUMENT, "user1", "Short document.").join();

            assertNotNull(result);
            assertEquals(1, result.getAddedEpisode().size());
            assertEquals(1, backend.refreshCalls);
        }

        @Test
        void test_add_memory_after_delayed_attach_with_reference_time() {
            FakeGraphStore backend = new FakeGraphStore();
            GraphMemory mem = memory(backend);
            FakeEmbedding embedder = new FakeEmbedding();
            mem.attachEmbedder(embedder);

            GraphMemoryStates.GraphMemUpdate result = mem.addMemory(
                    EpisodeType.DOCUMENT,
                    "user1",
                    "Short document.",
                    null,
                    Instant.parse("2025-01-01T12:00:00Z")).join();

            assertNotNull(result);
            assertSame(embedder, mem.getEmbedder());
            assertEquals(Instant.parse("2025-01-01T12:00:00Z").getEpochSecond(),
                    mem.getState().getReferenceTimestamp());
        }
    }

    @Nested
    @DisplayName("Init State")
    class TestGraphMemoryInitState {
        @Test
        void test_init_state_returns_state_with_reference_time() {
            GraphMemory mem = memory(new FakeGraphStore());

            GraphMemoryStates.GraphMemState state = mem.initState(Instant.parse("2025-01-01T12:00:00Z"));

            assertTrue(state.getReferenceTimestamp() > 0);
            assertEquals("cn", state.getPrompting().getLanguage());
        }

        @Test
        void test_init_state_invalid_reference_time_raises() {
            GraphMemory mem = memory(new FakeGraphStore());

            BaseError err = assertThrows(BaseError.class, () -> mem.initState("2025-01-01"));
            assertTrue(err.getMessage().contains("reference_time"));
        }

        @Test
        void test_init_state_chinese_from_strategy_fallback_to_language() {
            GraphMemory.AddMemStrategy strategy = new GraphMemory.AddMemStrategy();
            strategy.setChineseEntity(false);
            strategy.setChineseRelation(true);
            strategy.setChineseEntityDedupe(false);
            GraphMemory mem = new GraphMemory(graphConfig(), params -> CompletableFuture.completedFuture(
                    new GraphMemory.LlmResponse("{}")), true, null, strategy,
                    new FakeGraphStore(), Map.of(), "en", false);

            GraphMemoryStates.GraphMemState state = mem.initState(null);

            assertEquals("en", state.getPrompting().getEntityExtractionLanguage());
            assertEquals("cn", state.getPrompting().getRelationExtractionLanguage());
            assertEquals("en", state.getPrompting().getEntityDedupeLanguage());
        }
    }

    @Nested
    @DisplayName("Search")
    class TestGraphMemorySearch {
        @Test
        void test_search_unknown_strategy_raises() {
            GraphMemory mem = memory(new FakeGraphStore());

            BaseError err = futureError(mem.search("q", "user", "nonexistent", true, true, true, List.of(0.1f)));
            assertTrue(err.getMessage().contains("Strategy"));
        }

        @Test
        void test_search_empty_strategy_raises() {
            GraphMemory mem = memory(new FakeGraphStore());

            BaseError err = futureError(mem.search("q", "user", "", true, true, true, List.of(0.1f)));
            assertTrue(err.getMessage().contains("non-empty"));
        }

        @Test
        void test_search_no_embedder_no_query_embedding_raises() {
            GraphMemory mem = memory(new FakeGraphStore());

            BaseError err = futureError(mem.search("q", "user", "default", true, true, true, null));
            assertTrue(err.getMessage().contains("attach_embedder"));
        }

        @Test
        void test_search_invalid_query_embedding_type_raises() {
            GraphMemory mem = memory(new FakeGraphStore());
            List<Float> invalid = new ArrayList<>();
            invalid.add(null);

            BaseError err = futureError(mem.search("q", "user", "default", true, true, true, invalid));
            assertTrue(err.getMessage().contains("query_embedding"));
        }

        @Test
        void test_search_success_with_query_embedding_returns_result() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.searchRows(GraphMemory.ENTITY_COLLECTION, row("uuid", "e1", "name", "E", "content", "", "distance", 0.9));
            GraphMemory mem = memory(backend);

            Map<String, List<GraphMemory.SearchHit>> result =
                    mem.search("query", "user", "default", true, true, true, List.of(0.1f)).join();

            assertTrue(result.containsKey(GraphMemory.ENTITY_COLLECTION));
            assertTrue(result.containsKey(GraphMemory.RELATION_COLLECTION));
            assertTrue(result.containsKey(GraphMemory.EPISODE_COLLECTION));
            assertEquals(1, result.get(GraphMemory.ENTITY_COLLECTION).size());
            assertEquals(0.9, result.get(GraphMemory.ENTITY_COLLECTION).get(0).score());
        }
    }

    @Nested
    @DisplayName("Replace Relation Side")
    class TestReplaceOneSideOfRelation {
        @Test
        void test_replace_one_side_first_time_appends_deferred_and_updates() {
            Relation rel = relation("r1", "e1", "e2", "c");
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            Map<String, Map<String, Relation>> updates = new LinkedHashMap<>();
            updates.put("tgt", new LinkedHashMap<>());
            state.getRelationDeferredUpdates().put("tgt", new ArrayList<>());

            GraphMemory.replaceOneSideOfRelation("lhs", rel, "tgt", updates, state);

            assertEquals(1, state.getRelationDeferredUpdates().get("tgt").size());
            assertSame(rel, updates.get("tgt").get("r1"));
            assertFalse(state.getFaultyRelations().containsKey("r1"));
        }

        @Test
        void test_replace_one_side_duplicate_marks_faulty_and_removes_from_deferred() {
            Relation rel = relation("r1", "e1", "e2", "c");
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            Map<String, Map<String, Relation>> updates = new LinkedHashMap<>();
            updates.put("tgt", new LinkedHashMap<>(Map.of("r1", rel)));
            state.getRelationDeferredUpdates().put("tgt", new ArrayList<>(
                    List.of(new GraphMemoryStates.RelationDeferredUpdate(rel, "lhs", "tgt"))));

            GraphMemory.replaceOneSideOfRelation("lhs", rel, "tgt", updates, state);

            assertSame(rel, state.getFaultyRelations().get("r1"));
            assertFalse(updates.get("tgt").containsKey("r1"));
            assertTrue(state.getRelationDeferredUpdates().get("tgt").isEmpty());
        }
    }

    @Nested
    @DisplayName("Perform Search")
    class TestPerformSearch {
        @Test
        void test_perform_search_rerank_without_reranker_raises() {
            GraphMemory mem = memory(new FakeGraphStore());
            GraphMemory.SearchConfig config = new GraphMemory.SearchConfig();
            config.setRerank(true);
            mem.registerSearchStrategy("rerank_strat", config, null, null, true);

            BaseError err = assertThrows(BaseError.class, () -> mem.performSearch(
                    0, "user", "rerank_strat", new ArrayList<>(), Map.of("query", "q", "query_embedding", List.of(0.0f))));
            assertTrue(err.getMessage().contains("reranker"));
        }

        @Test
        void test_perform_search_appends_task_when_rerank_false() {
            GraphMemory mem = memory(new FakeGraphStore());
            List<CompletableFuture<GraphMemory.SearchResult>> tasks = new ArrayList<>();

            mem.performSearch(0, "user", "default", tasks, Map.of("query", "q", "query_embedding", List.of(0.0f)));

            assertEquals(1, tasks.size());
            assertTrue(tasks.get(0).isDone() || !tasks.get(0).isCancelled());
        }
    }

    @Nested
    @DisplayName("Parse Relation Filtering")
    class TestParseRelationFilteringResult {
        @Test
        void test_parse_relation_filtering_result_empty_tasks_no_op() {
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();

            GraphMemory.parseRelationFilteringResult(List.of(), state).join();

            assertTrue(state.getRelationFilterTasks().isEmpty());
        }

        @Test
        void test_parse_relation_filtering_result_applies_merge_infos() {
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            Entity e = entity("E", "e1");
            Relation rel = relation("r1", "e1", "e2", "c");
            state.getMergeInfos().put("e1", new GraphMemoryStates.EntityMerge(e));
            state.getRelationDeferredUpdates().put("e1", new ArrayList<>());
            CompletableFuture<GraphMemory.LlmResponse> future =
                    CompletableFuture.completedFuture(new GraphMemory.LlmResponse("{\"relevant_relations\":[1]}"));
            state.getRelationFilterTasks().put(future, new GraphMemoryStates.RelationFilterContext(e, List.of(rel)));

            GraphMemory.parseRelationFilteringResult(List.of(rel), state).join();

            assertEquals(List.of(rel), state.getMergeInfos().get("e1").getNewRelations());
        }

        @Test
        void test_parse_relation_filtering_result_relation_not_kept_goes_to_removed() {
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            Entity e = entity("E", "e1");
            Relation keep = relation("r1", "e1", "e2", "c1");
            Relation drop = relation("r2", "e1", "e2", "c2");
            state.getMergeInfos().put("e1", new GraphMemoryStates.EntityMerge(e));
            state.getRelationDeferredUpdates().put("e1", new ArrayList<>(List.of(
                    new GraphMemoryStates.RelationDeferredUpdate(keep, "lhs", "e1"),
                    new GraphMemoryStates.RelationDeferredUpdate(drop, "lhs", "e1"))));
            CompletableFuture<GraphMemory.LlmResponse> future =
                    CompletableFuture.completedFuture(new GraphMemory.LlmResponse("{\"relevant_relations\":[1]}"));
            state.getRelationFilterTasks().put(future,
                    new GraphMemoryStates.RelationFilterContext(e, List.of(keep, drop)));

            GraphMemory.parseRelationFilteringResult(List.of(keep, drop), state).join();

            assertEquals(List.of(keep), state.getMergeInfos().get("e1").getNewRelations());
            assertTrue(state.getMemUpdate().getRemovedRelation().contains("r2"));
            assertTrue(state.getToRemove().contains(drop));
        }

        @Test
        void test_parse_relation_filtering_result_two_targets_one_relation_not_in_new_relations() {
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            Entity e1 = entity("E1", "e1");
            Entity e2 = entity("E2", "e2");
            Relation relA = relation("ra", "e1", "x", "c");
            Relation relB = relation("rb", "e2", "y", "c");
            state.getMergeInfos().put("e1", new GraphMemoryStates.EntityMerge(e1));
            state.getMergeInfos().put("e2", new GraphMemoryStates.EntityMerge(e2));
            state.getRelationDeferredUpdates().put("e1", new ArrayList<>(List.of(
                    new GraphMemoryStates.RelationDeferredUpdate(relA, "lhs", "e1"))));
            state.getRelationDeferredUpdates().put("e2", new ArrayList<>(List.of(
                    new GraphMemoryStates.RelationDeferredUpdate(relB, "lhs", "e2"))));
            state.getRelationFilterTasks().put(
                    CompletableFuture.completedFuture(new GraphMemory.LlmResponse("{\"relevant_relations\":[1]}")),
                    new GraphMemoryStates.RelationFilterContext(e1, List.of(relA)));
            state.getRelationFilterTasks().put(
                    CompletableFuture.completedFuture(new GraphMemory.LlmResponse("{\"relevant_relations\":[]}")),
                    new GraphMemoryStates.RelationFilterContext(e2, List.of(relB)));

            GraphMemory.parseRelationFilteringResult(List.of(relA, relB), state).join();

            assertEquals(List.of(relA), state.getMergeInfos().get("e1").getNewRelations());
            assertTrue(state.getMergeInfos().get("e2").getNewRelations().isEmpty());
            assertTrue(state.getMemUpdate().getRemovedRelation().contains("rb"));
        }

        @Test
        void test_parse_relation_filtering_result_exception_falls_back_to_full_list() {
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            Entity e = entity("E", "e1");
            Relation rel = relation("r1", "e1", "e2", "c");
            state.getMergeInfos().put("e1", new GraphMemoryStates.EntityMerge(e));
            state.getRelationDeferredUpdates().put("e1", new ArrayList<>());
            CompletableFuture<GraphMemory.LlmResponse> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RuntimeException("parse failed"));
            state.getRelationFilterTasks().put(failed, new GraphMemoryStates.RelationFilterContext(e, List.of(rel)));

            GraphMemory.parseRelationFilteringResult(List.of(rel), state).join();

            assertEquals(List.of(rel), state.getMergeInfos().get("e1").getNewRelations());
        }
    }

    @Nested
    @DisplayName("Invoke LLM")
    class TestInvokeLlm {
        @Test
        void test_invoke_llm_success_returns_response() {
            QueueLlm llm = new QueueLlm("{\"result\":\"ok\"}");
            GraphMemory mem = memory(new FakeGraphStore(), llm);

            GraphMemory.LlmResponse response = mem.invokeLlm(Map.of(), template("test"), null, Map.of()).join();

            assertEquals("{\"result\":\"ok\"}", response.content());
        }

        @Test
        void test_invoke_llm_retries_then_raises() {
            GraphMemory mem = memory(new FakeGraphStore(), QueueLlm.failing());

            BaseError err = futureError(mem.invokeLlm(Map.of(), template("test"), null, Map.of()));
            assertTrue(err.getMessage().contains("LLM"));
        }

        @Test
        void test_invoke_llm_merges_llm_extra_kwargs() {
            QueueLlm llm = new QueueLlm("ok");
            GraphMemory mem = new GraphMemory(graphConfig(), llm, true, null, new GraphMemory.AddMemStrategy(),
                    new FakeGraphStore(), Map.of("temperature", 0.3), "cn", false);

            mem.invokeLlm(Map.of("k", "v"), template("t"), null, Map.of()).join();

            assertEquals(0.3, llm.calls.get(0).get("temperature"));
        }

        @Test
        void test_invoke_llm_debug_logs_when_enabled() {
            QueueLlm llm = new QueueLlm("resp");
            GraphMemory mem = new GraphMemory(graphConfig(), llm, true, null, new GraphMemory.AddMemStrategy(),
                    new FakeGraphStore(), Map.of(), "cn", true);

            mem.invokeLlm(Map.of(), template("extract_entity"), null, Map.of()).join();

            assertTrue(mem.isDebug());
            assertEquals(1, llm.calls.size());
        }
    }

    @Nested
    @DisplayName("Prepare Episodes")
    class TestPrepareEpisodes {
        @Test
        void test_prepare_episodes_str_content_returns_stripped() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.setEmpty(GraphMemory.EPISODE_COLLECTION, true);
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();

            String out = memory(backend).prepareEpisodes(EpisodeType.DOCUMENT, "user1", "  hello world  ", state).join();

            assertEquals("hello world", out);
            assertEquals("", state.getHistory());
        }

        @Test
        void test_prepare_episodes_str_with_content_fmt_kwargs_raises() {
            GraphMemory mem = memory(new FakeGraphStore());

            BaseError err = futureError(mem.prepareEpisodes(
                    EpisodeType.DOCUMENT, "user1", "text", new GraphMemoryStates.GraphMemState(), Map.of("k", "v")));
            assertTrue(err.getMessage().contains("content_fmt_kwargs"));
        }

        @Test
        void test_prepare_episodes_conversation_list_formats_messages() {
            Object content = List.of(row("role", "user", "content", "Hi"), row("role", "assistant", "content", "Hello"));

            String out = memory(new FakeGraphStore()).prepareEpisodes(
                    EpisodeType.CONVERSATION, "user1", content, new GraphMemoryStates.GraphMemState()).join();

            assertTrue(out.contains("Hi"));
            assertTrue(out.contains("Hello"));
        }

        @Test
        void test_prepare_episodes_non_conversation_with_list_raises() {
            GraphMemory mem = memory(new FakeGraphStore());

            BaseError err = futureError(mem.prepareEpisodes(
                    EpisodeType.DOCUMENT, "user1", List.of(row("role", "user", "content", "x")),
                    new GraphMemoryStates.GraphMemState()));
            assertTrue(err.getMessage().contains("str when source type"));
        }

        @Test
        void test_prepare_episodes_conversation_missing_role_or_content_raises() {
            GraphMemory mem = memory(new FakeGraphStore());

            BaseError err1 = futureError(mem.prepareEpisodes(
                    EpisodeType.CONVERSATION, "user1", List.of(row("role", "user")),
                    new GraphMemoryStates.GraphMemState()));
            BaseError err2 = futureError(mem.prepareEpisodes(
                    EpisodeType.CONVERSATION, "user1", List.of(row("content", "hi")),
                    new GraphMemoryStates.GraphMemState()));

            assertTrue(err1.getMessage().contains("role"));
            assertTrue(err2.getMessage().contains("role"));
        }

        @Test
        void test_prepare_episodes_conversation_not_list_or_dict_raises() {
            GraphMemory mem = memory(new FakeGraphStore());

            BaseError err = futureError(mem.prepareEpisodes(
                    EpisodeType.CONVERSATION, "user1", 123, new GraphMemoryStates.GraphMemState()));
            assertTrue(err.getMessage().contains("str or list"));
        }

        @Test
        void test_prepare_episodes_empty_content_raises() {
            GraphMemory mem = memory(new FakeGraphStore());

            BaseError err = futureError(mem.prepareEpisodes(
                    EpisodeType.DOCUMENT, "user1", "   ", new GraphMemoryStates.GraphMemState()));
            assertTrue(err.getMessage().contains("non-empty"));
        }

        @Test
        void test_prepare_episodes_recall_fills_history_when_db_not_empty() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.setEmpty(GraphMemory.EPISODE_COLLECTION, false);
            backend.searchRows(GraphMemory.EPISODE_COLLECTION,
                    row("uuid", "ep1", "content", "past", "created_at", 1000, "valid_since", 1000, "distance", 0.9));
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            state.getStrategy().getRecallEpisode().setTopK(2);

            String out = memory(backend).prepareEpisodes(EpisodeType.DOCUMENT, "user1", "query text", state).join();

            assertEquals("query text", out);
            assertEquals(1, state.getLookupTables().getEpisodes().size());
            assertFalse(state.getHistory().isBlank());
        }

        @Test
        void test_prepare_episodes_exclude_future_results_adds_lte_filter() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.setEmpty(GraphMemory.EPISODE_COLLECTION, false);
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            state.getStrategy().getRecallEpisode().setTopK(2);
            state.getStrategy().getRecallEpisode().setExcludeFutureResults(true);

            memory(backend).prepareEpisodes(EpisodeType.DOCUMENT, "user1", "q", state).join();

            assertEquals(1, backend.searchCalls);
        }

        @Test
        void test_prepare_episodes_same_kind_adds_obj_type_filter() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.setEmpty(GraphMemory.EPISODE_COLLECTION, false);
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            state.getStrategy().getRecallEpisode().setTopK(2);
            state.getStrategy().getRecallEpisode().setSameKind(true);

            memory(backend).prepareEpisodes(EpisodeType.CONVERSATION, "user1", "q", state).join();

            assertEquals(1, backend.searchCalls);
        }

        @Test
        void test_prepare_episodes_minimize_filters_by_distance_leq() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.setEmpty(GraphMemory.EPISODE_COLLECTION, false);
            backend.searchRows(GraphMemory.EPISODE_COLLECTION,
                    row("uuid", "ep1", "content", "far", "created_at", 0, "valid_since", 0, "distance", 0.5),
                    row("uuid", "ep2", "content", "near", "created_at", 0, "valid_since", 0, "distance", 0.05));
            GraphMemory mem = memory(backend);
            mem.setMetricIsSim(false);
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            state.getStrategy().getRecallEpisode().setMinScore(0.1);
            state.getStrategy().getRecallEpisode().getRankConfig().setHigherIsBetter(false);

            mem.prepareEpisodes(EpisodeType.DOCUMENT, "user1", "q", state).join();

            assertTrue(state.getHistory().contains("near"));
        }

        @Test
        void test_prepare_episodes_minimize_else_branch_filters_episodes_by_distance_leq() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.setEmpty(GraphMemory.EPISODE_COLLECTION, false);
            backend.searchRows(GraphMemory.EPISODE_COLLECTION,
                    row("uuid", "ep1", "content", "ok", "created_at", 0, "valid_since", 0, "distance", 0.05),
                    row("uuid", "ep2", "content", "no", "created_at", 0, "valid_since", 0, "distance", 0.9));
            GraphMemory mem = memory(backend);
            mem.setMetricIsSim(false);
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            state.getStrategy().getRecallEpisode().setMinScore(0.1);
            state.getStrategy().getRecallEpisode().getRankConfig().setHigherIsBetter(false);

            mem.prepareEpisodes(EpisodeType.DOCUMENT, "user1", "q", state).join();

            assertEquals(1, state.getLookupTables().getEpisodes().size());
            assertEquals("ok", state.getLookupTables().getEpisodes().values().iterator().next().getContent());
        }
    }

    @Nested
    @DisplayName("Fetch Relevant Entities")
    class TestFetchRelevantEntities {
        @Test
        void test_fetch_relevant_entities_no_existing_entity_returns_early() {
            FakeGraphStore backend = new FakeGraphStore();
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();

            memory(backend).fetchRelevantEntities(List.of(new GraphMemory.EntityDeclaration("E", 0)),
                    true, "user", state).join();

            assertEquals(0, backend.searchCalls);
            assertEquals(0, backend.queryCalls);
        }

        @Test
        void test_fetch_relevant_entities_tasks_at_most_one_returns_early() {
            FakeGraphStore backend = new FakeGraphStore();
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            state.getTasks().add(CompletableFuture.completedFuture(List.of()));

            memory(backend).fetchRelevantEntities(List.of(new GraphMemory.EntityDeclaration("E", 0)),
                    false, "user", state).join();

            assertEquals(0, backend.searchCalls);
        }

        @Test
        void test_fetch_relevant_entities_runs_search_and_query_when_tasks_sufficient() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.searchRows(GraphMemory.ENTITY_COLLECTION, row("uuid", "e1", "name", "E", "content", ""));
            backend.queryRows(GraphMemory.ENTITY_COLLECTION, row("uuid", "e2", "name", "Alice", "content", ""));
            GraphMemoryStates.GraphMemState state = stateWithEmbedAndRelationTask();

            memory(backend).fetchRelevantEntities(List.of(new GraphMemory.EntityDeclaration("Alice", 0)),
                    false, "user", state).join();

            assertTrue(backend.searchCalls > 0);
            assertTrue(backend.queryCalls > 0);
            assertTrue(state.getRetrievedEntities().containsKey("e1") || state.getRetrievedEntities().containsKey("e2"));
        }

        @Test
        void test_fetch_relevant_entities_minimize_and_entity_type_none_hits_else_branches() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.searchRows(GraphMemory.ENTITY_COLLECTION, row("uuid", "e1", "name", "E", "content", "", "distance", 0.03));
            backend.queryRows(GraphMemory.ENTITY_COLLECTION, row("uuid", "e2", "name", "Alice", "content", ""));
            GraphMemory mem = memory(backend);
            mem.setMetricIsSim(false);
            GraphMemoryStates.GraphMemState state = stateWithEmbedAndRelationTask();
            state.setEntityTypes(List.of());
            state.getStrategy().getRecallEntity().getRankConfig().setHigherIsBetter(false);
            state.getStrategy().getRecallEntity().setMinScore(0.1);

            mem.fetchRelevantEntities(List.of(new GraphMemory.EntityDeclaration("Alice", 0)),
                    false, "user", state).join();

            assertTrue(backend.searchCalls > 0);
            assertTrue(backend.queryCalls > 0);
            assertTrue(state.getRetrievedEntities().containsKey("e1") || state.getRetrievedEntities().containsKey("e2"));
        }

        @Test
        void test_fetch_relevant_entities_typed_search_minimize_filters_by_distance_leq() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.searchRows(GraphMemory.ENTITY_COLLECTION, row("uuid", "e1", "name", "E", "content", "", "distance", 0.03));
            GraphMemory mem = memory(backend);
            mem.setMetricIsSim(false);
            GraphMemoryStates.GraphMemState state = stateWithEmbedAndRelationTask();
            state.setEntityTypes(List.of(new GraphMemory.EntityTypeDef("Entity")));
            state.getStrategy().getRecallEntity().getRankConfig().setHigherIsBetter(false);
            state.getStrategy().getRecallEntity().setMinScore(0.05);

            mem.fetchRelevantEntities(List.of(new GraphMemory.EntityDeclaration("Alice", 0)),
                    false, "user", state).join();

            assertTrue(backend.searchCalls >= 2);
            assertTrue(state.getRetrievedEntities().containsKey("e1"));
        }
    }

    @Nested
    @DisplayName("Extract Entity Declarations")
    class TestExtractEntityDeclarations {
        @Test
        void test_extract_entity_declarations_returns_no_existing_and_list() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.setEmpty(GraphMemory.ENTITY_COLLECTION, true);
            GraphMemory mem = memory(backend, new QueueLlm("[{\"name\":\"Alice\",\"entity_type_id\":0}]"));

            GraphMemory.ExtractDeclarationsResult result = mem.extractEntityDeclarations(
                    EpisodeType.CONVERSATION, "content", new GraphMemoryStates.GraphMemState()).join();

            assertTrue(result.noExistingEntity());
            assertEquals(1, result.declarations().size());
            assertEquals("Alice", result.declarations().get(0).getName());
        }

        @Test
        void test_extract_entity_declarations_filters_user_assistant_names() {
            GraphMemory mem = memory(new FakeGraphStore(), new QueueLlm("[{\"name\":\"user\",\"entity_type_id\":0}]"));

            GraphMemory.ExtractDeclarationsResult result = mem.extractEntityDeclarations(
                    EpisodeType.CONVERSATION, "c", new GraphMemoryStates.GraphMemState()).join();

            assertTrue(result.declarations().isEmpty());
        }

        @Test
        void test_extract_entity_declarations_dict_response_normalized_to_list() {
            GraphMemory mem = memory(new FakeGraphStore(),
                    new QueueLlm("{\"entities\":[{\"name\":\"Alice\",\"entity_type_id\":0}]}"));

            GraphMemory.ExtractDeclarationsResult result = mem.extractEntityDeclarations(
                    EpisodeType.DOCUMENT, "c", new GraphMemoryStates.GraphMemState()).join();

            assertEquals(1, result.declarations().size());
            assertEquals("Alice", result.declarations().get(0).getName());
        }

        @Test
        void test_extract_entity_declarations_dict_value_single_dict_wrapped_in_list() {
            GraphMemory mem = memory(new FakeGraphStore(),
                    new QueueLlm("{\"entity\":{\"name\":\"Bob\",\"entity_type_id\":0}}"));

            GraphMemory.ExtractDeclarationsResult result = mem.extractEntityDeclarations(
                    EpisodeType.DOCUMENT, "c", new GraphMemoryStates.GraphMemState()).join();

            assertEquals(1, result.declarations().size());
            assertEquals("Bob", result.declarations().get(0).getName());
        }

        @Test
        void test_extract_entity_declarations_non_str_name_treated_as_empty() {
            GraphMemory mem = memory(new FakeGraphStore(), new QueueLlm("[{\"name\":123,\"entity_type_id\":0}]"));

            GraphMemory.ExtractDeclarationsResult result = mem.extractEntityDeclarations(
                    EpisodeType.DOCUMENT, "c", new GraphMemoryStates.GraphMemState()).join();

            assertTrue(result.declarations().isEmpty());
        }

        @Test
        void test_extract_entity_declarations_non_list_non_dict_becomes_empty() {
            GraphMemory mem = memory(new FakeGraphStore(), new QueueLlm("{\"key\":\"not_a_list\"}"));

            GraphMemory.ExtractDeclarationsResult result = mem.extractEntityDeclarations(
                    EpisodeType.DOCUMENT, "c", new GraphMemoryStates.GraphMemState()).join();

            assertTrue(result.declarations().isEmpty());
        }

        @Test
        void test_extract_entity_declarations_appends_embed_task_when_entities_exist() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.setEmpty(GraphMemory.ENTITY_COLLECTION, false);
            backend.attachEmbedder(new FakeEmbedding());
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            GraphMemory mem = memory(backend, new QueueLlm("[{\"name\":\"Alice\",\"entity_type_id\":0}]"));

            GraphMemory.ExtractDeclarationsResult result =
                    mem.extractEntityDeclarations(EpisodeType.DOCUMENT, "c", state).join();

            assertFalse(result.noExistingEntity());
            assertEquals(1, result.declarations().size());
            assertEquals(1, state.getTasks().size());
            state.getTasks().get(0).join();
            assertEquals(1, ((FakeEmbedding) backend.embedder).embedDocumentsCalls);
        }
    }

    @Nested
    @DisplayName("Resolve Entity Merges")
    class TestResolveEntityMerges {
        @Test
        void test_resolve_entity_merges_empty_no_op() {
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();

            memory(new FakeGraphStore()).resolveEntityMerges(List.of(), state).join();

            assertTrue(state.getMergeInfos().isEmpty());
        }

        @Test
        void test_resolve_entity_merges_sets_merge_infos_and_dispatch() {
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            Entity tgt = entity("T", "tgt");
            Entity src = entity("S", "src");
            src.setEpisodes(List.of("ep1"));

            memory(new FakeGraphStore()).resolveEntityMerges(List.of(new GraphMemory.MergeArgument(tgt, List.of(src))), state).join();

            assertTrue(state.getMergeInfos().containsKey("tgt"));
            assertSame(tgt, state.getMergeInfos().get("tgt").getTarget());
            assertTrue(state.getMergeInfos().get("tgt").getSource().containsKey("src"));
        }

        @Test
        void test_resolve_entity_merges_two_src_entities_episodes_deduped() {
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            Entity tgt = entity("T", "tgt");
            Entity src1 = entity("S1", "s1");
            src1.setEpisodes(List.of("ep1", "ep2"));
            Entity src2 = entity("S2", "s2");
            src2.setEpisodes(List.of("ep1", "ep3"));

            memory(new FakeGraphStore()).resolveEntityMerges(
                    List.of(new GraphMemory.MergeArgument(tgt, List.of(src1, src2))), state).join();

            assertEquals(Set.of("ep1", "ep2", "ep3"), Set.copyOf(tgt.getEpisodes()));
            assertEquals(tgt.getEpisodes().size(), Set.copyOf(tgt.getEpisodes()).size());
        }
    }

    @Nested
    @DisplayName("Dispatch Entity Merge Tasks")
    class TestDispatchEntityMergeTasks {
        @Test
        void test_dispatch_entity_merge_tasks_episodes_updated() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.queryRows(GraphMemory.EPISODE_COLLECTION, row("uuid", "ep1", "content", "c"));
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            state.getStrategy().setMergeFilter(false);

            memory(backend).dispatchEntityMergeTasks(Set.of("ep1"), Map.of(), state).join();

            assertEquals(1, state.getMemUpdateSkipEmbed().getUpdatedEpisode().size());
            assertEquals("ep1", state.getMemUpdateSkipEmbed().getUpdatedEpisode().get(0).getUuid());
        }

        @Test
        void test_dispatch_entity_merge_tasks_merge_filter_true_creates_relation_filter_tasks() {
            Entity tgt = entity("T", "tgt");
            Relation rel = relation("r1", "e1", "e2", "c");
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            state.getStrategy().setMergeFilter(true);
            state.getLookupTables().getEntities().put("tgt", tgt);
            state.getMergeInfos().put("tgt", new GraphMemoryStates.EntityMerge(tgt));
            Map<String, Map<String, Relation>> updates = Map.of("tgt", Map.of("r1", rel));

            memory(new FakeGraphStore(), new QueueLlm("{\"relevant_relations\":[1]}"))
                    .dispatchEntityMergeTasks(Set.of(), updates, state).join();

            assertEquals(1, state.getRelationFilterTasks().size());
            GraphMemoryStates.RelationFilterContext context =
                    state.getRelationFilterTasks().values().iterator().next();
            assertSame(tgt, context.getTargetEntity());
            assertEquals(List.of(rel), context.getRelations());
        }
    }

    @Nested
    @DisplayName("Entity Enrich")
    class TestEntityEnrich {
        @Test
        void test_entity_enrich_non_blocking_updates_all() {
            GraphMemory mem = memory(new FakeGraphStore(), new QueueLlm("{\"summary\":\"s\",\"attributes\":{}}"));
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            Entity e1 = entity("E1", "e1");

            List<Entity> result = mem.entityEnrich(List.of(e1), "content", state).join();

            assertEquals(List.of(e1), result);
            assertEquals("s", e1.getContent());
        }

        @Test
        void test_entity_enrich_blocking_waits_pending_merge_then_extracts() {
            GraphMemory mem = memory(new FakeGraphStore(), new QueueLlm("{\"summary\":\"merged\",\"attributes\":{}}"));
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            Entity e1 = entity("E1", "e1");
            CompletableFuture<GraphMemory.LlmResponse> pending =
                    CompletableFuture.completedFuture(new GraphMemory.LlmResponse("{\"summary\":\"merged\",\"attributes\":{}}"));
            state.getPendingMerge().put("e1", pending);
            state.getMergingTasks().add(pending);

            List<Entity> result = mem.entityEnrich(List.of(e1), "content", state).join();

            assertEquals(List.of(e1), result);
            assertEquals("merged", e1.getContent());
        }
    }

    @Nested
    @DisplayName("Resolve Each Relation")
    class TestResolveEachRelation {
        @Test
        void test_resolve_each_relation_replace_one_side_called() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.queryRows(GraphMemory.RELATION_COLLECTION, relationRow("r1", "src", "other", "c"));
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            Entity src = entity("Src", "src");
            src.setRelations(List.of("r1"));
            Map<String, Map<String, Relation>> updates = new LinkedHashMap<>();
            updates.put("tgt", new LinkedHashMap<>());
            state.getRelationDeferredUpdates().put("tgt", new ArrayList<>());

            memory(backend).resolveEachRelation("tgt", src, Map.of("src", "tgt"), updates, state, Set.of()).join();

            assertTrue(updates.get("tgt").containsKey("r1"));
        }

        @Test
        void test_resolve_each_relation_self_pointing_marked_faulty() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.queryRows(GraphMemory.RELATION_COLLECTION, relationRow("r1", "tgt", "tgt", "c"));
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            Entity src = entity("S", "tgt");
            src.setRelations(List.of("r1"));

            memory(backend).resolveEachRelation("tgt", src, Map.of(), new LinkedHashMap<>(), state, Set.of("tgt")).join();

            assertTrue(state.getFaultyRelations().containsKey("r1"));
        }

        @Test
        void test_resolve_each_relation_not_connected_marked_faulty() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.queryRows(GraphMemory.RELATION_COLLECTION, relationRow("r1", "other1", "other2", "c"));
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            Entity src = entity("Src", "src");
            src.setRelations(List.of("r1"));
            Map<String, Map<String, Relation>> updates = new LinkedHashMap<>();
            updates.put("tgt", new LinkedHashMap<>());
            state.getRelationDeferredUpdates().put("tgt", new ArrayList<>());

            memory(backend).resolveEachRelation("tgt", src, Map.of("src", "tgt"), updates, state, Set.of()).join();

            assertTrue(state.getFaultyRelations().containsKey("r1"));
        }

        @Test
        void test_resolve_each_relation_while_loop_chain_replaces_after_two_steps() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.queryRows(GraphMemory.RELATION_COLLECTION, relationRow("r1", "mid", "other", "c"));
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            Entity src = entity("Src", "src");
            src.setRelations(List.of("r1"));
            Map<String, Map<String, Relation>> updates = new LinkedHashMap<>();
            updates.put("tgt", new LinkedHashMap<>());
            state.getRelationDeferredUpdates().put("tgt", new ArrayList<>());

            memory(backend).resolveEachRelation(
                    "tgt", src, Map.of("src", "mid", "mid", "tgt"), updates, state, Set.of()).join();

            assertEquals("lhs", state.getRelationDeferredUpdates().get("tgt").get(0).getSide());
            assertSame(updates.get("tgt").get("r1"), state.getRelationDeferredUpdates().get("tgt").get(0).getRelation());
        }
    }

    @Nested
    @DisplayName("Entity Merge")
    class TestEntityMerge {
        @Test
        void test_entity_merge_empty_existing_returns_extracted() {
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            List<GraphMemory.EntityDeclaration> declarations = List.of(new GraphMemory.EntityDeclaration("E", 0));

            List<?> out = memory(new FakeGraphStore()).entityMerge(declarations, List.of(), state).join();

            assertSame(declarations, out);
        }

        @Test
        void test_entity_merge_merge_entities_false_clears_merging_args() {
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            state.getStrategy().setMergeEntities(false);
            state.getTasks().add(CompletableFuture.completedFuture(new GraphMemory.LlmResponse("[]")));
            List<GraphMemory.EntityDeclaration> declarations = List.of(new GraphMemory.EntityDeclaration("E", 0));
            List<Map<String, Object>> existing = List.of(
                    row("uuid", "tgt", "name", "T", "content", ""),
                    row("uuid", "src", "name", "S", "content", ""));

            memory(new FakeGraphStore()).entityMerge(declarations, existing, state).join();

            assertTrue(state.getMergeInfos().isEmpty());
            assertTrue(state.getMemUpdate().getRemovedEntity().isEmpty());
        }

        @Test
        void test_entity_merge_with_existing_dispatches_blocking_and_non_blocking_tasks() {
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            Entity tgt = entity("T", "tgt");
            Entity src = entity("S", "src");
            state.getLookupTables().getEntities().put("tgt", tgt);
            state.getLookupTables().getEntities().put("src", src);
            state.getTasks().add(CompletableFuture.completedFuture(new GraphMemory.LlmResponse("[]")));
            List<Map<String, Object>> existing = List.of(row("uuid", "tgt", "name", "T"), row("uuid", "src", "name", "S"));

            List<?> out = memory(new FakeGraphStore(), new QueueLlm("{\"summary\":\"m\",\"attributes\":{}}"))
                    .entityMerge(List.of(tgt), existing, state).join();

            assertFalse(state.getMergingTasks().isEmpty());
            assertEquals(List.of(tgt), out);
        }

        @Test
        void test_entity_merge_tgt_not_in_extracted_dispatches_non_blocking_task() {
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            state.getTasks().add(CompletableFuture.completedFuture(new GraphMemory.LlmResponse("[]")));
            List<GraphMemory.EntityDeclaration> declarations = List.of(new GraphMemory.EntityDeclaration("Other", 0));
            List<Map<String, Object>> existing = List.of(row("uuid", "tgt", "name", "T"), row("uuid", "src", "name", "S"));

            List<?> out = memory(new FakeGraphStore(), new QueueLlm("{\"summary\":\"m\",\"attributes\":{}}"))
                    .entityMerge(declarations, existing, state).join();

            assertFalse(state.getMergingTasks().isEmpty());
            assertTrue(state.getPendingMerge().isEmpty());
            assertEquals(declarations, out);
        }
    }

    @Nested
    @DisplayName("Handle Relation Dedupe")
    class TestHandleRelationDedupe {
        @Test
        void test_handle_relation_dedupe_removes_to_remove_from_relations() {
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            Relation keep = relation("r1", "e1", "e2", "c");
            Relation remove = relation("r2", "e1", "e2", "c2");
            state.getToRemove().add(remove);
            List<Relation> relations = new ArrayList<>(List.of(keep, remove));

            memory(new FakeGraphStore()).handleRelationDedupe("user", "content", relations, state).join();

            assertFalse(relations.contains(remove));
            assertTrue(relations.contains(keep));
        }

        @Test
        void test_handle_relation_dedupe_skip_when_no_merge_relations() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.setEmpty(GraphMemory.RELATION_COLLECTION, false);
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            state.getStrategy().setMergeRelations(false);
            state.getTmpBuffer().add("x");

            memory(backend).handleRelationDedupe("user", "content", new ArrayList<>(), state).join();

            assertEquals(0, backend.searchCalls);
        }

        @Test
        void test_handle_relation_dedupe_calls_embed_and_relation_dedupe_when_conditions_met() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.setEmpty(GraphMemory.RELATION_COLLECTION, false);
            FakeEmbedding embedder = new FakeEmbedding();
            backend.attachEmbedder(embedder);
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            state.getTmpBuffer().add("rel content");
            Relation rel = relation("r1", "e1", "e2", "rel content");

            memory(backend).handleRelationDedupe("user", "content", new ArrayList<>(List.of(rel)), state).join();

            assertEquals(1, embedder.embedDocumentsCalls);
            assertEquals(1, backend.searchCalls);
        }
    }

    @Nested
    @DisplayName("Relation Dedupe")
    class TestRelationDedupe {
        @Test
        void test_relation_dedupe_no_similar_relations_skips_llm() {
            FakeGraphStore backend = new FakeGraphStore();
            QueueLlm llm = new QueueLlm("{}");
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();

            memory(backend, llm).relationDedupe("user", "content",
                    List.of(relation("r1", "e1", "e2", "c")), List.of(vector()), state).join();

            assertTrue(llm.calls.isEmpty());
        }

        @Test
        void test_relation_dedupe_lhs_rhs_falsy_skips_relation() {
            FakeGraphStore backend = new FakeGraphStore();
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();

            memory(backend).relationDedupe("user", "content",
                    List.of(relation("r1", "", "e2", "c")), List.of(vector()), state).join();

            assertEquals(0, backend.searchCalls);
        }

        @Test
        void test_relation_dedupe_minimize_filters_by_distance_leq() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.searchRows(GraphMemory.RELATION_COLLECTION,
                    relationRow("r0", "e1", "e2", "c", "distance", 0.02));
            GraphMemory mem = memory(backend);
            mem.setMetricIsSim(false);
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            state.getStrategy().getRecallRelation().getRankConfig().setHigherIsBetter(false);
            state.getStrategy().getRecallRelation().setMinScore(0.05);

            mem.relationDedupe("user", "content",
                    List.of(relation("r1", "e1", "e2", "c")), List.of(vector()), state).join();

            assertEquals(1, backend.searchCalls);
            assertTrue(state.getRetrievedRelations().containsKey("r0"));
        }
    }

    @Nested
    @DisplayName("Update Entities For Relation Removal")
    class TestUpdateEntitiesForRelationRemoval {
        @Test
        void test_update_entities_for_relation_removal_removes_relation_from_entity() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.queryRows(GraphMemory.ENTITY_COLLECTION, row("uuid", "e1", "name", "E", "relations", List.of("r1")));
            GraphMemoryStates.GraphMemState state = removalState();

            memory(backend).updateEntitiesForRelationRemoval(state, List.of()).join();

            Entity ent = state.getLookupTables().getEntities().get("e1");
            assertNotNull(ent);
            assertFalse(ent.getRelations().contains("r1"));
        }

        @Test
        void test_update_entities_for_relation_removal_uses_cached_entity_when_present() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.queryRows(GraphMemory.ENTITY_COLLECTION, row("uuid", "e1", "name", "E", "relations", List.of("r1")));
            GraphMemoryStates.GraphMemState state = removalState();
            Entity cached = entity("E", "e1");
            cached.setRelations(List.of("r1"));
            state.getLookupTables().getEntities().put("e1", cached);

            memory(backend).updateEntitiesForRelationRemoval(state, List.of()).join();

            assertTrue(cached.getRelations().isEmpty());
            assertTrue(state.getMemUpdateSkipEmbed().getUpdatedEntity().contains(cached));
        }

        @Test
        void test_update_entities_for_relation_removal_needs_re_embed_uses_update_needs_embed_entity() {
            FakeGraphStore backend = new FakeGraphStore();
            backend.queryRows(GraphMemory.ENTITY_COLLECTION, row("uuid", "e1", "name", "E", "relations", List.of("r1")));
            GraphMemoryStates.GraphMemState state = removalState();
            Entity inUpdate = entity("E", "e1");
            inUpdate.setContent("new");
            inUpdate.setRelations(List.of("r1"));

            memory(backend).updateEntitiesForRelationRemoval(state, List.of(inUpdate)).join();

            assertTrue(inUpdate.getRelations().isEmpty());
            assertFalse(state.getMemUpdateSkipEmbed().getUpdatedEntity().contains(inUpdate));
        }
    }

    private static GraphMemory memory(FakeGraphStore backend) {
        return memory(backend, new QueueLlm("{}"));
    }

    private static GraphMemory memory(FakeGraphStore backend, QueueLlm llm) {
        return new GraphMemory(graphConfig(), backend, llm);
    }

    private static GraphConfig graphConfig() {
        return GraphConfig.builder()
                .uri(System.getProperty("java.io.tmpdir") + "/test_graph_memory_base")
                .name("test_graph_memory_base")
                .backend("in_memory")
                .embedDim(64)
                .build();
    }

    private static GraphMemoryStates.GraphMemState stateWithEmbedAndRelationTask() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.setEntityTypes(List.of(new GraphMemory.EntityTypeDef("Entity")));
        state.getTasks().add(CompletableFuture.completedFuture(List.of(vector())));
        state.getTasks().add(new CompletableFuture<>());
        return state;
    }

    private static GraphMemoryStates.GraphMemState removalState() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        Relation rel = relation("r1", "e1", "e2", "c");
        state.getToRemove().add(rel);
        state.getMemUpdate().getRemovedRelation().add("r1");
        return state;
    }

    private static BaseError futureError(CompletableFuture<?> future) {
        CompletionException error = assertThrows(CompletionException.class, future::join);
        assertTrue(error.getCause() instanceof BaseError, () -> "Expected BaseError but got " + error.getCause());
        return (BaseError) error.getCause();
    }

    private static Entity entity(String name, String uuid) {
        Entity entity = new Entity();
        entity.setName(name);
        entity.setUuid(uuid);
        entity.setContent("");
        return entity;
    }

    private static Relation relation(String uuid, String lhs, String rhs, String content) {
        Relation relation = new Relation();
        relation.setUuid(uuid);
        relation.setName("R");
        relation.setLhs(lhs);
        relation.setRhs(rhs);
        relation.setContent(content);
        return relation;
    }

    private static Map<String, Object> relationRow(String uuid, String lhs, String rhs, String content, Object... rest) {
        List<Object> values = new ArrayList<>(List.of("uuid", uuid, "name", "R", "lhs", lhs, "rhs", rhs, "content", content));
        values.addAll(Arrays.asList(rest));
        return row(values.toArray());
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            row.put(String.valueOf(values[i]), values[i + 1]);
        }
        return row;
    }

    private static List<Float> vector() {
        return List.of(0.1f, 0.1f, 0.1f, 0.1f);
    }

    private static com.openjiuwen.core.foundation.prompt.PromptTemplate template(String name) {
        return new com.openjiuwen.core.foundation.prompt.PromptTemplate(name, "test", "{{", "}}");
    }

    static class QueueLlm implements GraphMemory.LlmInvoker {
        private final ArrayDeque<String> responses = new ArrayDeque<>();
        private final boolean failing;
        final List<Map<String, Object>> calls = new ArrayList<>();

        QueueLlm(String... responses) {
            this(false, responses);
        }

        private QueueLlm(boolean failing, String... responses) {
            this.failing = failing;
            this.responses.addAll(Arrays.asList(responses));
        }

        static QueueLlm failing() {
            return new QueueLlm(true);
        }

        @Override
        public CompletableFuture<GraphMemory.LlmResponse> invoke(Map<String, Object> params) {
            calls.add(new LinkedHashMap<>(params));
            if (failing) {
                return CompletableFuture.failedFuture(new RuntimeException("fail"));
            }
            String content = responses.isEmpty() ? "{}" : responses.removeFirst();
            return CompletableFuture.completedFuture(new GraphMemory.LlmResponse(content));
        }
    }

    static class FakeEmbedding extends Embedding {
        int embedQueryCalls;
        int embedDocumentsCalls;
        List<String> lastTexts = List.of();

        @Override
        public List<Float> embedQuery(String text) {
            embedQueryCalls++;
            return vector();
        }

        @Override
        public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize) {
            embedDocumentsCalls++;
            lastTexts = new ArrayList<>(texts);
            List<List<Float>> result = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                result.add(vector());
            }
            return result;
        }

        @Override
        public int getDimension() {
            return 64;
        }
    }

    static class FakeReranker implements Reranker {
        @Override
        public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK) {
            return candidates.stream().limit(topK).toList();
        }
    }

    static class FakeGraphStore implements GraphStore {
        private final GraphConfig config = graphConfig();
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final Map<String, Boolean> empty = new HashMap<>();
        private final Map<String, List<Map<String, Object>>> searchRows = new HashMap<>();
        private final Map<String, List<Map<String, Object>>> queryRows = new HashMap<>();
        Embedding embedder;
        int attachEmbedderCalls;
        int refreshCalls;
        int searchCalls;
        int queryCalls;

        void setEmpty(String collection, boolean value) {
            empty.put(collection, value);
        }

        void searchRows(String collection, Map<String, Object>... rows) {
            searchRows.put(collection, new ArrayList<>(Arrays.asList(rows)));
            setEmpty(collection, false);
        }

        void queryRows(String collection, Map<String, Object>... rows) {
            queryRows.put(collection, new ArrayList<>(Arrays.asList(rows)));
        }

        @Override
        public GraphConfig getConfig() {
            return config;
        }

        @Override
        public ExecutorService getEmbedExecutor() {
            return executor;
        }

        @Override
        public Embedding getEmbedder() {
            return embedder;
        }

        @Override
        public void refresh() {
            refreshCalls++;
        }

        @Override
        public void addData(String collection, Iterable<Map<String, Object>> data, boolean flush, boolean upsert) {}

        @Override
        public void addEntity(Iterable<?> entities, boolean flush, boolean upsert, boolean noEmbed) {}

        @Override
        public void addRelation(Iterable<?> relations, boolean flush, boolean upsert, boolean noEmbed) {}

        @Override
        public void addEpisode(Iterable<?> episodes, boolean flush, boolean upsert, boolean noEmbed) {}

        @Override
        public boolean isEmpty(String collection) {
            return empty.getOrDefault(collection, !searchRows.containsKey(collection));
        }

        @Override
        public List<Map<String, Object>> query(String collection, List<Object> ids, QueryExpr expr, boolean silenceErrors) {
            queryCalls++;
            List<Map<String, Object>> rows = new ArrayList<>(queryRows.getOrDefault(collection, List.of()));
            if (ids == null || ids.isEmpty()) {
                return rows;
            }
            return rows.stream()
                    .filter(row -> ids.contains(row.get("uuid")) || ids.contains(row.get("id")))
                    .toList();
        }

        @Override
        public Map<String, Object> delete(String collection, List<Object> ids, QueryExpr expr) {
            return Map.of("deleted", 0);
        }

        @Override
        public Map<String, List<Map<String, Object>>> search(String queryText, int k, String collection,
                Object rankerConfig, int bfsDepth, int bfsK, QueryExpr filterExpr, List<String> outputFields,
                List<Float> queryEmbedding, Map<String, Object> kwargs) {
            searchCalls++;
            List<Map<String, Object>> rows = searchRows.getOrDefault(collection, List.of()).stream()
                    .limit(k)
                    .map(LinkedHashMap::new)
                    .collect(java.util.stream.Collectors.toList());
            return Map.of(collection, rows);
        }

        @Override
        public void attachEmbedder(Embedding embedder) {
            attachEmbedderCalls++;
            this.embedder = embedder;
        }

        @Override
        public void close() {
            executor.shutdownNow();
        }
    }
}
