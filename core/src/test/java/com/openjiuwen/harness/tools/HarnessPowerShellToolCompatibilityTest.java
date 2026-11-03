
package com.openjiuwen.harness.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class HarnessPowerShellToolCompatibilityTest {
    @Test
    void invokeShouldForcePowerShellShellType() {
        AtomicReference<String> shellType = new AtomicReference<>();
        PowerShellTool tool = new PowerShellTool("auto", (command, actualShellType) -> {
            shellType.set(actualShellType);
            return new PowerShellTool.ShellResult("ok\n", "", 0);
        });

        ToolOutput result = tool.invoke("Write-Output ok");

        assertThat(result.isSuccess()).isTrue();
        assertThat(shellType.get()).isEqualTo("powershell");
        assertThat(((Map<?, ?>) result.getData()).get("shell_type")).isEqualTo("powershell");
    }

    @Test
    void readOnlyShouldBlockWriteCommands() {
        PowerShellTool tool =
            new PowerShellTool("read_only", (command, shellType) -> new PowerShellTool.ShellResult("", "", 0));

        ToolOutput result = tool.invoke("Set-Content test.txt hi");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("Read-only");
    }

    @Test
    void injectionPatternShouldBeBlocked() {
        PowerShellTool tool =
            new PowerShellTool("auto", (command, shellType) -> new PowerShellTool.ShellResult("", "", 0));

        ToolOutput result = tool.invoke("Invoke-Expression \"Get-ChildItem\"");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError().toLowerCase()).contains("injection");
    }
}
