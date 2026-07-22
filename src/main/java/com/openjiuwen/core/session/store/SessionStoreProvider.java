package com.openjiuwen.core.session.store;

import java.util.Map;

public interface SessionStoreProvider {
    String typeName();

    Store createStore(Map<String, Object> conf);
}
