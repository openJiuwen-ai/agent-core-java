package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.foundation.store.graph.InMemoryGraphStore;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.config.graph.AddMemStrategy;
import com.openjiuwen.core.memory.config.graph.EpisodeType;
import com.openjiuwen.core.memory.graph.extraction.EntityDeclaration;
import com.openjiuwen.core.memory.graph.extraction.EntityDef;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class GraphParseStateTest {

    @TempDir
    Path tempDir;

    @Test
    void parseIsoAndDeclareEntitiesShouldMatchPythonShape() {
        int[] parsed = ParseLlmResponse.parseIso("2025-09-10T15:56:53+08:00");
        EntityDeclaration declaration = new EntityDeclaration();
        declaration.setName("Alice");
        declaration.setEntityTypeId(0);
        EntityDef entityDef = new EntityDef();
        entityDef.setName("Human");

        List<Entity> entities = ParseLlmResponse.declareEntities(List.of(declaration), List.of(entityDef), Map.of("user_id", "u1"));

        assertThat(parsed[0]).isPositive();
        assertThat(parsed[1]).isEqualTo(32);
        assertThat(entities).hasSize(1);
        assertThat(entities.get(0).getName()).isEqualTo("Alice");
        assertThat(entities.get(0).getObjType()).isEqualTo("Human");
    }

    @Test
    void dict2relationAndParseRelationMergingShouldWork() {
        Entity lhs = new Entity();
        lhs.setUuid("e1");
        lhs.setName("lhs");
        Entity rhs = new Entity();
        rhs.setUuid("e2");
        rhs.setName("rhs");

        Relation relation = ParseLlmResponse.dict2relation(Map.of(
                "name", "works_with",
                "fact", "lhs works with rhs",
                "source_id", 1,
                "target_id", 2,
                "valid_since", "2025-01-01T00:00:00+08:00"
        ), List.of(lhs, rhs), Map.of("user_id", "u1"));

        assertThat(relation).isNotNull();
        assertThat(relation.getName()).isEqualTo("works_with");
        assertThat(relation.getLhs()).isSameAs(lhs);

        var removed = ParseLlmResponse.parseRelationMerging(Map.of(
                "need_merging", true,
                "combined_content", "merged fact",
                "duplicate_ids", List.of(1)
        ), relation, List.of(Map.of("uuid", "r1")));

        assertThat(relation.getContent()).isEqualTo("merged fact");
        assertThat(removed).contains("r1");
    }

    @Test
    void lookupTablesAndUpdateMergeShouldWork() {
        States.LookupTables lookupTables = new States.LookupTables();
        Entity entity = lookupTables.getEntity(Map.of("uuid", "u1", "name", "Alice"));
        Relation relation = lookupTables.getRelation(Map.of("uuid", "r1", "name", "rel"));
        Episode episode = lookupTables.getEpisode(Map.of("uuid", "ep1", "content", "episode"));

        States.GraphMemUpdate left = new States.GraphMemUpdate();
        left.getRemovedEntity().add("u1");
        States.GraphMemUpdate right = new States.GraphMemUpdate();
        right.getRemovedRelation().add("r1");
        States.GraphMemUpdate merged = left.or(right);

        assertThat(entity.getUuid()).isEqualTo("u1");
        assertThat(relation.getUuid()).isEqualTo("r1");
        assertThat(episode.getUuid()).isEqualTo("ep1");
        assertThat(merged.getRemovedEntity()).contains("u1");
        assertThat(merged.getRemovedRelation()).contains("r1");
    }

    @Test
    void postprocessHelpersShouldWork() throws Exception {
        GraphStore store = InMemoryGraphStore.fromConfig(GraphConfig.builder().uri(tempDir.resolve("graph.db").toString()).backend("in_memory").build());
        States.GraphMemState state = new States.GraphMemState();
        state.getPrompting().setLanguage("cn");
        state.setEpisodeType(EpisodeType.CONVERSATION);
        state.setReferenceTimestamp(123);

        Episode episode = PostprocessGraphObjects.createEpisode(store, "u1", "episode content", state);
        Entity entity = new Entity();
        entity.setUuid("e1");
        entity.setName("Alice");
        Relation relation = new Relation();
        relation.setUuid("r1");
        relation.setName("works_with");
        relation.setContent("Alice works with Bob");
        relation.setLhs(entity);
        relation.setRhs(entity);

        PostprocessGraphObjects.processEntities(store, List.of(entity), episode, state);
        PostprocessGraphObjects.validateEntitiesEpisodes(List.of(entity), episode, state);

        Entity other = new Entity();
        other.setUuid("e2");
        relation.setRhs(other);
        PostprocessGraphObjects.processRelations(store, List.of(entity, other), List.of(relation), state);

        var task = new PostprocessGraphObjects.RelationTask(
                relation,
                List.of(Map.of("uuid", "old-rel")),
                CompletableFuture.completedFuture(Map.of("content", "{\"need_merging\":true,\"combined_content\":\"merged\",\"duplicate_ids\":[1]}"))
        );
        PostprocessGraphObjects.parseRelationUuidsToRemove(List.of(task), state);

        assertThat(state.getMemUpdate().getAddedEpisode()).hasSize(1);
        assertThat(state.getMemUpdate().getAddedEntity()).isNotEmpty();
        assertThat(state.getMemUpdate().getAddedRelation()).hasSize(1);
        assertThat(state.getToRemove()).contains("old-rel");
    }
}
