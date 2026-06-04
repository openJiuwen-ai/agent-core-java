/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * GitHub CLI preflight helpers for auto-harness research stages.
 * <p>
 * Mirrors Python's {@code openjiuwen.auto_harness.infra.github_cli}.
 */
public final class GitHubCli {

    private GitHubCli() {
    }

    @FunctionalInterface
    public interface Which {
        String find(String name);
    }

    @FunctionalInterface
    public interface CommandRunner {
        ProcessResult run(List<String> command);
    }

    @FunctionalInterface
    public interface InstallCommandSupplier {
        List<InstallCommand> get();
    }

    public record ProcessResult(int returnCode, String stderr, String stdout) {
    }

    public record InstallCommand(List<String> command, String label) {
    }

    public static GitHubCliStatus ensureGithubCliReady(Consumer<String> emit) {
        return ensureGithubCliReady(emit, GitHubCli::findOnPath, GitHubCli::runCommand, GitHubCli::installCommands);
    }

    public static GitHubCliStatus ensureGithubCliReady(
            Consumer<String> emit,
            Which which,
            CommandRunner runner,
            InstallCommandSupplier installCommandSupplier) {
        Consumer<String> sink = emit == null ? ignored -> { } : emit;
        Which whichFn = which == null ? GitHubCli::findOnPath : which;
        CommandRunner runFn = runner == null ? GitHubCli::runCommand : runner;
        InstallCommandSupplier installFn = installCommandSupplier == null ? GitHubCli::installCommands : installCommandSupplier;

        String ghPath = nullToEmpty(whichFn.find("gh"));
        boolean installedNow = false;
        if (ghPath.isBlank()) {
            sink.accept("\u672a\u68c0\u6d4b\u5230 `gh`\uff0c\u6b63\u5728\u5c1d\u8bd5\u81ea\u52a8\u5b89\u88c5 GitHub CLI\u3002");
            ghPath = installGithubCli(sink, whichFn, runFn, installFn);
            installedNow = !ghPath.isBlank();
            if (ghPath.isBlank()) {
                sink.accept("\u81ea\u52a8\u5b89\u88c5 `gh` \u5931\u8d25\u3002"
                        + "\u672c\u8f6e\u4f1a\u9000\u56de\u7f51\u9875\u8865\u5145\u8c03\u7814\uff0c"
                        + "\u65e0\u6cd5\u6267\u884c GitHub-first \u6e90\u7801\u7b56\u7565\u3002");
                sink.accept("\u8bf7\u5148\u5b89\u88c5 GitHub CLI \u540e\u91cd\u8bd5\uff1ahttps://cli.github.com/");
                return new GitHubCliStatus(false, false, false, "");
            }
            sink.accept("\u5df2\u5b89\u88c5 `gh`: " + ghPath);
        }

        boolean authenticated = isGhAuthenticated(ghPath, runFn);
        if (authenticated) {
            sink.accept("\u68c0\u6d4b\u5230 `gh` \u5df2\u767b\u5f55\u3002");
        } else {
            sink.accept("\u68c0\u6d4b\u5230 `gh` \u672a\u767b\u5f55\u3002"
                    + "\u516c\u5f00\u4ed3\u5e93\u901a\u5e38\u4ecd\u53ef clone\uff0c"
                    + "\u4f46\u533f\u540d\u8bf7\u6c42\u901f\u7387\u8f83\u4f4e\u3002");
            sink.accept("\u5efa\u8bae\u5148\u6267\u884c `gh auth login --web` \u5b8c\u6210\u6d4f\u89c8\u5668\u767b\u5f55\uff1b"
                    + "\u82e5\u5df2\u6709 token\uff0c\u4e5f\u53ef\u4f7f\u7528 `gh auth login --with-token`\u3002");
        }

        return new GitHubCliStatus(true, authenticated, installedNow, ghPath);
    }

    public static boolean isGhAuthenticated(String ghPath, CommandRunner runner) {
        try {
            ProcessResult result = runner.run(List.of(ghPath, "auth", "status"));
            return result.returnCode() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static String installGithubCli(
            Consumer<String> emit,
            Which which,
            CommandRunner runner,
            InstallCommandSupplier installCommandSupplier) {
        for (InstallCommand installCommand : installCommandSupplier.get()) {
            try {
                emit.accept("\u5c1d\u8bd5\u5b89\u88c5 GitHub CLI: `" + installCommand.label() + "`");
                ProcessResult result = runner.run(installCommand.command());
                if (result.returnCode() == 0) {
                    String ghPath = nullToEmpty(which.find("gh"));
                    if (!ghPath.isBlank()) {
                        return ghPath;
                    }
                }
                String stderr = nullToEmpty(result.stderr()).strip();
                if (!stderr.isBlank()) {
                    emit.accept("`" + installCommand.label() + "` \u5b89\u88c5\u5931\u8d25: " + truncate(stderr));
                }
            } catch (Exception e) {
                emit.accept("`" + installCommand.label() + "` \u6267\u884c\u5931\u8d25: " + e.getMessage());
            }
        }
        return "";
    }

    public static List<InstallCommand> installCommands() {
        List<InstallCommand> commands = new ArrayList<>();
        String system = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!findOnPath("brew").isBlank()) {
            commands.add(new InstallCommand(List.of("brew", "install", "gh"), "brew install gh"));
        }
        if (system.contains("linux")) {
            if (!findOnPath("apt-get").isBlank()) {
                commands.add(new InstallCommand(List.of("apt-get", "install", "-y", "gh"), "apt-get install -y gh"));
            }
            if (!findOnPath("dnf").isBlank()) {
                commands.add(new InstallCommand(List.of("dnf", "install", "-y", "gh"), "dnf install -y gh"));
            }
            if (!findOnPath("yum").isBlank()) {
                commands.add(new InstallCommand(List.of("yum", "install", "-y", "gh"), "yum install -y gh"));
            }
            if (!findOnPath("pacman").isBlank()) {
                commands.add(new InstallCommand(
                        List.of("pacman", "-S", "--noconfirm", "github-cli"),
                        "pacman -S --noconfirm github-cli"));
            }
            if (!findOnPath("zypper").isBlank()) {
                commands.add(new InstallCommand(
                        List.of("zypper", "--non-interactive", "install", "gh"),
                        "zypper --non-interactive install gh"));
            }
        }
        if (system.contains("windows") && !findOnPath("winget").isBlank()) {
            commands.add(new InstallCommand(
                    List.of("winget", "install", "--id", "GitHub.cli", "--exact",
                            "--accept-source-agreements", "--accept-package-agreements"),
                    "winget install --id GitHub.cli --exact"));
        }
        return commands;
    }

    public static String truncate(String text) {
        return truncate(text, 240);
    }

    public static String truncate(String text, int limit) {
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit - 3) + "...";
    }

    private static ProcessResult runCommand(List<String> command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            Process process = builder.start();
            String stdout;
            String stderr;
            try (InputStream out = process.getInputStream(); InputStream err = process.getErrorStream()) {
                stdout = new String(out.readAllBytes(), StandardCharsets.UTF_8);
                stderr = new String(err.readAllBytes(), StandardCharsets.UTF_8);
            }
            int code = process.waitFor();
            return new ProcessResult(code, stderr, stdout);
        } catch (IOException e) {
            return new ProcessResult(127, e.getMessage(), "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ProcessResult(130, "Interrupted", "");
        }
    }

    private static String findOnPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            path = System.getenv("Path");
        }
        if (path == null || path.isBlank()) {
            return "";
        }
        String separator = System.getProperty("path.separator");
        for (String entry : path.split(java.util.regex.Pattern.quote(separator))) {
            Path candidate = Path.of(entry).resolve(executable);
            if (Files.isRegularFile(candidate)) {
                return candidate.toString();
            }
            Path exeCandidate = Path.of(entry).resolve(executable + ".exe");
            if (Files.isRegularFile(exeCandidate)) {
                return exeCandidate.toString();
            }
        }
        return "";
    }

    private static String nullToEmpty(String value) {
        return Objects.toString(value, "");
    }
}
