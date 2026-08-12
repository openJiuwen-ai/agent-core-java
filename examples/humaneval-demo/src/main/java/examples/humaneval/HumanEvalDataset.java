/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.humaneval;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads the OpenAI HumanEval JSONL dataset from a local file.
 *
 * <p>The dataset is 164 handcrafted Python programming tasks. Each line is a
 * JSON object with {@code task_id}, {@code prompt}, {@code test}, and
 * {@code entry_point}. The loader returns a list of {@link HumanEvalTask}
 * records in dataset order.
 *
 * <p>Default dataset path is
 * {@code examples/humaneval-demo/src/main/resources/humaneval/snapshots/master/HumanEval.jsonl}.
 * Override with system property {@code openjiuwen.humaneval.path} or the
 * {@code HUMANEVAL_PATH} environment variable. The file may be plain
 * {@code .jsonl} or {@code .jsonl.gz}.
 *
 * @since 2026-08-08
 */
public final class HumanEvalDataset {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String DEFAULT_PATH =
            "examples/humaneval-demo/src/main/resources/humaneval/snapshots/master/HumanEval.jsonl";

    private HumanEvalDataset() {
    }

    /**
     * Resolves the dataset path from system property, environment, or default.
     *
     * @return the resolved path
     * @throws IllegalStateException if no candidate file exists
     */
    public static Path resolvePath() {
        String propertyValue = System.getProperty("openjiuwen.humaneval.path");
        if (propertyValue != null && !propertyValue.isBlank()) {
            return Path.of(propertyValue).toAbsolutePath().normalize();
        }

        String envValue = System.getenv("HUMANEVAL_PATH");
        if (envValue != null && !envValue.isBlank()) {
            return Path.of(envValue).toAbsolutePath().normalize();
        }

        return Path.of(DEFAULT_PATH).toAbsolutePath().normalize();
    }

    /**
     * Loads all HumanEval tasks from the resolved dataset file.
     *
     * @return list of tasks in dataset order
     * @throws IllegalStateException if the file cannot be read or parsed
     */
    public static List<HumanEvalTask> load() {
        return load(resolvePath());
    }

    /**
     * Loads HumanEval tasks from an explicit path.
     *
     * @param datasetPath path to {@code HumanEval.jsonl} or {@code .jsonl.gz}
     * @return list of tasks
     * @throws IllegalStateException if the file cannot be read or parsed
     */
    public static List<HumanEvalTask> load(Path datasetPath) {
        if (datasetPath == null) {
            throw new IllegalStateException("datasetPath is null");
        }
        if (!Files.isRegularFile(datasetPath)) {
            throw new IllegalStateException("HumanEval dataset not found: " + datasetPath);
        }

        boolean gzipped = datasetPath.getFileName().toString().endsWith(".gz");
        try (BufferedReader reader = openReader(datasetPath, gzipped)) {
            List<HumanEvalTask> tasks = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                tasks.add(parseLine(line));
            }
            return tasks;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read HumanEval dataset: " + datasetPath, e);
        }
    }

    private static BufferedReader openReader(Path datasetPath, boolean gzipped) throws IOException {
        if (gzipped) {
            java.io.InputStream raw = Files.newInputStream(datasetPath);
            java.io.InputStream gzip = new java.util.zip.GZIPInputStream(raw);
            return new BufferedReader(new java.io.InputStreamReader(gzip, StandardCharsets.UTF_8));
        }
        return Files.newBufferedReader(datasetPath, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static HumanEvalTask parseLine(String line) {
        try {
            Map<String, Object> obj = MAPPER.readValue(line, Map.class);
            String taskId = stringField(obj, "task_id");
            String prompt = stringField(obj, "prompt");
            String test = stringField(obj, "test");
            String entryPoint = stringField(obj, "entry_point");
            return new HumanEvalTask(taskId, prompt, test, entryPoint);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse HumanEval line: " + line, e);
        }
    }

    private static String stringField(Map<String, Object> obj, String key) {
        Object value = obj.get(key);
        if (value == null) {
            throw new IllegalStateException("Missing required field in HumanEval line: " + key);
        }
        return value.toString();
    }
}
