package com.openjiuwen.unit_tests.core.foundation.store.graph;

import com.openjiuwen.core.foundation.store.graph.BaseGraphObject;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.NamedGraphObject;
import com.openjiuwen.core.foundation.store.graph.Relation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestGraphObject {

    @Test
    void testBaseDefaultsUserId() {
        assertEquals("default_user", new BaseGraphObject().getUserId());
    }

    @Test
    void testBaseDefaultsObjType() {
        assertEquals("", new BaseGraphObject().getObjType());
    }

    @Test
    void testBaseDefaultsLanguage() {
        assertEquals("cn", new BaseGraphObject().getLanguage());
    }

    @Test
    void testBaseDefaultsMetadata() {
        assertEquals(Map.of(), new BaseGraphObject().getMetadata());
    }

    @Test
    void testBaseDefaultsContent() {
        assertEquals("", new BaseGraphObject().getContent());
    }

    @Test
    void testUuidAndCreatedAtDefaultFactory() {
        BaseGraphObject object = new BaseGraphObject();
        assertEquals(32, object.getUuid().length());
        assertTrue(object.getCreatedAt() > 0);
    }

    @Test
    void testVersionPropertyReturnsOne() {
        assertEquals(1, new BaseGraphObject().getVersion());
    }

    @Test
    void testFetchEmbedTaskReturnsContentEmbedding() {
        BaseGraphObject object = new BaseGraphObject();
        object.setContent("hello");
        List<BaseGraphObject.EmbedTask> tasks = object.fetchEmbedTask();
        assertEquals(1, tasks.size());
        assertEquals("content_embedding", tasks.get(0).getAttributeName());
        assertEquals("hello", tasks.get(0).getContentToEmbed());
    }

    @Test
    void testMetadataNoneBecomesEmptyMap() {
        BaseGraphObject object = new BaseGraphObject();
        object.setMetadata(null);
        assertNotNull(object.getMetadata());
        assertTrue(object.getMetadata().isEmpty());
    }

    @Test
    void testNamedGraphObjectDefaultNameEmpty() {
        assertEquals("", new NamedGraphObject().getName());
    }

    @Test
    void testNamedGraphObjectExplicitName() {
        assertEquals("foo", new NamedGraphObject("foo").getName());
    }

    @Test
    void testEntityDefaults() {
        Entity entity = new Entity();
        assertEquals("Entity", entity.getObjType());
        assertTrue(entity.getRelations().isEmpty());
        assertTrue(entity.getEpisodes().isEmpty());
        assertEquals(Map.of(), entity.getAttributes());
    }

    @Test
    void testEntityFetchEmbedTaskContentAndNameEmbedding() {
        Entity entity = new Entity("n", "Entity", "c");
        List<BaseGraphObject.EmbedTask> tasks = entity.fetchEmbedTask();
        assertEquals(2, tasks.size());
        assertEquals("content_embedding", tasks.get(0).getAttributeName());
        assertEquals("name_embedding", tasks.get(1).getAttributeName());
    }

    @Test
    void testEntitySetRelationsAndEpisodes() {
        Entity entity = new Entity();
        entity.setRelations(List.of("bbb", "aaa"));
        entity.setEpisodes(List.of("ep1", "ep2"));
        assertEquals(List.of("bbb", "aaa"), entity.getRelations());
        assertEquals(List.of("ep1", "ep2"), entity.getEpisodes());
    }

    @Test
    void testRelationDefaults() {
        Relation relation = new Relation();
        assertEquals("Relation", relation.getObjType());
        assertEquals(-1, relation.getValidUntil());
        assertEquals(relation.getCreatedAt(), relation.getValidSince());
    }

    @Test
    void testRelationUpdateConnectedEntitiesAddsSelfToRelations() {
        Entity lhs = new Entity();
        Entity rhs = new Entity();
        Relation relation = new Relation();
        relation.updateConnectedEntities(lhs, rhs);
        assertTrue(lhs.getRelations().contains(relation.getUuid()));
        assertTrue(rhs.getRelations().contains(relation.getUuid()));
    }

    @Test
    void testRelationUpdateConnectedEntitiesIsIdempotent() {
        Entity lhs = new Entity();
        Relation relation = new Relation();
        relation.updateConnectedEntities(lhs, null);
        relation.updateConnectedEntities(lhs, null);
        assertEquals(1, lhs.getRelations().size());
    }

    @Test
    void testEpisodeDefaults() {
        Episode episode = new Episode();
        assertEquals("Episode", episode.getObjType());
        assertTrue(episode.getEntities().isEmpty());
    }

    @Test
    void testEpisodeEntitiesRoundTrip() {
        Episode episode = new Episode();
        episode.setEntities(List.of("z-id", "a-id"));
        assertFalse(episode.getEntities().isEmpty());
        assertEquals(List.of("z-id", "a-id"), episode.getEntities());
    }
}
