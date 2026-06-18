/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.memory.config.EpisodeType;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * <p>Mirrors Python's {@code GraphMemory} behavior in
 * {@code openjiuwen/core/memory/graph/graph_memory/base.py}.</p>
 */
class GraphMemoryTest {

    @Test
    void initStateMirrorsConfiguredPromptLanguageAndReferenceTime() {
        GraphMemory memory = new GraphMemory();

        GraphMemoryStates.GraphMemState state = memory.initState(Instant.ofEpochSecond(1234L));

        assertThat(state.getReferenceTimestamp()).isEqualTo(1234L);
        assertThat(state.getPrompting().getLanguage()).isEqualTo("cn");
        assertThat(state.getEntityTypes()).extracting("name").containsExactly("Entity", "Human", "AI");
    }

    @Test
    void prepareEpisodesFormatsConversationMessagesAndRejectsStringReplacements() {
        GraphMemory memory = new GraphMemory();
        GraphMemoryStates.GraphMemState state = memory.initState(null);

        String content = memory.prepareEpisodes(
                EpisodeType.CONVERSATION,
                "user-1",
                List.of(Map.of("role", "user", "content", "hello")),
                state,
                Map.of("user", "Visitor")).join();

        assertThat(content).isEqualTo("Visitor: hello");
        assertThatThrownBy(() -> memory.prepareEpisodes(
                EpisodeType.DOCUMENT,
                "user-1",
                "plain",
                state,
                Map.of("user", "Visitor")).join())
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void addMemoryPersistsEpisodeAndSearchesIt() {
        GraphMemory memory = new GraphMemory();
        memory.attachEmbedder(new FixedEmbedding());

        GraphMemoryStates.GraphMemUpdate update =
                memory.addMemory(EpisodeType.DOCUMENT, "user-1", "graph memory document").join();
        Map<String, List<GraphMemory.SearchHit>> result =
                memory.search("graph", "user-1", "default", false, false, true, null).join();

        assertThat(update.getAddedEpisode()).hasSize(1);
        assertThat(update.getAddedEpisode().get(0).getContent()).isEqualTo("graph memory document");
        assertThat(result).containsKey(GraphMemory.EPISODE_COLLECTION);
        assertThat(result.get(GraphMemory.EPISODE_COLLECTION)).hasSize(1);
    }

    /**
     * <p>Mirrors Python's attachable embedder dependency used by {@code GraphMemory} in
     * {@code openjiuwen/core/memory/graph/graph_memory/base.py}.</p>
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
