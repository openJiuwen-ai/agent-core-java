# Memory 模块中文 UT 测试报告

## 1. 背景

本次工作基于 Python 版本 memory 模块源码与单元测试行为，对 Java 版本 memory 模块进行对照检查、缺漏补齐、问题修复，并将 Python 侧关键 UT 场景提取后用 Java 实现对应单元测试。

目标包括：

- 对照 Python memory 源码，检查 Java memory 模块中的错误与缺漏。
- 将 Python memory 模块关键 UT 场景迁移为 Java 单元测试。
- 根据 Java 单元测试暴露的问题修复 Java memory 模块实现。
- 输出可复用的中文测试报告，记录范围、结果与风险。

## 2. 对照基线

### 2.1 Python 源码基线

- `agent-core-python/openjiuwen/core/memory/...`

### 2.2 Python 单测基线

本次主要参考了以下 Python 测试语义：

- `tests/unit_tests/core/memory/test_crypto.py`
- `tests/unit_tests/core/memory/test_search_manager.py`
- `tests/unit_tests/core/memory/test_set_scope_config.py`
- `tests/unit_tests/core/memory/test_sql_db_store.py`
- `tests/unit_tests/core/memory/test_timestamp_beijing_time.py`
- `tests/unit_tests/core/memory/manage/test_mem_update_checker.py`
- `tests/unit_tests/core/memory/prompt/test_prompt_applier.py`
- `tests/unit_tests/core/memory/migration/test_migration_plan.py`
- `tests/unit_tests/core/memory/migration/migrator/test_db_model.py`

## 3. Java 侧检查与修复范围

### 3.1 主要修复点

1. 修复 memory 模块中错误的类引用、包路径和类型声明，保证模块可编译。
2. 修复 `LongTermMemory` scope 配置加解密流程中的 `api_key` / `apiKey` 双写问题。
3. 修复缺省时间戳逻辑，使其与 Python 行为一致，使用系统默认时区。
4. 修复 `PromptApplier` 模板渲染、缓存清理与缺失模板提示行为。
5. 修复 `SqlDbStore` 查询结果字段大小写兼容问题，避免 H2/JDBC 返回大写列名导致读取异常。
6. 修复 `ModelClientConfig`、`EmbeddingConfig` 的 Jackson 序列化/反序列化兼容问题。
7. 修复 migration、search、update checker、embedding 初始化等 memory 相关配套逻辑缺漏。

### 3.2 关键实现修复说明

#### 3.2.1 Scope 配置加解密修复

问题：

- `LongTermMemory` 在写入 scope 配置时，同时写入 `api_key` 和 `apiKey`。
- 回读配置时，Jackson 反序列化链路对重复风格字段兼容不稳定，导致 `MemoryScopeConfig` 解析失败。

修复：

- 兼容读取历史数据中的 `apiKey`。
- 持久化时统一只保留规范字段 `api_key`。
- 读取后移除 `apiKey`，避免脏字段再次写回。

效果：

- scope 配置可正常持久化与回读。
- API Key 仍为加密存储，且读取后可正确解密。

#### 3.2.2 默认时间戳行为修复

问题：

- Java 版本在缺失时间戳时使用 UTC，不符合 Python 版本“跟随系统默认时区”的行为。

修复：

- 缺省时间戳改为 `OffsetDateTime.now(ZoneId.systemDefault())`。

效果：

- 与 Python 测试语义一致。
- 在 `Asia/Shanghai` 等环境下时间行为符合预期。

#### 3.2.3 PromptApplier 行为修复

修复内容：

- 缺失模板文件时返回明确错误信息。
- 补充无参 `clearCache()`。
- 修正模板格式化后的返回值处理逻辑。

#### 3.2.4 SqlDbStore 行为修复

修复内容：

- 补充 `getEngine()`。
- 结果集转 Map 时统一将列名转换为小写，避免数据库返回大写列名导致字段访问失败。

## 4. 新增 Java 单元测试

本次新增并实现以下 memory 相关测试：

- `src/test/java/com/openjiuwen/core/memory/common/MemoryCryptoTest.java`
- `src/test/java/com/openjiuwen/core/memory/prompt/PromptApplierTest.java`
- `src/test/java/com/openjiuwen/core/memory/manage/search/SearchManagerTest.java`
- `src/test/java/com/openjiuwen/core/memory/manage/update/MemUpdateCheckerTest.java`
- `src/test/java/com/openjiuwen/core/memory/manage/mem_model/SqlDbStoreTest.java`
- `src/test/java/com/openjiuwen/core/memory/manage/mem_model/DbModelTest.java`
- `src/test/java/com/openjiuwen/core/memory/migration/MigrationPlanTest.java`
- `src/test/java/com/openjiuwen/core/memory/LongTermMemoryTest.java`

同时新增测试支撑资源：

- `src/test/java/com/openjiuwen/core/memory/support/TestInMemoryKVStore.java`
- `src/test/java/com/openjiuwen/core/memory/support/TestDbStore.java`
- `src/test/resources/memory/prompt/ut_prompt_template.md`
- `src/test/resources/memory/prompt/ut_plain_template.md`

## 5. 测试覆盖说明

### 5.1 覆盖模块

| 模块 | 主要覆盖内容 |
| --- | --- |
| MemoryCrypto | 加解密回环、非法密钥长度校验 |
| PromptApplier | 单例、模板变量替换、缓存、缺失模板提示 |
| SearchManager | 空变量名、按名称读取变量、变量列表筛选 |
| MemUpdateChecker | 新增内存判断、冲突判断、异常 JSON 回退、输入格式化 |
| SqlDbStore | 插入、查询、更新、删除、存在性判断、排序行为 |
| DbModel | 建表、schema version 持久化、SQL 注册覆盖 |
| MigrationPlan | registry 查询、空结果处理、测试期 registry 覆盖 |
| LongTermMemory | scope 配置加解密持久化、空字符串变量读取、默认时区时间戳 |

### 5.2 测试数量

- 测试类数量：8
- 测试用例数量：27

## 6. 执行方式

执行命令：

```bash
mvn -q "-Dtest=MemoryCryptoTest,PromptApplierTest,SearchManagerTest,MemUpdateCheckerTest,SqlDbStoreTest,DbModelTest,MigrationPlanTest,LongTermMemoryTest" test
```

## 7. 测试结果

### 7.1 汇总结果

| 指标 | 数量 |
| --- | --- |
| 总用例数 | 27 |
| 通过 | 27 |
| 失败 | 0 |
| 错误 | 0 |
| 跳过 | 0 |

### 7.2 结果结论

- 本次迁移并实现的 Java memory 模块 UT 已全部通过。
- 对照 Python 基线提取的关键行为已在 Java 侧得到覆盖和验证。
- 本轮测试中发现的 scope 配置反序列化问题已修复并验证通过。

## 8. 过程中的特殊说明

### 8.1 MemUpdateChecker 的异常日志

`MemUpdateCheckerTest` 中包含“畸形 JSON 输入回退”的用例。该用例会触发 `JsonOutputParser` 解析错误日志，这是预期行为，用于验证当模型返回非法 JSON 时，Java 实现能正确走 fallback 分支而不是崩溃。

因此：

- 日志中出现 JSON parse error 不代表测试失败。
- 该类测试最终执行结果为通过。

## 9. 结论

本次 memory 模块修复与 UT 迁移工作已完成，结论如下：

1. Java memory 模块已完成与 Python 基线关键行为的对齐修复。
2. Java 侧补齐了 memory 核心子模块的单元测试，覆盖 27 个用例。
3. 目标测试集已全部通过，当前未发现阻断 memory 模块功能的已知问题。

## 10. 当前边界与后续建议

### 10.1 当前边界

- 本次验证范围聚焦 memory 相关目标测试集，并未执行全仓库 `mvn test` 全量回归。

### 10.2 后续建议

1. 建议在 CI 中纳入本次新增的 memory 测试集，避免后续回归破坏。
2. 建议后续继续对照 Python 版本补充更深层集成场景，例如向量检索、真实 embedding 初始化与更完整的 migration 路径验证。
3. 建议在后续全量回归中关注与 logging、retrieval、operator 等模块的交叉影响。
