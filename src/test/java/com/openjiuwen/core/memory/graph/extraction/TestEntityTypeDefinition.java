/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EntityTypeDefinition.
 * <p>
 * Mirrors Python's test_entity_type_definition.py from
 * <code>tests/unit_tests/core/memory/graph/extraction/test_entity_type_definition.py</code>.
 */
@DisplayName("Entity Type Definition Tests")
class TestEntityTypeDefinition {

    @Nested
    @DisplayName("EntityDefAttr Tests")
    class TestEntityDefAttr {

        @Test
        @DisplayName("default content is empty")
        void testDefaultContentEmpty() {
            EntityTypeDefinition.EntityDefAttr attr = new EntityTypeDefinition.EntityDefAttr();
            assertEquals("", attr.getContent());
        }

        @Test
        @DisplayName("content can be set")
        void testContentCanBeSet() {
            EntityTypeDefinition.EntityDefAttr attr = new EntityTypeDefinition.EntityDefAttr();
            attr.setContent("summary text");
            assertEquals("summary text", attr.getContent());
        }

        @Test
        @DisplayName("response format returns schema")
        void testResponseFormatReturnsSchema() {
            EntityTypeDefinition.EntityDefAttr attr = new EntityTypeDefinition.EntityDefAttr();
            Map<String, Object> schema = attr.responseFormat();

            assertNotNull(schema);
            assertEquals("object", schema.get("type"));
            assertTrue(schema.containsKey("properties"));
        }
    }

    @Nested
    @DisplayName("EntityDef Tests")
    class TestEntityDef {

        @Test
        @DisplayName("default name is Entity")
        void testDefaultNameEntity() {
            EntityTypeDefinition.EntityDef ent = new EntityTypeDefinition.EntityDef();
            assertEquals("Entity", ent.getName());
        }

        @Test
        @DisplayName("attributes is EntityDefAttr by default")
        void testAttributesIsEntityDefAttr() {
            EntityTypeDefinition.EntityDef ent = new EntityTypeDefinition.EntityDef();
            assertNotNull(ent.getAttributes());
            assertTrue(ent.getAttributes() instanceof EntityTypeDefinition.EntityDefAttr);
        }

        @Test
        @DisplayName("name can be set")
        void testNameCanBeSet() {
            EntityTypeDefinition.EntityDef ent = new EntityTypeDefinition.EntityDef();
            ent.setName("CustomEntity");
            assertEquals("CustomEntity", ent.getName());
        }

        @Test
        @DisplayName("description can be set")
        void testDescriptionCanBeSet() {
            EntityTypeDefinition.EntityDef ent = new EntityTypeDefinition.EntityDef();
            Map<String, String> desc = new HashMap<>();
            desc.put("en", "Custom description");
            ent.setDescription(desc);
            assertEquals(desc, ent.getDescription());
        }
    }

    @Nested
    @DisplayName("HumanEntity Tests")
    class TestHumanEntity {

        @Test
        @DisplayName("name is Human")
        void testNameIsHuman() {
            EntityTypeDefinition.HumanEntity ent = new EntityTypeDefinition.HumanEntity();
            assertEquals("Human", ent.getName());
        }

        @Test
        @DisplayName("HumanEntity extends EntityDef")
        void testHumanEntityExtendsEntityDef() {
            EntityTypeDefinition.HumanEntity ent = new EntityTypeDefinition.HumanEntity();
            assertTrue(ent instanceof EntityTypeDefinition.EntityDef);
        }
    }

    @Nested
    @DisplayName("AIEntity Tests")
    class TestAIEntity {

        @Test
        @DisplayName("name is AI")
        void testNameIsAI() {
            EntityTypeDefinition.AIEntity ent = new EntityTypeDefinition.AIEntity();
            assertEquals("AI", ent.getName());
        }

        @Test
        @DisplayName("AIEntity extends EntityDef")
        void testAIEntityExtendsEntityDef() {
            EntityTypeDefinition.AIEntity ent = new EntityTypeDefinition.AIEntity();
            assertTrue(ent instanceof EntityTypeDefinition.EntityDef);
        }
    }

    @Nested
    @DisplayName("RelationDef Tests")
    class TestRelationDef {

        @Test
        @DisplayName("default name is Relation")
        void testDefaultNameRelation() {
            EntityTypeDefinition.RelationDef rel = new EntityTypeDefinition.RelationDef();
            assertEquals("Relation", rel.getName());
        }

        @Test
        @DisplayName("lhs and rhs can be set")
        void testLhsRhsCanBeSet() {
            EntityTypeDefinition.RelationDef rel = new EntityTypeDefinition.RelationDef();
            rel.setLhs(EntityTypeDefinition.HumanEntity.class);
            rel.setRhs(EntityTypeDefinition.AIEntity.class);

            assertEquals(EntityTypeDefinition.HumanEntity.class, rel.getLhs());
            assertEquals(EntityTypeDefinition.AIEntity.class, rel.getRhs());
        }

        @Test
        @DisplayName("name can be set")
        void testNameCanBeSet() {
            EntityTypeDefinition.RelationDef rel = new EntityTypeDefinition.RelationDef();
            rel.setName("CustomRelation");
            assertEquals("CustomRelation", rel.getName());
        }
    }
}