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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GraphMemoryStates.
 * <p>
 * Mirrors Python's test_states.py from
 * <code>tests/unit_tests/core/memory/graph/graph_memory/test_states.py</code>.
 */
@DisplayName("Graph Memory States Tests")
class TestGraphMemoryStates {

    @Nested
    @DisplayName("LookupTables Tests")
    class TestLookupTables {

        @Test
        @DisplayName("get entity creates and caches")
        void testGetEntityCreatesAndCaches() {
            GraphMemoryStates.LookupTables tbl = new GraphMemoryStates.LookupTables();
            Map<String, Object> inputObj = new HashMap<>();
            inputObj.put("uuid", "e1");
            inputObj.put("name", "Ent1");
            inputObj.put("content", "");

            Entity ent = tbl.getEntity(inputObj);

            assertNotNull(ent);
            assertEquals("Ent1", ent.getName());
            // Second call should return same cached entity
            Entity ent2 = tbl.getEntity(inputObj);
            assertEquals(ent, ent2);
        }

        @Test
        @DisplayName("get relation creates and caches")
        void testGetRelationCreatesAndCaches() {
            GraphMemoryStates.LookupTables tbl = new GraphMemoryStates.LookupTables();
            Map<String, Object> inputObj = new HashMap<>();
            inputObj.put("uuid", "r1");
            inputObj.put("name", "R");

            Relation rel = tbl.getRelation(inputObj);

            assertNotNull(rel);
            assertEquals("R", rel.getName());
            // Second call should return same cached relation
            Relation rel2 = tbl.getRelation(inputObj);
            assertEquals(rel, rel2);
        }

        @Test
        @DisplayName("get episode creates and caches")
        void testGetEpisodeCreatesAndCaches() {
            GraphMemoryStates.LookupTables tbl = new GraphMemoryStates.LookupTables();
            Map<String, Object> inputObj = new HashMap<>();
            inputObj.put("uuid", "ep1");
            inputObj.put("content", "episode content");

            Episode ep = tbl.getEpisode(inputObj);

            assertNotNull(ep);
            // Second call should return same cached episode
            Episode ep2 = tbl.getEpisode(inputObj);
            assertEquals(ep, ep2);
        }

        @Test
        @DisplayName("clear empties all tables")
        void testClearEmptiesAllTables() {
            GraphMemoryStates.LookupTables tbl = new GraphMemoryStates.LookupTables();

            // Add some entries
            Map<String, Object> entityInput = new HashMap<>();
            entityInput.put("uuid", "e1");
            entityInput.put("name", "Entity1");
            tbl.getEntity(entityInput);

            Map<String, Object> relationInput = new HashMap<>();
            relationInput.put("uuid", "r1");
            relationInput.put("name", "Relation1");
            tbl.getRelation(relationInput);

            Map<String, Object> episodeInput = new HashMap<>();
            episodeInput.put("uuid", "ep1");
            tbl.getEpisode(episodeInput);

            // Clear
            tbl.clear();

            // Verify all tables are empty
            assertTrue(tbl.getEntities().isEmpty());
            assertTrue(tbl.getRelations().isEmpty());
            assertTrue(tbl.getEpisodes().isEmpty());
        }

        @Test
        @DisplayName("get entities returns map")
        void testGetEntitiesReturnsMap() {
            GraphMemoryStates.LookupTables tbl = new GraphMemoryStates.LookupTables();

            Map<String, Entity> entities = tbl.getEntities();
            assertNotNull(entities);
        }

        @Test
        @DisplayName("get relations returns map")
        void testGetRelationsReturnsMap() {
            GraphMemoryStates.LookupTables tbl = new GraphMemoryStates.LookupTables();

            Map<String, Relation> relations = tbl.getRelations();
            assertNotNull(relations);
        }

        @Test
        @DisplayName("get episodes returns map")
        void testGetEpisodesReturnsMap() {
            GraphMemoryStates.LookupTables tbl = new GraphMemoryStates.LookupTables();

            Map<String, Episode> episodes = tbl.getEpisodes();
            assertNotNull(episodes);
        }
    }

    @Nested
    @DisplayName("Entity Tests")
    class TestEntity {

        @Test
        @DisplayName("entity creation")
        void testEntityCreation() {
            Entity entity = new Entity();
            entity.setUuid("uuid-123");
            entity.setName("TestEntity");
            entity.setContent("Test content");

            assertEquals("uuid-123", entity.getUuid());
            assertEquals("TestEntity", entity.getName());
            assertEquals("Test content", entity.getContent());
        }

        @Test
        @DisplayName("entity is graph object")
        void testEntityIsGraphObject() {
            Entity entity = new Entity();
            assertTrue(entity instanceof com.openjiuwen.core.foundation.store.graph.BaseGraphObject);
        }
    }

    @Nested
    @DisplayName("Relation Tests")
    class TestRelation {

        @Test
        @DisplayName("relation creation")
        void testRelationCreation() {
            Relation relation = new Relation();
            relation.setUuid("uuid-rel-1");
            relation.setName("TestRelation");

            assertEquals("uuid-rel-1", relation.getUuid());
            assertEquals("TestRelation", relation.getName());
        }

        @Test
        @DisplayName("relation is graph object")
        void testRelationIsGraphObject() {
            Relation relation = new Relation();
            assertTrue(relation instanceof com.openjiuwen.core.foundation.store.graph.BaseGraphObject);
        }
    }

    @Nested
    @DisplayName("Episode Tests")
    class TestEpisode {

        @Test
        @DisplayName("episode creation")
        void testEpisodeCreation() {
            Episode episode = new Episode();
            episode.setUuid("uuid-ep-1");
            episode.setContent("Episode content");

            assertEquals("uuid-ep-1", episode.getUuid());
            assertEquals("Episode content", episode.getContent());
        }

        @Test
        @DisplayName("episode is graph object")
        void testEpisodeIsGraphObject() {
            Episode episode = new Episode();
            assertTrue(episode instanceof com.openjiuwen.core.foundation.store.graph.BaseGraphObject);
        }
    }
}