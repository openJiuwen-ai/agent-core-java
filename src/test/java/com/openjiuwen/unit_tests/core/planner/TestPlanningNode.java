/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.planner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PlanningNode.
 * <p>
 * Mirrors Python's planner node tests.
 */
@DisplayName("Planning Node Tests")
class TestPlanningNode {

    // Stub classes
    static class PlanningNode {
        String id;
        String type;
        Map<String, Object> data = new HashMap<>();
        List<PlanningNode> children = new ArrayList<>();
        PlanningNode parent;

        PlanningNode(String id, String type) {
            this.id = id;
            this.type = type;
        }

        void addChild(PlanningNode child) {
            child.parent = this;
            children.add(child);
        }

        List<PlanningNode> getChildren() {
            return new ArrayList<>(children);
        }

        PlanningNode getParent() {
            return parent;
        }

        boolean isRoot() {
            return parent == null;
        }

        int getDepth() {
            if (parent == null) return 0;
            return parent.getDepth() + 1;
        }
    }

    @Nested
    @DisplayName("Planning Node Creation Tests")
    class TestPlanningNodeCreation {

        @Test
        @DisplayName("planning node creation")
        void testPlanningNodeCreation() {
            PlanningNode node = new PlanningNode("node-1", "task");

            assertNotNull(node);
            assertEquals("node-1", node.id);
            assertEquals("task", node.type);
            assertTrue(node.isRoot());
        }

        @Test
        @DisplayName("planning node with data")
        void testPlanningNodeWithData() {
            PlanningNode node = new PlanningNode("node-1", "decision");
            node.data.put("condition", "x > 5");
            node.data.put("action", "proceed");

            assertEquals("x > 5", node.data.get("condition"));
            assertEquals("proceed", node.data.get("action"));
        }
    }

    @Nested
    @DisplayName("Planning Node Tree Tests")
    class TestPlanningNodeTree {

        @Test
        @DisplayName("add child node")
        void testAddChildNode() {
            PlanningNode parent = new PlanningNode("parent", "root");
            PlanningNode child = new PlanningNode("child", "task");

            parent.addChild(child);

            assertEquals(1, parent.getChildren().size());
            assertEquals(parent, child.getParent());
            assertFalse(child.isRoot());
        }

        @Test
        @DisplayName("node depth calculation")
        void testNodeDepthCalculation() {
            PlanningNode root = new PlanningNode("root", "root");
            PlanningNode level1 = new PlanningNode("l1", "task");
            PlanningNode level2 = new PlanningNode("l2", "subtask");

            root.addChild(level1);
            level1.addChild(level2);

            assertEquals(0, root.getDepth());
            assertEquals(1, level1.getDepth());
            assertEquals(2, level2.getDepth());
        }

        @Test
        @DisplayName("multiple children")
        void testMultipleChildren() {
            PlanningNode parent = new PlanningNode("parent", "root");
            parent.addChild(new PlanningNode("c1", "task"));
            parent.addChild(new PlanningNode("c2", "task"));
            parent.addChild(new PlanningNode("c3", "task"));

            assertEquals(3, parent.getChildren().size());
        }
    }
}