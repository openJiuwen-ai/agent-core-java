package com.openjiuwen.core.memory.manage.search;

import com.openjiuwen.core.memory.manage.index.BaseMemoryManager;
import com.openjiuwen.core.memory.manage.index.VariableManager;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.UserMemStore;
import com.openjiuwen.core.memory.manage.mem_model.VariableUnit;
import com.openjiuwen.core.memory.support.TestInMemoryKVStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SearchManagerTest {

    private SearchManager searchManager;
    private VariableManager variableManager;

    @BeforeEach
    void setUp() {
        TestInMemoryKVStore kvStore = new TestInMemoryKVStore();
        byte[] cryptoKey = "test_key_32_bytes_long_enough_12".getBytes();
        variableManager = new VariableManager(kvStore, cryptoKey);
        UserMemStore userMemStore = new UserMemStore(kvStore);
        Map<String, BaseMemoryManager> managers = new LinkedHashMap<>();
        managers.put(MemoryType.VARIABLE.getValue(), variableManager);
        searchManager = new SearchManager(managers, userMemStore, cryptoKey);
    }

    @Test
    void getUserVariableReturnsNullForEmptyName() {
        assertNull(searchManager.getUserVariable("user", "scope", ""));
    }

    @Test
    void getUserVariableReturnsNullForWhitespaceName() {
        assertNull(searchManager.getUserVariable("user", "scope", "   "));
    }

    @Test
    void getUserVariableReturnsStoredValue() {
        VariableUnit variableUnit = VariableUnit.builder()
                .variableName("test_variable")
                .variableMem("test_value")
                .build();

        variableManager.addMemories("user", "scope", List.of(variableUnit), null, Map.of());

        assertEquals("test_value", searchManager.getUserVariable("user", "scope", "test_variable"));
    }
}
