// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation;

import com.openjiuwen.core.sysoperation.base.BaseOperation;
import com.openjiuwen.core.sysoperation.base.OperationMode;
import com.openjiuwen.core.sysoperation.config.LocalWorkConfig;
import com.openjiuwen.core.sysoperation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysoperation.registry.OperationRegistry;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SysOperation and configuration validation.
 * 
 * <p>严格对齐 Python 测试: test_sys_operation.py
 * 
 * <p>Covers:
 * <ul>
 *   <li>SysOperation: LOCAL/SANDBOX mode initialization, operation instance retrieval and caching</li>
 *   <li>SysOperation._getOperation: instance caching, edge cases</li>
 *   <li>LocalWorkConfig: default shell allowlist validation</li>
 * </ul>
 * 
 * <p>注意: Python 测试依赖 Runner.resource_mgr，该模块尚未转换。
 * 此测试直接实例化 SysOperation 进行测试，与 Python 行为等价。
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
class SysOperationTest {

    private Path tempWorkDir;

    @BeforeEach
    void setUp() throws IOException {
        // Clear registry and create temp directory
        OperationRegistry.clear();
        tempWorkDir = Files.createTempDirectory("sys_operation_test");
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up temp directory
        if (tempWorkDir != null && Files.exists(tempWorkDir)) {
            Files.walk(tempWorkDir)
                .sorted((a, b) -> -a.compareTo(b))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
        }
    }

    @Nested
    @DisplayName("TestLocalWorkConfigDefaults")
    class TestLocalWorkConfigDefaults {

        @Test
        @DisplayName("test_default_shell_allowlist_contains_safe_commands - 测试 shell_allowlist 有默认安全命令")
        void testDefaultShellAllowlistContainsSafeCommands() {
            LocalWorkConfig config = new LocalWorkConfig();

            // Verify common safe commands are in default allowlist
            String[] expectedCommands = {"echo", "ls", "cd", "pwd", "python", "pip", "npm", "node", "git"};
            for (String cmd : expectedCommands) {
                assertTrue(config.getShellAllowlist().contains(cmd),
                    cmd + " should be in default allowlist");
            }
        }
    }

    @Nested
    @DisplayName("TestSysOperation")
    class TestSysOperation {

        @Test
        @DisplayName("test_local_mode_initialization - 测试 SysOperation 在 LOCAL 模式正确初始化")
        void testLocalModeInitialization() {
            SysOperationCard card = SysOperationCard.builder()
                .id("test_local_sys_op")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder()
                    .workDir(tempWorkDir.toString())
                    .build())
                .build();

            SysOperation sysOp = new SysOperation(card);

            assertEquals(OperationMode.LOCAL, sysOp.getMode());
            assertTrue(sysOp.getRunConfig() instanceof LocalWorkConfig);
        }

        @Test
        @DisplayName("test_sandbox_mode_initialization - 测试 SysOperation 在 SANDBOX 模式正确初始化")
        void testSandboxModeInitialization() {
            SysOperationCard card = SysOperationCard.builder()
                .id("test_sandbox_sys_op")
                .mode(OperationMode.SANDBOX)
                .gatewayConfig(SandboxGatewayConfig.builder()
                    .gatewayUrl("https://test.sandbox.com")
                    .build())
                .build();

            SysOperation sysOp = new SysOperation(card);

            assertEquals(OperationMode.SANDBOX, sysOp.getMode());
            assertTrue(sysOp.getRunConfig() instanceof SandboxGatewayConfig);
        }

        @Test
        @DisplayName("test_fs_operation_retrieval - 测试 fs() 方法返回文件系统操作实例")
        void testFsOperationRetrieval() {
            // Register a mock fs operation for testing
            registerMockOperation("fs", OperationMode.LOCAL, "File system operation");

            SysOperationCard card = SysOperationCard.builder()
                .id("test_fs_op")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(tempWorkDir.toString()).build())
                .build();

            SysOperation sysOp = new SysOperation(card);
            BaseOperation fsOp = sysOp.fs();

            assertNotNull(fsOp);
            assertTrue(fsOp instanceof BaseOperation);
            assertEquals("fs", fsOp.getName());
            assertEquals(OperationMode.LOCAL, fsOp.getMode());
        }

        @Test
        @DisplayName("test_code_operation_retrieval - 测试 code() 方法返回代码执行操作实例")
        void testCodeOperationRetrieval() {
            // Register a mock code operation for testing
            registerMockOperation("code", OperationMode.LOCAL, "Code execution operation");

            SysOperationCard card = SysOperationCard.builder()
                .id("test_code_op")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(tempWorkDir.toString()).build())
                .build();

            SysOperation sysOp = new SysOperation(card);
            BaseOperation codeOp = sysOp.code();

            assertNotNull(codeOp);
            assertTrue(codeOp instanceof BaseOperation);
            assertEquals("code", codeOp.getName());
        }

        @Test
        @DisplayName("test_shell_operation_retrieval - 测试 shell() 方法返回 shell 命令操作实例")
        void testShellOperationRetrieval() {
            // Register a mock shell operation for testing
            registerMockOperation("shell", OperationMode.LOCAL, "Shell command operation");

            SysOperationCard card = SysOperationCard.builder()
                .id("test_shell_op")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(tempWorkDir.toString()).build())
                .build();

            SysOperation sysOp = new SysOperation(card);
            BaseOperation shellOp = sysOp.shell();

            assertNotNull(shellOp);
            assertTrue(shellOp instanceof BaseOperation);
            assertEquals("shell", shellOp.getName());
        }

        @Test
        @DisplayName("test_operation_instance_caching - 测试操作实例被缓存并复用")
        void testOperationInstanceCaching() {
            // Register mock operations
            registerMockOperation("fs", OperationMode.LOCAL, "File system operation");
            registerMockOperation("code", OperationMode.LOCAL, "Code operation");

            SysOperationCard card = SysOperationCard.builder()
                .id("test_cache")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(tempWorkDir.toString()).build())
                .build();

            SysOperation sysOp = new SysOperation(card);

            BaseOperation fsOp1 = sysOp.fs();
            BaseOperation fsOp2 = sysOp.fs();

            // Should be the same instance (cached)
            assertSame(fsOp1, fsOp2);

            // Same for other operations
            BaseOperation codeOp1 = sysOp.code();
            BaseOperation codeOp2 = sysOp.code();
            assertSame(codeOp1, codeOp2);
        }

        @Test
        @DisplayName("test_nonexistent_operation_returns_null - 测试访问不存在的操作返回 null")
        void testNonexistentOperationReturnsNull() {
            SysOperationCard card = SysOperationCard.builder()
                .id("test_nonexistent")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(tempWorkDir.toString()).build())
                .build();

            SysOperation sysOp = new SysOperation(card);
            BaseOperation result = sysOp.getOperation("nonexistent_operation");

            assertNull(result);
        }

        @Test
        @DisplayName("test_default_work_config_when_none - 测试 work_config 为 null 时使用默认 LocalWorkConfig")
        void testDefaultWorkConfigWhenNone() {
            SysOperationCard card = SysOperationCard.builder()
                .id("default_config_test")
                .mode(OperationMode.LOCAL)
                // workConfig is null
                .build();

            SysOperation sysOp = new SysOperation(card);

            // Should use default LocalWorkConfig
            assertTrue(sysOp.getRunConfig() instanceof LocalWorkConfig);
            LocalWorkConfig config = (LocalWorkConfig) sysOp.getRunConfig();
            // Default shell_allowlist should be populated
            assertNotNull(config.getShellAllowlist());
            assertFalse(config.getShellAllowlist().isEmpty());
        }

        @Test
        @DisplayName("test_config_passed_to_operation_instance - 测试 run_config 正确传递给操作实例")
        void testConfigPassedToOperationInstance() {
            // Register mock operation
            registerMockOperation("fs", OperationMode.LOCAL, "File system operation");

            LocalWorkConfig workConfig = LocalWorkConfig.builder()
                .workDir(tempWorkDir.toString())
                .build();

            SysOperationCard card = SysOperationCard.builder()
                .id("test_config_pass")
                .mode(OperationMode.LOCAL)
                .workConfig(workConfig)
                .build();

            SysOperation sysOp = new SysOperation(card);
            BaseOperation fsOp = sysOp.fs();

            // Verify the operation has the correct run_config
            assertNotNull(fsOp.getRunConfig());
            assertTrue(fsOp.getRunConfig() instanceof LocalWorkConfig);
            assertEquals(tempWorkDir.toString(), ((LocalWorkConfig) fsOp.getRunConfig()).getWorkDir());
        }

        @Test
        @DisplayName("test_multiple_operations_cached_independently - 测试不同操作独立缓存")
        void testMultipleOperationsCachedIndependently() {
            // Register mock operations
            registerMockOperation("fs", OperationMode.LOCAL, "File system operation");
            registerMockOperation("code", OperationMode.LOCAL, "Code operation");
            registerMockOperation("shell", OperationMode.LOCAL, "Shell operation");

            SysOperationCard card = SysOperationCard.builder()
                .id("test_independent_cache")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(tempWorkDir.toString()).build())
                .build();

            SysOperation sysOp = new SysOperation(card);

            BaseOperation fsOp = sysOp.fs();
            BaseOperation codeOp = sysOp.code();
            BaseOperation shellOp = sysOp.shell();

            // All should be different instances
            assertNotSame(fsOp, codeOp);
            assertNotSame(codeOp, shellOp);
            assertNotSame(fsOp, shellOp);

            // All should be cached
            assertTrue(sysOp.hasInstance("fs"));
            assertTrue(sysOp.hasInstance("code"));
            assertTrue(sysOp.hasInstance("shell"));
        }
    }

    @Nested
    @DisplayName("TestSysOperationGetOperationEdgeCases")
    class TestSysOperationGetOperationEdgeCases {

        @Test
        @DisplayName("test_get_operation_with_empty_operation_info - 测试 _getOperation 处理空 dict 从注册表")
        void testGetOperationWithEmptyOperationInfo() {
            // Don't register any operation - registry will return empty

            SysOperationCard card = SysOperationCard.builder()
                .id("empty_info_test")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(tempWorkDir.toString()).build())
                .build();

            SysOperation sysOp = new SysOperation(card);

            // Should return null for non-registered operation
            BaseOperation result = sysOp.getOperation("empty_test");
            assertNull(result);
        }

        @Test
        @DisplayName("test_cached_instance_returned_without_registry_call - 测试缓存实例返回无需再次调用注册表")
        void testCachedInstanceReturnedWithoutRegistryCall() {
            // Register mock operation
            registerMockOperation("fs", OperationMode.LOCAL, "File system operation");

            SysOperationCard card = SysOperationCard.builder()
                .id("cache_efficiency_test")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(tempWorkDir.toString()).build())
                .build();

            SysOperation sysOp = new SysOperation(card);

            // First call - should hit registry
            BaseOperation fsOp1 = sysOp.fs();
            assertNotNull(fsOp1);
            assertTrue(sysOp.hasInstance("fs"));

            // Clear registry
            OperationRegistry.clear();

            // Second call - should use cache, not registry (which is now empty)
            BaseOperation fsOp2 = sysOp.fs();
            assertSame(fsOp1, fsOp2);
        }
    }

    // Helper method to register mock operations for testing
    private void registerMockOperation(String name, OperationMode mode, String description) {
        OperationRegistry.register(
            MockOperation.class,
            name,
            mode,
            description
        );
    }

    /**
     * Mock operation class for testing purposes.
     * This simulates actual operation classes without implementing real functionality.
     */
    public static class MockOperation extends BaseOperation {
        public MockOperation(String name, OperationMode mode, String description, Object runConfig) {
            super(name, mode, description, runConfig);
        }
    }
}

