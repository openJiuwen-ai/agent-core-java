package com.openjiuwen.core.session.store;

import java.nio.file.Path;
import java.util.Map;

public class FileSessionStoreProvider implements SessionStoreProvider {
    @Override
    public String typeName() {
        return "file";
    }

    @Override
    public Store createStore(Map<String, Object> conf) {
        String storePath = conf != null ? (String) conf.get("storePath") : null;
        return new FileStore(Path.of(storePath != null ? storePath : "session_store.json"));
    }
}
