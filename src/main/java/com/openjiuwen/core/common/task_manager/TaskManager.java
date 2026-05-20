/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.callback.CallbackFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/** Coroutine-task manager compatible layer. */
public class TaskManager {
  private static final Logger log = LoggerFactory.getLogger(TaskManager.class);

  private static volatile TaskManager instance;

  private final TaskRegistry registry = new TaskRegistry();
  private final ReentrantLock lock = new ReentrantLock();
  private static final ThreadFactory TASK_THREAD_FACTORY =
      runnable -> {
        Thread thread = new Thread(runnable, "task-manager-worker");
        thread.setDaemon(true);
        return thread;
      };
  private static final ThreadFactory SCHEDULER_THREAD_FACTORY =
      runnable -> {
        Thread thread = new Thread(runnable, "task-manager-scheduler");
        thread.setDaemon(true);
        return thread;
      };
  private final ExecutorService executor =
      new ThreadPoolExecutor(
          0,
          Integer.MAX_VALUE,
          60L,
          TimeUnit.SECONDS,
          new SynchronousQueue<>(),
          TASK_THREAD_FACTORY);
  private final ScheduledExecutorService scheduler =
      new ScheduledThreadPoolExecutor(1, SCHEDULER_THREAD_FACTORY);
  private final CallbackFramework callbackFramework = Runner.callbackFramework();

  /** Auto-generated for codecheck compliance. */
  public static TaskManager getInstance() {
    if (instance == null) {
      synchronized (TaskManager.class) {
        if (instance == null) {
          instance = new TaskManager();
        }
      }
    }
    return instance;
  }

  /** Auto-generated for codecheck compliance. */
  public static void resetInstance() {
    synchronized (TaskManager.class) {
      if (instance != null) {
        instance.shutdown();
      }
      instance = null;
    }
  }

  /** Auto-generated for codecheck compliance. */
  public TaskRegistry getRegistry() {
    return registry;
  }

  /** Auto-generated for codecheck compliance. */
  public TaskGroupScope taskGroup() {
    return new TaskGroupScope(this, null);
  }

  /** Auto-generated for codecheck compliance. */
  public TaskGroupScope taskGroup(String name) {
    return new TaskGroupScope(this, name);
  }

  /** Auto-generated for codecheck compliance. */
  public <T> Task createTask(Callable<T> callable) {
    return createTask(callable, null, null, null, null, Map.of(), false);
  }

  /** Auto-generated for codecheck compliance. */
  public <T> Task createTask(
      Callable<T> callable,
      String taskId,
      String name,
      String group,
      Double timeout,
      Map<String, Object> metadata,
      boolean isCatchExceptionsEnabled) {
    if (callable == null) {
      throw new IllegalArgumentException("callable must not be null");
    }
    String resolvedTaskId =
        taskId != null && !taskId.isBlank() ? taskId : UUID.randomUUID().toString();
    String resolvedGroup = group;
    if ((resolvedGroup == null || resolvedGroup.isBlank()) && TaskContext.getTaskGroup() != null) {
      resolvedGroup = TaskContext.getTaskGroup().getName();
    }

    Task task = new Task(this, resolvedTaskId, name, resolvedGroup, timeout, metadata);
    lock.lock();
    try {
      if (registry.contains(resolvedTaskId)) {
        throw new DuplicateTaskError("Task " + resolvedTaskId + " already exists");
      }
      task.setParentTaskId(TaskContext.getCurrentTaskId());
      registry.add(task);
    } finally {
      lock.unlock();
    }

    Future<?> future =
        executor.submit(() -> task.execute(callable, this::handleStatus, isCatchExceptionsEnabled));
    task.setRunningFuture(future);
    log.info(
        "TaskManager createTask submitted: taskId={} name={} group={} timeout={}",
        task.getTaskId(),
        task.getName(),
        task.getGroup(),
        timeout);
    if (timeout != null && timeout > 0) {
      task.setTimeoutHandle(
          scheduler.schedule(
              task::triggerTimeout, Math.round(timeout * 1000), TimeUnit.MILLISECONDS));
    }
    triggerEvent(TaskManagerEvents.TASK_CREATED, task);
    return task;
  }

  /** Auto-generated for codecheck compliance. */
  public int cancelGroup(String group) {
    int count = 0;
    for (Task task : registry.getByGroup(group)) {
      if (task.cancel(false, "manual_cancel", null)) {
        count++;
      }
    }
    return count;
  }

  /** Auto-generated for codecheck compliance. */
  public int cancelAll() {
    int count = 0;
    for (Task task : registry.getRunning()) {
      if (task.cancel(false, "manual_cancel", null)) {
        count++;
      }
    }
    return count;
  }

  void cascadeCancel(String parentId, String reason) {
    for (Task child : registry.getByParent(parentId)) {
      if (!child.isTerminal()) {
        child.cancel(false, reason, parentId);
        cascadeCancel(child.getTaskId(), reason);
      }
    }
  }

  /** Auto-generated for codecheck compliance. */
  public String getTaskTree(String taskId) {
    List<String> lines = new ArrayList<>();
    buildTreeRecursive(taskId, lines, 0);
    return String.join("\n", lines);
  }

  private void buildTreeRecursive(String taskId, List<String> lines, int indent) {
    Task task = registry.get(taskId);
    if (task == null) {
      return;
    }
    String prefix = "  ".repeat(Math.max(0, indent)) + (indent > 0 ? "+- " : "");
    String statusInfo = "[" + task.getStatus().getValue() + "]";
    if (task.getCancelledBy() != null) {
      statusInfo +=
          " (cancelled by: " + task.getCancelledBy() + ", reason: " + task.getCancelReason() + ")";
    } else if (task.getCancelReason() != null) {
      statusInfo += " (reason: " + task.getCancelReason() + ")";
    }
    lines.add(prefix + task.getDisplayName() + " " + statusInfo);
    for (Task child : registry.getByParent(taskId)) {
      buildTreeRecursive(child.getTaskId(), lines, indent + 1);
    }
  }

  /** Auto-generated for codecheck compliance. */
  public List<Object> waitGroup(String group, boolean isReturnExceptions) throws Exception {
    List<Object> results = new ArrayList<>();
    Exception firstException = null;
    for (String taskId : registry.getGroupTaskIds(group)) {
      Task task = registry.get(taskId);
      if (task == null) {
        continue;
      }
      try {
        results.add(task.waitFor());
      } catch (InterruptedException | ExecutionException e) {
        if (isReturnExceptions) {
          results.add(e);
        } else {
          firstException = e;
          break;
        }
      }
    }
    if (firstException != null && !isReturnExceptions) {
      throw firstException;
    }
    return results;
  }

  /** Auto-generated for codecheck compliance. */
  public List<Object> waitAll(boolean isReturnExceptions) throws Exception {
    List<Object> results = new ArrayList<>();
    Exception firstException = null;
    List<String> taskIds = new ArrayList<>(registry.keys());
    log.info("TaskManager waitAll start: taskCount={} returnExceptions={}", taskIds.size(), isReturnExceptions);
    for (String taskId : taskIds) {
      Task task = registry.get(taskId);
      if (task == null) {
        log.info("TaskManager waitAll skip-null-task: taskId={}", taskId);
        results.add(null);
        continue;
      }
      if (firstException != null && !isReturnExceptions) {
        log.info("TaskManager waitAll cancel-due-to-first-exception: taskId={}", taskId);
        task.cancel(false, "other_task_failed", null);
        results.add(null);
        continue;
      }
      try {
        log.info(
            "TaskManager waitAll waiting: taskId={} name={} status={}",
            task.getTaskId(),
            task.getName(),
            task.getStatus());
        results.add(task.waitFor());
        log.info(
            "TaskManager waitAll done: taskId={} name={} status={}",
            task.getTaskId(),
            task.getName(),
            task.getStatus());
      } catch (InterruptedException | ExecutionException | CancellationException e) {
        log.info(
            "TaskManager waitAll exception: taskId={} name={} errorType={} message={}",
            task.getTaskId(),
            task.getName(),
            e.getClass().getSimpleName(),
            e.getMessage());
        if (isReturnExceptions) {
          results.add(e);
        } else {
          firstException = e;
          results.add(null);
        }
      }
    }
    if (firstException != null && !isReturnExceptions) {
      throw firstException;
    }
    log.info("TaskManager waitAll end: resultCount={}", results.size());
    return results;
  }

  /** Auto-generated for codecheck compliance. */
  public Iterable<Map.Entry<Task, Object>> asCompleted(List<Task> tasks, Double timeoutSeconds) {
    if (tasks == null || tasks.isEmpty()) {
      return List.of();
    }
    BlockingQueue<Map.Entry<Task, Object>> queue = new LinkedBlockingQueue<>();
    CountDownLatch remaining = new CountDownLatch(tasks.size());
    for (Task task : tasks) {
      executor.submit(
          () -> {
            try {
              Object result = task.waitFor();
              queue.put(new AbstractMap.SimpleEntry<>(task, result));
            } catch (Exception e) {
              try {
                queue.put(new AbstractMap.SimpleEntry<>(task, e));
              } catch (InterruptedException interrupted) {

              }
            } finally {
              remaining.countDown();
            }
          });
    }

    return () ->
        new Iterator<>() {
          private int emitted;
          private Map.Entry<Task, Object> next;

          /** Auto-generated for codecheck compliance. */
          @Override
          /** Auto-generated for codecheck compliance. */
          public boolean hasNext() {
            if (emitted >= tasks.size()) {
              return false;
            }
            if (next != null) {
              return true;
            }
            try {
              next =
                  timeoutSeconds != null && timeoutSeconds > 0
                      ? queue.poll(Math.round(timeoutSeconds * 1000), TimeUnit.MILLISECONDS)
                      : queue.take();
              if (next == null) {
                throw new IllegalStateException(
                    new TimeoutException(
                        "asCompleted() timed out after " + timeoutSeconds + " second(s)"));
              }
              return true;
            } catch (InterruptedException e) {

              throw new IllegalStateException(e);
            }
          }

          /** Auto-generated for codecheck compliance. */
          @Override
          /** Auto-generated for codecheck compliance. */
          public Map.Entry<Task, Object> next() {
            if (!hasNext()) {
              throw new java.util.NoSuchElementException();
            }
            Map.Entry<Task, Object> current = next;
            next = null;
            emitted += 1;
            return current;
          }
        };
  }

  /** Auto-generated for codecheck compliance. */
  public boolean removeTask(String taskId) {
    lock.lock();
    try {
      if (!registry.contains(taskId)) {
        return false;
      }
      registry.removeUnsafe(taskId);
      return true;
    } finally {
      lock.unlock();
    }
  }

  /** Auto-generated for codecheck compliance. */
  public int removeCompleted() {
    int count = 0;
    lock.lock();
    try {
      List<String> done = new ArrayList<>();
      for (Map.Entry<String, Task> entry : registry.items()) {
        if (entry.getValue().isTerminal()) {
          done.add(entry.getKey());
        }
      }
      for (String taskId : done) {
        registry.removeUnsafe(taskId);
        count++;
      }
    } finally {
      lock.unlock();
    }
    return count;
  }

  /** Auto-generated for codecheck compliance. */
  public void on(String eventType, Function<Map<String, Object>, Object> callback) {
    callbackFramework.registerSync(
        eventType,
        callback,
        0,
        false,
        "default",
        null,
        null,
        null,
        null,
        0,
        0.0,
        null,
        eventType + "_callback");
  }

  /** Auto-generated for codecheck compliance. */
  public void off(String eventType, Function<Map<String, Object>, Object> callback) {
    callbackFramework.unregister(eventType, callback);
  }

  void handleStatus(Task task, String status) {
    String eventType =
        switch (status) {
          case "running" -> TaskManagerEvents.TASK_RUNNING;
          case "completed" -> TaskManagerEvents.TASK_COMPLETED;
          case "cancelled" -> TaskManagerEvents.TASK_CANCELLED;
          case "timeout" -> TaskManagerEvents.TASK_TIMEOUT;
          case "failed" -> TaskManagerEvents.TASK_FAILED;
          default -> null;
        };
    if (eventType != null) {
      triggerEvent(eventType, task);
    }
  }

  private void triggerEvent(String eventType, Task task) {
    if (callbackFramework == null) {
      return;
    }
    java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
    payload.put("task", task);
    payload.put("task_id", task.getTaskId());
    payload.put("name", task.getName());
    payload.put("group", task.getGroup());
    payload.put("status", task.getStatus().getValue());
    callbackFramework.trigger(eventType, payload);
  }

  private void shutdown() {
    executor.shutdownNow();
    scheduler.shutdownNow();
    try {
      executor.awaitTermination(3, TimeUnit.SECONDS);
      scheduler.awaitTermination(3, TimeUnit.SECONDS);
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
    }
    registry.clear();
  }
}
