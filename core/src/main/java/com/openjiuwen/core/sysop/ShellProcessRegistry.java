/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggingUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Track in-flight shell processes so callers can cancel them by session id.
 *
 * <p>Mirrors Python's {@code openjiuwen/core/sys_operation/shell_process_registry.py}.</p>
 */
public final class ShellProcessRegistry {

    private static final String DEFAULT_TRACE_ID = "default_trace_id";
    private static final Duration TERMINATE_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration KILL_TIMEOUT = Duration.ofSeconds(1);
    private static final InheritableThreadLocal<String> SHELL_SESSION_ID = new InheritableThreadLocal<>();

    public static final SessionProcessRegistry SHELL_PROCESS_REGISTRY = new SessionProcessRegistry();

    private ShellProcessRegistry() {
    }

    /**
     * Token used to restore the prior ThreadLocal state.
     *
     * <p>Mirrors the restore-token pattern returned by Python ContextVar#set.</p>
     */
    public record ContextToken<T>(T previousValue, boolean hadValue) {
    }

    public static ContextToken<String> setShellSessionId(String sessionId) {
        String previous = SHELL_SESSION_ID.get();
        boolean hadValue = previous != null;
        SHELL_SESSION_ID.set(sessionId);
        return new ContextToken<>(previous, hadValue);
    }

    public static void resetShellSessionId(ContextToken<String> token) {
        if (token == null || !token.hadValue()) {
            SHELL_SESSION_ID.remove();
            return;
        }
        SHELL_SESSION_ID.set(token.previousValue());
    }

    public static String getShellSessionId() {
        return SHELL_SESSION_ID.get();
    }

    public static String resolveShellSessionId() {
        String sessionId = trimToNull(getShellSessionId());
        if (sessionId != null) {
            return sessionId;
        }
        String traceId = trimToNull(LoggingUtils.getSessionId());
        if (traceId == null || DEFAULT_TRACE_ID.equals(traceId)) {
            return null;
        }
        return traceId;
    }

    public static void registerShellProcess(String sessionId, Process proc) {
        SHELL_PROCESS_REGISTRY.register(sessionId, proc);
    }

    public static void unregisterShellProcess(String sessionId, Process proc) {
        SHELL_PROCESS_REGISTRY.unregister(sessionId, proc);
    }

    public static int killShellProcessesForSession(String sessionId) {
        return SHELL_PROCESS_REGISTRY.killSession(sessionId);
    }

    public static int killShellProcessesForSessionTree(String sessionId) {
        return SHELL_PROCESS_REGISTRY.killSessionTree(sessionId);
    }

    public static boolean consumeShellSessionCancelled(String sessionId) {
        return SHELL_PROCESS_REGISTRY.consumeCancelled(sessionId);
    }

    public static boolean terminateShellProcess(Process proc) {
        if (proc == null || !proc.isAlive()) {
            return false;
        }
        boolean windows = isWindows();
        try {
            if (windows) {
                proc.destroy();
            } else {
                destroyProcessTree(proc, false);
            }
        } catch (RuntimeException exception) {
            proc.destroy();
        }
        if (waitFor(proc, TERMINATE_TIMEOUT)) {
            return true;
        }

        try {
            if (windows) {
                proc.destroyForcibly();
            } else {
                destroyProcessTree(proc, true);
            }
        } catch (RuntimeException exception) {
            proc.destroyForcibly();
            Loggers.SYS_OPERATION.warning(
                    "Failed to kill shell process " + safePid(proc) + ": " + exception.getMessage()
            );
        }
        if (!waitFor(proc, KILL_TIMEOUT)) {
            Loggers.SYS_OPERATION.warning(
                    "Timeout expired waiting for shell process " + safePid(proc) + " termination"
            );
        }
        return true;
    }

    private static void destroyProcessTree(Process process, boolean forcibly) {
        List<ProcessHandle> descendants = new ArrayList<>(process.toHandle().descendants().toList());
        for (int index = descendants.size() - 1; index >= 0; index -= 1) {
            destroyHandle(descendants.get(index), forcibly);
        }
        destroyHandle(process.toHandle(), forcibly);
    }

    private static void destroyHandle(ProcessHandle handle, boolean forcibly) {
        if (!handle.isAlive()) {
            return;
        }
        if (forcibly) {
            handle.destroyForcibly();
            return;
        }
        handle.destroy();
    }

    private static boolean waitFor(Process proc, Duration timeout) {
        try {
            return proc.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static long safePid(Process proc) {
        try {
            return proc.pid();
        } catch (UnsupportedOperationException exception) {
            return -1L;
        }
    }

    /**
     * Session-scoped registry of in-flight shell processes.
     *
     * <p>Mirrors Python's {@code ShellProcessRegistry} class in
     * {@code openjiuwen/core/sys_operation/shell_process_registry.py}.</p>
     */
    public static final class SessionProcessRegistry {

        private final Object lock = new Object();
        private final Map<String, Set<Process>> processes = new HashMap<>();
        private final Set<String> cancelledSessions = new HashSet<>();

        public void register(String sessionId, Process proc) {
            String sid = trimToNull(sessionId);
            if (sid == null || proc == null) {
                return;
            }
            synchronized (lock) {
                processes.computeIfAbsent(sid, ignored -> new HashSet<>()).add(proc);
            }
        }

        public void unregister(String sessionId, Process proc) {
            String sid = trimToNull(sessionId);
            if (sid == null || proc == null) {
                return;
            }
            synchronized (lock) {
                Set<Process> bucket = processes.get(sid);
                if (bucket == null) {
                    return;
                }
                bucket.remove(proc);
                if (bucket.isEmpty()) {
                    processes.remove(sid);
                }
            }
        }

        public int killSession(String sessionId) {
            String sid = trimToNull(sessionId);
            if (sid == null) {
                return 0;
            }
            List<Process> tracked;
            synchronized (lock) {
                cancelledSessions.add(sid);
                Set<Process> removed = processes.remove(sid);
                tracked = removed == null ? List.of() : new ArrayList<>(removed);
            }
            return terminateProcesses(tracked);
        }

        public int killSessionTree(String sessionId) {
            String sid = trimToNull(sessionId);
            if (sid == null) {
                return 0;
            }
            String prefix = sid + "_";
            List<String> matchingKeys;
            synchronized (lock) {
                matchingKeys = processes.keySet().stream()
                        .filter(key -> key.equals(sid) || key.startsWith(prefix))
                        .toList();
                cancelledSessions.addAll(matchingKeys);
            }
            int killed = 0;
            for (String key : matchingKeys) {
                List<Process> tracked;
                synchronized (lock) {
                    Set<Process> removed = processes.remove(key);
                    tracked = removed == null ? List.of() : new ArrayList<>(removed);
                }
                killed += terminateProcesses(tracked);
            }
            return killed;
        }

        public boolean consumeCancelled(String sessionId) {
            String sid = trimToNull(sessionId);
            if (sid == null) {
                return false;
            }
            synchronized (lock) {
                if (!cancelledSessions.contains(sid)) {
                    return false;
                }
                cancelledSessions.remove(sid);
                return true;
            }
        }

        private int terminateProcesses(List<Process> tracked) {
            int killed = 0;
            for (Process proc : tracked) {
                if (terminateShellProcess(proc)) {
                    killed += 1;
                }
            }
            return killed;
        }
    }
}
