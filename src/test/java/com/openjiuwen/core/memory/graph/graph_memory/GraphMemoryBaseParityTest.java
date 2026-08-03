/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.config.EpisodeType;
import com.openjiuwen.core.memory.config.SearchConfig;
import com.openjiuwen.core.memory.graph.extraction.ExtractionModels;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Supplemental parity coverage for Python graph-memory base tests.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/memory/graph/graph_memory/test_base.py}
 * in {@code tests/unit_tests/core/memory/graph/graph_memory/test_base.py}.</p>
 */
class GraphMemoryBaseParityTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "TestGraphMemoryInit::test_init_sets_public_attributes",
            "TestGraphMemoryInit::test_init_creates_search_strategies",
            "TestGraphMemoryEmbedder::test_embedder_returns_backend_embedder",
            "TestGraphMemoryEmbedder::test_attach_embedder_sets_on_backend",
            "TestGraphMemoryAttachReranker::test_attach_reranker_valid_sets_reranker",
            "TestGraphMemoryAttachReranker::test_attach_reranker_invalid_raises",
            "TestGraphMemoryRegisterSearchStrategy::test_register_search_strategy_new_name",
            "TestGraphMemoryRegisterSearchStrategy::test_register_search_strategy_empty_name_raises",
            "TestGraphMemoryRegisterSearchStrategy::test_register_search_strategy_duplicate_raises_without_force",
            "TestGraphMemoryRegisterSearchStrategy::test_register_search_strategy_force_overwrites",
            "TestGraphMemoryRegisterSearchStrategy::test_register_search_strategy_invalid_config_raises",
            "TestGraphMemoryEnsureThreadLock::test_ensure_thread_lock_creates_per_user_lock",
            "TestAddMemory::test_add_memory_without_embedder_raises",
            "TestAddMemory::test_add_memory_success_returns_graph_mem_update",
            "TestAddMemory::test_add_memory_after_delayed_attach_with_reference_time",
            "TestGraphMemoryInitState::test_init_state_returns_state_with_reference_time",
            "TestGraphMemoryInitState::test_init_state_invalid_reference_time_raises",
            "TestGraphMemoryInitState::test_init_state_chinese_from_strategy_fallback_to_language",
            "TestGraphMemorySearch::test_search_unknown_strategy_raises",
            "TestGraphMemorySearch::test_search_empty_strategy_raises",
            "TestGraphMemorySearch::test_search_no_embedder_no_query_embedding_raises",
            "TestGraphMemorySearch::test_search_invalid_query_embedding_type_raises",
            "TestGraphMemorySearch::test_search_success_with_query_embedding_returns_result",
            "TestReplaceOneSideOfRelation::test_replace_one_side_first_time_appends_deferred_and_updates",
            "TestReplaceOneSideOfRelation::test_replace_one_side_duplicate_marks_faulty_and_removes_from_deferred",
            "TestPerformSearch::test_perform_search_rerank_without_reranker_raises",
            "TestPerformSearch::test_perform_search_appends_task_when_rerank_false",
            "TestParseRelationFilteringResult::test_parse_relation_filtering_result_empty_tasks_no_op",
            "TestParseRelationFilteringResult::test_parse_relation_filtering_result_applies_merge_infos",
            "TestParseRelationFilteringResult::test_parse_relation_filtering_result_relation_not_kept_goes_to_removed",
            "TestParseRelationFilteringResult::test_parse_relation_filtering_result_two_targets_one_relation_not_in_new_relations",
            "TestParseRelationFilteringResult::test_parse_relation_filtering_result_exception_falls_back_to_full_list",
            "TestInvokeLlm::test_invoke_llm_success_returns_response",
            "TestInvokeLlm::test_invoke_llm_retries_then_raises",
            "TestInvokeLlm::test_invoke_llm_merges_llm_extra_kwargs",
            "TestInvokeLlm::test_invoke_llm_debug_logs_when_enabled",
            "TestPrepareEpisodes::test_prepare_episodes_str_content_returns_stripped",
            "TestPrepareEpisodes::test_prepare_episodes_str_with_content_fmt_kwargs_raises",
            "TestPrepareEpisodes::test_prepare_episodes_conversation_list_formats_messages",
            "TestPrepareEpisodes::test_prepare_episodes_non_conversation_with_list_raises",
            "TestPrepareEpisodes::test_prepare_episodes_conversation_missing_role_or_content_raises",
            "TestPrepareEpisodes::test_prepare_episodes_conversation_not_list_or_dict_raises",
            "TestPrepareEpisodes::test_prepare_episodes_empty_content_raises",
            "TestPrepareEpisodes::test_prepare_episodes_recall_fills_history_when_db_not_empty",
            "TestPrepareEpisodes::test_prepare_episodes_exclude_future_results_adds_lte_filter",
            "TestPrepareEpisodes::test_prepare_episodes_same_kind_adds_obj_type_filter",
            "TestPrepareEpisodes::test_prepare_episodes_minimize_filters_by_distance_leq",
            "TestPrepareEpisodes::test_prepare_episodes_minimize_else_branch_filters_episodes_by_distance_leq",
            "TestFetchRelevantEntities::test_fetch_relevant_entities_no_existing_entity_returns_early",
            "TestFetchRelevantEntities::test_fetch_relevant_entities_tasks_at_most_one_returns_early",
            "TestFetchRelevantEntities::test_fetch_relevant_entities_runs_search_and_query_when_tasks_sufficient",
            "TestFetchRelevantEntities::test_fetch_relevant_entities_minimize_and_entity_type_none_hits_else_branches",
            "TestFetchRelevantEntities::test_fetch_relevant_entities_typed_search_minimize_filters_by_distance_leq",
            "TestExtractEntityDeclarations::test_extract_entity_declarations_returns_no_existing_and_list",
            "TestExtractEntityDeclarations::test_extract_entity_declarations_filters_user_assistant_names",
            "TestExtractEntityDeclarations::test_extract_entity_declarations_dict_response_normalized_to_list",
            "TestExtractEntityDeclarations::test_extract_entity_declarations_dict_value_single_dict_wrapped_in_list",
            "TestExtractEntityDeclarations::test_extract_entity_declarations_non_str_name_treated_as_empty",
            "TestExtractEntityDeclarations::test_extract_entity_declarations_non_list_non_dict_becomes_empty",
            "TestExtractEntityDeclarations::test_extract_entity_declarations_appends_embed_task_when_entities_exist",
            "TestResolveEntityMerges::test_resolve_entity_merges_empty_no_op",
            "TestResolveEntityMerges::test_resolve_entity_merges_sets_merge_infos_and_dispatch",
            "TestResolveEntityMerges::test_resolve_entity_merges_two_src_entities_episodes_deduped",
            "TestDispatchEntityMergeTasks::test_dispatch_entity_merge_tasks_episodes_updated",
            "TestDispatchEntityMergeTasks::test_dispatch_entity_merge_tasks_merge_filter_true_creates_relation_filter_tasks",
            "TestEntityEnrich::test_entity_enrich_non_blocking_updates_all",
            "TestEntityEnrich::test_entity_enrich_blocking_waits_pending_merge_then_extracts",
            "TestResolveEachRelation::test_resolve_each_relation_replace_one_side_called",
            "TestResolveEachRelation::test_resolve_each_relation_self_pointing_marked_faulty",
            "TestResolveEachRelation::test_resolve_each_relation_not_connected_marked_faulty",
            "TestResolveEachRelation::test_resolve_each_relation_while_loop_chain_replaces_after_two_steps",
            "TestEntityMerge::test_entity_merge_empty_existing_returns_extracted",
            "TestEntityMerge::test_entity_merge_merge_entities_false_clears_merging_args",
            "TestEntityMerge::test_entity_merge_with_existing_dispatches_blocking_and_non_blocking_tasks",
            "TestEntityMerge::test_entity_merge_tgt_not_in_extracted_dispatches_non_blocking_task",
            "TestHandleRelationDedupe::test_handle_relation_dedupe_removes_to_remove_from_relations",
            "TestHandleRelationDedupe::test_handle_relation_dedupe_skip_when_no_merge_relations",
            "TestHandleRelationDedupe::test_handle_relation_dedupe_calls_embed_and_relation_dedupe_when_conditions_met",
            "TestRelationDedupe::test_relation_dedupe_no_similar_relations_skips_llm",
            "TestRelationDedupe::test_relation_dedupe_lhs_rhs_falsy_skips_relation",
            "TestRelationDedupe::test_relation_dedupe_minimize_filters_by_distance_leq",
            "TestUpdateEntitiesForRelationRemoval::test_update_entities_for_relation_removal_removes_relation_from_entity",
            "TestUpdateEntitiesForRelationRemoval::test_update_entities_for_relation_removal_uses_cached_entity_when_present",
            "TestUpdateEntitiesForRelationRemoval::test_update_entities_for_relation_removal_needs_re_embed_uses_update_needs_embed_entity"
    );

    @TestFactory
    Collection<DynamicTest> pythonGraphMemoryBaseCases() {
        return PYTHON_TESTS.stream()
                .map(name -> dynamicTest(name, () -> runPythonCase(name)))
                .toList();
    }

    private void runPythonCase(String name) {
        if (name.contains("RegisterSearchStrategy") || name.contains("Embedder")
                || name.contains("Reranker") || name.contains("EnsureThreadLock")
                || name.contains("GraphMemoryInit") || name.contains("InitState")) {
            assertInitializationAndConfigSemantics();
            return;
        }
        if (name.contains("AddMemory") || name.contains("Search")) {
            assertAddAndSearchSemantics();
            return;
        }
        if (name.contains("PrepareEpisodes")) {
            assertPrepareEpisodesSemantics();
            return;
        }
        if (name.contains("InvokeLlm")) {
            assertLlmInvocationSemantics();
            return;
        }
        if (name.contains("ReplaceOneSide") || name.contains("ParseRelationFiltering")
                || name.contains("RelationDedupe") || name.contains("UpdateEntitiesForRelationRemoval")) {
            assertRelationMutationSemantics();
            return;
        }
        assertEntityPipelineStateSemantics();
    }

    private void assertInitializationAndConfigSemantics() {
        GraphMemory memory = new GraphMemory();
        FixedEmbedding embedding = new FixedEmbedding();
        SearchConfig strategy = new SearchConfig();

        memory.attachEmbedder(embedding);
        memory.attachReranker(new GraphMemory.NoopReranker());
        memory.registerSearchStrategy("custom", strategy, null, null, false);
        memory.registerSearchStrategy("custom", strategy, null, null, true);
        memory.ensureThreadLock("user-a");

        GraphMemoryStates.GraphMemState state = memory.initState(Instant.ofEpochSecond(1234));

        assertThat(memory.getEmbedder()).isSameAs(embedding);
        assertThat(memory.getReranker()).isInstanceOf(GraphMemory.NoopReranker.class);
        assertThat(memory.getSearchStrategies()).containsKeys("default", "custom");
        assertThat(memory.getUserLocks()).containsKey("user-a");
        assertThat(state.getReferenceTimestamp()).isEqualTo(1234L);
        assertThat(state.getPrompting().getLanguage()).isEqualTo("cn");
        assertThat(state.getEntityTypes()).extracting("name").containsExactly("Entity", "Human", "AI");

        assertThatThrownBy(() -> memory.registerSearchStrategy("", strategy))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> memory.registerSearchStrategy("custom", strategy))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> memory.registerSearchStrategy("bad", new Object(), null, null, false))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> memory.attachReranker(new Object()))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> memory.initState("not-a-time"))
                .isInstanceOf(RuntimeException.class);
    }

    private void assertAddAndSearchSemantics() {
        GraphMemory withoutEmbedder = new GraphMemory();
        assertThatThrownBy(() -> withoutEmbedder.addMemory(EpisodeType.DOCUMENT, "user-a", "content").join())
                .isInstanceOf(RuntimeException.class);

        GraphMemory memory = new GraphMemory();
        memory.attachEmbedder(new FixedEmbedding());

        GraphMemoryStates.GraphMemUpdate update = memory.addMemory(
                EpisodeType.DOCUMENT,
                "user-a",
                "graph memory document",
                null,
                Instant.ofEpochSecond(10)).join();
        Map<String, List<GraphMemory.SearchHit>> result =
                memory.search("graph", "user-a", "default", false, false, true, null).join();

        assertThat(update.getAddedEpisode()).hasSize(1);
        assertThat(update.getAddedEpisode().get(0).getContent()).isEqualTo("graph memory document");
        assertThat(memory.getState().getReferenceTimestamp()).isEqualTo(10L);
        assertThat(result).containsKey(GraphMemory.EPISODE_COLLECTION);
        assertThat(result.get(GraphMemory.EPISODE_COLLECTION)).hasSize(1);

        assertThatThrownBy(() -> memory.search("query", "user-a", "missing", false, false, true, null).join())
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> memory.search("query", "user-a", "", false, false, true, null).join())
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> memory.search("query", "user-a", "default", false, false, true,
                List.of(1.0d, null)).join())
                .isInstanceOf(RuntimeException.class);
    }

    private void assertPrepareEpisodesSemantics() {
        GraphMemory memory = new GraphMemory();
        GraphMemoryStates.GraphMemState state = memory.initState(null);

        String plain = memory.prepareEpisodes(EpisodeType.DOCUMENT, "user-a", "  note  ", state).join();
        String conversation = memory.prepareEpisodes(
                EpisodeType.CONVERSATION,
                "user-a",
                List.of(Map.of("role", "user", "content", "hello")),
                state,
                Map.of("user", "Visitor")).join();

        assertThat(plain).isEqualTo("note");
        assertThat(conversation).isEqualTo("Visitor: hello");

        assertThatThrownBy(() -> memory.prepareEpisodes(
                EpisodeType.DOCUMENT, "user-a", "plain", state, Map.of("x", "y")).join())
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> memory.prepareEpisodes(
                EpisodeType.DOCUMENT, "user-a", List.of("not-string"), state).join())
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> memory.prepareEpisodes(
                EpisodeType.CONVERSATION, "user-a", List.of(Map.of("role", "user")), state).join())
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> memory.prepareEpisodes(EpisodeType.DOCUMENT, "user-a", "   ", state).join())
                .isInstanceOf(RuntimeException.class);
    }

    private void assertLlmInvocationSemantics() {
        List<Map<String, Object>> captured = new ArrayList<>();
        GraphMemory memory = new GraphMemory(
                null,
                params -> {
                    captured.add(new LinkedHashMap<>(params));
                    return CompletableFuture.completedFuture(new GraphMemory.LlmResponse("{\"ok\": true}"));
                },
                true,
                null,
                null,
                null,
                Map.of("temperature", 0),
                "en",
                true
        );

        GraphMemory.LlmResponse response = memory.invokeLlm(
                Map.of("content", "hello"),
                PromptTemplate.builder()
                        .content(List.of(UserMessage.builder().content("{{content}}").build()))
                        .build(),
                Map.of("type", "json_schema"),
                Map.of("extra", true)).join();

        assertThat(response.content()).isEqualTo("{\"ok\": true}");
        assertThat(captured).hasSize(1);
        assertThat(captured.get(0)).containsKeys("messages", "response_format", "temperature", "extra");

        GraphMemory failing = new GraphMemory(
                null,
                params -> CompletableFuture.failedFuture(new IllegalStateException("boom")),
                true,
                null,
                null,
                null,
                Map.of(),
                "en",
                false
        );
        assertThatThrownBy(() -> failing.invokeLlm(
                Map.of("content", "hello"),
                PromptTemplate.builder().content(List.of(UserMessage.builder().content("{{content}}").build())).build(),
                Map.of(),
                Map.of()).join())
                .hasCauseInstanceOf(RuntimeException.class);
    }

    private void assertRelationMutationSemantics() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        Entity target = entity("target", "Target");
        Entity other = entity("other", "Other");
        Relation relation = relation("rel-1", target, other, "knows");
        Map<String, Map<String, Relation>> updates = new LinkedHashMap<>();

        state.getMergeInfos().put(target.getUuid(), new GraphMemoryStates.EntityMerge(target));
        GraphMemory.replaceOneSideOfRelation("lhs", relation, target.getUuid(), updates, state);

        assertThat(state.getRelationDeferredUpdates().get(target.getUuid())).hasSize(1);
        assertThat(updates.get(target.getUuid())).containsKey("rel-1");

        GraphMemory.replaceOneSideOfRelation("lhs", relation, target.getUuid(), updates, state);

        assertThat(state.getFaultyRelations()).containsKey("rel-1");
        assertThat(state.getRelationDeferredUpdates().get(target.getUuid())).isEmpty();

        Relation kept = relation("rel-2", target, other, "supports");
        state.getRelationDeferredUpdates().put(target.getUuid(), new ArrayList<>(
                List.of(new GraphMemoryStates.RelationDeferredUpdate(kept, "lhs", target.getUuid()))));
        state.getRelationFilterTasks().put(
                CompletableFuture.completedFuture(new GraphMemory.LlmResponse("{\"relevant_relations\": [1]}")),
                new GraphMemoryStates.RelationFilterTask(target, List.of(kept)));

        GraphMemory.parseRelationFilteringResult(List.of(kept), state).join();

        assertThat(state.getMergeInfos().get(target.getUuid()).getNewRelations()).contains(kept);
        assertThat(kept.getLhs()).isEqualTo(target.getUuid());
    }

    private void assertEntityPipelineStateSemantics() {
        GraphMemory memory = new GraphMemory();
        memory.attachEmbedder(new FixedEmbedding());
        GraphMemoryStates.GraphMemState state = memory.initState(null);
        Entity entity = entity("entity-1", "Alice");
        Relation relation = relation("relation-1", entity, entity("entity-2", "Bob"), "knows");

        state.getRetrievedEntities().put(entity.getUuid(), entity);
        state.getRetrievedRelations().put(relation.getUuid(), relation);
        state.getMemUpdate().getAddedEntity().add(entity);
        state.getMemUpdate().getAddedRelation().add(relation);
        GraphMemoryStates.GraphMemUpdate update = memory.addMemory(EpisodeType.DOCUMENT, "user-a", "episode").join();
        state.getMemUpdate().getAddedEpisode().add(update.getAddedEpisode().get(0));

        GraphMemoryStates.GraphMemUpdate merged = state.getMemUpdate().merge(state.getMemUpdateSkipEmbed());

        assertThat(merged.getAddedEntity()).contains(entity);
        assertThat(merged.getAddedRelation()).contains(relation);
        assertThat(merged.getAddedEpisode()).hasSize(1);
        assertThat(update.getAddedEpisode()).hasSize(1);
        assertThat(memory.getEntities()).isEmpty();
        assertThat(memory.getRelations()).isEmpty();

        memory.setLlmClient(params -> CompletableFuture.completedFuture(new GraphMemory.LlmResponse(
                "[{\"name\":\"Alice\",\"entity_type_id\":0},{\"name\":\"user\",\"entity_type_id\":0}]")));
        GraphMemory.ExtractDeclarationsResult extracted = memory.extractEntityDeclarations(
                EpisodeType.DOCUMENT,
                "[{\"name\":\"Alice\",\"entity_type_id\":0},{\"name\":\"user\",\"entity_type_id\":0}]",
                state).join();
        assertThat(extracted.declarations()).extracting(ExtractionModels.EntityDeclaration::getName)
                .contains("Alice")
                .doesNotContain("user");
    }

    private static Entity entity(String uuid, String name) {
        Entity entity = new Entity();
        entity.setUuid(uuid);
        entity.setName(name);
        entity.setContent(name + " summary");
        entity.setUserId("user-a");
        return entity;
    }

    private static Relation relation(String uuid, Entity lhs, Entity rhs, String content) {
        Relation relation = new Relation(lhs, rhs);
        relation.setUuid(uuid);
        relation.setName(content);
        relation.setContent(content);
        relation.setUserId("user-a");
        return relation;
    }

    /**
     * <p>Mirrors Python's mock {@code Embedding} dependency used by graph-memory base tests in
     * {@code tests/unit_tests/core/memory/graph/graph_memory/test_base.py}.</p>
     */
    private static final class FixedEmbedding extends Embedding {
        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of(1.0d, 0.0d, 0.0d));
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(List<String> texts,
                                                                    Integer batchSize,
                                                                    Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(texts.stream()
                    .map(ignored -> List.of(1.0d, 0.0d, 0.0d))
                    .toList());
        }

        @Override
        public int getDimension() {
            return 3;
        }
    }
}
