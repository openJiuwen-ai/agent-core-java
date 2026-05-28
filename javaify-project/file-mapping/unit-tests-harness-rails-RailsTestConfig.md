# Python To Java Mapping Template

## 1. Basic Info

- Python source: `agent-core-0.1.12\tests\unit_tests\harness\rails\conftest.py`
- Java target: `src/test/java/com/openjiuwen/unit_tests/harness/rails/RailsTestConfig.java`
- Python project root: `./agent-core-0.1.12`
- Java project root: `./agent-core-java-0.1.12`
- Java package: `com.openjiuwen.unit_tests.harness.rails`
- Java class: `RailsTestConfig`
- Conversion date: 2026-05-19

## 2. Reference Baseline

- Fixed reference version: `0.1.7`
- Python reference: `D:\gitcode\zhangjun\agent-core-0.1.7\openjiuwen\core\application\workflow_agent\workflow_agent.py`
- Java reference: `D:\gitcode\zhangjun\agent-core-java-0.1.7\src\main\java\com\openjiuwen\core\application\workflow\WorkflowAgent.java`
- Canonical reference files present: No
- Fallback substitute references used: Existing test files in unit_tests package

## 3. File-Level Mapping

- Python module responsibility: Pytest conftest.py that stubs jsonschema_path module if not installed
- Java class responsibility: Test configuration class that provides SchemaPath stub for optional dependency handling
- Key framework abstractions reused: JUnit 5 static initialization pattern

## 4. Field Mapping

| Python field / state | Java field / state | Notes |
| --- | --- | --- |
| sys.modules["jsonschema_path"] | SchemaPathStub inner class | Java uses inner class instead of dynamic module stub |

## 5. Constructor And Initialization Mapping

| Python location | Java location | Mapping detail |
| --- | --- | --- |
| Module-level execution of _stub_jsonschema_path() | static{} initialization block | Java uses static initializer to run setup before tests |

## 6. Method Mapping

| Python method | Java method | Mapping detail |
| --- | --- | --- |
| _stub_jsonschema_path() | ensureSchemaPathStub() | Static method returns stub instance |
| m = types.ModuleType("jsonschema_path") | SchemaPathStub inner class | Class-based stub instead of dynamic module |
| m.SchemaPath = object | SchemaPathStub extends Object | Base Object behavior preserved |

## 7. Behavior Preservation

- Validation logic: Check for module existence → Java always provides stub (no conditional check needed)
- Branch logic: Python checks "jsonschema_path" not in sys.modules → Java uses static initialization
- State updates: Python updates sys.modules → Java uses static field
- Streaming behavior: Not applicable
- Error handling: None required for simple stub

## 8. Java-Specific Adaptations

- Additional helpers introduced: SchemaPathStub inner class for type-safe stub
- Type-system adaptations: Java uses class-based stub instead of Python's dynamic module creation
- Framework integration differences: No pytest conftest concept in Java; uses static initialization

## 9. Placeholder Tracking

No placeholders introduced.

## 10. Compile Verification

- Module or command: mvn compile
- Result: Pending verification
- Notes: Simple configuration class, minimal dependencies

## 11. Review Result

- `Mirrors Python's` present: Yes
- No missing Python behavior detected: Yes
- Mapping file refreshed after final code update: Yes
- Review passed: Pending compilation check
- Reviewer notes: Simple stub file translation, adapted to Java static initialization pattern