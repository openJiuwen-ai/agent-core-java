/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's graph object models in
 * {@code openjiuwen/core/foundation/store/graph/graph_object.py}.
 *
 * <p>Mirrors Python's graph object unit tests in
 * {@code tests/unit_tests/core/foundation/store/graph/test_graph_object.py}.</p>
 */
class GraphObjectTest {

    @Test
    void baseGraphObjectDefaultsAndValidationMirrorPydanticModel() {
        BaseGraphObject object = new BaseGraphObject();
        object.setContent("hello");
        object.setMetadata(null);

        assertThat(object.getUuid()).hasSize(32);
        assertThat(object.getCreatedAt()).isPositive();
        assertThat(object.getUserId()).isEqualTo("default_user");
        assertThat(object.getObjType()).isEmpty();
        assertThat(object.getLanguage()).isEqualTo("cn");
        assertThat(object.getMetadata()).isEmpty();
        assertThat(object.getVersion()).isEqualTo(1);
        assertThat(object.fetchEmbedTask())
                .extracting(BaseGraphObject.EmbeddingTask::attributeName, BaseGraphObject.EmbeddingTask::contentToEmbed)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("content_embedding", "hello"));

        object.setLanguage("en");
        assertThat(object.getLanguage()).isEqualTo("en");
        assertThatThrownBy(() -> object.setLanguage("jp")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void baseGraphObjectDefaultScalarFieldsMirrorPythonDefaults() {
        BaseGraphObject object = new BaseGraphObject();

        assertThat(object.getUserId()).isEqualTo("default_user");
        assertThat(object.getObjType()).isEmpty();
        assertThat(object.getLanguage()).isEqualTo("cn");
        assertThat(object.getMetadata()).isEmpty();
        assertThat(object.getContent()).isEmpty();
    }

    @Test
    void baseGraphObjectUuidAndCreatedAtUseDefaultFactories() {
        BaseGraphObject object = new BaseGraphObject();

        assertThat(object.getUuid()).hasSize(32);
        assertThat(object.getCreatedAt()).isPositive();
    }

    @Test
    void baseGraphObjectLanguageAcceptsOnlyCnAndEn() {
        BaseGraphObject object = new BaseGraphObject();

        object.setLanguage("cn");
        assertThat(object.getLanguage()).isEqualTo("cn");
        object.setLanguage("en");
        assertThat(object.getLanguage()).isEqualTo("en");
        assertThatThrownBy(() -> object.setLanguage("fr")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void baseGraphObjectVersionPropertyReturnsOne() {
        assertThat(new BaseGraphObject().getVersion()).isEqualTo(1);
    }

    @Test
    void baseGraphObjectFetchEmbedTaskReturnsSelfContentTuple() {
        BaseGraphObject object = new BaseGraphObject();
        object.setContent("hello");

        List<BaseGraphObject.EmbeddingTask> tasks = object.fetchEmbedTask();

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).graphObject()).isSameAs(object);
        assertThat(tasks.get(0).attributeName()).isEqualTo("content_embedding");
        assertThat(tasks.get(0).contentToEmbed()).isEqualTo("hello");
    }

    @Test
    void baseGraphObjectMetadataNullBecomesEmptyMap() {
        BaseGraphObject object = new BaseGraphObject();

        object.setMetadata(null);

        assertThat(object.getMetadata()).isEmpty();
    }

    @Test
    void baseGraphObjectMetadataGetterReturnsCopy() {
        BaseGraphObject object = new BaseGraphObject();
        object.setMetadata(Map.of("k", "v"));

        Map<String, Object> metadata = object.getMetadata();
        metadata.put("other", "value");

        assertThat(object.getMetadata()).containsExactlyEntriesOf(Map.of("k", "v"));
    }

    @Test
    void namedGraphObjectNameDefaultsToEmptyAndCanBeSet() {
        NamedGraphObject object = new NamedGraphObject();

        assertThat(object.getName()).isEmpty();
        object.setName("foo");
        assertThat(object.getName()).isEqualTo("foo");
    }

    @Test
    void entitySerializesRelationsEpisodesAndEmbedTasks() {
        Entity entity = new Entity();
        entity.setUuid("entity-b");
        entity.setContent("content");
        entity.setName("name");
        entity.setAttributes(null);
        Relation relation = new Relation();
        relation.setUuid("relation-a");
        entity.setRelations(List.of("relation-c", relation, "relation-c"));
        entity.setEpisodes(List.of("ep-2", "ep-1", "ep-1"));

        assertThat(entity.getObjType()).isEqualTo("Entity");
        assertThat(entity.getAttributes()).isEmpty();
        assertThat(entity.serializeRelations()).containsExactly("relation-a", "relation-c");
        assertThat(entity.serializeEpisodes()).containsExactly("ep-1", "ep-2");
        assertThat(entity.fetchEmbedTask())
                .extracting(BaseGraphObject.EmbeddingTask::attributeName, BaseGraphObject.EmbeddingTask::contentToEmbed)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("content_embedding", "content"),
                        org.assertj.core.groups.Tuple.tuple("name_embedding", "name")
                );
    }

    @Test
    void entityDefaultsMirrorPythonModel() {
        Entity entity = new Entity();

        assertThat(entity.getObjType()).isEqualTo("Entity");
        assertThat(entity.getRelations()).isEmpty();
        assertThat(entity.getEpisodes()).isEmpty();
        assertThat(entity.getAttributes()).isEmpty();
    }

    @Test
    void entityFetchEmbedTaskReturnsContentThenNameTasks() {
        Entity entity = new Entity();
        entity.setContent("content");
        entity.setName("name");

        assertThat(entity.fetchEmbedTask())
                .extracting(BaseGraphObject.EmbeddingTask::attributeName, BaseGraphObject.EmbeddingTask::contentToEmbed)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("content_embedding", "content"),
                        org.assertj.core.groups.Tuple.tuple("name_embedding", "name")
                );
    }

    @Test
    void entitySerializesRelationsAndEpisodesAsSortedUniqueUuidStrings() {
        Entity other = new Entity();
        other.setUuid("aaa");
        Entity entity = new Entity();

        entity.setRelations(List.of(other, "bbb", "bbb"));
        entity.setEpisodes(List.of("ep2", "ep1", "ep1"));

        assertThat(entity.serializeRelations()).containsExactly("aaa", "bbb");
        assertThat(entity.serializeEpisodes()).containsExactly("ep1", "ep2");
    }

    @Test
    void entityAttributesNullBecomesEmptyMap() {
        Entity entity = new Entity();

        entity.setAttributes(null);

        assertThat(entity.getAttributes()).isEmpty();
    }

    @Test
    void relationDefaultsAndUpdatesConnectedEntities() {
        Entity lhs = new Entity();
        lhs.setUuid("lhs-id");
        Entity rhs = new Entity();
        rhs.setUuid("rhs-id");
        Relation relation = new Relation(lhs, rhs);
        relation.setUuid("relation-id");
        long createdAt = relation.getCreatedAt();

        relation.updateConnectedEntities().updateConnectedEntities();

        assertThat(relation.getObjType()).isEqualTo("Relation");
        assertThat(relation.getValidSince()).isEqualTo(createdAt);
        assertThat(relation.getValidUntil()).isEqualTo(-1);
        assertThat(relation.getOffsetSince()).isZero();
        assertThat(relation.getOffsetUntil()).isZero();
        assertThat(relation.serializeLhs()).isEqualTo("lhs-id");
        assertThat(relation.serializeRhs()).isEqualTo("rhs-id");
        assertThat(lhs.getRelations()).containsExactly(relation);
        assertThat(rhs.getRelations()).containsExactly(relation);

        Relation stringRelation = new Relation("lhs-string", "rhs-string");
        assertThat(stringRelation.serializeLhs()).isEqualTo("lhs-string");
        assertThat(stringRelation.serializeRhs()).isEqualTo("rhs-string");
    }

    @Test
    void relationConstructorStoresRequiredEndpoints() {
        Relation relation = new Relation("id1", "id2");

        assertThat(relation.getLhs()).isEqualTo("id1");
        assertThat(relation.getRhs()).isEqualTo("id2");
    }

    @Test
    void relationDefaultsMirrorPythonModel() {
        Relation relation = new Relation("l", "r");

        assertThat(relation.getObjType()).isEqualTo("Relation");
        assertThat(relation.getValidUntil()).isEqualTo(-1);
        assertThat(relation.getValidSince()).isEqualTo(relation.getCreatedAt());
        assertThat(relation.getOffsetSince()).isZero();
        assertThat(relation.getOffsetUntil()).isZero();
    }

    @Test
    void relationValidSinceMinusOneResetsToCreatedAt() {
        Relation relation = new Relation("l", "r");

        relation.setValidSince(-1);

        assertThat(relation.getValidSince()).isEqualTo(relation.getCreatedAt());
    }

    @Test
    void relationUpdateConnectedEntitiesIsIdempotent() {
        Entity lhs = new Entity();
        Relation relation = new Relation(lhs, "rhs");

        relation.updateConnectedEntities();
        relation.updateConnectedEntities();

        assertThat(lhs.getRelations()).containsExactly(relation);
    }

    @Test
    void relationSerializesEntityEndpointToUuid() {
        Entity lhs = new Entity();
        lhs.setUuid("entity-uuid-1");
        Relation relation = new Relation(lhs, "str-id");

        assertThat(relation.serializeLhs()).isEqualTo("entity-uuid-1");
        assertThat(relation.serializeRhs()).isEqualTo("str-id");
    }

    @Test
    void episodeDefaultsAndEntitySerializationMirrorPythonSerializer() {
        Episode episode = new Episode();
        episode.setUuid("episode-id");
        Entity entity = new Entity();
        entity.setUuid("entity-a");
        episode.setEntities(List.of("entity-b", entity, "entity-b"));

        assertThat(episode.getObjType()).isEqualTo("Episode");
        assertThat(episode.getValidSince()).isEqualTo(episode.getCreatedAt());
        assertThat(episode.serializeEntities()).containsExactly("entity-a", "entity-b");
    }

    @Test
    void episodeDefaultsMirrorPythonModel() {
        Episode episode = new Episode();

        assertThat(episode.getObjType()).isEqualTo("Episode");
        assertThat(episode.getEntities()).isEmpty();
        assertThat(episode.getValidSince()).isEqualTo(episode.getCreatedAt());
    }

    @Test
    void episodeSerializesEntitiesAsSortedUniqueUuidStrings() {
        Entity entity = new Entity();
        entity.setUuid("a-id");
        Episode episode = new Episode();

        episode.setEntities(List.of("z-id", entity, "z-id"));

        assertThat(episode.serializeEntities()).containsExactly("a-id", "z-id");
    }
}
