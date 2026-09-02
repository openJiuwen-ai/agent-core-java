
package com.openjiuwen.harness.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class FileTodoStorageTest {
    @TempDir
    Path tempDir;

    private FileTodoStorage storage;

    @BeforeEach
    void setUp() {
        storage = new FileTodoStorage(tempDir);
    }

    @Test
    void loadEmptyDirReturnsEmptyList() throws IOException {
        List<TodoItem> items = storage.load("session1");
        assertThat(items).isEmpty();
    }

    @Test
    void loadNonexistentSessionReturnsEmptyList() throws IOException {
        List<TodoItem> items = storage.load("nonexistent-session");
        assertThat(items).isEmpty();
    }

    @Test
    void saveThenLoadReturnsSavedItems() throws IOException {
        List<TodoItem> todos = new ArrayList<>();
        todos.add(TodoItem.create("Task A"));
        todos.add(TodoItem.create("Task B"));

        storage.save("session1", todos);
        List<TodoItem> loaded = storage.load("session1");

        assertThat(loaded).hasSize(2);
        assertThat(loaded.get(0).getContent()).isEqualTo("Task A");
        assertThat(loaded.get(1).getContent()).isEqualTo("Task B");
    }

    @Test
    void saveCreatesDirectory() throws IOException {
        Path sessionDir = tempDir.resolve("new-session");
        assertThat(Files.exists(sessionDir)).isFalse();

        storage.save("new-session", List.of(TodoItem.create("Task")));

        assertThat(Files.exists(sessionDir)).isTrue();
        assertThat(Files.exists(sessionDir.resolve("todo.json"))).isTrue();
    }

    @Test
    void deleteRemovesDirectory() throws IOException {
        storage.save("del-session", List.of(TodoItem.create("Task")));
        Path sessionDir = tempDir.resolve("del-session");
        assertThat(Files.exists(sessionDir)).isTrue();

        storage.delete("del-session");
        assertThat(Files.exists(sessionDir)).isFalse();
    }

    @Test
    void saveOverwritesPreviousData() throws IOException {
        storage.save("overwrite-session", List.of(TodoItem.create("Old Task")));
        List<TodoItem> firstLoad = storage.load("overwrite-session");
        assertThat(firstLoad).hasSize(1);
        assertThat(firstLoad.get(0).getContent()).isEqualTo("Old Task");

        storage.save("overwrite-session", List.of(TodoItem.create("New Task 1"), TodoItem.create("New Task 2")));
        List<TodoItem> secondLoad = storage.load("overwrite-session");
        assertThat(secondLoad).hasSize(2);
        assertThat(secondLoad.get(0).getContent()).isEqualTo("New Task 1");
        assertThat(secondLoad.get(1).getContent()).isEqualTo("New Task 2");
    }
}
