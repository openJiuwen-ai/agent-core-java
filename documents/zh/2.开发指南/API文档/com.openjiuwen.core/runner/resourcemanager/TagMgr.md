# com.openjiuwen.core.runner.resourcemanager.TagMgr

## 类 TagMgr

```java
public class TagMgr
```

`TagMgr` 负责资源与标签的双向索引、组合匹配和调试展示。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `resourceTags` | `Map<String, Set<String>>` | `new HashMap<>()` | - |
| `tagToResource` | `Map<String, Set<String>>` | `new HashMap<>()` | - |
| `lock` | `ReentrantLock` | `new ReentrantLock()` | - |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public TagMgr()` | - |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void clear()` | 清空全部标签与资源映射，恢复初始状态。 |
| `public boolean hasTag(String tag)` | - |
| `public List<String> listTags()` | - |
| `public boolean hasResource(String resourceId)` | - |
| `public boolean hasResourceTag(String resourceId, String tag)` | - |
| `public List<String> getResourcesTags(String resourceId)` | - |
| `public List<String> tagResource(String resourceId, Object tags)` | - |
| `public List<String> removeResource(String resourceId)` | - |
| `public List<String> removeResourceTags(String resourceId, Object tags, boolean skipIfNotExists)` | - |
| `public List<String> updateResourceTags(String resourceId, Object tags, TagUpdateStrategy strategy)` | - |
| `public List<String> removeTag(String tag, boolean skipIfNotExists)` | - |
| `public List<String> getTagResources(String tag)` | - |
| `public List<String> findResourcesByTags(Object tags, TagMatchStrategy strategy, boolean skipIfNotExists)` | - |
| `public String display(boolean enableLog)` | 输出当前标签管理器状态，并可控制是否写入日志。 |
| `public String display()` | 输出当前标签管理器状态，默认启用日志记录。 |

## 相关测试

- `TagMgrTest`
