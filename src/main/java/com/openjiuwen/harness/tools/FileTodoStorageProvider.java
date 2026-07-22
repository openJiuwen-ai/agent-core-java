package com.openjiuwen.harness.tools;

import java.nio.file.Path;
import java.util.Map;

public class FileTodoStorageProvider implements TodoStorageProvider {
    @Override
    public String typeName() {
        return "file";
    }

    @Override
    public TodoStorage create(Map<String, Object> conf) {
        String basePath = conf != null ? (String) conf.get("basePath") : null;
        return new FileTodoStorage(Path.of(basePath != null ? basePath : "."));
    }
}
