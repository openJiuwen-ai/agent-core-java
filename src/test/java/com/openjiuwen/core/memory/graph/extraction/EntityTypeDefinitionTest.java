/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's entity type definition tests in
 * {@code tests/unit_tests/core/memory/graph/extraction/test_entity_type_definition.py}.
 */
@DisplayName("Entity Type Definition Tests")
class EntityTypeDefinitionTest {

    @Nested
    @DisplayName("EntityDefAttr Tests")
    class EntityDefAttrTests {

        @Test
        @DisplayName("default content is empty")
        void defaultContentIsEmpty() {
            EntityTypeDefinition.EntityDefAttr attr = new EntityTypeDefinition.EntityDefAttr();
            assertEquals("", attr.getContent());
        }

        @Test
        @DisplayName("content can be set")
        void contentCanBeSet() {
            EntityTypeDefinition.EntityDefAttr attr = new EntityTypeDefinition.EntityDefAttr();
            attr.setContent("summary text");
            assertEquals("summary text", attr.getContent());
        }

        @Test
        @DisplayName("response format returns schema")
        void responseFormatReturnsSchema() {
            EntityTypeDefinition.EntityDefAttr attr = new EntityTypeDefinition.EntityDefAttr();
            Map<String, Object> schema = attr.responseFormat();

            assertNotNull(schema);
            assertEquals("object", schema.get("type"));
            assertTrue(schema.containsKey("properties"));
        }
    }

    @Nested
    @DisplayName("EntityDef Tests")
    class EntityDefTests {

        @Test
        @DisplayName("default name is Entity")
        void defaultNameIsEntity() {
            EntityTypeDefinition.EntityDef entity = new EntityTypeDefinition.EntityDef();
            assertEquals("Entity", entity.getName());
        }

        @Test
        @DisplayName("attributes default to EntityDefAttr")
        void attributesDefaultToEntityDefAttr() {
            EntityTypeDefinition.EntityDef entity = new EntityTypeDefinition.EntityDef();
            assertNotNull(entity.getAttributes());
            assertTrue(entity.getAttributes() instanceof EntityTypeDefinition.EntityDefAttr);
        }

        @Test
        @DisplayName("name can be set")
        void nameCanBeSet() {
            EntityTypeDefinition.EntityDef entity = new EntityTypeDefinition.EntityDef();
            entity.setName("CustomEntity");
            assertEquals("CustomEntity", entity.getName());
        }

        @Test
        @DisplayName("description can be set")
        void descriptionCanBeSet() {
            EntityTypeDefinition.EntityDef entity = new EntityTypeDefinition.EntityDef();
            Map<String, String> description = new LinkedHashMap<>();
            description.put("en", "Custom description");
            entity.setDescription(description);
            assertEquals(description, entity.getDescription());
        }
    }

    @Nested
    @DisplayName("HumanEntity Tests")
    class HumanEntityTests {

        @Test
        @DisplayName("name is Human")
        void nameIsHuman() {
            EntityTypeDefinition.HumanEntity entity = new EntityTypeDefinition.HumanEntity();
            assertEquals("Human", entity.getName());
        }

        @Test
        @DisplayName("HumanEntity extends EntityDef")
        void humanEntityExtendsEntityDef() {
            EntityTypeDefinition.HumanEntity entity = new EntityTypeDefinition.HumanEntity();
            assertTrue(entity instanceof EntityTypeDefinition.EntityDef);
        }
    }

    @Nested
    @DisplayName("AIEntity Tests")
    class AIEntityTests {

        @Test
        @DisplayName("name is AI")
        void nameIsAi() {
            EntityTypeDefinition.AIEntity entity = new EntityTypeDefinition.AIEntity();
            assertEquals("AI", entity.getName());
        }

        @Test
        @DisplayName("AIEntity extends EntityDef")
        void aiEntityExtendsEntityDef() {
            EntityTypeDefinition.AIEntity entity = new EntityTypeDefinition.AIEntity();
            assertTrue(entity instanceof EntityTypeDefinition.EntityDef);
        }
    }

    @Nested
    @DisplayName("RelationDef Tests")
    class RelationDefTests {

        @Test
        @DisplayName("default name is Relation")
        void defaultNameIsRelation() {
            EntityTypeDefinition.RelationDef relation = new EntityTypeDefinition.RelationDef();
            assertEquals("Relation", relation.getName());
        }

        @Test
        @DisplayName("lhs and rhs can be set")
        void lhsAndRhsCanBeSet() {
            EntityTypeDefinition.RelationDef relation = new EntityTypeDefinition.RelationDef();
            relation.setLhs(EntityTypeDefinition.HumanEntity.class);
            relation.setRhs(EntityTypeDefinition.AIEntity.class);

            assertEquals(EntityTypeDefinition.HumanEntity.class, relation.getLhs());
            assertEquals(EntityTypeDefinition.AIEntity.class, relation.getRhs());
        }

        @Test
        @DisplayName("name can be set")
        void nameCanBeSet() {
            EntityTypeDefinition.RelationDef relation = new EntityTypeDefinition.RelationDef();
            relation.setName("CustomRelation");
            assertEquals("CustomRelation", relation.getName());
        }
    }
}
