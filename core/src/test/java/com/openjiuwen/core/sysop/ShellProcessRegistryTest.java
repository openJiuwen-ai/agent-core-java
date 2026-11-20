/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

import com.openjiuwen.core.common.logging.LoggingUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class ShellProcessRegistryTest {

    private static final long PROCESS_WAIT_SECONDS = 5L;

    @TempDir
    private Path tempDir;

    @AfterEach
    void tearDown() {
        ShellProcessRegistry.resetShellSessionId(new ShellProcessRegistry.ContextToken<>(null, false));
        LoggingUtils.setSessionId();
    }

    @Test
    void sessionIdTokenRestoresPriorBinding() {
        ShellProcessRegistry.ContextToken<String> outer = ShellProcessRegistry.setShellSessionId("outer");
        ShellProcessRegistry.ContextToken<String> inner = ShellProcessRegistry.setShellSessionId("inner");

        assertThat(ShellProcessRegistry.getShellSessionId()).isEqualTo("inner");

        ShellProcessRegistry.resetShellSessionId(inner);
        assertThat(ShellProcessRegistry.getShellSessionId()).isEqualTo("outer");

        ShellProcessRegistry.resetShellSessionId(outer);
        assertThat(ShellProcessRegistry.getShellSessionId()).isNull();
    }

    @Test
    void resolveShellSessionIdFallsBackToLoggingTraceId() {
        LoggingUtils.setSessionId("trace-123");
        assertThat(ShellProcessRegistry.resolveShellSessionId()).isEqualTo("trace-123");

        ShellProcessRegistry.ContextToken<String> token = ShellProcessRegistry.setShellSessionId("shell-456");
        assertThat(ShellProcessRegistry.resolveShellSessionId()).isEqualTo("shell-456");

        ShellProcessRegistry.resetShellSessionId(token);
        LoggingUtils.setSessionId();
        assertThat(ShellProcessRegistry.resolveShellSessionId()).isNull();
    }

    @Test
    void killTrackedProcessForSessionMatchesPythonBehavior() throws Exception {
        Process process = startLongRunningProcess();
        ShellProcessRegistry.SHELL_PROCESS_REGISTRY.register("sess_kill", process);

        int killed = ShellProcessRegistry.killShellProcessesForSession("sess_kill");

        assertThat(killed).isEqualTo(1);
        assertThat(process.waitFor(3, TimeUnit.SECONDS)).isTrue();
        assertThat(ShellProcessRegistry.consumeShellSessionCancelled("sess_kill")).isTrue();
        assertThat(ShellProcessRegistry.consumeShellSessionCancelled("sess_kill")).isFalse();
    }

    @Test
    void killSessionTreeIncludesChildSessionKeys() throws Exception {
        Process parent = startLongRunningProcess();
        Process child = startLongRunningProcess();
        ShellProcessRegistry.SHELL_PROCESS_REGISTRY.register("sess_tree", parent);
        ShellProcessRegistry.SHELL_PROCESS_REGISTRY.register("sess_tree_child", child);

        int killed = ShellProcessRegistry.killShellProcessesForSessionTree("sess_tree");

        assertThat(killed).isEqualTo(2);
        assertThat(parent.waitFor(3, TimeUnit.SECONDS)).isTrue();
        assertThat(child.waitFor(3, TimeUnit.SECONDS)).isTrue();
        assertThat(ShellProcessRegistry.consumeShellSessionCancelled("sess_tree")).isTrue();
        assertThat(ShellProcessRegistry.consumeShellSessionCancelled("sess_tree_child")).isTrue();
    }

    @Test
    void cancelledSessionKillsProcessRegisteredLater() throws Exception {
        Process process = startLongRunningProcess();
        try {
            assertThat(ShellProcessRegistry.killShellProcessesForSession("late_registration")).isZero();

            ShellProcessRegistry.registerShellProcess("late_registration", process);

            assertThat(process.waitFor(PROCESS_WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(ShellProcessRegistry.consumeShellSessionCancelled("late_registration")).isFalse();
        } finally {
            destroyProcess(process);
        }
    }

    @Test
    void terminateShellProcessKillsDescendantTree() throws Exception {
        Process shell = startShellProcessTree();
        long childPid = -1L;
        long grandchildPid = -1L;
        try {
            childPid = awaitPid(tempDir.resolve("child.pid"));
            grandchildPid = awaitPid(tempDir.resolve("grandchild.pid"));
            ProcessHandle child = ProcessHandle.of(childPid).orElseThrow();
            ProcessHandle grandchild = ProcessHandle.of(grandchildPid).orElseThrow();
            assertThat(shell.isAlive()).isTrue();
            assertThat(child.isAlive()).isTrue();
            assertThat(grandchild.isAlive()).isTrue();

            assertThat(ShellProcessRegistry.terminateShellProcess(shell)).isTrue();

            assertThat(shell.waitFor(PROCESS_WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            child.onExit().get(PROCESS_WAIT_SECONDS, TimeUnit.SECONDS);
            grandchild.onExit().get(PROCESS_WAIT_SECONDS, TimeUnit.SECONDS);
        } finally {
            destroyProcess(grandchildPid);
            destroyProcess(childPid);
            destroyProcess(shell);
        }
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void terminateShellProcessKillsTermResistantDescendant() throws Exception {
        Process shell = startTermResistantProcessTree();
        long descendantPid = -1L;
        try {
            descendantPid = awaitPid(tempDir.resolve("resistant.pid"));
            ProcessHandle descendant = ProcessHandle.of(descendantPid).orElseThrow();
            assertThat(shell.isAlive()).isTrue();
            assertThat(descendant.isAlive()).isTrue();

            assertThat(ShellProcessRegistry.terminateShellProcess(shell)).isTrue();

            assertThat(shell.waitFor(PROCESS_WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            descendant.onExit().get(PROCESS_WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(descendant.isAlive()).isFalse();
        } finally {
            destroyProcessTree(descendantPid);
            destroyProcess(shell);
        }
    }

    private Process startLongRunningProcess() throws IOException {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return new ProcessBuilder("powershell", "-NoProfile", "-Command", "Start-Sleep -Seconds 30")
                    .start();
        }
        return new ProcessBuilder("sleep", "30").start();
    }

    private Process startShellProcessTree() throws IOException {
        String command = javaCommand(tempDir, "branch").stream()
                .map(ShellProcessRegistryTest::shellQuote)
                .reduce((left, right) -> left + " " + right)
                .orElseThrow();
        Path script = tempDir.resolve(isWindows() ? "tree.cmd" : "tree.sh");
        Files.writeString(script, command, StandardCharsets.UTF_8);
        List<String> shellCommand = isWindows()
                ? List.of("cmd.exe", "/d", "/c", script.toString())
                : List.of("/bin/sh", script.toString());
        return new ProcessBuilder(shellCommand)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    private Process startTermResistantProcessTree() throws IOException {
        Path script = tempDir.resolve("resistant-tree.sh");
        String pidFile = shellQuote(tempDir.resolve("resistant.pid").toString());
        String childCommand = "trap '' TERM; printf '%s' $$ > " + pidFile + "; while :; do sleep 60; done";
        Files.writeString(script, "/bin/sh -c " + shellQuote(childCommand) + " &\nwait\n", StandardCharsets.UTF_8);
        return new ProcessBuilder("/bin/sh", script.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    private static List<String> javaCommand(Path directory, String role) {
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java")
                .toString();
        return List.of(javaExecutable, "-cp", System.getProperty("java.class.path"),
                TreeProcess.class.getName(), directory.toString(), role);
    }

    private static String shellQuote(String value) {
        if (isWindows()) {
            return '"' + value + '"';
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static long awaitPid(Path pidFile) {
        awaitCondition(() -> readPid(pidFile).isPresent());
        return readPid(pidFile).orElseThrow();
    }

    private static OptionalLong readPid(Path pidFile) {
        try {
            String value = Files.readString(pidFile, StandardCharsets.UTF_8).trim();
            return value.isEmpty() ? OptionalLong.empty() : OptionalLong.of(Long.parseLong(value));
        } catch (IOException | NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    private static void awaitCondition(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PROCESS_WAIT_SECONDS);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10L));
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static void destroyProcess(long pid) {
        if (pid > 0L) {
            ProcessHandle.of(pid).ifPresent(ShellProcessRegistryTest::destroyProcess);
        }
    }

    private static void destroyProcessTree(long pid) {
        if (pid <= 0L) {
            return;
        }
        ProcessHandle.of(pid).ifPresent(process -> {
            process.descendants().toList().forEach(ShellProcessRegistryTest::destroyProcess);
            destroyProcess(process);
        });
    }

    private static void destroyProcess(ProcessHandle process) {
        process.destroyForcibly();
        process.onExit().orTimeout(PROCESS_WAIT_SECONDS, TimeUnit.SECONDS).join();
    }

    private static void destroyProcess(Process process) {
        process.destroyForcibly();
        process.onExit().orTimeout(PROCESS_WAIT_SECONDS, TimeUnit.SECONDS).join();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    static final class TreeProcess {
        private TreeProcess() {
        }

        /**
         * Runs a child or grandchild process that stays alive until terminated.
         *
         * @param args working directory and tree role
         * @throws Exception if the child cannot be started or awaited
         */
        public static void main(String[] args) throws Exception {
            Path directory = Path.of(args[0]);
            if ("leaf".equals(args[1])) {
                writePid(directory.resolve("grandchild.pid"));
                new CountDownLatch(1).await();
                return;
            }
            Process grandchild = new ProcessBuilder(javaCommand(directory, "leaf"))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            writePid(directory.resolve("child.pid"));
            grandchild.waitFor();
        }

        private static void writePid(Path path) throws IOException {
            Files.writeString(path, Long.toString(ProcessHandle.current().pid()), StandardCharsets.UTF_8);
        }
    }
}
