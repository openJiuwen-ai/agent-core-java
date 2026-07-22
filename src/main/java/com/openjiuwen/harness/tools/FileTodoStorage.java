package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantWorkspaceResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FileTodoStorage implements TodoStorage {
    private final Path workspace;
    private final TenantWorkspaceResolver workspaceResolver;

    public FileTodoStorage(Path workspace) {
        this.workspace = workspace.toAbsolutePath().normalize();
        this.workspaceResolver = null;
    }

    public FileTodoStorage(Path workspace, TenantWorkspaceResolver workspaceResolver) {
        this.workspace = workspace.toAbsolutePath().normalize();
        this.workspaceResolver = workspaceResolver;
    }

    private Path resolveWorkspace() {
        if (workspaceResolver != null) {
            TenantContext ctx = TenantContextHolder.getCurrentTenant();
            if (ctx != null && ctx.isTenantAware()) {
                return workspaceResolver.resolveTodoDir(ctx);
            }
        }
        return workspace;
    }

    @Override
    public List<TodoItem> load(String sessionId) throws IOException {
        Path file = resolveWorkspace().resolve(sessionId).resolve("todo.json");
        if (!Files.exists(file)) { return new ArrayList<>(); }
        String json = Files.readString(file);
        if (json.isBlank()) { return new ArrayList<>(); }
        TodoItem[] items = JsonUtils.safeJsonLoads(json, TodoItem[].class, new TodoItem[0]);
        return new ArrayList<>(List.of(items));
    }

    @Override
    public void save(String sessionId, List<TodoItem> todos) throws IOException {
        Path file = resolveWorkspace().resolve(sessionId).resolve("todo.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, JsonUtils.safeJsonDumps(todos, "[]"));
    }

    @Override
    public void delete(String sessionId) throws IOException {
        Path dir = resolveWorkspace().resolve(sessionId);
        if (Files.exists(dir)) {
            Files.walk(dir).sorted(Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
        }
    }
}
