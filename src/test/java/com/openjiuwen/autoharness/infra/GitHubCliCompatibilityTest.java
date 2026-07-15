
package com.openjiuwen.autoharness.infra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class GitHubCliCompatibilityTest {
    @Test
    void ensureReadyShouldReportAuthenticatedGhWhenAlreadyInstalled() {
        List<String> messages = new ArrayList<>();
        GitHubCli cli = new GitHubCli(command -> "gh".equals(command) ? "/usr/bin/gh" : "",
                (command, timeoutMillis) -> new GitHubCli.CommandResult(0, "", ""), "Linux", false);

        GitHubCliStatus status = cli.ensureReady(messages::add);

        assertThat(status.isAvailable()).isTrue();
        assertThat(status.isAuthenticated()).isTrue();
        assertThat(status.isInstalledNow()).isFalse();
        assertThat(status.getPath()).isEqualTo("/usr/bin/gh");
        assertThat(String.join("\n", messages)).contains("已登录");
    }

    @Test
    void ensureReadyShouldInstallGhAndPromptForLoginWhenAuthMissing() {
        List<String> messages = new ArrayList<>();
        AtomicInteger ghLookups = new AtomicInteger();
        GitHubCli.CommandLocator locator = command -> {
            if (!"gh".equals(command)) {
                return "";
            }
            return ghLookups.getAndIncrement() == 0 ? "" : "/usr/local/bin/gh";
        };
        List<List<String>> runCalls = new ArrayList<>();
        GitHubCli cli = new GitHubCli(locator, (command, timeoutMillis) -> {
            runCalls.add(command);
            if (command.size() >= 3 && "auth".equals(command.get(1)) && "status".equals(command.get(2))) {
                return new GitHubCli.CommandResult(1, "", "not logged in");
            }
            return new GitHubCli.CommandResult(0, "", "");
        }, "Linux", false, List.of(new GitHubCli.InstallCommand(List.of("brew", "install", "gh"), "brew install gh")));

        GitHubCliStatus status = cli.ensureReady(messages::add);

        assertThat(status.isAvailable()).isTrue();
        assertThat(status.isInstalledNow()).isTrue();
        assertThat(status.isAuthenticated()).isFalse();
        assertThat(runCalls).containsExactly(List.of("brew", "install", "gh"),
                List.of("/usr/local/bin/gh", "auth", "status"));
        assertThat(String.join("\n", messages)).contains("建议先执行 `gh auth login --web`");
    }

    @Test
    void ensureReadyShouldFallBackWhenGhInstallFails() {
        List<String> messages = new ArrayList<>();
        GitHubCli cli = new GitHubCli(command -> "",
                (command, timeoutMillis) -> new GitHubCli.CommandResult(1, "", "permission denied"), "Linux", false,
                List.of(new GitHubCli.InstallCommand(List.of("apt-get", "install", "-y", "gh"),
                        "apt-get install -y gh")));

        GitHubCliStatus status = cli.ensureReady(messages::add);

        assertThat(status.isAvailable()).isFalse();
        assertThat(status.isAuthenticated()).isFalse();
        String joined = String.join("\n", messages);
        assertThat(joined).contains("自动安装 `gh` 失败");
        assertThat(joined).contains("https://cli.github.com/");
    }

    @Test
    void installCommandsShouldUsePlatformPackageManagersAndNonInteractiveSudo() {
        GitHubCli cli = new GitHubCli(
                command -> Map.of("sudo", "/usr/bin/sudo", "apt-get", "/usr/bin/apt-get", "pacman", "/usr/bin/pacman")
                        .getOrDefault(command, ""),
                (command, timeoutMillis) -> new GitHubCli.CommandResult(0, "", ""), "Linux", false);

        List<GitHubCli.InstallCommand> commands = cli.installCommands();

        assertThat(commands).extracting(GitHubCli.InstallCommand::label).contains("apt-get install -y gh",
                "pacman -S --noconfirm github-cli");
        assertThat(commands).extracting(GitHubCli.InstallCommand::command).contains(
                List.of("sudo", "-n", "apt-get", "install", "-y", "gh"),
                List.of("sudo", "-n", "pacman", "-S", "--noconfirm", "github-cli"));
    }
}
