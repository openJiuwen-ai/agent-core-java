# Store SPI API 文档

> 包路径：`com.openjiuwen.spi.store`

`store-spi.md` 是对存储 SPI 的补充文档。它不是 `com.openjiuwen.core` 下的一级业务模块，而是被 `foundation`、`retrieval`、`memory` 等模块复用的抽象接口层。

---

## 目录

- [1. 基础存储抽象](#1-基础存储抽象)
- [2. Query 表达式体系](#2-query-表达式体系)
- [3. Vector 存储 SPI](#3-vector-存储-spi)

---

## 1. 基础存储抽象

### 1.1 BaseDbStore

关系型数据库存储抽象基类。

**源码位置**：`com.openjiuwen.spi.store.BaseDbStore`

```java
public abstract class BaseDbStore<E>
```

| 方法签名 | 返回类型 | 说明 |
|---|---|---|
| `public abstract E getEngine()` | `E` | 返回底层数据库引擎或数据源，例如 JDBC `DataSource` |

### 1.2 BaseKVStore

KV 存储统一抽象；Java 版用同步阻塞接口替代 Python 版异步接口。

**源码位置**：`com.openjiuwen.spi.store.BaseKVStore`

| 方法签名 | 返回类型 | 说明 |
|---|---|---|
| `public abstract void set(String key, Object value)` | `void` | 设置或覆盖键值 |
| `public abstract boolean exclusiveSet(String key, Object value, Integer expiry)` | `boolean` | 仅当 key 不存在时写入，可指定过期秒数 |
| `public abstract Object get(String key)` | `Object` | 读取单个 key |
| `public abstract boolean exists(String key)` | `boolean` | 判断 key 是否存在 |
| `public abstract void delete(String key)` | `void` | 删除单个 key |
| `public abstract Map<String, Object> getByPrefix(String prefix)` | `Map<String, Object>` | 按前缀批量读取 |
| `public abstract void deleteByPrefix(String prefix, Integer batchSize)` | `void` | 按前缀批量删除 |
| `public abstract List<Object> mget(List<String> keys)` | `List<Object>` | 批量读取多个 key |
| `public abstract int batchDelete(List<String> keys, Integer batchSize)` | `int` | 批量删除多个 key |
| `public abstract KVStorePipeline pipeline()` | `KVStorePipeline` | 创建批处理管道 |

### 1.3 KVStorePipeline

KV 批处理管道，内部通过 `Function<List<Object[]>, List<Object>> executorFunc` 执行累计操作。

**源码位置**：`com.openjiuwen.spi.store.KVStorePipeline`

**构造方法**

```java
public KVStorePipeline(Function<List<Object[]>, List<Object>> executorFunc)
```

| 方法签名 | 返回类型 | 说明 |
|---|---|---|
| `public KVStorePipeline set(String key, Object value)` | `KVStorePipeline` | 追加 `set` 操作 |
| `public KVStorePipeline get(String key)` | `KVStorePipeline` | 追加 `get` 操作 |
| `public KVStorePipeline exists(String key)` | `KVStorePipeline` | 追加 `exists` 操作 |
| `public List<Object> execute()` | `List<Object>` | 执行累计操作并清空当前队列 |

### 1.4 BaseObjectStorageClient

对象存储抽象客户端。

**源码位置**：`com.openjiuwen.spi.store.object.BaseObjectStorageClient`

| 方法签名 | 返回类型 | 说明 |
|---|---|---|
| `public abstract boolean uploadFile(String bucketName, String objectName, Path filePath) throws Exception` | `boolean` | 上传本地文件 |
| `public abstract boolean downloadFile(String bucketName, String objectName, Path filePath) throws Exception` | `boolean` | 下载对象到本地 |
| `public abstract boolean deleteObject(String bucketName, String objectName) throws Exception` | `boolean` | 删除对象 |
| `public abstract boolean createBucket(String bucketName, String location) throws Exception` | `boolean` | 创建桶 |
| `public abstract boolean deleteBucket(String bucketName) throws Exception` | `boolean` | 删除桶 |
| `public abstract List<Map<String, Object>> listObjects(String bucketName, String objectPrefix, int maxObjects) throws Exception` | `List<Map<String, Object>>` | 列举对象元数据 |

---

## 2. Query 表达式体系

### 2.1 QueryExpr

所有查询表达式的抽象基类，提供逻辑组合与字符串转义工具。

**源码位置**：`com.openjiuwen.spi.store.query.QueryExpr`

| 方法签名 | 返回类型 | 说明 |
|---|---|---|
| `public LogicalExpr and(QueryExpr other)` | `LogicalExpr` | 与另一个表达式做 `AND` |
| `public LogicalExpr or(QueryExpr other)` | `LogicalExpr` | 与另一个表达式做 `OR` |
| `public LogicalExpr xor(QueryExpr other)` | `LogicalExpr` | 与另一个表达式做 `XOR` |
| `public LogicalExpr not()` | `LogicalExpr` | 取反 |
| `public static String sanitizeStr(Object value)` | `String` | 对值加双引号并转义内部引号 |
| `public abstract Object toExpr(String database)` | `Object` | 转换成具体数据库方言表达式 |

### 2.2 QueryExpressions

静态工厂方法集合，对应 Python 版 `store.query` 的顶层辅助函数。

**源码位置**：`com.openjiuwen.spi.store.query.QueryExpressions`

| 方法签名 | 返回类型 | 说明 |
|---|---|---|
| `public static ComparisonExpr eq(String field, Object value)` | `ComparisonExpr` | 等于过滤 |
| `public static ComparisonExpr ne(String field, Object value)` | `ComparisonExpr` | 不等于过滤 |
| `public static ComparisonExpr gt(String field, Number value)` | `ComparisonExpr` | 大于过滤 |
| `public static ComparisonExpr lt(String field, Number value)` | `ComparisonExpr` | 小于过滤 |
| `public static ComparisonExpr gte(String field, Number value)` | `ComparisonExpr` | 大于等于过滤 |
| `public static ComparisonExpr lte(String field, Number value)` | `ComparisonExpr` | 小于等于过滤 |
| `public static QueryExpr inList(String field, Collection<?> values)` | `QueryExpr` | 构造 `IN` 过滤；单元素时退化为 `ComparisonExpr` |
| `public static RangeExpr wildcardMatch(String field, String pattern, String operator)` | `RangeExpr` | 构造通配匹配 |
| `public static RangeExpr wildcardMatch(String field, String pattern)` | `RangeExpr` | 默认 `operator="wildcard"` |
| `public static NullExpr isNull(String field)` | `NullExpr` | 构造 `IS NULL` |
| `public static NullExpr isNotNull(String field)` | `NullExpr` | 构造 `IS NOT NULL` |
| `public static JSONExpr jsonKey(String field, String key, String operator, Object value)` | `JSONExpr` | 构造 JSON key 过滤 |
| `public static ArrayExpr arrayIndex(String field, int index, String operator, Object value)` | `ArrayExpr` | 构造数组下标过滤 |
| `public static QueryExpr filterUser(List<String> users, String userIdField)` | `QueryExpr` | 对指定用户字段做用户过滤 |
| `public static QueryExpr filterUser(List<String> users)` | `QueryExpr` | 使用默认字段 `user_id` |
| `public static QueryExpr filterUser(String user)` | `QueryExpr` | 单用户快捷写法 |
| `public static QueryExpr chainFilters(List<QueryExpr> filters)` | `QueryExpr` | 依次用 `AND` 串联多个过滤条件 |

### 2.3 具体表达式类型

以下类型都位于 `com.openjiuwen.spi.store.query` 包，并统一实现 `toExpr(String database)`。

| 类型 | 构造方法 | 公开方法 | 说明 |
|---|---|---|---|
| `ArithmeticExpr` | `ArithmeticExpr(String field, String arithmeticOperator, Number arithmeticValue, String comparisonOperator, Number comparisonValue)` | `getField()`、`getArithmeticOperator()`、`getArithmeticValue()`、`getComparisonOperator()`、`getComparisonValue()`、`toExpr(...)` | 算术运算后再比较 |
| `ArrayExpr` | `ArrayExpr(String field, Integer index, String operator, Object value)` | `getField()`、`getIndex()`、`getOperator()`、`getValue()`、`toExpr(...)` | 数组字段过滤；`index` 可为 `null` |
| `ComparisonExpr` | `ComparisonExpr(String field, String operator, Object value)` | `getField()`、`getOperator()`、`getValue()`、`toExpr(...)` | 普通比较过滤 |
| `CustomExpr` | `CustomExpr(Object expr)` | `getExpr()`、`toExpr(...)` | 直接包裹数据库原生表达式 |
| `JSONExpr` | `JSONExpr(String field, String key, String operator, Object value)` | `getField()`、`getKey()`、`getOperator()`、`getValue()`、`toExpr(...)` | JSON 字段过滤 |
| `LogicalExpr` | `LogicalExpr(String operator, QueryExpr left, QueryExpr right)` | `getOperator()`、`getLeft()`、`getRight()`、`toExpr(...)` | 逻辑组合；`not` 场景下 `right` 为 `null` |
| `MatchExpr` | `MatchExpr(String field, String value, MatchMode matchMode)`、`MatchExpr(String field, String value)` | `getField()`、`getValue()`、`getMatchMode()`、`toExpr(...)` | 文本匹配；二参构造默认使用 `MatchMode.EXACT` |
| `NullExpr` | `NullExpr(String field, boolean isNull)` | `getField()`、`isNull()`、`toExpr(...)` | 空值检查 |
| `RangeExpr` | `RangeExpr(String field, String operator, Object value)` | `getField()`、`getOperator()`、`getValue()`、`getValueAsCollection()`、`toExpr(...)` | 范围/通配/in 过滤；`value` 可能是集合或模式字符串 |

### 2.4 MatchMode

文本匹配模式枚举。

**源码位置**：`com.openjiuwen.spi.store.query.MatchMode`

**枚举常量**

- `PREFIX`
- `SUFFIX`
- `INFIX`
- `EXACT`

### 2.5 QueryLanguageDefinition

数据库查询方言定义对象，把不同 `QueryExpr` 子类映射到数据库原生表达式。

**源码位置**：`com.openjiuwen.spi.store.query.QueryLanguageDefinition`

| 方法签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object applyComparison(QueryExpr expr)` | `Object` | 应用 comparison 处理函数 |
| `public Object applyRange(QueryExpr expr)` | `Object` | 应用 range 处理函数 |
| `public Object applyArithmetic(QueryExpr expr)` | `Object` | 应用 arithmetic 处理函数 |
| `public Object applyNullCheck(QueryExpr expr)` | `Object` | 应用 nullCheck 处理函数 |
| `public Object applyJsonFilter(QueryExpr expr)` | `Object` | 应用 jsonFilter 处理函数 |
| `public Object applyArray(QueryExpr expr)` | `Object` | 应用 array 处理函数 |
| `public Object applyLogical(QueryExpr expr)` | `Object` | 应用 logical 处理函数 |
| `public Object applyTextMatch(QueryExpr expr)` | `Object` | 应用 textMatch 处理函数 |
| `public static Builder builder()` | `Builder` | 创建构建器 |

### 2.6 QueryLanguageDefinition.Builder

`QueryLanguageDefinition` 的嵌套构建器。

| 方法签名 | 返回类型 | 说明 |
|---|---|---|
| `public Builder comparison(Function<QueryExpr, Object> comparison)` | `Builder` | 设置 comparison 处理函数 |
| `public Builder range(Function<QueryExpr, Object> range)` | `Builder` | 设置 range 处理函数 |
| `public Builder arithmetic(Function<QueryExpr, Object> arithmetic)` | `Builder` | 设置 arithmetic 处理函数 |
| `public Builder nullCheck(Function<QueryExpr, Object> nullCheck)` | `Builder` | 设置 nullCheck 处理函数 |
| `public Builder jsonFilter(Function<QueryExpr, Object> jsonFilter)` | `Builder` | 设置 jsonFilter 处理函数 |
| `public Builder array(Function<QueryExpr, Object> array)` | `Builder` | 设置 array 处理函数 |
| `public Builder logical(Function<QueryExpr, Object> logical)` | `Builder` | 设置 logical 处理函数 |
| `public Builder textMatch(Function<QueryExpr, Object> textMatch)` | `Builder` | 设置 textMatch 处理函数 |
| `public QueryLanguageDefinition build()` | `QueryLanguageDefinition` | 构建方言定义对象 |

### 2.7 QueryLanguageRegistry

查询方言注册中心，替代 Python 版的模块级全局字典。

**源码位置**：`com.openjiuwen.spi.store.query.QueryLanguageRegistry`

| 方法签名 | 返回类型 | 说明 |
|---|---|---|
| `public static void register(String name, QueryLanguageDefinition definition)` | `void` | 注册方言 |
| `public static QueryLanguageDefinition get(String name)` | `QueryLanguageDefinition` | 按名称读取方言；未注册时抛出 `RETRIEVAL_VECTOR_STORE_QUERY_INVALID` |
| `public static boolean isRegistered(String name)` | `boolean` | 判断是否已注册 |

---

## 3. Vector 存储 SPI

### 3.1 BaseVectorStore

向量存储统一抽象，要求调用方在写入/搜索前自行准备向量数据。

**源码位置**：`com.openjiuwen.spi.store.vector.BaseVectorStore`

| 方法签名 | 返回类型 | 说明 |
|---|---|---|
| `public abstract void createCollection(String collectionName, Object schema, Map<String, Object> kwargs) throws Exception` | `void` | 创建集合 |
| `public abstract void deleteCollection(String collectionName, Map<String, Object> kwargs) throws Exception` | `void` | 删除集合 |
| `public abstract boolean collectionExists(String collectionName, Map<String, Object> kwargs) throws Exception` | `boolean` | 判断集合是否存在 |
| `public abstract CollectionSchema getSchema(String collectionName, Map<String, Object> kwargs) throws Exception` | `CollectionSchema` | 读取集合 schema |
| `public abstract void addDocs(String collectionName, List<Map<String, Object>> docs, Map<String, Object> kwargs) throws Exception` | `void` | 写入文档；文档中需包含字段值和向量值 |
| `public abstract List<VectorSearchResult> search(String collectionName, List<Float> queryVector, String vectorField, int topK, Map<String, Object> filters, Map<String, Object> kwargs) throws Exception` | `List<VectorSearchResult>` | 相似度搜索 |
| `public abstract void deleteDocsByIds(String collectionName, List<String> ids, Map<String, Object> kwargs) throws Exception` | `void` | 按 ID 删除文档 |
| `public abstract void deleteDocsByFilters(String collectionName, Map<String, Object> filters, Map<String, Object> kwargs) throws Exception` | `void` | 按标量过滤条件删除文档 |

### 3.2 CollectionSchema

向量集合 schema 定义对象。

**源码位置**：`com.openjiuwen.spi.store.vector.CollectionSchema`

| 字段 | 类型 | 说明 |
|---|---|---|
| `fields` | `List<FieldSchema>` | 字段定义列表 |
| `description` | `String` | 集合描述 |
| `enableDynamicField` | `boolean` | 是否启用动态字段 |

**构造方法**

```java
public CollectionSchema(List<FieldSchema> fields, String description, boolean enableDynamicField)
public CollectionSchema()
```

| 方法签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<FieldSchema> getFields()` | `List<FieldSchema>` | 返回字段列表 |
| `public String getDescription()` | `String` | 返回描述 |
| `public boolean isEnableDynamicField()` | `boolean` | 返回动态字段开关 |
| `public CollectionSchema addField(FieldSchema field)` | `CollectionSchema` | 添加字段；重复字段名或重复主键会抛错 |
| `public CollectionSchema addField(Map<String, Object> fieldDict)` | `CollectionSchema` | 从字典定义添加字段 |
| `public CollectionSchema removeField(String fieldName)` | `CollectionSchema` | 删除字段 |
| `public Optional<FieldSchema> getField(String fieldName)` | `Optional<FieldSchema>` | 按名称查字段 |
| `public boolean hasField(String fieldName)` | `boolean` | 判断字段是否存在 |
| `public Optional<FieldSchema> getPrimaryKeyField()` | `Optional<FieldSchema>` | 获取主键字段 |
| `public List<FieldSchema> getVectorFields()` | `List<FieldSchema>` | 获取所有向量字段 |
| `public Map<String, Object> toDict()` | `Map<String, Object>` | 导出为字典 |
| `public static CollectionSchema fromDict(Map<String, Object> data)` | `CollectionSchema` | 从字典恢复 schema |
| `public static CollectionSchema fromFields(List<?> fields, String description, boolean enableDynamicField)` | `CollectionSchema` | 从 `FieldSchema` / `Map` 混合列表创建 schema |

### 3.3 FieldSchema

单个向量字段的 schema 定义。

**源码位置**：`com.openjiuwen.spi.store.vector.FieldSchema`

| 字段 | 类型 | Builder 默认值 | 说明 |
|---|---|---|---|
| `name` | `String` | - | 字段名 |
| `dtype` | `VectorDataType` | `VARCHAR` | 字段类型 |
| `isPrimary` | `boolean` | `false` | 是否主键 |
| `autoId` | `boolean` | `false` | 是否自动生成主键 |
| `maxLength` | `Integer` | `65535` | 字符串最大长度 |
| `dim` | `Integer` | - | 向量维度；`FLOAT_VECTOR` 必填 |
| `elementType` | `VectorDataType` | - | `ARRAY` 元素类型 |
| `maxCapacity` | `Integer` | - | `ARRAY` 最大容量 |
| `description` | `String` | - | 字段描述 |

| 方法签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getName()` | `String` | 获取字段名 |
| `public VectorDataType getDtype()` | `VectorDataType` | 获取字段类型 |
| `public boolean isPrimary()` | `boolean` | 是否主键 |
| `public boolean isAutoId()` | `boolean` | 是否自动 ID |
| `public Integer getMaxLength()` | `Integer` | 获取最大长度 |
| `public Integer getDim()` | `Integer` | 获取向量维度 |
| `public VectorDataType getElementType()` | `VectorDataType` | 获取数组元素类型 |
| `public Integer getMaxCapacity()` | `Integer` | 获取数组最大容量 |
| `public String getDescription()` | `String` | 获取描述 |
| `public Map<String, Object> toDict()` | `Map<String, Object>` | 导出字段定义 |
| `public static FieldSchema fromDict(Map<String, Object> data)` | `FieldSchema` | 从字典恢复字段定义 |
| `public static Builder builder()` | `Builder` | 创建构建器 |

**校验规则**

- `dim <= 0` 时抛出 `STORE_VECTOR_SCHEMA_INVALID`
- `dtype == FLOAT_VECTOR` 且缺少 `dim` 时抛出 `STORE_VECTOR_SCHEMA_INVALID`

### 3.4 FieldSchema.Builder

`FieldSchema` 的嵌套构建器。

| 方法签名 | 返回类型 | 说明 |
|---|---|---|
| `public Builder name(String name)` | `Builder` | 设置字段名 |
| `public Builder dtype(VectorDataType dtype)` | `Builder` | 设置字段类型 |
| `public Builder isPrimary(boolean isPrimary)` | `Builder` | 设置是否主键 |
| `public Builder autoId(boolean autoId)` | `Builder` | 设置是否自动 ID |
| `public Builder maxLength(Integer maxLength)` | `Builder` | 设置最大长度 |
| `public Builder dim(Integer dim)` | `Builder` | 设置向量维度 |
| `public Builder elementType(VectorDataType elementType)` | `Builder` | 设置数组元素类型 |
| `public Builder maxCapacity(Integer maxCapacity)` | `Builder` | 设置数组最大容量 |
| `public Builder description(String description)` | `Builder` | 设置字段描述 |
| `public FieldSchema build()` | `FieldSchema` | 构建字段定义 |

### 3.5 VectorDataType

向量存储字段支持的数据类型枚举。

**源码位置**：`com.openjiuwen.spi.store.vector.VectorDataType`

**枚举常量**

- `VARCHAR`
- `FLOAT_VECTOR`
- `INT64`
- `INT32`
- `INT16`
- `INT8`
- `FLOAT`
- `DOUBLE`
- `BOOL`
- `JSON`
- `ARRAY`

### 3.6 VectorSearchResult

向量检索结果模型，使用 Lombok `@Data` + `@Builder` + `@AllArgsConstructor`。

**源码位置**：`com.openjiuwen.spi.store.vector.VectorSearchResult`

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `score` | `double` | - | 相似度/相关性分数，越高越相关 |
| `fields` | `Map<String, Object>` | `Map.of()` | 命中文档的全部字段值 |

**Lombok 生成方法**

- 按字段生成 getter
- Builder：`builder()`
- 全参构造：`VectorSearchResult(double score, Map<String, Object> fields)`
