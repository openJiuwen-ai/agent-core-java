/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.internal.AgentTeamSession;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.workspace.DirectoryBuilder;
import com.openjiuwen.harness.workspace.Workspace;
import com.openjiuwen.harness.workspace.WorkspaceNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_deep_agent_workspace} in
 * {@code tests.unit_tests.harness.test_deep_agent_workspace}.
 */
class TestDeepAgentWorkspace {

    @TempDir
    Path tempDir;

    @Test
    @Tag("level0")
    @DisplayName("default workspace schema has required directories")
    void testWorkspaceDefaultSchemaHasRequiredDirectories() {
        Workspace workspace = new Workspace("./default", "cn");
        var names = workspace.getDirectories().stream().map(node -> String.valueOf(node.get("name"))).toList();

        assertTrue(names.contains("AGENT.md"));
        assertTrue(names.contains("SOUL.md"));
        assertTrue(names.contains("HEARTBEAT.md"));
        assertTrue(names.contains("IDENTITY.md"));
        assertTrue(names.contains("USER.md"));
        assertTrue(names.contains("memory"));
        assertTrue(names.contains("todo"));
        assertTrue(names.contains("messages"));
        assertTrue(names.contains("skills"));
        assertTrue(names.contains("agents"));
    }

    @Test
    @Tag("level0")
    @DisplayName("custom directories are preserved")
    void testWorkspaceCustomDirectoriesPreserved() {
        Workspace workspace = new Workspace("./custom", new ArrayList<>(List.of(
                node("custom1", "custom1", "Custom 1"),
                node("custom2", "custom2", "Custom 2"))), "cn");

        assertEquals("custom1", workspace.getDirectory("custom1"));
        assertEquals("custom2", workspace.getDirectory("custom2"));
    }

    @Test
    @Tag("level0")
    @DisplayName("missing default directories are supplemented")
    void testWorkspaceMissingDefaultsSupplemented() {
        Workspace workspace = new Workspace("./partial", new ArrayList<>(List.of(
                node("custom", "custom", "Custom"))), "cn");

        assertEquals("custom", workspace.getDirectory("custom"));
        assertEquals("AGENT.md", workspace.getDirectory("AGENT.md"));
        assertEquals("USER.md", workspace.getDirectory("USER.md"));
    }

    @Test
    @Tag("level0")
    @DisplayName("getDirectory returns expected paths")
    void testWorkspaceGetDirectoryReturnsPath() {
        Workspace workspace = new Workspace("./", "cn");

        assertEquals("AGENT.md", workspace.getDirectory("AGENT.md"));
        assertEquals("USER.md", workspace.getDirectory("USER.md"));
        assertNull(workspace.getDirectory("nonexistent"));
    }

    @Test
    @Tag("level0")
    @DisplayName("workspace accepts Path root")
    void testWorkspaceAcceptsPathlibPath() {
        Workspace workspace = new Workspace(Path.of("/tmp/test"));

        assertTrue(workspace.getRootPath().contains("tmp"));
        assertTrue(workspace.getRootPath().contains("test"));
    }

    @Test
    @Tag("level0")
    @DisplayName("setDirectory adds a new node")
    void testWorkspaceSetDirectoryAddsNew() {
        Workspace workspace = new Workspace("./test", "cn");
        int initialCount = workspace.getDirectories().size();

        workspace.setDirectory(node("new_dir", "new_dir", "New"));

        assertEquals(initialCount + 1, workspace.getDirectories().size());
        assertEquals("new_dir", workspace.getDirectory("new_dir"));
    }

    @Test
    @Tag("level0")
    @DisplayName("setDirectory updates existing node")
    void testWorkspaceSetDirectoryUpdatesExisting() {
        Workspace workspace = new Workspace("./test", "cn");

        workspace.setDirectory(node("AGENT.md", "AGENT.md", "Updated desc"));

        Map<String, Object> agentNode = byName(workspace, "AGENT.md");
        assertNotNull(agentNode);
        assertEquals("Updated desc", agentNode.get("description"));
    }

    @Test
    @Tag("level0")
    @DisplayName("getDirectory accepts WorkspaceNode enum")
    void testWorkspaceGetDirectoryWithEnum() {
        Workspace workspace = new Workspace("./test", "cn");

        assertEquals("USER.md", workspace.getDirectory(WorkspaceNode.USER_MD));
        assertEquals("skills", workspace.getDirectory(WorkspaceNode.SKILLS));
        assertEquals("memory", workspace.getDirectory(WorkspaceNode.MEMORY));
        assertEquals("todo", workspace.getDirectory(WorkspaceNode.TODO));
        assertEquals("messages", workspace.getDirectory(WorkspaceNode.MESSAGES));
        assertEquals("agents", workspace.getDirectory(WorkspaceNode.AGENTS));
        assertEquals("AGENT.md", workspace.getDirectory(WorkspaceNode.AGENT_MD));
        assertEquals("SOUL.md", workspace.getDirectory(WorkspaceNode.SOUL_MD));
        assertEquals("HEARTBEAT.md", workspace.getDirectory(WorkspaceNode.HEARTBEAT_MD));
        assertEquals("IDENTITY.md", workspace.getDirectory(WorkspaceNode.IDENTITY_MD));
        assertEquals("MEMORY.md", workspace.getDirectory(WorkspaceNode.MEMORY_MD));
        assertEquals("daily_memory", workspace.getDirectory(WorkspaceNode.DAILY_MEMORY));
    }

    @Test
    @Tag("level0")
    @DisplayName("getDirectory still works with string names")
    void testWorkspaceGetDirectoryWithStringStillWorks() {
        Workspace workspace = new Workspace("./test", "cn");

        assertEquals("USER.md", workspace.getDirectory("USER.md"));
        assertEquals("skills", workspace.getDirectory("skills"));
        assertEquals("AGENT.md", workspace.getDirectory("AGENT.md"));
    }

    @Test
    @Tag("level0")
    @DisplayName("enum and string lookups are equivalent")
    void testWorkspaceGetDirectoryEnumAndStringEquivalent() {
        Workspace workspace = new Workspace("./test", "cn");

        assertEquals(workspace.getDirectory(WorkspaceNode.USER_MD), workspace.getDirectory("USER.md"));
        assertEquals(workspace.getDirectory(WorkspaceNode.SKILLS), workspace.getDirectory("skills"));
        assertEquals(workspace.getDirectory(WorkspaceNode.MEMORY), workspace.getDirectory("memory"));
        assertEquals(workspace.getDirectory(WorkspaceNode.AGENT_MD), workspace.getDirectory("AGENT.md"));
    }

    @Test
    @Tag("level0")
    @DisplayName("nonexistent directory lookup returns null")
    void testWorkspaceGetDirectoryNonexistentWithEnum() {
        Workspace workspace = new Workspace("./test", "cn");

        assertNotNull(workspace.getDirectory(WorkspaceNode.USER_MD));
        assertNull(workspace.getDirectory("nonexistent_dir"));
    }

    @Test
    @Tag("level0")
    @DisplayName("default workspace language is chinese")
    void testWorkspaceDefaultLanguageIsChinese() {
        Workspace workspace = new Workspace("./test", "cn");
        Map<String, Object> agentNode = byName(workspace, "AGENT.md");

        assertEquals("cn", workspace.getLanguage());
        assertNotNull(agentNode);
        assertTrue(String.valueOf(agentNode.get("description")).contains("基础"));
    }

    @Test
    @Tag("level0")
    @DisplayName("english schema uses english descriptions")
    void testWorkspaceEnglishSchema() {
        Workspace workspace = new Workspace("./test", "en");
        Map<String, Object> agentNode = byName(workspace, "AGENT.md");

        assertEquals("en", workspace.getLanguage());
        assertNotNull(agentNode);
        assertTrue(String.valueOf(agentNode.get("description")).contains("Basic"));
    }

    @Test
    @Tag("level0")
    @DisplayName("english default content is loaded")
    void testWorkspaceEnglishDefaultContent() {
        Workspace workspace = new Workspace("./test", "en");
        Map<String, Object> agentNode = byName(workspace, "AGENT.md");

        assertNotNull(agentNode);
        assertTrue(String.valueOf(agentNode.get("default_content")).contains("This folder is home"));
    }

    @Test
    @Tag("level0")
    @DisplayName("chinese default content is loaded")
    void testWorkspaceChineseDefaultContent() {
        Workspace workspace = new Workspace("./test", "cn");
        Map<String, Object> agentNode = byName(workspace, "AGENT.md");

        assertNotNull(agentNode);
        assertTrue(String.valueOf(agentNode.get("default_content")).contains("智能体"));
    }

    @Test
    @Tag("level0")
    @DisplayName("getWorkspaceSchema respects language")
    void testGetWorkspaceSchemaReturnsCorrectLanguage() {
        Map<String, Object> cnAgent = byName(Workspace.getWorkspaceSchema("cn"), "AGENT.md");
        Map<String, Object> enAgent = byName(Workspace.getWorkspaceSchema("en"), "AGENT.md");

        assertNotNull(cnAgent);
        assertNotNull(enAgent);
        assertNotEquals(cnAgent.get("description"), enAgent.get("description"));
        assertTrue(String.valueOf(cnAgent.get("description")).contains("基础"));
        assertTrue(String.valueOf(enAgent.get("description")).contains("Basic"));
    }

    @Test
    @Tag("level0")
    @DisplayName("getDefaultDirectory returns language specific copy")
    void testGetDefaultDirectoryWithLanguage() {
        Map<String, Object> cnAgent = byName(Workspace.getDefaultDirectory("cn"), "AGENT.md");
        Map<String, Object> enAgent = byName(Workspace.getDefaultDirectory("en"), "AGENT.md");

        assertNotNull(cnAgent);
        assertNotNull(enAgent);
        assertNotEquals(cnAgent.get("description"), enAgent.get("description"));
    }

    @Test
    @Tag("level0")
    @DisplayName("different language workspaces keep independent schema")
    void testWorkspaceInstanceIndependentSchemas() {
        Workspace workspaceCn = new Workspace("./test", "cn");
        Workspace workspaceEn = new Workspace("./test", "en");

        assertNotEquals(byName(workspaceCn, "AGENT.md").get("description"),
                byName(workspaceEn, "AGENT.md").get("description"));
    }

    @Test
    @Tag("level0")
    @DisplayName("directory builder creates marker files for directories")
    void testDirectoryBuilderCreatesDirectoriesWithMarkers() {
        DirectoryBuilder builder = new DirectoryBuilder(makeSysOperation(tempDir), tempDir.toString());
        builder.build(List.of(node("agent", "agent", "Agent dir"), node("user", "user", "User dir")));

        assertTrue(Files.exists(tempDir.resolve("agent").resolve(".workspace")));
        assertTrue(Files.exists(tempDir.resolve("user").resolve(".workspace")));
    }

    @Test
    @Tag("level0")
    @DisplayName("directory builder creates nested directory structures")
    void testDirectoryBuilderCreatesNestedDirectories() {
        DirectoryBuilder builder = new DirectoryBuilder(makeSysOperation(tempDir), tempDir.toString());
        builder.build(List.of(dirWithChildren("project", "project", "Project", List.of(
                node("src", "src", "Source"),
                node("tests", "tests", "Tests")))));

        assertTrue(Files.exists(tempDir.resolve("project").resolve(".workspace")));
        assertTrue(Files.exists(tempDir.resolve("project").resolve("src").resolve(".workspace")));
        assertTrue(Files.exists(tempDir.resolve("project").resolve("tests").resolve(".workspace")));
    }

    @Test
    @Tag("level0")
    @DisplayName("tracking helper records created directories")
    void testDirectoryBuilderReusesCachedDirectoriesAcrossBuilds() {
        TrackingDirectoryWalker tracker = new TrackingDirectoryWalker();
        tracker.build(List.of(node("agent", "agent", "Agent"), node("user", "user", "User")));

        assertEquals(2, tracker.createCalls.size());
        assertTrue(tracker.createCalls.contains("agent"));
        assertTrue(tracker.createCalls.contains("user"));
    }

    @Test
    @Tag("level0")
    @DisplayName("initWorkspace creates expected default structure")
    void testInitWorkspaceCreatesDirectories() throws Exception {
        DeepAgent agent = makeAgent(tempDir, new Workspace(tempDir.toString(), "cn"), true, true);

        agent.initWorkspace();

        assertTrue(Files.exists(tempDir.resolve("AGENT.md")));
        assertTrue(Files.exists(tempDir.resolve("SOUL.md")));
        assertTrue(Files.exists(tempDir.resolve("HEARTBEAT.md")));
        assertTrue(Files.exists(tempDir.resolve("IDENTITY.md")));
        assertTrue(Files.exists(tempDir.resolve("memory").resolve("MEMORY.md")));
        assertTrue(Files.exists(tempDir.resolve("memory").resolve("daily_memory").resolve(".workspace")));
    }

    @Test
    @Tag("level0")
    @DisplayName("initWorkspace honors custom directories")
    void testInitWorkspaceWithCustomDirectories() throws Exception {
        Workspace workspace = new Workspace(tempDir.toString(), new ArrayList<>(List.of(
                dirWithChildren("project", "project", "Project", List.of(
                        node("src", "src", "Source"))))), "cn");
        DeepAgent agent = makeAgent(tempDir, workspace, true, true);

        agent.initWorkspace();

        assertTrue(Files.exists(tempDir.resolve("project").resolve(".workspace")));
        assertTrue(Files.exists(tempDir.resolve("project").resolve("src").resolve(".workspace")));
    }

    @Test
    @Tag("level0")
    @DisplayName("full workspace flow creates custom nested structure")
    void testFullWorkspaceFlowCreateOnly() throws Exception {
        Workspace workspace = new Workspace(tempDir.toString(), new ArrayList<>(List.of(
                dirWithChildren("myapp", "myapp", "My application", List.of(
                        node("backend", "backend", "Backend"),
                        node("frontend", "frontend", "Frontend"))))), "cn");
        DeepAgent agent = makeAgent(tempDir, workspace, true, true);

        agent.initWorkspace();

        assertTrue(Files.exists(tempDir.resolve("myapp").resolve(".workspace")));
        assertTrue(Files.exists(tempDir.resolve("myapp").resolve("backend").resolve(".workspace")));
        assertTrue(Files.exists(tempDir.resolve("myapp").resolve("frontend").resolve(".workspace")));
    }

    @Test
    @Tag("level0")
    @DisplayName("ensureInitialized skips repeated workspace init")
    void testEnsureInitializedSkipsWhenAlreadyInitialized() throws Exception {
        DeepAgent agent = makeAgent(tempDir, new Workspace(tempDir.toString(), "cn"), true, true);

        agent.ensureInitialized();
        long firstModified = Files.getLastModifiedTime(tempDir.resolve("memory").resolve(".workspace")).toMillis();
        agent.ensureInitialized();
        long secondModified = Files.getLastModifiedTime(tempDir.resolve("memory").resolve(".workspace")).toMillis();

        assertEquals(firstModified, secondModified);
    }

    @Test
    @Tag("level0")
    @DisplayName("ensureInitialized skips when disabled")
    void testEnsureInitializedSkipsWhenDisabled() {
        DeepAgent agent = makeAgent(tempDir, new Workspace(tempDir.toString(), "cn"), false, true);

        agent.ensureInitialized();

        assertFalse(Files.exists(tempDir.resolve("memory")));
    }

    @Test
    @Tag("level0")
    @DisplayName("ensureInitialized skips without sys operation")
    void testEnsureInitializedSkipsWithoutSysOperation() {
        DeepAgent agent = makeAgent(tempDir, new Workspace(tempDir.toString(), "cn"), true, false);

        agent.ensureInitialized();

        assertFalse(Files.exists(tempDir.resolve(".workspace")));
    }

    @Test
    @Tag("level0")
    @DisplayName("workspace root is exactly the provided root path")
    void testWorkspaceAgentIdNaming() throws Exception {
        DeepAgent agent = makeAgent(tempDir, new Workspace(tempDir.toString(), "cn"), true, true);

        agent.ensureInitialized();

        assertTrue(Files.exists(tempDir));
        assertTrue(Files.exists(tempDir.resolve("memory").resolve(".workspace")));
    }

    @Test
    @Tag("level0")
    @DisplayName("autoCreateWorkspace defaults to true")
    void testConfigDefaultAutoCreateWorkspace() {
        assertTrue(new DeepAgentConfig().getAutoCreateWorkspace());
    }

    @Test
    @Tag("level0")
    @DisplayName("initWorkspace writes default content into markdown files")
    void testInitWorkspaceWritesDefaultContentToMdFiles() throws Exception {
        DeepAgent agent = makeAgent(tempDir, new Workspace(tempDir.toString(), "cn"), true, true);

        agent.initWorkspace();

        String content = Files.readString(tempDir.resolve("AGENT.md"));
        assertFalse(content.isBlank());
        assertTrue(content.contains("智能体"));
    }

    @Test
    @Tag("level0")
    @DisplayName("directory builder writes provided default content")
    void testDirectoryBuilderWithDefaultContent() throws Exception {
        DirectoryBuilder builder = new DirectoryBuilder(makeSysOperation(tempDir), tempDir.toString());
        builder.build(List.of(Map.of(
                "name", "test",
                "path", "test.md",
                "is_file", true,
                "default_content", "# Test\nHello World",
                "children", new ArrayList<Map<String, Object>>())));

        assertEquals("# Test\nHello World", Files.readString(tempDir.resolve("test.md")));
    }

    @Test
    @Tag("level0")
    @DisplayName("directory builder creates empty file without default content")
    void testDirectoryBuilderWithoutDefaultContentCreatesEmptyFile() throws Exception {
        DirectoryBuilder builder = new DirectoryBuilder(makeSysOperation(tempDir), tempDir.toString());
        builder.build(List.of(Map.of(
                "name", "empty",
                "path", "empty.md",
                "is_file", true,
                "children", new ArrayList<Map<String, Object>>())));

        assertTrue(Files.exists(tempDir.resolve("empty.md")));
        assertEquals("", Files.readString(tempDir.resolve("empty.md")));
    }

    @Test
    @Tag("level0")
    @DisplayName("english workspace writes english SOUL content")
    void testInitWorkspaceEnglishSoulMdHasEnglishContent() throws Exception {
        DeepAgent agent = makeAgent(tempDir, new Workspace(tempDir.toString(), "en"), true, true);

        agent.initWorkspace();

        String content = Files.readString(tempDir.resolve("SOUL.md"));
        assertTrue(content.contains("SOUL"));
        assertTrue(content.contains("genuinely helpful") || content.contains("Have opinions"));
    }

    @Test
    @Tag("level0")
    @DisplayName("workspace creates files rather than directories for markdown nodes")
    void testWorkspaceCreatesFilesNotDirectories() throws Exception {
        DeepAgent agent = makeAgent(tempDir, new Workspace(tempDir.toString(), "cn"), true, true);

        agent.initWorkspace();

        assertTrue(Files.isRegularFile(tempDir.resolve("AGENT.md")));
        assertTrue(Files.isRegularFile(tempDir.resolve("SOUL.md")));
        assertTrue(Files.isRegularFile(tempDir.resolve("HEARTBEAT.md")));
        assertTrue(Files.isRegularFile(tempDir.resolve("IDENTITY.md")));
        assertTrue(Files.isRegularFile(tempDir.resolve("memory").resolve("MEMORY.md")));
        assertFalse(Files.exists(tempDir.resolve("AGENT.md").resolve(".workspace")));
    }

    @Test
    @Tag("level0")
    @DisplayName("memory directory has expected nested structure")
    void testWorkspaceMemorySubdirectoryStructure() throws Exception {
        DeepAgent agent = makeAgent(tempDir, new Workspace(tempDir.toString(), "cn"), true, true);

        agent.initWorkspace();

        assertTrue(Files.exists(tempDir.resolve("memory")));
        assertTrue(Files.exists(tempDir.resolve("memory").resolve(".workspace")));
        assertTrue(Files.isRegularFile(tempDir.resolve("memory").resolve("MEMORY.md")));
        assertTrue(Files.exists(tempDir.resolve("memory").resolve("daily_memory").resolve(".workspace")));
    }

    @Test
    @Tag("level0")
    @DisplayName("todo messages skills and agents use isolated directory structure")
    void testWorkspaceTodoSessionIsolatedStructure() throws Exception {
        DeepAgent agent = makeAgent(tempDir, new Workspace(tempDir.toString(), "cn"), true, true);

        agent.initWorkspace();

        assertTrue(Files.exists(tempDir.resolve("todo").resolve(".workspace")));
        assertTrue(Files.exists(tempDir.resolve("messages").resolve(".workspace")));
        assertTrue(Files.exists(tempDir.resolve("skills").resolve(".workspace")));
        assertTrue(Files.exists(tempDir.resolve("agents").resolve(".workspace")));
    }

    @Test
    @Tag("level0")
    @DisplayName("invoke path can trigger workspace initialization")
    void testDeepAgentInvokeTriggersWorkspaceInit() {
        AgentCard card = AgentCard.builder().name("test").description("test").build();
        DeepAgent agent = new DeepAgent(card) {
            @Override
            public Object invoke(Object inputs, Session session) {
                ensureInitialized();
                return Map.of("output", "ok");
            }
        };
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(card);
        config.setWorkspace(new Workspace(tempDir.toString(), "cn"));
        config.setSysOperation(makeSysOperation(tempDir));
        config.setAutoCreateWorkspace(true);
        agent.configure(config);

        agent.invoke("test query", new AgentTeamSession("s1", "test"));

        assertTrue(Files.exists(tempDir.resolve("memory").resolve(".workspace")));
        assertTrue(Files.exists(tempDir.resolve("todo").resolve(".workspace")));
        assertTrue(Files.exists(tempDir.resolve("messages").resolve(".workspace")));
        assertTrue(Files.exists(tempDir.resolve("skills").resolve(".workspace")));
        assertTrue(Files.exists(tempDir.resolve("agents").resolve(".workspace")));
        assertTrue(Files.exists(tempDir.resolve("AGENT.md")));
        assertTrue(Files.exists(tempDir.resolve("SOUL.md")));
        assertTrue(Files.exists(tempDir.resolve("HEARTBEAT.md")));
        assertTrue(Files.exists(tempDir.resolve("IDENTITY.md")));
        assertTrue(Files.exists(tempDir.resolve("USER.md")));
    }

    @Test
    @Tag("level0")
    @DisplayName("getNodePath resolves top level string names")
    void testGetNodePathWithStringName() {
        Workspace workspace = new Workspace("/workspace", "cn");

        assertEquals(Path.of("/workspace/memory"), workspace.getNodePath("memory"));
        assertEquals(Path.of("/workspace/todo"), workspace.getNodePath("todo"));
        assertEquals(Path.of("/workspace/skills"), workspace.getNodePath("skills"));
        assertEquals(Path.of("/workspace/AGENT.md"), workspace.getNodePath("AGENT.md"));
        assertEquals(Path.of("/workspace/SOUL.md"), workspace.getNodePath("SOUL.md"));
    }

    @Test
    @Tag("level0")
    @DisplayName("getNodePath resolves enum values")
    void testGetNodePathWithWorkspaceNodeEnum() {
        Workspace workspace = new Workspace("/workspace", "cn");

        assertEquals(Path.of("/workspace/memory"), workspace.getNodePath(WorkspaceNode.MEMORY));
        assertEquals(Path.of("/workspace/todo"), workspace.getNodePath(WorkspaceNode.TODO));
        assertEquals(Path.of("/workspace/skills"), workspace.getNodePath(WorkspaceNode.SKILLS));
        assertEquals(Path.of("/workspace/AGENT.md"), workspace.getNodePath(WorkspaceNode.AGENT_MD));
    }

    @Test
    @Tag("level0")
    @DisplayName("getNodePath returns null for nested nodes")
    void testGetNodePathReturnsNoneForNestedNodes() {
        Workspace workspace = new Workspace("/workspace", "cn");

        assertNull(workspace.getNodePath("MEMORY.md"));
        assertNull(workspace.getNodePath("daily_memory"));
    }

    @Test
    @Tag("level0")
    @DisplayName("getNodePath returns null for unknown node")
    void testGetNodePathReturnsNoneForUnknownNode() {
        Workspace workspace = new Workspace("/workspace", "cn");

        assertNull(workspace.getNodePath("unknown_directory"));
    }

    @Test
    @Tag("level0")
    @DisplayName("getNodePath works after DeepAgent configure")
    void testGetNodePathAfterDeepAgentConfigure() {
        Workspace workspace = new Workspace(tempDir.toString(), "cn");
        DeepAgent agent = makeAgent(tempDir, workspace, true, true);

        assertEquals(tempDir.resolve("memory"), agent.getDeepConfig().getWorkspace().getNodePath("memory"));
        assertEquals(tempDir.resolve("AGENT.md"), agent.getDeepConfig().getWorkspace().getNodePath("AGENT.md"));
    }

    private DeepAgent makeAgent(Path root, Workspace workspace, boolean autoCreateWorkspace, boolean withSysOperation) {
        AgentCard card = AgentCard.builder().name("test").description("test").build();
        DeepAgent agent = new DeepAgent(card);
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(card);
        config.setWorkspace(workspace);
        config.setAutoCreateWorkspace(autoCreateWorkspace);
        if (withSysOperation) {
            config.setSysOperation(makeSysOperation(root));
        }
        agent.configure(config);
        return agent;
    }

    private SysOperation makeSysOperation(Path root) {
        SysOperationCard card = SysOperationCard.builder()
                .id("workspace-test")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(root.toString()).build())
                .build();
        return new SysOperation(card);
    }

    private static Map<String, Object> node(String name, String path, String description) {
        return Map.of(
                "name", name,
                "path", path,
                "description", description,
                "children", new ArrayList<Map<String, Object>>());
    }

    private static Map<String, Object> dirWithChildren(String name, String path, String description,
                                                       List<Map<String, Object>> children) {
        return Map.of(
                "name", name,
                "path", path,
                "description", description,
                "children", new ArrayList<>(children));
    }

    private static Map<String, Object> byName(Workspace workspace, String name) {
        return byName(workspace.getDirectories(), name);
    }

    private static Map<String, Object> byName(List<Map<String, Object>> nodes, String name) {
        for (Map<String, Object> node : nodes) {
            if (name.equals(node.get("name"))) {
                return node;
            }
        }
        return null;
    }

    static final class TrackingDirectoryWalker {
        private final List<String> createCalls = new ArrayList<>();

        void build(List<Map<String, Object>> directories) {
            for (Map<String, Object> node : directories) {
                walk(node, "");
            }
        }

        @SuppressWarnings("unchecked")
        private void walk(Map<String, Object> node, String parentPath) {
            String relativePath = String.valueOf(node.get("path"));
            String fullPath = parentPath.isEmpty() ? relativePath : parentPath + "/" + relativePath;
            createCalls.add(fullPath);
            for (Map<String, Object> child : (List<Map<String, Object>>) node.getOrDefault("children", List.of())) {
                walk(child, fullPath);
            }
        }
    }
}
