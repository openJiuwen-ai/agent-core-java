/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.workflow;

/**
 * Mock nodes for workflow testing.
 * 
 * Note: Simplified placeholder implementation.
 */
public class MockNodes {

    /**
     * Base class for mock workflow nodes.
     */
    public static abstract class MockNodeBase {
        protected String nodeId;

        public MockNodeBase(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }
    }
}