// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.RunnerConfig;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResourceMgr 测试类
 * 
 * 对应Python: test_resource_manager.py
 */
@DisplayName("ResourceMgr 测试")
class ResourceMgrTest {

    // ==================== Mock Classes ====================

    static class MockAgentCard {
        final String id;
        final String name;

        MockAgentCard(String id) {
            this(id, "test_agent");
        }

        MockAgentCard(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() { return id; }
        public String getName() { return name; }
    }

    static class MockWorkflowCard {
        final String id;
        final String name;

        MockWorkflowCard(String id) {
            this(id, "test_workflow");
        }

        MockWorkflowCard(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() { return id; }
        public String getName() { return name; }
    }

    static class MockGroupCard {
        final String id;
        final String name;

        MockGroupCard(String id) {
            this(id, "test_group");
        }

        MockGroupCard(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() { return id; }
        public String getName() { return name; }
    }

    static class MockSysOperationCard {
        final String id;
        final String name;

        MockSysOperationCard(String id) {
            this(id, "test_op");
        }

        MockSysOperationCard(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() { return id; }
        public String getName() { return name; }
    }

    static class MockToolCard {
        final String id;
        final String name;

        MockToolCard(String id) {
            this(id, "test_tool");
        }

        MockToolCard(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() { return id; }
        public String getName() { return name; }
    }

    static class MockTool {
        final MockToolCard card;

        MockTool(String toolId) {
            this.card = new MockToolCard(toolId);
        }
    }

    static class MockBaseAgent {
        final String agentId;

        MockBaseAgent(String agentId) {
            this.agentId = agentId;
        }
    }

    static class MockWorkflow {
        final String workflowId;

        MockWorkflow(String workflowId) {
            this.workflowId = workflowId;
        }
    }

    static class MockBaseGroup {
        final String groupId;

        MockBaseGroup(String groupId) {
            this.groupId = groupId;
        }
    }

    static class MockPromptTemplate {
        final String templateId;

        MockPromptTemplate(String templateId) {
            this.templateId = templateId;
        }
    }

    @BeforeEach
    void setUp() {
        RunnerConfig.setRunnerConfig(RunnerConfig.DEFAULT_RUNNER_CONFIG);
    }

    // ==================== 输入验证测试 ====================

    @Nested
    @DisplayName("输入验证")
    class ValidationTest {

        @Test
        @DisplayName("Card为null时抛出RESOURCE_CARD_VALUE_INVALID")
        void testValidateCardNullRaisesException() {
            ResourceMgr mgr = new ResourceMgr();

            BaseError error = assertThrows(BaseError.class,
                () -> mgr.addAgent(null, null, () -> new MockBaseAgent("a1"), null));

            assertEquals(StatusCode.RESOURCE_CARD_VALUE_INVALID.getCode(), error.getCode());
        }

        @Test
        @DisplayName("Card.id为空时抛出RESOURCE_CARD_VALUE_INVALID")
        void testValidateCardIdEmptyRaisesException() {
            ResourceMgr mgr = new ResourceMgr();

            BaseError error = assertThrows(BaseError.class,
                () -> mgr.addAgent("", null, () -> new MockBaseAgent("a1"), null));

            assertEquals(StatusCode.RESOURCE_CARD_VALUE_INVALID.getCode(), error.getCode());
        }

        @Test
        @DisplayName("Provider为null时抛出RESOURCE_PROVIDER_INVALID")
        void testValidateProviderNullRaisesException() {
            ResourceMgr mgr = new ResourceMgr();

            BaseError error = assertThrows(BaseError.class,
                () -> mgr.addAgent("agent_1", null, null, null));

            assertEquals(StatusCode.RESOURCE_PROVIDER_INVALID.getCode(), error.getCode());
        }

        @Test
        @DisplayName("Tag为空列表时抛出RESOURCE_TAG_VALUE_INVALID")
        void testValidateTagEmptyRaisesException() {
            ResourceMgr mgr = new ResourceMgr();

            BaseError error = assertThrows(BaseError.class,
                () -> mgr.addAgent("agent_1", null, () -> new MockBaseAgent("a1"), List.of()));

            assertEquals(StatusCode.RESOURCE_TAG_VALUE_INVALID.getCode(), error.getCode());
        }

        @Test
        @DisplayName("Tag包含重复项时抛出RESOURCE_TAG_VALUE_INVALID")
        void testValidateTagDuplicateRaisesException() {
            ResourceMgr mgr = new ResourceMgr();

            BaseError error = assertThrows(BaseError.class,
                () -> mgr.addAgent("agent_1", null, () -> new MockBaseAgent("a1"),
                    Arrays.asList("tag1", "tag1")));

            assertEquals(StatusCode.RESOURCE_TAG_VALUE_INVALID.getCode(), error.getCode());
        }

        @Test
        @DisplayName("Tag同时包含GLOBAL与其他标签时抛出RESOURCE_TAG_VALUE_INVALID")
        void testValidateTagGlobalWithOthersRaisesException() {
            ResourceMgr mgr = new ResourceMgr();

            BaseError error = assertThrows(BaseError.class,
                () -> mgr.addAgent("agent_1", null, () -> new MockBaseAgent("a1"),
                    Arrays.asList(Tag.GLOBAL, "other_tag")));

            assertEquals(StatusCode.RESOURCE_TAG_VALUE_INVALID.getCode(), error.getCode());
        }

        @Test
        @DisplayName("resource_id为null时抛出RESOURCE_ID_VALUE_INVALID")
        void testValidateResourceIdNullRaisesException() {
            ResourceMgr mgr = new ResourceMgr();

            BaseError error = assertThrows(BaseError.class,
                () -> mgr.addModel(null, Object::new, null));

            assertEquals(StatusCode.RESOURCE_ID_VALUE_INVALID.getCode(), error.getCode());
        }
    }

    // ==================== Agent操作测试 ====================

    @Nested
    @DisplayName("Agent操作")
    class AgentOperationsTest {

        @Test
        @DisplayName("成功添加Agent")
        void testAddAgentSuccess() {
            ResourceMgr mgr = new ResourceMgr();

            Result<?> result = mgr.addAgent("agent_1", null,
                () -> new MockBaseAgent("a1"), null);

            assertTrue(result.isOk());
        }

        @Test
        @DisplayName("添加重复ID的Agent返回Error")
        void testAddAgentDuplicateReturnsError() {
            ResourceMgr mgr = new ResourceMgr();

            mgr.addAgent("agent_1", null, () -> new MockBaseAgent("a1"), null);
            Result<?> result = mgr.addAgent("agent_1", null, () -> new MockBaseAgent("a2"), null);

            assertTrue(result.isErr());
        }

        @Test
        @DisplayName("批量添加Agents")
        void testAddAgentsBatch() {
            ResourceMgr mgr = new ResourceMgr();

            List<Result<?>> results = mgr.addAgents(List.of(
                new ResourceMgr.AgentEntry("agent_1", () -> new Object()),
                new ResourceMgr.AgentEntry("agent_2", () -> new Object())
            ), null);

            assertEquals(2, results.size());
            assertTrue(results.stream().allMatch(Result::isOk));
        }

        @Test
        @DisplayName("批量添加空列表抛出异常")
        void testAddAgentsEmptyRaisesException() {
            ResourceMgr mgr = new ResourceMgr();

            assertThrows(BaseError.class, () -> mgr.addAgents(List.of(), null));
        }

        @Test
        @DisplayName("通过ID获取Agent")
        void testGetAgentById() {
            ResourceMgr mgr = new ResourceMgr();
            MockBaseAgent expectedAgent = new MockBaseAgent("a1");

            mgr.addAgent("agent_1", null, () -> expectedAgent, null);

            CompletableFuture<?> future = mgr.getAgent("agent_1", null, null);
            Object result = future.join();

            assertSame(expectedAgent, result);
        }

        @Test
        @DisplayName("通过ID删除Agent")
        void testRemoveAgentById() {
            ResourceMgr mgr = new ResourceMgr();

            mgr.addAgent("agent_1", null, () -> new MockBaseAgent("a1"), null);
            List<Result<?>> results = mgr.removeAgent(List.of("agent_1"),
                null, TagMatchStrategy.ALL, false);

            assertEquals(1, results.size());
            assertTrue(results.get(0).isOk());
        }
    }

    // ==================== Workflow操作测试 ====================

    @Nested
    @DisplayName("Workflow操作")
    class WorkflowOperationsTest {

        @Test
        @DisplayName("成功添加Workflow")
        void testAddWorkflowSuccess() {
            ResourceMgr mgr = new ResourceMgr();

            Result<?> result = mgr.addWorkflow("workflow_1", null,
                () -> new MockWorkflow("wf1"), null);

            assertTrue(result.isOk());
        }

        @Test
        @DisplayName("通过ID获取Workflow")
        void testGetWorkflowById() {
            ResourceMgr mgr = new ResourceMgr();
            MockWorkflow expectedWorkflow = new MockWorkflow("wf1");

            mgr.addWorkflow("workflow_1", null, () -> expectedWorkflow, null);

            CompletableFuture<?> future = mgr.getWorkflow("workflow_1", null, null);
            Object result = future.join();

            assertSame(expectedWorkflow, result);
        }
    }

    // ==================== Tool操作测试 ====================

    @Nested
    @DisplayName("Tool操作")
    class ToolOperationsTest {

        @Test
        @DisplayName("成功添加Tool到底层管理器")
        void testAddToolSuccess() {
            ResourceMgr mgr = new ResourceMgr();
            Object mockTool = new Object();

            // 直接使用底层管理器添加
            mgr.getResourceRegistry().tool().addTool("tool_1", mockTool);

            Object result = mgr.getResourceRegistry().tool().getTool("tool_1");
            assertSame(mockTool, result);
        }

        @Test
        @DisplayName("Tool为null时抛出RESOURCE_VALUE_INVALID")
        void testAddToolNullRaisesException() {
            ResourceMgr mgr = new ResourceMgr();

            assertThrows(BaseError.class, () -> mgr.addTool(null, null, null));
        }

        @Test
        @DisplayName("通过ID获取Tool")
        void testGetToolById() {
            ResourceMgr mgr = new ResourceMgr();
            Object mockTool = new Object();

            mgr.getResourceRegistry().tool().addTool("tool_1", mockTool);

            Object result = mgr.getResourceRegistry().tool().getTool("tool_1");
            assertSame(mockTool, result);
        }
    }

    // ==================== Prompt操作测试 ====================

    @Nested
    @DisplayName("Prompt操作")
    class PromptOperationsTest {

        @Test
        @DisplayName("成功添加Prompt")
        void testAddPromptSuccess() {
            ResourceMgr mgr = new ResourceMgr();
            MockPromptTemplate template = new MockPromptTemplate("prompt_1");

            Result<?> result = mgr.addPrompt("prompt_1", template, null);

            assertTrue(result.isOk());
        }

        @Test
        @DisplayName("prompt_id为null时抛出异常")
        void testAddPromptIdNullRaisesException() {
            ResourceMgr mgr = new ResourceMgr();
            MockPromptTemplate template = new MockPromptTemplate("p1");

            assertThrows(BaseError.class, () -> mgr.addPrompt(null, template, null));
        }

        @Test
        @DisplayName("template为null时抛出异常")
        void testAddPromptTemplateNullRaisesException() {
            ResourceMgr mgr = new ResourceMgr();

            assertThrows(BaseError.class, () -> mgr.addPrompt("prompt_1", null, null));
        }

        @Test
        @DisplayName("通过ID获取Prompt")
        void testGetPromptById() {
            ResourceMgr mgr = new ResourceMgr();
            MockPromptTemplate template = new MockPromptTemplate("prompt_1");
            mgr.addPrompt("prompt_1", template, null);

            Object result = mgr.getPrompt("prompt_1", null, null);

            assertSame(template, result);
        }
    }

    // ==================== Model操作测试 ====================

    @Nested
    @DisplayName("Model操作")
    class ModelOperationsTest {

        @Test
        @DisplayName("成功添加Model")
        void testAddModelSuccess() {
            ResourceMgr mgr = new ResourceMgr();

            Result<?> result = mgr.addModel("model_1", Object::new, null);

            assertTrue(result.isOk());
        }

        @Test
        @DisplayName("model_id为null时抛出异常")
        void testAddModelIdNullRaisesException() {
            ResourceMgr mgr = new ResourceMgr();

            assertThrows(BaseError.class, () -> mgr.addModel(null, Object::new, null));
        }
    }

    // ==================== AgentGroup操作测试 ====================

    @Nested
    @DisplayName("AgentGroup操作")
    class AgentGroupOperationsTest {

        @Test
        @DisplayName("成功添加AgentGroup")
        void testAddAgentGroupSuccess() {
            ResourceMgr mgr = new ResourceMgr();

            Result<?> result = mgr.addAgentGroup("group_1", null,
                () -> new MockBaseGroup("g1"), null);

            assertTrue(result.isOk());
        }

        @Test
        @DisplayName("通过ID获取AgentGroup")
        void testGetAgentGroupById() {
            ResourceMgr mgr = new ResourceMgr();
            MockBaseGroup expectedGroup = new MockBaseGroup("g1");

            mgr.addAgentGroup("group_1", null, () -> expectedGroup, null);

            CompletableFuture<?> future = mgr.getAgentGroup("group_1", null, null);
            Object result = future.join();

            assertSame(expectedGroup, result);
        }
    }

    // ==================== SysOperation操作测试 ====================

    @Nested
    @DisplayName("SysOperation操作")
    class SysOperationOperationsTest {

        @Test
        @DisplayName("成功添加SysOperation到底层管理器")
        void testAddSysOperationSuccess() {
            ResourceMgr mgr = new ResourceMgr();
            Object mockSysOp = new Object();

            mgr.getResourceRegistry().sysOperation().addSysOperation("sys_op_1", mockSysOp);

            Object result = mgr.getResourceRegistry().sysOperation().getSysOperation("sys_op_1");
            assertSame(mockSysOp, result);
        }

        @Test
        @DisplayName("通过ID获取SysOperation")
        void testGetSysOperationById() {
            ResourceMgr mgr = new ResourceMgr();
            Object mockSysOp = new Object();

            mgr.getResourceRegistry().sysOperation().addSysOperation("sys_op_1", mockSysOp);

            Object result = mgr.getResourceRegistry().sysOperation().getSysOperation("sys_op_1");
            assertSame(mockSysOp, result);
        }
    }

    // ==================== 标签操作测试 ====================

    @Nested
    @DisplayName("标签操作")
    class TagOperationsTest {

        @Test
        @DisplayName("列出所有标签")
        void testListTags() {
            ResourceMgr mgr = new ResourceMgr();

            mgr.addAgent("agent_1", null, () -> new MockBaseAgent("a1"), List.of("custom_tag"));

            List<String> tags = mgr.listTags();
            assertTrue(tags.contains("custom_tag"));
        }

        @Test
        @DisplayName("检查标签是否存在")
        void testHasTag() {
            ResourceMgr mgr = new ResourceMgr();

            mgr.addAgent("agent_1", null, () -> new MockBaseAgent("a1"), List.of("existing_tag"));

            assertTrue(mgr.hasTag("existing_tag"));
            assertFalse(mgr.hasTag("nonexistent_tag"));
        }

        @Test
        @DisplayName("通过标签获取资源")
        void testGetResourceByTag() {
            ResourceMgr mgr = new ResourceMgr();

            mgr.addAgent("agent_1", "agent_card_1", () -> new MockBaseAgent("a1"),
                List.of("search_tag"));

            List<Object> resources = mgr.getResourceByTag("search_tag");
            assertNotNull(resources);
            assertEquals(1, resources.size());
        }

        @Test
        @DisplayName("为资源添加标签")
        void testAddResourceTag() {
            ResourceMgr mgr = new ResourceMgr();

            mgr.addAgent("agent_1", null, () -> new MockBaseAgent("a1"),
                List.of("initial_tag"));
            Result<?> result = mgr.addResourceTag("agent_1", List.of("new_tag"));

            assertTrue(result.isOk());
            assertTrue(mgr.resourceHasTag("agent_1", "new_tag"));
            assertTrue(mgr.resourceHasTag("agent_1", "initial_tag"));
        }

        @Test
        @DisplayName("从资源移除标签")
        @SuppressWarnings("unchecked")
        void testRemoveResourceTag() {
            ResourceMgr mgr = new ResourceMgr();

            mgr.addAgent("agent_1", null, () -> new MockBaseAgent("a1"),
                Arrays.asList("tag1", "tag2"));
            Result<?> result = mgr.removeResourceTag("agent_1", List.of("tag1"), false);

            assertTrue(result.isOk());
            List<String> remainingTags = (List<String>) result.msg();
            assertFalse(remainingTags.contains("tag1"));
            assertTrue(remainingTags.contains("tag2"));
        }

        @Test
        @DisplayName("替换资源标签")
        @SuppressWarnings("unchecked")
        void testUpdateResourceTag() {
            ResourceMgr mgr = new ResourceMgr();

            mgr.addAgent("agent_1", null, () -> new MockBaseAgent("a1"),
                List.of("old_tag"));
            Result<?> result = mgr.updateResourceTag("agent_1",
                Arrays.asList("new_tag1", "new_tag2"));

            assertTrue(result.isOk());
            List<String> tags = (List<String>) result.msg();
            assertTrue(tags.contains("new_tag1"));
            assertTrue(tags.contains("new_tag2"));
        }

        @Test
        @DisplayName("获取资源的标签列表")
        void testGetResourceTag() {
            ResourceMgr mgr = new ResourceMgr();

            mgr.addAgent("agent_1", null, () -> new MockBaseAgent("a1"),
                Arrays.asList("tag1", "tag2"));

            List<String> tags = mgr.getResourceTag("agent_1");
            assertNotNull(tags);
            assertTrue(tags.contains("tag1"));
            assertTrue(tags.contains("tag2"));
        }

        @Test
        @DisplayName("判断资源是否拥有指定标签")
        void testResourceHasTag() {
            ResourceMgr mgr = new ResourceMgr();

            mgr.addAgent("agent_1", null, () -> new MockBaseAgent("a1"),
                List.of("my_tag"));

            assertTrue(mgr.resourceHasTag("agent_1", "my_tag"));
            assertFalse(mgr.resourceHasTag("agent_1", "other_tag"));
        }

        @Test
        @DisplayName("删除标签并释放关联资源")
        void testRemoveTag() {
            ResourceMgr mgr = new ResourceMgr();

            mgr.addAgent("agent_1", null, () -> new MockBaseAgent("a1"),
                List.of("to_remove"));

            List<Result<?>> results = mgr.removeTag(List.of("to_remove"), false);

            assertEquals(1, results.size());
            assertTrue(results.get(0).isOk());
        }
    }

    // ==================== MCP服务器操作测试 ====================

    @Nested
    @DisplayName("MCP服务器操作")
    class McpServerOperationsTest {

        @Test
        @DisplayName("server_config为null时抛出RESOURCE_MCP_SERVER_PARAM_INVALID")
        void testAddMcpServerConfigNullRaisesException() {
            ResourceMgr mgr = new ResourceMgr();

            BaseError error = assertThrows(BaseError.class,
                () -> ResourceMgr.validateServerConfig(null));

            assertEquals(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID.getCode(), error.getCode());
        }

        @Test
        @DisplayName("expiry_time<=0时抛出异常")
        void testAddMcpServerNegativeExpiryRaisesException() {
            ResourceMgr mgr = new ResourceMgr();

            BaseError error = assertThrows(BaseError.class,
                () -> mgr.validateExpiryTime(-1.0));

            assertEquals(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID.getCode(), error.getCode());
        }
    }

    // ==================== Release测试 ====================

    @Nested
    @DisplayName("Release资源")
    class ReleaseTest {

        @Test
        @DisplayName("release调用工具管理器的release")
        void testReleaseCallsToolRelease() {
            ResourceMgr mgr = new ResourceMgr();

            // release不应抛出异常
            CompletableFuture<Void> future = mgr.release();
            assertDoesNotThrow(() -> future.join());
        }
    }

    // ==================== 集成测试 ====================

    @Nested
    @DisplayName("集成测试")
    class IntegrationTest {

        @Test
        @DisplayName("混合资源类型和标签管理")
        void testMixedResourceTypesWithTags() {
            ResourceMgr mgr = new ResourceMgr();

            mgr.addAgent("agent_1", null, () -> new MockBaseAgent("a1"),
                List.of("shared_tag"));
            mgr.addWorkflow("workflow_1", null, () -> new MockWorkflow("wf1"),
                List.of("shared_tag"));

            // 通过标签查找
            List<Object> resources = mgr.getResourceByTag("shared_tag");
            assertEquals(2, resources.size());

            // 验证可以分别获取
            Object agent = mgr.getAgent("agent_1", null, null).join();
            Object workflow = mgr.getWorkflow("workflow_1", null, null).join();

            assertNotNull(agent);
            assertNotNull(workflow);
        }

        @Test
        @DisplayName("未指定tag时默认使用GLOBAL标签")
        void testGlobalTagDefaultBehavior() {
            ResourceMgr mgr = new ResourceMgr();

            mgr.addAgent("agent_1", null, () -> new MockBaseAgent("a1"), null);

            assertTrue(mgr.resourceHasTag("agent_1", Tag.GLOBAL));
        }
    }
}

