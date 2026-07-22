package com.openjiuwen.harness.tools;

import java.util.List;

public interface TodoStorageProvider {
    String typeName();

    TodoStorage create(java.util.Map<String, Object> conf);
}
