/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.foundation.store.graph.Episode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
    @DisplayName("GraphMemory Construction Tests")
    class TestGraphMemoryConstruction {

        @Test
        @DisplayName("graph memory can be created")
        void testGraphMemoryCanBeCreated() {
            GraphMemory graphMemory = new GraphMemory();
            assertNotNull(graphMemory);
        }

        @Test
        @DisplayName("graph memory has state")
        void testGraphMemoryHasState() {
            GraphMemory graphMemory = new GraphMemory();
            assertNotNull(graphMemory.getState());
        }
    }

    @Nested
    @DisplayName("GraphMemory Operations Tests")
    class TestGraphMemoryOperations {

        @Test
        @DisplayName("add returns completable future")
        void testAddReturnsCompletableFuture() {
            GraphMemory graphMemory = new GraphMemory();
            CompletableFuture<GraphMemoryStates.GraphMemUpdate> result = 
                graphMemory.add("test content", "message");

            assertNotNull(result);
        }

        @Test
        @DisplayName("search returns completable future")
        void testSearchReturnsCompletableFuture() {
            GraphMemory graphMemory = new GraphMemory();
            CompletableFuture<Map<String, Object>> result = 
                graphMemory.search("test query", 10);

            assertNotNull(result);
        }

        @Test
        @DisplayName("get entities returns list")
        void testGetEntitiesReturnsList() {
            GraphMemory graphMemory = new GraphMemory();
            List<Entity> entities = graphMemory.getEntities();

            assertNotNull(entities);
        }

        @Test
        @DisplayName("get relations returns list")
        void testGetRelationsReturnsList() {
            GraphMemory graphMemory = new GraphMemory();
            List<Relation> relations = graphMemory.getRelations();

            assertNotNull(relations);
        }

        @Test
        @DisplayName("get episodes returns list")
        void testGetEpisodesReturnsList() {
            GraphMemory graphMemory = new GraphMemory();
            List<Episode> episodes = graphMemory.getEpisodes();

            assertNotNull(episodes);
        }
    }

    @Nested
    @DisplayName("GraphMemState Tests")
    class TestGraphMemState {

        @Test
        @DisplayName("state can be created")
        void testStateCanBeCreated() {
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            assertNotNull(state);
        }

        @Test
        @DisplayName("state has lookup tables")
        void testStateHasLookupTables() {
            GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
            assertNotNull(state.getLookupTables());
        }
    }

    @Nested
    @DisplayName("GraphMemUpdate Tests")
    class TestGraphMemUpdate {

        @Test
        @DisplayName("update can be created")
        void testUpdateCanBeCreated() {
            GraphMemoryStates.GraphMemUpdate update = new GraphMemoryStates.GraphMemUpdate();
            assertNotNull(update);
        }
    }

    @Nested
    @DisplayName("EntityMerge Tests")
    class TestEntityMerge {

        @Test
        @DisplayName("entity merge can be created")
        void testEntityMergeCanBeCreated() {
            Entity entity = new Entity();
            GraphMemoryStates.EntityMerge merge = new GraphMemoryStates.EntityMerge(entity);
            assertNotNull(merge);
        }
    }
}