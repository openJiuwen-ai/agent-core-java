# com.openjiuwen.core.sys_operation.BaseCodeOperation

## 类 BaseCodeOperation

```java
public abstract class BaseCodeOperation extends BaseOperation
```

`BaseCodeOperation` 是代码执行能力的抽象基类，约定了同步执行和流式执行两套公开接口，并默认把这两种能力暴露为工具。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public List<ToolCard> listTools()` | 返回 `executeCode` 与 `executeCodeStream` 两个标准工具卡片。 |
| `public abstract ExecuteCodeResult executeCode( String code, String language, int timeout, Map<String, String> environment, Map<String, Object> options)` | 同步执行代码，返回完整结果对象。 |
| `public abstract Iterator<ExecuteCodeStreamResult> executeCodeStream( String code, String language, int timeout, Map<String, String> environment, Map<String, Object> options)` | 流式执行代码，按 chunk 返回输出和退出信息。 |

## 相关测试

- `LocalCodeOperationTest`
