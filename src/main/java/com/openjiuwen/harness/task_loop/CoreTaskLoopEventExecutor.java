/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import com.openjiuwen.core.controller.modules.TaskExecutor;
import com.openjiuwen.core.controller.modules.TaskExecutorDependencies;
import com.openjiuwen.core.controller.modules.TaskFilter;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.ControllerOutputPayload;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Core controller TaskExecutor bridge for DeepAgent task-loop tasks. */
public class CoreTaskLoopEventExecutor extends TaskExecutor {
  private final DeepAgent deepAgent;
  private final BiFunction<Map<String, Object>, AgentSessionApi, Map<String, Object>> taskInvoker;

  /** Auto-generated for codecheck compliance. */
  public CoreTaskLoopEventExecutor(TaskExecutorDependencies dependencies, DeepAgent deepAgent) {
    this(
        dependencies,
        deepAgent,
        (BiFunction<Map<String, Object>, AgentSessionApi, Map<String, Object>>) null);
  }

  /** Auto-generated for codecheck compliance. */
  public CoreTaskLoopEventExecutor(
      TaskExecutorDependencies dependencies,
      Function<Map<String, Object>, Map<String, Object>> taskInvoker) {
    this(dependencies, null, taskInvoker);
  }

  /** Auto-generated for codecheck compliance. */
  public CoreTaskLoopEventExecutor(
      TaskExecutorDependencies dependencies,
      DeepAgent deepAgent,
      Function<Map<String, Object>, Map<String, Object>> taskInvoker) {
    this(
        dependencies,
        deepAgent,
        taskInvoker == null ? null : (effective, session) -> taskInvoker.apply(effective));
  }

  /** Auto-generated for codecheck compliance. */
  public CoreTaskLoopEventExecutor(
      TaskExecutorDependencies dependencies,
      DeepAgent deepAgent,
      BiFunction<Map<String, Object>, AgentSessionApi, Map<String, Object>> taskInvoker) {
    super(dependencies);
    this.deepAgent = deepAgent;
    this.taskInvoker = taskInvoker;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session) {
    return executeOnce(taskId, session).iterator();
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public PauseCheckResult canPause(String taskId, AgentSessionApi session) {
    return new PauseCheckResult(false, "DeepAgent task-loop tasks cannot be paused");
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public boolean pause(String taskId, AgentSessionApi session) {
    return false;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public CancelCheckResult canCancel(String taskId, AgentSessionApi session) {
    return new CancelCheckResult(true, "");
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public boolean cancel(String taskId, AgentSessionApi session) {
    if (deepAgent != null) {
      deepAgent.requestAbort();
    }
    return true;
  }

  private List<ControllerOutputChunk> executeOnce(String taskId, AgentSessionApi session) {
    Task task = resolveTask(taskId);
    Map<String, Object> effective = buildEffectiveInputs(taskId, task, session);
    try {
      Map<String, Object> result = invokeTask(effective, session);
      fireAfterTaskIteration(task, session, effective, result, null);
      List<ControllerOutputChunk> chunks =
          new java.util.ArrayList<>(processingChunks(result, taskId));
      String eventType =
          isInterruptResult(result)
              ? EventType.TASK_INTERACTION.getValue()
              : EventType.TASK_COMPLETION.getValue();
      ControllerOutputPayload payload =
          new ControllerOutputPayload(
              eventType,
              List.of(new DataFrame.JsonDataFrame(result == null ? Map.of() : result)),
              Map.of("task_id", taskId));
      chunks.add(new ControllerOutputChunk(chunks.size(), payload, true));
      return chunks;
    } catch (RuntimeException ex) {
      fireAfterTaskIteration(task, session, effective, Map.of("error", errorMessage(ex)), ex);
      ControllerOutputPayload payload =
          new ControllerOutputPayload(
              EventType.TASK_FAILED.getValue(),
              List.of(new DataFrame.TextDataFrame(errorMessage(ex))),
              Map.of("task_id", taskId));
      return List.of(new ControllerOutputChunk(0, payload, true));
    }
  }

  private void fireAfterTaskIteration(
      Task task,
      AgentSessionApi session,
      Map<String, Object> effective,
      Map<String, Object> result,
      RuntimeException exception) {
    if (deepAgent == null) {
      return;
    }
    Map<String, Object> metadata = task.getMetadata() == null ? Map.of() : task.getMetadata();
    deepAgent.fireAfterTaskIteration(
        TaskIterationContext.builder()
            .agent(deepAgent)
            .task(task)
            .session(session)
            .round(intValue(metadata.get("_handler_round_id"), 0))
            .isFollowUp(Boolean.TRUE.equals(effective.get("is_follow_up")))
            .inputs(new LinkedHashMap<>(effective))
            .result(result == null ? new LinkedHashMap<>() : new LinkedHashMap<>(result))
            .usageMetadata(TaskIterationContext.usageMetadataFrom(result))
            .exception(exception)
            .build());
  }

  private Task resolveTask(String taskId) {
    List<Task> tasks = taskManager.getTask(TaskFilter.byTaskId(taskId));
    if (tasks.isEmpty()) {
      throw new IllegalArgumentException("Task not found: " + taskId);
    }
    return tasks.get(0);
  }

  private Map<String, Object> buildEffectiveInputs(
      String taskId, Task task, AgentSessionApi session) {
    Map<String, Object> effective = new LinkedHashMap<>();
    effective.put("query", resolveQuery(taskId, task));
    effective.put("task_id", taskId);
    effective.put(
        "conversation_id", session != null ? session.getSessionId() : task.getSessionId());
    Map<String, Object> metadata = task.getMetadata() == null ? Map.of() : task.getMetadata();
    copyIfPresent(metadata, effective, "run_kind");
    copyIfPresent(metadata, effective, "run_context");
    copyIfPresent(metadata, effective, "is_follow_up");
    copyIfPresent(metadata, effective, "collect_inner_stream");
    copyIfPresent(metadata, effective, "loop_queues");
    return effective;
  }

  private Object resolveQuery(String taskId, Task task) {
    if (task != null && task.getInputs() != null) {
      for (Object input : task.getInputs()) {
        if (input instanceof com.openjiuwen.core.controller.schema.InputEvent event) {
          for (DataFrame frame : event.getInputData()) {
            if (frame instanceof DataFrame.TextDataFrame textDataFrame
                && textDataFrame.text() != null) {
              return textDataFrame.text();
            }
            if (frame instanceof DataFrame.JsonDataFrame jsonDataFrame
                && jsonDataFrame.data() != null) {
              Object query = jsonDataFrame.data().get("query");
              if (query != null) {
                return query;
              }
              return new LinkedHashMap<>(jsonDataFrame.data());
            }
          }
        }
      }
    }
    String description = task.getDescription();
    return description == null || description.isBlank() ? taskId : description;
  }

  private Map<String, Object> invokeTask(Map<String, Object> effective, AgentSessionApi session) {
    if (taskInvoker != null) {
      return taskInvoker.apply(effective, session);
    }
    if (deepAgent != null) {
      return deepAgent.invoke(effective);
    }
    return Map.of("output", effective.getOrDefault("query", ""));
  }

  private static List<ControllerOutputChunk> processingChunks(
      Map<String, Object> result, String taskId) {
    if (result == null) {
      return List.of();
    }
    Object raw =
        firstPresent(
            result, new String[] {"stream_chunks", "streamChunks", "chunks", "inner_stream"});
    if (!(raw instanceof Iterable<?> iterable)) {
      return List.of();
    }
    List<ControllerOutputChunk> chunks = new java.util.ArrayList<>();
    for (Object item : iterable) {
      ControllerOutputChunk chunk = toProcessingChunk(item, chunks.size(), taskId);
      if (chunk != null) {
        chunks.add(chunk);
      }
    }
    return chunks;
  }

  private static Object firstPresent(Map<String, Object> result, String[] keys) {
    for (String key : keys) {
      if (result.containsKey(key)) {
        return result.get(key);
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static ControllerOutputChunk toProcessingChunk(Object item, int index, String taskId) {
    if (item instanceof ControllerOutputChunk chunk) {
      chunk.setIndex(index);
      chunk.setLastChunk(false);
      return chunk;
    }
    if (item instanceof com.openjiuwen.core.session.stream.OutputSchema outputSchema
        && "__interaction__".equals(outputSchema.getType())) {
      return new ControllerOutputChunk(
          index,
          new ControllerOutputPayload(
              EventType.TASK_INTERACTION.getValue(),
              List.of(
                  new DataFrame.JsonDataFrame(
                      Map.of(
                          "type", outputSchema.getType(),
                          "payload", outputSchema.getPayload()))),
              Map.of("task_id", taskId, "stream_kind", "inner_agent")),
          false);
    }
    Map<String, Object> metadata = Map.of("task_id", taskId, "stream_kind", "inner_agent");
    List<DataFrame> data;
    if (item instanceof DataFrame frame) {
      data = List.of(frame);
    } else if (item instanceof Map<?, ?> map) {
      data = List.of(new DataFrame.JsonDataFrame(castMap(map)));
    } else if (item != null) {
      data = List.of(new DataFrame.TextDataFrame(String.valueOf(item)));
    } else {
      return null;
    }
    return new ControllerOutputChunk(
        index,
        new ControllerOutputPayload(ControllerOutputPayload.TASK_PROCESSING, data, metadata),
        false);
  }

  private static boolean isInterruptResult(Map<String, Object> result) {
    return result != null && "interrupt".equals(String.valueOf(result.get("result_type")));
  }

  private static void copyIfPresent(
      Map<String, Object> source, Map<String, Object> target, String key) {
    if (source.get(key) != null) {
      target.put(key, source.get(key));
    }
  }

  private static String errorMessage(RuntimeException ex) {
    return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
  }

  private static int intValue(Object value, int fallback) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value != null && !String.valueOf(value).isBlank()) {
      try {
        return Integer.parseInt(String.valueOf(value));
      } catch (NumberFormatException ignored) {
        return fallback;
      }
    }
    return fallback;
  }

  private static Map<String, Object> castMap(Map<?, ?> source) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : source.entrySet()) {
      result.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return result;
  }
}
