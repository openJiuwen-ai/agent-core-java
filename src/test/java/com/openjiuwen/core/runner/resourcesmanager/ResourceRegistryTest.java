// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import com.openjiuwen.core.runner.RunnerConfig;
import org.junit.jupiter.api.*;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResourceRegistry 测试类
 * 
 * 对应Python: test_resource_registry.py
 */
@DisplayName("ResourceRegistry 测试")
class ResourceRegistryTest {

    @BeforeEach
    void setUp() {
        RunnerConfig.setRunnerConfig(RunnerConfig.DEFAULT_RUNNER_CONFIG);
    }

    @Nested
    @DisplayName("管理器访问方法测试")
    class ManagerAccessTest {

        @Test
        @DisplayName("初始化创建所有管理器")
        void testRegistryInitialization() {
            ResourceRegistry registry = new ResourceRegistry();

            assertNotNull(registry.tool());
            assertNotNull(registry.workflow());
            assertNotNull(registry.prompt());
            assertNotNull(registry.model());
            assertNotNull(registry.agent());
            assertNotNull(registry.agentGroup());
            assertNotNull(registry.sysOperation());
        }

        @Test
        @DisplayName("tool()返回ToolMgr实例")
        void testToolReturnsToolManager() {
            ResourceRegistry registry = new ResourceRegistry();

            ToolMgr toolMgr = registry.tool();

            assertNotNull(toolMgr);
            assertInstanceOf(ToolMgr.class, toolMgr);
        }

        @Test
        @DisplayName("prompt()返回PromptMgr实例")
        void testPromptReturnsPromptManager() {
            ResourceRegistry registry = new ResourceRegistry();

            PromptMgr promptMgr = registry.prompt();

            assertNotNull(promptMgr);
            assertInstanceOf(PromptMgr.class, promptMgr);
        }

        @Test
        @DisplayName("model()返回ModelMgr实例")
        void testModelReturnsModelManager() {
            ResourceRegistry registry = new ResourceRegistry();

            ModelMgr<?> modelMgr = registry.model();

            assertNotNull(modelMgr);
            assertInstanceOf(ModelMgr.class, modelMgr);
        }

        @Test
        @DisplayName("workflow()返回WorkflowMgr实例")
        void testWorkflowReturnsWorkflowManager() {
            ResourceRegistry registry = new ResourceRegistry();

            WorkflowMgr<?> workflowMgr = registry.workflow();

            assertNotNull(workflowMgr);
            assertInstanceOf(WorkflowMgr.class, workflowMgr);
        }

        @Test
        @DisplayName("agent()返回AgentMgr实例")
        void testAgentReturnsAgentManager() {
            ResourceRegistry registry = new ResourceRegistry();

            AgentMgr<?> agentMgr = registry.agent();

            assertNotNull(agentMgr);
            assertInstanceOf(AgentMgr.class, agentMgr);
        }

        @Test
        @DisplayName("agentGroup()返回AgentGroupMgr实例")
        void testAgentGroupReturnsAgentGroupManager() {
            ResourceRegistry registry = new ResourceRegistry();

            AgentGroupMgr<?> agentGroupMgr = registry.agentGroup();

            assertNotNull(agentGroupMgr);
            assertInstanceOf(AgentGroupMgr.class, agentGroupMgr);
        }

        @Test
        @DisplayName("sysOperation()返回SysOperationMgr实例")
        void testSysOperationReturnsSysOperationManager() {
            ResourceRegistry registry = new ResourceRegistry();

            SysOperationMgr sysOpMgr = registry.sysOperation();

            assertNotNull(sysOpMgr);
            assertInstanceOf(SysOperationMgr.class, sysOpMgr);
        }

        @Test
        @DisplayName("多次调用返回同一管理器实例")
        void testManagerAccessorsReturnSameInstance() {
            ResourceRegistry registry = new ResourceRegistry();

            assertSame(registry.tool(), registry.tool());
            assertSame(registry.prompt(), registry.prompt());
            assertSame(registry.model(), registry.model());
            assertSame(registry.workflow(), registry.workflow());
            assertSame(registry.agent(), registry.agent());
            assertSame(registry.agentGroup(), registry.agentGroup());
            assertSame(registry.sysOperation(), registry.sysOperation());
        }
    }

    @Nested
    @DisplayName("removeById 跨管理器删除资源")
    class RemoveByIdTest {

        private ResourceRegistry registry;

        @BeforeEach
        void setUp() {
            registry = new ResourceRegistry();
        }

        @Test
        @DisplayName("通过ID删除Tool资源")
        void testRemoveToolById() {
            Object mockTool = new Object();
            registry.tool().addTool("tool_1", mockTool);

            registry.removeById("tool_1");

            assertNull(registry.tool().getTool("tool_1"));
        }

        @Test
        @DisplayName("通过ID删除Workflow资源")
        void testRemoveWorkflowById() {
            registry.workflow().addWorkflow("workflow_1", Object::new);

            registry.removeById("workflow_1");

            assertFalse(registry.workflow().containsProvider("workflow_1"));
        }

        @Test
        @DisplayName("通过ID删除Agent资源")
        void testRemoveAgentById() {
            registry.agent().addAgent("agent_1", Object::new);

            registry.removeById("agent_1");

            assertFalse(registry.agent().containsProvider("agent_1"));
        }

        @Test
        @DisplayName("通过ID删除AgentGroup资源")
        void testRemoveAgentGroupById() {
            registry.agentGroup().addAgentGroup("group_1", Object::new);

            registry.removeById("group_1");

            assertFalse(registry.agentGroup().containsProvider("group_1"));
        }

        @Test
        @DisplayName("通过ID删除Prompt资源")
        void testRemovePromptById() {
            Object mockTemplate = new Object();
            registry.prompt().addPrompt("prompt_1", mockTemplate);

            registry.removeById("prompt_1");

            assertNull(registry.prompt().getPrompt("prompt_1"));
        }

        @Test
        @DisplayName("通过ID删除Model资源")
        void testRemoveModelById() {
            registry.model().addModel("model_1", Object::new);

            registry.removeById("model_1");

            assertFalse(registry.model().containsProvider("model_1"));
        }

        @Test
        @DisplayName("通过ID删除SysOperation资源")
        void testRemoveSysOperationById() {
            Object mockSysOp = new Object();
            registry.sysOperation().addSysOperation("sys_op_1", mockSysOp);

            registry.removeById("sys_op_1");

            assertNull(registry.sysOperation().getSysOperation("sys_op_1"));
        }

        @Test
        @DisplayName("删除不存在的ID不抛出异常")
        void testRemoveNonexistentIdNoError() {
            assertDoesNotThrow(() -> registry.removeById("nonexistent_id"));
        }

        @Test
        @DisplayName("removeById在找到第一个匹配后停止")
        void testRemoveStopsAtFirstMatch() {
            Object mockTool = new Object();
            registry.tool().addTool("shared_id", mockTool);
            registry.workflow().addWorkflow("shared_id", Object::new);

            // 第一次删除应该删除tool（tool优先检查）
            registry.removeById("shared_id");

            // workflow应该还存在
            assertTrue(registry.workflow().containsProvider("shared_id"));

            // 第二次删除应该删除workflow
            registry.removeById("shared_id");
            assertFalse(registry.workflow().containsProvider("shared_id"));
        }
    }

    @Nested
    @DisplayName("集成测试")
    class IntegrationTest {

        @Test
        @DisplayName("不同Registry实例的独立性")
        void testRegistryIndependence() {
            ResourceRegistry registry1 = new ResourceRegistry();
            ResourceRegistry registry2 = new ResourceRegistry();

            Object mockTool = new Object();
            registry1.tool().addTool("tool_1", mockTool);

            assertNotNull(registry1.tool().getTool("tool_1"));
            assertNull(registry2.tool().getTool("tool_1"));
        }

        @Test
        @DisplayName("混合资源类型管理")
        void testMixedResourceTypesManagement() {
            ResourceRegistry registry = new ResourceRegistry();

            Object mockTool = new Object();
            Object mockTemplate = new Object();
            Object mockSysOp = new Object();

            registry.tool().addTool("resource_1", mockTool);
            registry.workflow().addWorkflow("resource_2", Object::new);
            registry.prompt().addPrompt("resource_3", mockTemplate);
            registry.sysOperation().addSysOperation("resource_4", mockSysOp);

            // 验证各自独立存在
            assertNotNull(registry.tool().getTool("resource_1"));
            assertTrue(registry.workflow().containsProvider("resource_2"));
            assertNotNull(registry.prompt().getPrompt("resource_3"));
            assertNotNull(registry.sysOperation().getSysOperation("resource_4"));

            // 按顺序删除
            registry.removeById("resource_1");
            registry.removeById("resource_2");
            registry.removeById("resource_3");
            registry.removeById("resource_4");

            // 验证都已删除
            assertNull(registry.tool().getTool("resource_1"));
            assertFalse(registry.workflow().containsProvider("resource_2"));
            assertNull(registry.prompt().getPrompt("resource_3"));
            assertNull(registry.sysOperation().getSysOperation("resource_4"));
        }
    }
}

