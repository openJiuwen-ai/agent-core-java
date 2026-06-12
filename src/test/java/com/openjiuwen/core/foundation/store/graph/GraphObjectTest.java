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
}
