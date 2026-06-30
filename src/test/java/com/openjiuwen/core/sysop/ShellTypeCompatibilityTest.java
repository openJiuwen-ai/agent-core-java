package com.openjiuwen.core.sysop;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShellTypeCompatibilityTest {

    @Test
    void fromStringShouldMatchPythonStyleValuesAndFallbackToAuto() {
        assertThat(ShellType.fromString("auto")).isEqualTo(ShellType.AUTO);
        assertThat(ShellType.fromString("cmd")).isEqualTo(ShellType.CMD);
        assertThat(ShellType.fromString("powershell")).isEqualTo(ShellType.POWERSHELL);
        assertThat(ShellType.fromString("bash")).isEqualTo(ShellType.BASH);
        assertThat(ShellType.fromString("sh")).isEqualTo(ShellType.SH);
        assertThat(ShellType.fromString("unknown")).isEqualTo(ShellType.AUTO);
        assertThat(ShellType.fromString(null)).isEqualTo(ShellType.AUTO);
    }
}
