# com.openjiuwen.core.foundation.store.graph.GraphStoreStorageConfig

## class GraphStoreStorageConfig

```java
public class GraphStoreStorageConfig
```

图存储字段容量配置对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `uuid` | `int` | `32` | UUID 字段长度。 |
| `name` | `int` | `500` | 名称字段长度。 |
| `content` | `int` | `65535` | 内容字段长度。 |
| `language` | `int` | `10` | 语言字段长度。 |
| `userId` | `int` | `32` | 用户 ID 字段长度。 |
| `entities` | `int` | `4096` | 实体字段长度。 |
| `relations` | `int` | `4096` | 关系字段长度。 |
| `episodes` | `int` | `4096` | 事件字段长度。 |
| `objType` | `int` | `20` | 对象类型字段长度。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public GraphStoreStorageConfig()` | 使用默认字段容量配置。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public int getUuid()` | 返回 `uuid` 长度。 |
| `public int getName()` | 返回 `name` 长度。 |
| `public int getContent()` | 返回 `content` 长度。 |
| `public int getLanguage()` | 返回 `language` 长度。 |
| `public int getUserId()` | 返回 `userId` 长度。 |
| `public int getEntities()` | 返回 `entities` 长度。 |
| `public int getRelations()` | 返回 `relations` 长度。 |
| `public int getEpisodes()` | 返回 `episodes` 长度。 |
| `public int getObjType()` | 返回 `objType` 长度。 |
| `public static Builder builder()` | 创建构建器。 |

## 使用说明

- `Builder` 用于链式覆盖默认字段容量配置，再通过 `build()` 生成实例。
