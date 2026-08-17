/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.humaneval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Runs a generated Python completion against the HumanEval assertions.
 *
 * <p>Execution model matches the OpenAI HumanEval harness: build a Python
 * program of the form {@code <prompt> + <completion> + <test> + "check(" +
 * entryPoint + ")"} and run it with {@code python}. Exit code 0 means the
 * completion passed; anything else means failure.
 *
 * @since 2026-08-08
 */
public final class HumanEvalExecutor {

    /** Exit code meaning the task passed. */
    public static final int PASS = 0;
    private static final int TIMEOUT_SECONDS = 15;

    private final String pythonExecutable;
    private final Path tempDir;

    /**
     * Constructs an executor that writes temp programs under {@code tempDir}.
     *
     * @param pythonExecutable python interpreter path, e.g. {@code python}
     * @param tempDir          directory for temp program files
     */
    public HumanEvalExecutor(String pythonExecutable, Path tempDir) {
        this.pythonExecutable = pythonExecutable;
        this.tempDir = tempDir;
    }

    /**
     * Runs {@code prompt + completion + test + check(entry_point)} as a program.
     *
     * @param task       the HumanEval task
     * @param completion model-generated completion (function body only)
     * @return true if the task passed, false otherwise
     * @throws IllegalStateException if the python process cannot be started
     */
    public boolean run(HumanEvalTask task, String completion) {
        String program = buildProgram(task, completion);
        Path script = writeScript(task, program);
        return runPython(script);
    }

    private String buildProgram(HumanEvalTask task, String completion) {
        String body = stripMarkdown(completion);
        String indented = indentBody(body);
        StringBuilder sb = new StringBuilder(task.prompt().length() + indented.length()
                + task.test().length() + 32);
        sb.append(task.prompt());
        if (!indented.isEmpty() && !indented.startsWith("\n")) {
            sb.append("\n");
        }
        sb.append(indented);
        sb.append("\n");
        sb.append(task.test());
        sb.append("\ncheck(").append(task.entryPoint()).append(")\n");
        return sb.toString();
    }

    private String stripMarkdown(String completion) {
        String stripped = stripOuterBlankLines(completion);
        if (stripped.startsWith("```")) {
            int firstNewline = stripped.indexOf('\n');
            if (firstNewline >= 0) {
                stripped = stripped.substring(firstNewline + 1);
            }
            int lastFence = stripped.lastIndexOf("```");
            if (lastFence >= 0) {
                stripped = stripped.substring(0, lastFence);
            }
            return stripOuterBlankLines(stripped);
        }
        return stripped;
    }

    private String stripOuterBlankLines(String text) {
        int start = 0;
        int end = text.length();
        while (start < end) {
            char ch = text.charAt(start);
            if (ch == '\n' || ch == '\r') {
                start++;
            } else {
                break;
            }
        }
        while (end > start) {
            char ch = text.charAt(end - 1);
            if (ch == '\n' || ch == '\r') {
                end--;
            } else {
                break;
            }
        }
        return text.substring(start, end);
    }

    private String indentBody(String body) {
        if (body.isEmpty()) {
            return body;
        }
        String[] lines = body.split("\n", -1);
        int minIndent = findMinIndent(lines);
        int shift = Math.max(4 - minIndent, -minIndent);
        StringBuilder out = new StringBuilder(body.length() + lines.length * 4);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
                out.append(indentLine(line, shift)).append("\r");
            } else {
                out.append(indentLine(line, shift));
            }
            if (i < lines.length - 1) {
                out.append("\n");
            }
        }
        return out.toString();
    }

    private int findMinIndent(String[] lines) {
        int minIndent = Integer.MAX_VALUE;
        for (String line : lines) {
            String stripped = line.strip();
            if (stripped.isEmpty()) {
                continue;
            }
            int leading = line.length() - line.stripLeading().length();
            if (leading < minIndent) {
                minIndent = leading;
            }
        }
        return minIndent == Integer.MAX_VALUE ? 0 : minIndent;
    }

    private String indentLine(String line, int shift) {
        int leading = line.length() - line.stripLeading().length();
        String content = line.substring(leading);
        int newIndent = leading + shift;
        if (newIndent < 0) {
            newIndent = 0;
        }
        return " ".repeat(newIndent) + content;
    }

    private Path writeScript(HumanEvalTask task, String program) {
        Path script = tempDir.resolve(safeFileName(task.taskId()) + ".py");
        try {
            Files.writeString(script, program, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write temp script: " + script, e);
        }
        return script;
    }

    private String safeFileName(String taskId) {
        return taskId.replace('/', '_').replace('\\', '_');
    }

    private boolean runPython(Path script) {
        ProcessBuilder pb = new ProcessBuilder(pythonExecutable, script.toString())
                .redirectErrorStream(true);
        try {
            Process process = pb.start();
            try (var ignored = process.getInputStream()) {
                boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return false;
                }
                return process.exitValue() == PASS;
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to run python: " + pythonExecutable + " " + script, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
