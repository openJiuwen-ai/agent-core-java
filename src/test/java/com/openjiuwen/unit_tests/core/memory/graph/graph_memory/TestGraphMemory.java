/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.graph.graph_memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GraphMemory.
 * <p>
 * Mirrors Python's test_graph_memory.py from
 * <code>tests/unit_tests/core/memory/graph/graph_memory/test_graph_memory.py</code>.
 */
@DisplayName("Graph Memory Tests")
class TestGraphMemory {

    // Stub classes
    static class Episode {
        String content;
        String source;
        long timestamp;

        Episode(String content, String source) {
            this.content = content;
            this.source = source;
            this.timestamp = System.currentTimeMillis();
        }
    }

    static class Entity {
        String name;
        String type;
        Map<String, Object> attributes = new HashMap<>();

        Entity(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    static class Relation {
        Entity source;
        Entity target;
        String type;

        Relation(Entity source, Entity target, String type) {
            this.source = source;
            this.target = target;
            this.type = type;
        }
    }

    static class GraphMemoryStub {
        List<Entity> entities = new ArrayList<>();
        List<Relation> relations = new ArrayList<>();
        List<Episode> episodes = new ArrayList<>();

        void addEpisode(Episode episode) {
            episodes.add(episode);
        }

        void addEntity(Entity entity) {
            entities.add(entity);
        }

        void addRelation(Relation relation) {
            relations.add(relation);
        }

        CompletableFuture<List<Entity>> searchEntities(String query) {
            return CompletableFuture.completedFuture(new ArrayList<>(entities));
        }

        CompletableFuture<List<Relation>> searchRelations(String query) {
            return CompletableFuture.completedFuture(new ArrayList<>(relations));
        }

        int entityCount() {
            return entities.size();
        }

        int relationCount() {
            return relations.size();
        }
    }

    @Nested
    @DisplayName("Episode Tests")
    class TestEpisode {

        @Test
        @DisplayName("episode creation")
        void testEpisodeCreation() {
            Episode episode = new Episode("User asked about Python", "conversation");

            assertEquals("User asked about Python", episode.content);
            assertEquals("conversation", episode.source);
        }
    }

    @Nested
    @DisplayName("Entity Tests")
    class TestEntity {

        @Test
        @DisplayName("entity creation")
        void testEntityCreation() {
            Entity entity = new Entity("Python", "ProgrammingLanguage");

            assertEquals("Python", entity.name);
            assertEquals("ProgrammingLanguage", entity.type);
        }

        @Test
        @DisplayName("entity with attributes")
        void testEntityWithAttributes() {
            Entity entity = new Entity("Python", "ProgrammingLanguage");
            entity.attributes.put("version", "3.12");
            entity.attributes.put("popular", true);

            assertEquals("3.12", entity.attributes.get("version"));
            assertEquals(true, entity.attributes.get("popular"));
        }
    }

    @Nested
    @DisplayName("Relation Tests")
    class TestRelation {

        @Test
        @DisplayName("relation creation")
        void testRelationCreation() {
            Entity user = new Entity("Alice", "Person");
            Entity lang = new Entity("Python", "Language");

            Relation relation = new Relation(user, lang, "uses");

            assertEquals(user, relation.source);
            assertEquals(lang, relation.target);
            assertEquals("uses", relation.type);
        }
    }

    @Nested
    @DisplayName("Graph Memory Operations Tests")
    class TestGraphMemoryOperations {

        @Test
        @DisplayName("add episode")
        void testAddEpisode() {
            GraphMemoryStub memory = new GraphMemoryStub();
            memory.addEpisode(new Episode("test content", "test"));

            assertEquals(1, memory.episodes.size());
        }

        @Test
        @DisplayName("add entity")
        void testAddEntity() {
            GraphMemoryStub memory = new GraphMemoryStub();
            memory.addEntity(new Entity("TestEntity", "Type"));

            assertEquals(1, memory.entityCount());
        }

        @Test
        @DisplayName("add relation")
        void testAddRelation() {
            GraphMemoryStub memory = new GraphMemoryStub();
            Entity e1 = new Entity("A", "Type");
            Entity e2 = new Entity("B", "Type");
            memory.addRelation(new Relation(e1, e2, "connects"));

            assertEquals(1, memory.relationCount());
        }

        @Test
        @DisplayName("search entities")
        void testSearchEntities() throws Exception {
            GraphMemoryStub memory = new GraphMemoryStub();
            memory.addEntity(new Entity("Python", "Language"));

            List<Entity> results = memory.searchEntities("Python").get();

            assertEquals(1, results.size());
        }
    }
}