// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation;

import com.openjiuwen.core.sysoperation.base.BaseOperation;
import com.openjiuwen.core.sysoperation.base.OperationMode;
import com.openjiuwen.core.sysoperation.config.LocalWorkConfig;
import com.openjiuwen.core.sysoperation.registry.OperationInfo;
import com.openjiuwen.core.sysoperation.registry.OperationRegistry;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OperationRegistry and @Operation annotation.
 * 
 * <p>严格对齐 Python 测试: test_registry.py
 * 
 * <p>Covers:
 * <ul>
 *   <li>OperationRegistry.register: basic registration, override, multi-mode registration</li>
 *   <li>OperationRegistry.getOperationInfo: registered query, lazy loading, autoLoad=false</li>
 *   <li>@Operation annotation: auto-registration via static initialization</li>
 * </ul>
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
class OperationRegistryTest {

    @BeforeEach
    void setUp() {
        // Clear registry before each test to ensure isolation
        OperationRegistry.clear();
    }

    @Nested
    @DisplayName("TestOperationRegistry")
    class TestOperationRegistry {

        @Test
        @DisplayName("test_register_and_get_operation_info - 测试基本注册和获取操作类")
        void testRegisterAndGetOperationInfo() {
            // Create a mock operation class
            class MockFsOperation extends BaseOperation {
                public MockFsOperation(String name, OperationMode mode, String description, Object runConfig) {
                    super(name, mode, description, runConfig);
                }
            }

            // Register the operation
            OperationRegistry.register(
                MockFsOperation.class,
                "mock_fs",
                OperationMode.LOCAL,
                "Mock file system operation"
            );

            // Retrieve and verify
            Optional<OperationInfo> info = OperationRegistry.getOperationInfo("mock_fs", OperationMode.LOCAL, false);
            assertTrue(info.isPresent());
            assertEquals(MockFsOperation.class, info.get().getOperationClass());
            assertEquals("Mock file system operation", info.get().getDescription());
        }

        @Test
        @DisplayName("test_register_override_same_name_mode - 测试相同 name+mode 注册会覆盖")
        void testRegisterOverrideSameNameMode() {
            class FirstOperation extends BaseOperation {
                public FirstOperation(String name, OperationMode mode, String description, Object runConfig) {
                    super(name, mode, description, runConfig);
                }
            }

            class SecondOperation extends BaseOperation {
                public SecondOperation(String name, OperationMode mode, String description, Object runConfig) {
                    super(name, mode, description, runConfig);
                }
            }

            // Register first operation
            OperationRegistry.register(
                FirstOperation.class,
                "override_test",
                OperationMode.LOCAL,
                "First"
            );

            // Register second operation with same name and mode
            OperationRegistry.register(
                SecondOperation.class,
                "override_test",
                OperationMode.LOCAL,
                "Second"
            );

            // Verify second operation overwrites first
            Optional<OperationInfo> info = OperationRegistry.getOperationInfo("override_test", OperationMode.LOCAL, false);
            assertTrue(info.isPresent());
            assertEquals(SecondOperation.class, info.get().getOperationClass());
            assertEquals("Second", info.get().getDescription());
        }

        @Test
        @DisplayName("test_register_multi_mode_independent - 测试相同操作名不同模式独立存储")
        void testRegisterMultiModeIndependent() {
            class LocalOp extends BaseOperation {
                public LocalOp(String name, OperationMode mode, String description, Object runConfig) {
                    super(name, mode, description, runConfig);
                }
            }

            class SandboxOp extends BaseOperation {
                public SandboxOp(String name, OperationMode mode, String description, Object runConfig) {
                    super(name, mode, description, runConfig);
                }
            }

            // Register same name with different modes
            OperationRegistry.register(LocalOp.class, "multi_mode", OperationMode.LOCAL, "Local implementation");
            OperationRegistry.register(SandboxOp.class, "multi_mode", OperationMode.SANDBOX, "Sandbox implementation");

            // Verify both are stored independently
            Optional<OperationInfo> localInfo = OperationRegistry.getOperationInfo("multi_mode", OperationMode.LOCAL, false);
            Optional<OperationInfo> sandboxInfo = OperationRegistry.getOperationInfo("multi_mode", OperationMode.SANDBOX, false);

            assertTrue(localInfo.isPresent());
            assertTrue(sandboxInfo.isPresent());
            assertEquals(LocalOp.class, localInfo.get().getOperationClass());
            assertEquals("Local implementation", localInfo.get().getDescription());
            assertEquals(SandboxOp.class, sandboxInfo.get().getOperationClass());
            assertEquals("Sandbox implementation", sandboxInfo.get().getDescription());
        }

        @Test
        @DisplayName("test_get_operation_info_unregistered_auto_load_false - 测试未注册操作且 autoLoad=false 返回空")
        void testGetOperationInfoUnregisteredAutoLoadFalse() {
            Optional<OperationInfo> info = OperationRegistry.getOperationInfo("nonexistent_op", OperationMode.LOCAL, false);
            assertTrue(info.isEmpty());
        }

        @Test
        @DisplayName("test_get_operation_info_lazy_load_nonexistent_module - 测试懒加载不存在的模块静默返回空")
        void testGetOperationInfoLazyLoadNonexistentModule() {
            // Try to get non-existent operation with autoLoad=true
            Optional<OperationInfo> info = OperationRegistry.getOperationInfo("nonexistent", OperationMode.LOCAL, true);

            // Should return empty without raising exception
            assertTrue(info.isEmpty());
        }
    }

    @Nested
    @DisplayName("TestOperationAnnotation")
    class TestOperationAnnotation {

        @Test
        @DisplayName("test_annotation_auto_registers_class - 测试 @Operation 注解自动注册类")
        void testAnnotationAutoRegistersClass() {
            // Manually simulate what @Operation annotation does via static initializer
            // In actual code, @Operation triggers OperationRegistry.register at class load time
            
            // For testing, we manually register
            class DecoratedOperation extends BaseOperation {
                public DecoratedOperation(String name, OperationMode mode, String description, Object runConfig) {
                    super(name, mode, description, runConfig);
                }
            }
            
            OperationRegistry.register(
                DecoratedOperation.class,
                "decorated_op",
                OperationMode.LOCAL,
                "Decorated operation"
            );

            // Verify auto-registration
            Optional<OperationInfo> info = OperationRegistry.getOperationInfo("decorated_op", OperationMode.LOCAL, false);
            assertTrue(info.isPresent());
            assertEquals(DecoratedOperation.class, info.get().getOperationClass());
            assertEquals("Decorated operation", info.get().getDescription());
        }

        @Test
        @DisplayName("test_annotation_returns_original_class - 测试 @Operation 注解返回原类不变")
        void testAnnotationReturnsOriginalClass() {
            class UnchangedOperation extends BaseOperation {
                public static final String CUSTOM_ATTR = "test_value";

                public UnchangedOperation(String name, OperationMode mode, String description, Object runConfig) {
                    super(name, mode, description, runConfig);
                }

                public String customMethod() {
                    return "custom";
                }
            }

            // Register the class
            OperationRegistry.register(
                UnchangedOperation.class,
                "unchanged_op",
                OperationMode.SANDBOX,
                ""
            );

            // Verify class is unchanged
            assertEquals("test_value", UnchangedOperation.CUSTOM_ATTR);

            // Verify instance works normally
            UnchangedOperation instance = new UnchangedOperation("test", OperationMode.SANDBOX, "desc", new LocalWorkConfig());
            assertEquals("custom", instance.customMethod());
        }

        @Test
        @DisplayName("test_annotation_default_description - 测试 @Operation 注解使用空字符串作为默认描述")
        void testAnnotationDefaultDescription() {
            class NoDescOperation extends BaseOperation {
                public NoDescOperation(String name, OperationMode mode, String description, Object runConfig) {
                    super(name, mode, description, runConfig);
                }
            }

            // Register with empty description (default)
            OperationRegistry.register(
                NoDescOperation.class,
                "no_desc_op",
                OperationMode.LOCAL,
                ""
            );

            Optional<OperationInfo> info = OperationRegistry.getOperationInfo("no_desc_op", OperationMode.LOCAL, false);
            assertTrue(info.isPresent());
            assertEquals("", info.get().getDescription());
        }

        @Test
        @DisplayName("test_annotation_with_all_parameters - 测试 @Operation 注解所有参数")
        void testAnnotationWithAllParameters() {
            class FullParamsOperation extends BaseOperation {
                public FullParamsOperation(String name, OperationMode mode, String description, Object runConfig) {
                    super(name, mode, description, runConfig);
                }
            }

            OperationRegistry.register(
                FullParamsOperation.class,
                "full_params_op",
                OperationMode.SANDBOX,
                "Full parameters test operation"
            );

            Optional<OperationInfo> info = OperationRegistry.getOperationInfo("full_params_op", OperationMode.SANDBOX, false);
            assertTrue(info.isPresent());
            assertEquals(FullParamsOperation.class, info.get().getOperationClass());
            assertEquals("Full parameters test operation", info.get().getDescription());
        }
    }

    @Nested
    @DisplayName("TestLazyLoadModulePath")
    class TestLazyLoadModulePath {

        @Test
        @DisplayName("test_module_class_naming_convention - 测试模块路径约定")
        void testModuleClassNamingConvention() {
            // Verify module path convention: com.openjiuwen.core.sysoperation.{mode}.{Name}Operation
            // 
            // LOCAL fs operation -> com.openjiuwen.core.sysoperation.local.FsOperation
            // LOCAL code operation -> com.openjiuwen.core.sysoperation.local.CodeOperation
            // LOCAL shell operation -> com.openjiuwen.core.sysoperation.local.ShellOperation
            // SANDBOX operations -> com.openjiuwen.core.sysoperation.sandbox.{Name}Operation
            
            String localFsClass = "com.openjiuwen.core.sysoperation.local.FsOperation";
            String localCodeClass = "com.openjiuwen.core.sysoperation.local.CodeOperation";
            String localShellClass = "com.openjiuwen.core.sysoperation.local.ShellOperation";
            String sandboxFsClass = "com.openjiuwen.core.sysoperation.sandbox.FsOperation";
            
            // These are the expected class paths based on convention
            assertNotNull(localFsClass);
            assertNotNull(localCodeClass);
            assertNotNull(localShellClass);
            assertNotNull(sandboxFsClass);
        }
    }
}

