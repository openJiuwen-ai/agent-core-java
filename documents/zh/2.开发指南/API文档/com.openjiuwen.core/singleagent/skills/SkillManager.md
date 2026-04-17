# com.openjiuwen.core.singleagent.skills.SkillManager

## 类 SkillManager

```java
public class SkillManager
```

负责本地技能的注册、查询、注销与统计。

## 构造方法

| 签名 | 说明 |
|---|---|
| `public SkillManager(String sysOperationId)` | 使用给定的 `sysOperationId` 初始化技能管理器。 |

## 方法

| 签名 | 说明 |
|---|---|
| `public void setSysOperationId(String sysOperationId)` | 更新当前 `sysOperationId`。 |
| `public String getSysOperationId()` | 返回当前 `sysOperationId`。 |
| `public void register(String skillPath, String sessionId, boolean overwrite)` | 从字符串路径注册技能；空路径会直接返回，内部异常只记录告警。 |
| `public void register(String skillPath)` | 以默认 `sessionId = null`、`overwrite = false` 注册单个路径。 |
| `public void register(Path skillPath, String sessionId, boolean overwrite)` | `Path` 版本的注册入口，最终会转为字符串路径处理。 |
| `public void register(Path skillPath)` | `Path` 版本的简化重载。 |
| `public void register(List<String> skillPaths, String sessionId, boolean overwrite)` | 逐个注册字符串路径列表中的技能。 |
| `public void registerPaths(List<Path> skillPaths, String sessionId, boolean overwrite)` | 逐个注册 `Path` 列表中的技能。 |
| `public void registerPaths(List<Path> skillPaths)` | `Path` 列表版本的简化重载。 |
| `public void unregister(String name)` | 按技能名从注册表中移除技能。 |
| `public Skill get(String name)` | 按技能名获取技能对象。 |
| `public List<Skill> getAll()` | 返回按插入顺序拷贝出的技能列表。 |
| `public List<String> getNames()` | 返回全部技能名称列表。 |
| `public boolean has(String name)` | 判断指定技能是否已注册。 |
| `public void clear()` | 清空全部已注册技能。 |
| `public int count()` | 返回当前技能数量。 |
| `public String getDescription()` | 返回管理器附带的描述字符串。 |
| `public void setDescription(String description)` | 更新管理器附带的描述字符串。 |

## 说明

- `registerRoot(...)` 会优先把传入路径本身当作技能文件解析；若解析失败且路径是目录，则扫描其直接子目录中的 `Skill.md` 或 `SKILL.md`。
- `loadDescription(...)` 只从 YAML front matter 的 `description:` 字段提取技能描述。
- `SkillManagerTest` 验证了 `SKILL.md`/`Skill.md` 注册、重复注册保留原条目、注销和计数逻辑。
