/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation;

import com.openjiuwen.core.sys_operation.config.ContainerScope;
import com.openjiuwen.core.sys_operation.config.LocalWorkConfig;
import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused tests for root sys_operation package exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.sys_operation} in
 * {@code openjiuwen/core/sys_operation/__init__.py}.</p>
 */
class SysOperationPackageTest {

    @Test
    void exportedSymbolsMatchPythonAllOrderIncludingShellKillHelper() {
        assertThat(SysOperationPackage.PYTHON_MODULE).isEqualTo("openjiuwen/core/sys_operation/__init__.py");
        assertThat(SysOperationPackage.EXPORTED_SYMBOLS).containsExactlyElementsOf(List.of(
                "OperationMode",
                "LocalWorkConfig",
                "SandboxGatewayConfig",
                "SysOperationCard",
                "SysOperation",
                "generate_isolation_key_template",
                "kill_shell_processes_for_session"
        ));
    }

    @Test
    void exportedTypesExposeConcreteJavaReferences() {
        assertThat(SysOperationPackage.EXPORTED_TYPES)
                .containsEntry("OperationMode", OperationMode.class)
                .containsEntry("LocalWorkConfig", LocalWorkConfig.class)
                .containsEntry("SandboxGatewayConfig", SandboxGatewayConfig.class)
                .containsEntry("SysOperationCard", SysOperationCard.class)
                .containsEntry("SysOperation", SysOperation.class);
    }

    @Test
    void generateIsolationKeyTemplateDelegatesToSysOperationHelper() {
        assertThat(SysOperationPackage.generateIsolationKeyTemplate(
                "prefix",
                ContainerScope.SESSION,
                null,
                "pre_deploy",
                "aio"
        )).isEqualTo("session_pre_deploy_aio_prefix_{session_id}");
    }

    @Test
    void killShellProcessesForSessionDelegatesToRegistry() {
        int killed = SysOperationPackage.killShellProcessesForSession("package_session");

        assertThat(killed).isZero();
        assertThat(ShellProcessRegistry.consumeShellSessionCancelled("package_session")).isTrue();
        assertThat(ShellProcessRegistry.consumeShellSessionCancelled("package_session")).isFalse();
    }
}
