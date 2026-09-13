package com.openjiuwen.harness.security.shellast;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShellAstTest {

    @Nested
    class SimpleCommands {
        @Test
        void simpleCommand_yieldsOneSubcommand() {
            ShellAstParseResult result = ShellAst.parse("cat /etc/hosts");
            assertThat(result.getKind()).isEqualTo("simple");
            assertThat(result.getSubcommands()).hasSize(1);
            assertThat(result.getSubcommands().get(0).getArgv()).contains("cat", "/etc/hosts");
            assertThat(result.getFlags().hasRiskyStructure()).isFalse();
            assertThat(result.getBackend()).isEqualTo("fallback");
        }

        @Test
        void emptyCommand_isSimpleNoSubcommands() {
            ShellAstParseResult result = ShellAst.parse("");
            assertThat(result.getKind()).isEqualTo("simple");
            assertThat(result.getSubcommands()).isEmpty();
        }

        @Test
        void quotedArguments_areKeptIntact() {
            ShellAstParseResult result = ShellAst.parse("echo \"hello world\"");
            assertThat(result.getKind()).isEqualTo("simple");
            assertThat(result.getSubcommands().get(0).getArgv()).contains("echo", "hello world");
        }
    }

    @Nested
    class RiskyStructure {
        @Test
        void pipe_degradesToParseUnavailable() {
            ShellAstParseResult result = ShellAst.parse("cat x | grep y");
            assertThat(result.getKind()).isEqualTo("parse_unavailable");
            assertThat(result.getFlags().isPipeline()).isTrue();
            assertThat(result.getFlags().hasRiskyStructure()).isTrue();
        }

        @Test
        void commandSubstitution_degrades() {
            ShellAstParseResult result = ShellAst.parse("echo $(whoami)");
            assertThat(result.getKind()).isEqualTo("parse_unavailable");
            assertThat(result.getFlags().isCommandSubstitution()).isTrue();
        }

        @Test
        void heredoc_degrades() {
            ShellAstParseResult result = ShellAst.parse("cat <<EOF");
            assertThat(result.getKind()).isEqualTo("parse_unavailable");
            assertThat(result.getFlags().isHeredoc()).isTrue();
        }

        @Test
        void outputRedirection_degrades() {
            ShellAstParseResult result = ShellAst.parse("echo hi > out.txt");
            assertThat(result.getKind()).isEqualTo("parse_unavailable");
            assertThat(result.getFlags().isOutputRedirection()).isTrue();
        }
    }
}
