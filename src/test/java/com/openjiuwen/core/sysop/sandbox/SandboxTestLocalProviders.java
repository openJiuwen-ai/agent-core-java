package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.local.LocalCodeOperation;
import com.openjiuwen.core.sysop.local.LocalFsOperation;
import com.openjiuwen.core.sysop.local.LocalShellOperation;
import com.openjiuwen.core.sysop.result.ExecuteCodeResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test-only local providers mirroring Python's test-local sandbox provider pattern.
 */
final class SandboxTestLocalProviders {

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private SandboxTestLocalProviders() {
    }

    static void ensureRegistered() {
        if (INITIALIZED.compareAndSet(false, true)) {
            SandboxRegistry.registerProvider("local", "fs", TestLocalFsProvider.class);
            SandboxRegistry.registerProvider("local", "shell", TestLocalShellProvider.class);
            SandboxRegistry.registerProvider("local", "code", TestLocalCodeProvider.class);
        }
    }

    public static final class TestLocalFsProvider extends LocalFsOperation {

        public TestLocalFsProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
            super(SandboxOperationSupport.toLocalWorkConfig(config));
        }
    }

    public static final class TestLocalShellProvider extends LocalShellOperation {

        public TestLocalShellProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
            super(SandboxOperationSupport.toLocalWorkConfig(config));
        }
    }

    public static final class TestLocalCodeProvider extends LocalCodeOperation {

        private final SandboxGatewayConfig config;

        public TestLocalCodeProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
            super(SandboxOperationSupport.toLocalWorkConfig(config));
            this.config = config;
        }

        @Override
        public ExecuteCodeResult executeCode(String code, String language, int timeout,
                                             Map<String, String> environment, Map<String, Object> options) {
            return super.executeCode(
                    SandboxOperationSupport.wrapCodeWithSandboxCwd(code, language, config),
                    language,
                    timeout,
                    environment,
                    options
            );
        }

        @Override
        public Iterator<ExecuteCodeStreamResult> executeCodeStream(String code, String language, int timeout,
                                                                   Map<String, String> environment,
                                                                   Map<String, Object> options) {
            return super.executeCodeStream(
                    SandboxOperationSupport.wrapCodeWithSandboxCwd(code, language, config),
                    language,
                    timeout,
                    environment,
                    options
            );
        }
    }
}
