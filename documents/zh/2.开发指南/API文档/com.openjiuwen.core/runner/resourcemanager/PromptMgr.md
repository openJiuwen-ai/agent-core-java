# com.openjiuwen.core.runner.resourcemanager.PromptMgr

## 类 PromptMgr

```java
public class PromptMgr
```

`PromptMgr` 负责 `PromptTemplate` 的注册、批量导入、查询、移除与清空。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `repo` | `ConcurrentHashMap<String, PromptTemplate>` | `new ConcurrentHashMap<>()` | - |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void addPrompt(String templateId, PromptTemplate template)` | - |
| `public void addPrompts(List<PromptEntry> templates)` | - |
| `public PromptTemplate removePrompt(String templateId)` | - |
| `public void clear()` | 清空已注册的所有提示模板。 |
| `public PromptTemplate getPrompt(String templateId)` | - |

## 嵌套类型

- `PromptEntry`: -
