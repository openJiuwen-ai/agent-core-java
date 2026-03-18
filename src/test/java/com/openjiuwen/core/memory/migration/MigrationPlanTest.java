package com.openjiuwen.core.memory.migration;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationPlanTest {

    private Map<String, List<BaseOperation>> sqlBackup;
    private Map<String, List<BaseOperation>> vectorBackup;
    private Map<String, List<BaseOperation>> kvBackup;

    @BeforeEach
    void backupAndClearRegistries() {
        sqlBackup = copyOperations(MigrationPlan.getSqlRegistry().getAllOperations());
        vectorBackup = copyOperations(MigrationPlan.getVectorRegistry().getAllOperations());
        kvBackup = copyOperations(MigrationPlan.getKvRegistry().getAllOperations());

        MigrationPlan.getSqlRegistry().clear();
        MigrationPlan.getVectorRegistry().clear();
        MigrationPlan.getKvRegistry().clear();
    }

    @AfterEach
    void restoreRegistries() {
        MigrationPlan.getSqlRegistry().clear();
        MigrationPlan.getSqlRegistry().setOperations(sqlBackup);
        MigrationPlan.getVectorRegistry().clear();
        MigrationPlan.getVectorRegistry().setOperations(vectorBackup);
        MigrationPlan.getKvRegistry().clear();
        MigrationPlan.getKvRegistry().setOperations(kvBackup);
    }

    @Test
    void kvRegistryRegistersAndReturnsOperationsByVersionRange() {
        MigrationPlan.getKvRegistry().register("test_entity", new TestOperation(1, "v1"));
        MigrationPlan.getKvRegistry().register("test_entity", new TestOperation(2, "v2"));
        MigrationPlan.getKvRegistry().register("test_entity", new TestOperation(3, "v3"));

        List<BaseOperation> operations = MigrationPlan.getKvRegistry().getOperations("test_entity", 2, 3);

        assertEquals(2, operations.size());
        assertEquals(2, operations.get(0).getSchemaVersion());
        assertEquals(3, operations.get(1).getSchemaVersion());
        assertEquals(3, MigrationPlan.getKvRegistry().getCurrentVersion("test_entity"));
    }

    @Test
    void kvRegistryRejectsDuplicateOrLowerSchemaVersion() {
        MigrationPlan.getKvRegistry().register("test_entity", new TestOperation(3, "v3"));

        assertThrows(BaseError.class,
                () -> MigrationPlan.getKvRegistry().register("test_entity", new TestOperation(3, "duplicate")));
        assertThrows(BaseError.class,
                () -> MigrationPlan.getKvRegistry().register("test_entity", new TestOperation(1, "older")));
    }

    @Test
    void registriesSupportClearAndRestore() {
        MigrationPlan.getKvRegistry().register("entity1", new TestOperation(1, "one"));
        MigrationPlan.getKvRegistry().register("entity2", new TestOperation(2, "two"));

        Map<String, List<BaseOperation>> snapshot = copyOperations(MigrationPlan.getKvRegistry().getAllOperations());
        MigrationPlan.getKvRegistry().clear();
        assertTrue(MigrationPlan.getKvRegistry().getAllEntities().isEmpty());

        MigrationPlan.getKvRegistry().setOperations(snapshot);
        assertEquals(List.of("entity1", "entity2"), MigrationPlan.getKvRegistry().getAllEntities());
    }

    private static Map<String, List<BaseOperation>> copyOperations(Map<String, List<BaseOperation>> source) {
        Map<String, List<BaseOperation>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, new ArrayList<>(value)));
        return copy;
    }

    private static final class TestOperation extends BaseOperation {
        private TestOperation(int schemaVersion, String description) {
            super(new OperationMetadata(schemaVersion, description));
        }
    }
}
