/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.skills;

import com.openjiuwen.core.sys_operation.BaseFsOperation;
import com.openjiuwen.core.sys_operation.protocal.BaseFsProtocal;
import com.openjiuwen.core.sys_operation.result.FileSystemData;
import com.openjiuwen.core.sys_operation.result.FileSystemItem;
import com.openjiuwen.core.sys_operation.result.ListDirsResult;
import com.openjiuwen.core.sys_operation.result.ListFilesResult;
import com.openjiuwen.core.sys_operation.result.ReadFileData;
import com.openjiuwen.core.sys_operation.result.ReadFileResult;
import org.yaml.snakeyaml.Yaml;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * Registry for skill metadata loaded from {@code SKILL.md} files.
 *
 * <p>Mirrors Python's {@code SkillManager} in
 * {@code openjiuwen/core/single_agent/skills/skill_manager.py}.</p>
 */
public class SkillManager {
    private final Map<String, Skill> registry = new LinkedHashMap<>();
    private final Function<String, BaseFsOperation> fsResolver;
    private String sysOperationId;
    private String description = "";

    public SkillManager(String sysOperationId) {
        this(sysOperationId, SkillManager::resolveFsOperation);
    }

    SkillManager(String sysOperationId, Function<String, BaseFsOperation> fsResolver) {
        this.sysOperationId = sysOperationId;
        this.fsResolver = fsResolver == null ? SkillManager::resolveFsOperation : fsResolver;
    }

    public void setSysOperationId(String sysOperationId) {
        this.sysOperationId = sysOperationId;
    }

    public void set_sys_operation_id(String sysOperationId) {
        setSysOperationId(sysOperationId);
    }

    public Skill register(Path skillPath) throws IOException {
        List<Skill> registered = register(List.of(skillPath), false);
        return registered.isEmpty() ? null : registered.get(0);
    }

    public List<Skill> register(Path skillPath, boolean overwrite) throws IOException {
        if (skillPath == null) {
            return List.of();
        }
        return register(List.of(skillPath), overwrite);
    }

    public List<Skill> register(List<Path> skillPaths, boolean overwrite) throws IOException {
        List<Skill> registered = new ArrayList<>();
        if (skillPaths == null) {
            return registered;
        }
        BaseFsOperation fs = getFsOperation();
        for (Path path : skillPaths) {
            registered.addAll(registerRoot(fs, path, overwrite));
        }
        return registered;
    }

    public void unregister(String name) {
        registry.remove(name);
    }

    public Skill get(String name) {
        return registry.get(name);
    }

    public List<Skill> getAll() {
        return new ArrayList<>(registry.values());
    }

    public List<Skill> get_all() {
        return getAll();
    }

    public List<String> getNames() {
        return new ArrayList<>(registry.keySet());
    }

    public List<String> get_names() {
        return getNames();
    }

    public boolean has(String name) {
        return registry.containsKey(name);
    }

    public void clear() {
        registry.clear();
    }

    public int count() {
        return registry.size();
    }

    public String getSysOperationId() {
        return sysOperationId;
    }

    public String getDescription() {
        return description;
    }

    private List<Skill> registerRoot(BaseFsOperation fs, Path root, boolean overwrite) throws IOException {
        List<Skill> registered = new ArrayList<>();
        if (root == null) {
            return registered;
        }

        ListDirsResult dirsResult = join(fs.listDirectories(
                root.toString(),
                false,
                null,
                BaseFsOperation.SortBy.NAME,
                false,
                null
        ));
        if (dirsResult.getCode() != 0) {
            Optional<Skill> direct = createSkillFromPath(fs, root);
            if (direct.isPresent()) {
                addToRegistry(direct.get(), overwrite);
                registered.add(direct.get());
            }
            return registered;
        }

        Optional<Path> directSkillMd = findSkillMd(fs, root.toString());
        if (directSkillMd.isPresent()) {
            Optional<Skill> skill = createSkillFromPath(fs, directSkillMd.get());
            if (skill.isPresent()) {
                addToRegistry(skill.get(), overwrite);
                registered.add(skill.get());
            }
            return registered;
        }

        FileSystemData dirData = dirsResult.getData();
        List<FileSystemItem> dirItems = dirData == null ? null : dirData.getListItems();
        if (dirItems == null) {
            return registered;
        }

        for (FileSystemItem child : dirItems) {
            if (child == null || child.getPath() == null || child.getName() == null) {
                continue;
            }
            Optional<Path> childSkillMd = findSkillMd(fs, child.getPath());
            if (childSkillMd.isEmpty()) {
                continue;
            }
            Optional<Skill> skill = createSkillFromPath(fs, childSkillMd.get());
            if (skill.isPresent()) {
                addToRegistry(skill.get(), overwrite);
                registered.add(skill.get());
            }
        }
        return registered;
    }

    private Optional<Skill> createSkillFromPath(BaseFsOperation fs, Path path) throws IOException {
        String descriptionText = loadDescription(fs, path);
        if (descriptionText == null) {
            return Optional.empty();
        }
        Path skillDirectory = path.getParent();
        String skillName = skillDirectory == null ? "" : skillDirectory.getFileName().toString();
        return Optional.of(new Skill(skillName, descriptionText, skillDirectory));
    }

    private String loadDescription(BaseFsOperation fs, Path path) throws IOException {
        description = "";
        ReadFileResult result = join(fs.readFile(
                path.toString(),
                BaseFsOperation.FileMode.TEXT,
                null,
                null,
                (BaseFsProtocal.LineRange) null,
                StandardCharsets.UTF_8.name(),
                BaseFsOperation.DEFAULT_READ_CHUNK_SIZE,
                null
        ));
        if (result.getCode() != 0) {
            throw new FileNotFoundException(result.getMessage());
        }
        ReadFileData data = result.getData();
        Object content = data == null ? null : data.getContent();
        if (content == null) {
            throw new FileNotFoundException("read_file is None: " + path);
        }

        String text = content instanceof String value ? value : String.valueOf(content);
        Map<String, Object> yamlData = loadYamlFrontMatter(text);
        if (yamlData == null || !yamlData.containsKey("description")) {
            throw new IllegalArgumentException("Skill.md file does not contain a description field");
        }
        description = String.valueOf(yamlData.get("description"));
        return description;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYamlFrontMatter(String text) {
        if (text == null || !text.startsWith("---")) {
            return null;
        }
        int end = text.indexOf("---", 3);
        if (end < 0) {
            return null;
        }
        Object loaded = new Yaml().load(text.substring(3, end));
        return loaded instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private BaseFsOperation getFsOperation() {
        if (sysOperationId == null || sysOperationId.isBlank()) {
            throw new IllegalStateException("sys_operation is not available");
        }
        BaseFsOperation fs = fsResolver.apply(sysOperationId);
        if (fs == null) {
            throw new IllegalStateException("sys_operation is not available");
        }
        return fs;
    }

    private static Optional<Path> findSkillMd(BaseFsOperation fs, String directory) throws IOException {
        ListFilesResult filesResult = join(fs.listFiles(
                directory,
                false,
                null,
                BaseFsOperation.SortBy.NAME,
                false,
                null,
                null
        ));
        if (filesResult.getCode() != 0) {
            return Optional.empty();
        }
        FileSystemData fileData = filesResult.getData();
        List<FileSystemItem> fileItems = fileData == null ? null : fileData.getListItems();
        if (fileItems == null) {
            return Optional.empty();
        }
        for (FileSystemItem item : fileItems) {
            if (item == null || item.getName() == null || item.getPath() == null) {
                continue;
            }
            if ("skill.md".equals(item.getName().toLowerCase(Locale.ROOT))) {
                return Optional.of(Path.of(item.getPath()));
            }
        }
        return Optional.empty();
    }

    private void addToRegistry(Skill skill, boolean overwrite) {
        if (!overwrite && registry.containsKey(skill.getName())) {
            throw new IllegalArgumentException("Skill already exists: " + skill.getName());
        }
        registry.put(skill.getName(), skill);
    }

    private static BaseFsOperation resolveFsOperation(String sysOperationId) {
        try {
            Class<?> runnerType = Class.forName("com.openjiuwen.core.runner.Runner");
            Object resourceMgr = runnerType.getMethod("resourceMgr").invoke(null);
            Object sysOperation = resourceMgr.getClass()
                    .getMethod("getSysOperation", String.class)
                    .invoke(resourceMgr, sysOperationId);
            if (sysOperation == null) {
                return null;
            }
            Method fsMethod = sysOperation.getClass().getMethod("fs");
            Object fs = fsMethod.invoke(sysOperation);
            return fs instanceof BaseFsOperation operation ? operation : null;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("failed to resolve sys_operation " + sysOperationId, error);
        }
    }

    private static <T> T join(CompletableFuture<T> future) throws IOException {
        try {
            return future.join();
        } catch (CompletionException error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IOException(cause);
        }
    }
}
