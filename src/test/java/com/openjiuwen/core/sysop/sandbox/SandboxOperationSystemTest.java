
package com.openjiuwen.core.sysop.sandbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Tag("system-test")
class SandboxOperationSystemTest {
    @TempDir
    Path tempDir;

    private SandboxGatewayConfig config() {
        return SandboxGatewayConfig.builder().params(
                Map.of("root_path", tempDir.toString(), "shell_allowlist", List.of("pwd", "python3", "python", "echo")))
                .build();
    }

    @Test
    void sandboxFallbackOperationsWorkTogether() throws Exception {
        Files.writeString(tempDir.resolve("input.txt"), "sandbox");

        SandboxFsOperation fs = new SandboxFsOperation(config());
        SandboxShellOperation shell = new SandboxShellOperation(config());
        SandboxCodeOperation code = new SandboxCodeOperation(config());

        var read = fs.readFile("input.txt", "text", null, null, null, "utf-8", 0, null);
        var pwd = shell.executeCmd("pwd", ".", 300, null, null);
        var codeResult = code.executeCode("import os\nprint(os.getcwd())", "python", 300, null, null);

        assertEquals(StatusCode.SUCCESS.getCode(), read.getCode());
        assertEquals("sandbox", read.getData().getContentAsString());
        assertEquals(StatusCode.SUCCESS.getCode(), pwd.getCode());
        assertTrue(pwd.getData().getStdout().contains(tempDir.toString()));
        assertEquals(StatusCode.SUCCESS.getCode(), codeResult.getCode());
        assertTrue(codeResult.getData().getStdout().contains(tempDir.toString()));
    }
}
