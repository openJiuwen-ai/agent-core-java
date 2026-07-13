# JIT 与编译优化

本文件补充 SKILL.md 的编译优化内容，深入讲 JIT 分层编译、内联、循环展开、C2 vs Graal 等。用户问"JIT 怎么工作"或"为什么预热后变快"时按需读取。

## JIT 基本原理

JVM 解释执行字节码 → 采集方法调用频次 → 热点方法触发 JIT 编译 → 编译后直接执行机器码。

**优势**：
- 解释执行启动快，编译执行运行快
- JIT 基于运行时 profiling 做激进优化（虚方法单态化、分支预测）
- 比 AOT（C++）多一个运行时反馈的优势

**劣势**：
- 预热开销
- 单次编译可能因 profile 不准被反优化

## 分层编译（Tiered Compilation）

JDK8+ 默认开 `-XX:+TieredCompilation`，JVM 用 5 层编译：

| 层 | 解释器 | C1 | C2 | 用途 |
|---|---|---|---|---|
| 0 | ✓ | | | 解释执行，采集 profile |
| 1 | | ✓ | | C1 编译，无 profile |
| 2 | | ✓ | | C1 + 轻量 profile |
| 3 | | ✓ | | C1 + 完整 profile（多数方法停在这） |
| 4 | | | ✓ | C2 编译，激进优化 |

**流程**：
1. 方法从层 0 开始解释执行
2. 调用计数到阈值 → 层 3（C1 + profile）
3. C2 队列编译 → 层 4（C2 激进优化）
4. profile 失效（如虚方法新分支） → 反优化回层 0

**阈值**：
- 层 3：~1500 次调用
- 层 4：~10000 次调用（分层编译时自适应）

### C1（Client Compiler）

- 快速编译，代码质量中等
- 简单优化：方法内联、常量折叠
- 适合短跑应用 / CLI

### C2（Server Compiler）

- 慢编译，激进优化
- 高级优化：逃逸分析 + 标量替换 + 锁消除 + 循环展开 + 虚方法单态化
- 适合长跑应用

### Graal（JDK10+ 实验性）

- 用 Java 写的 C2 替代品
- 更激进的优化（部分场景比 C2 快 20%+）
- 启用：`-XX:+UnlockExperimentalVMOptions -XX:+UseJVMCICompiler`
- JDK17+ 部分场景稳定，但仍是实验性

## 内联（Inlining）

内联是 JIT 最重要的优化：把方法调用替换为方法体本身，省调用开销 + 后续优化空间。

### 内联收益

- 省调用开销（栈帧、参数传递）
- 内联后可做更多优化（常量折叠、死代码消除）
- 是其他优化的前提（逃逸分析依赖内联）

### 内联条件

| 条件 | 默认值 | 说明 |
|---|---|---|
| 方法大小 | < 35 字节码（`-XX:MaxInlineSize`） | 小方法优先 |
| 频次 | 热点（> CompileThreshold） | 调用频次高 |
| 类型可静态确定 | 非虚方法 / 单实现虚方法 | 虚方法内联需 CHA（类层次分析） |

### 虚方法内联

虚方法（非 final、非 private）默认不能内联 —— JIT 不知道运行时是哪个实现。但 JIT 用 CHA（Class Hierarchy Analysis）：

- **单态（monomorphic）**：该方法只有一个实现 → 内联
- **双态（bimorphic）**：两个实现 → 内联 + 分支
- **多态（megamorphic）**：> 2 个实现 → 不内联，查虚方法表

**帮助内联**：
- 加 `final` 类/方法 → 编译时确定非虚调用
- 避免不必要的接口（让 JIT 判定单态）
- 别在热路径用复杂继承层级

### -XX:MaxInlineSize vs -XX:FreqInlineSize

- `MaxInlineSize=35`：常规方法内联上限
- `FreqInlineSize=325`：热点方法内联上限（更大的方法如果是热点也内联）

### 内联日志

```bash
-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining
```

输出形如：
```
  @ 27   java.lang.String::hashCode (49 bytes)   inline (hot)
  @ 12   java.util.HashMap::getNode (110 bytes)   inline (hot)
  @ 7   com.foo.Bar::cachedCall (5 bytes)   inline (hot)
```

`inline (hot)` = 内联成功；`too big` = 方法太大；`not inline (no static binding)` = 多态。

## 循环优化

### 循环展开（Loop Unrolling）

把循环体复制多次，减少循环开销：

```java
// 优化前
for (int i = 0; i < 1000; i++) { ... }

// 展开后（每次循环做 4 次工作）
for (int i = 0; i < 1000; i += 4) {
    body(); body(); body(); body();
}
```

C2 默认展开小循环，可通过 `-XX:LoopMaxUnroll=64` 调整。

### 循环剥离（Loop Peeling）

把循环第一次/最后一次迭代剥离出来，简化中间循环。

### 循环向量化（Vectorization）

C2 把连续操作转为 SIMD 指令：

```java
// 数组逐元素相加
for (int i = 0; i < n; i++) {
    c[i] = a[i] + b[i];
}

// 向量化后（CPU SIMD 指令，一次处理 4-16 个）
// c[i:i+8] = a[i:i+8] + b[i:i+8]
```

**触发条件**：
- 数组连续访问
- 元素类型基本（int/long/float/double）
- 循环体简单
- 无依赖（前一次结果不影响后一次）

### 循环展开限制

- 太大不展开（默认 ≤ 64 字节码）
- 有异常不展开
- 有同步块不展开

## 逃逸分析与优化

逃逸分析是 C2 的核心优化，详见 `code_level_optimization.md` 的"逃逸分析"部分。

**核心收益**：
- 栈上分配 → 不占堆，无 GC
- 标量替换 → 对象字段拆为局部变量
- 锁消除 → 无竞争锁消除

**参数**：
- `-XX:+DoEscapeAnalysis`（JDK8+ 默认开）
- `-XX:+EliminateAllocations`：栈上分配（依赖逃逸分析，默认开）
- `-XX:+EliminateLocks`：锁消除（依赖逃逸分析，默认开）

## 分支预测

JIT 用 profile 数据做分支预测：

```java
if (log.isDebugEnabled()) {  // JIT 看到 isDebugEnabled 多数返回 false
    log.debug("...");        // 整个 if 块代码不优化
}
```

**收益**：
- 常走分支排前面，CPU 分支预测命中率高
- 少走分支可能被"冷"代码处理，性能近似消除

## 去虚化（Devirtualization）

虚方法内联依赖 CHA，但运行时类层次可能变（动态加载）。JIT 保守做：
- 单态方法内联
- 假设类层次稳定 → 如果新加载的类破坏假设 → 反优化

**GraalVM Native Image**：AOT 编译时已知全部类，可激进去虚化。

## 反优化（Deoptimization）

JIT 基于运行时 profile 做激进优化。如果 profile 失效 → 反优化回解释执行。

**触发条件**：
- 新加载的类破坏 CHA 假设（如虚方法新实现）
- 异常路径触发
- `-XX:+CompileCommand=exclude,Class.method` 排除某些方法

**表现**：
- 性能突然下降（编译版本丢弃）
- 重新 profile + 编译

**调试**：
```bash
-XX:+TraceDeoptimization
-XX:+PrintDeoptimization
```

## 代码缓存（Code Cache）

JIT 编译后的机器码放在 Code Cache：

| 参数 | 含义 | 默认 |
|---|---|---|
| `-XX:ReservedCodeCacheSize` | 上限 | 240M（JDK9+）/ 48M（JDK8） |
| `-XX:InitialCodeCacheSize` | 初始 | 160K |
| `-XX:CodeCacheExpansionSize` | 扩张步长 | 64K |

**满后行为**：
- 不再 JIT 编译新方法 → 性能下降
- 已编译方法可能被丢弃 → 反优化
- 不报错，难察觉

**监控**：
```bash
jcmd <pid> Compiler.CodeCache
```

**调优**：
- JDK8：默认 48M 太小，建议 256M
- JDK9+：默认 240M 多数够
- 大型应用（10万+ 类）：调到 512M

## JIT 调优参数汇总

### 启用 / 关闭

```
-XX:+TieredCompilation         # 分层编译（JDK8+ 默认开）
-XX:-TieredCompilation         # 关闭，只用 C2（短跑可能受益）
-XX:+PrintCompilation          # 打印 JIT 编译日志
-XX:+PrintInlining             # 打印内联决策（需 +UnlockDiagnosticVMOptions）
-XX:+PrintCodeCache            # 打印 Code Cache 状态
-XX:+PrintDeoptimization       # 打印反优化事件
-XX:+TraceClassLoading         # 打印类加载
```

### 阈值

```
-XX:CompileThreshold=10000             # C2 编译阈值
-XX:BackEdgeThreshold=100000           # OSR 阈值（循环回边）
-XX:OnStackReplacePercentage=140       # OSR 比例
-XX:MaxInlineSize=35                   # 内联方法最大字节码
-XX:FreqInlineSize=325                 # 热点方法内联最大字节码
-XX:LoopMaxUnroll=64                   # 循环展开最大字节码
```

### 编译器选择

```
-XX:+UseC1                      # 用 C1（关 C2）
-XX:+UseC2                      # 用 C2（关 C1）
-XX:+UseJVMCICompiler           # 用 Graal（实验性）
-XX:TieredStopAtLevel=4         # 最多编译到层 4（默认）
-XX:TieredStopAtLevel=1         # 只用 C1，停层 1（短跑）
```

## 预热（Warmup）

JIT 需要时间预热：
- 短跑应用（CLI、批处理）：可能全程没编译到层 4 → 慢
- 长跑应用（服务）：预热后稳定

### 短跑应用优化

**方案 1：分层编译 + C1 优先**
```
-XX:+TieredCompilation -XX:TieredStopAtLevel=1
```

**方案 2：CDS（Class Data Sharing）**
```
# 生成共享归档
java -Xshare:dump

# 运行时使用
java -Xshare:on -jar app.jar
```

**方案 3：AppCDS**（自定义类 CDS）
```
# JDK13+
java -XX:ArchiveClassesAtExit=app.jsa -cp app.jar App

# 运行
java -XX:SharedArchiveFile=app.jsa -cp app.jar App
```

**方案 4：GraalVM Native Image**
```bash
native-image -jar app.jar app
./app
```
- AOT 编译，启动毫秒级，无 JIT
- 适合 CLI / 函数计算
- 牺牲一点峰值性能换启动速度

### 长跑应用优化

**预热方法**：
- 启动后用模拟流量打 5-10 分钟
- 或在 `main` 里手动循环调用热方法

**注意**：生产环境开 G1/ZGC + 分层编译默认配置即可，不要乱调 JIT 参数。

## 监控 JIT 状态

```bash
# 看 Code Cache 占用
jcmd <pid> Compiler.CodeCache

# 看已编译的方法
jcmd <pid> Compiler.list

# 看 JIT 编译统计
jcmd <pid> Compiler.stats

# 看 C2 队列
jcmd <pid> Compiler.queue

# 看 JVM 启动参数
jcmd <pid> VM.flags
```

## GraalVM 对比（JDK 17 baseline）

| 维度 | HotSpot JIT (C1/C2) | GraalVM Native Image | GraalVM JIT（Graal） |
|---|---|---|---|
| 编译时机 | 运行时 | 构建时（AOT） | 运行时（Graal） |
| 启动速度 | 慢 | 毫秒级 | 慢 |
| 峰值性能 | 高 | 中（部分场景 80-95%） | 高（部分场景比 C2 快 20%+） |
| 内存占用 | 高 | 低 | 高 |
| 反射支持 | 完整 | 需配置（reachability metadata） | 完整 |
| 适用 | 长跑服务 | CLI / 函数计算 / 微服务 | 实验 |

**JDK 17 GraalVM Native Image 现状**：
- Production-ready
- 启动毫秒级，单二进制部署
- 反射需 `reachability-metadata.json` 或 `@RegisterReflectionForBinding`
- Spring Boot 3 / Micronaut / Quarkus 原生支持
- 牺牲约 5-20% 峰值性能换启动速度

**用 GraalVM JIT（实验性）**：
```
-XX:+UnlockExperimentalVMOptions -XX:+UseJVMCICompiler
```

## 常见 JIT 优化速查

| 优化 | 收益 | 触发条件 |
|---|---|---|
| 方法内联 | 5-10x | 方法小 + 热点 |
| 栈上分配 | 减少 GC | 对象不逃逸 |
| 标量替换 | 对象消失 | 对象不逃逸 |
| 锁消除 | 无锁开销 | 同步块内对象不逃逸 |
| 锁粗化 | 减少锁次数 | 相邻同步块 |
| 循环展开 | 减少循环开销 | 小循环 |
| 循环向量化 | SIMD 加速 | 数组连续访问 |
| 分支预测 | 流水线不破 | profile 数据 |
| 虚方法单态化 | 内联 | 单实现 |
| 死代码消除 | 删除不执行代码 | 编译时分析 |
| 常量折叠 | 编译时计算 | 操作数常量 |

## JDK 17 JIT 特性

### 分层编译默认开

JDK 17 分层编译默认开（`-XX:+TieredCompilation`），5 层编译全启用。多数场景不要关。

**短跑应用例外**：
- CLI 工具、批处理作业可能全程没预热到层 4
- 可考虑 `-XX:TieredStopAtLevel=1`（只用 C1）
- 或 GraalVM Native Image（AOT）

### Sealed Class 帮助去虚化

JDK 17 sealed class 编译时已知所有实现 → JIT 单态化内联：

```java
public sealed interface Shape permits Circle, Rectangle, Triangle {}

// JIT 看到 Shape 实际只有 3 个实现
// 如果运行时只用到 Circle → 单态化内联
// 用到 Circle + Rectangle → 双态化（branch + inline）
```

**对比非 sealed**：非 sealed 接口理论上实现无限多 → JIT 不敢激进内联。

### Pattern Matching 编译优化

JDK 17 pattern matching for switch（preview），编译为 `tableswitch`：

```java
switch (shape) {
    case Circle c -> ...;       // 编译为跳转表，无 instanceof 链
    case Rectangle r -> ...;
    case Triangle t -> ...;
}
```

比 `if instanceof + cast` 链快（多次类型检查 → 1 次）。

### Vector API（incubator）

JDK 17 incubator（`--add-modules jdk.incubator.vector`），明确 SIMD 化：

```java
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;

static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_256;

void vectorAdd(float[] a, float[] b, float[] c) {
    int i = 0;
    int upper = SPECIES.loopBound(a.length);
    for (; i < upper; i += SPECIES.length()) {
        FloatVector va = FloatVector.fromArray(SPECIES, a, i);
        FloatVector vb = FloatVector.fromArray(SPECIES, b, i);
        va.add(vb).intoArray(c, i);
    }
    for (; i < a.length; i++) c[i] = a[i] + b[i];
}
```

**对比 C2 自动向量化**：
- C2 自动：依赖 JIT 判断，不稳定（代码改一点可能就不向量化）
- Vector API：明确向量化，跨平台保证

**JDK 17 状态**：incubator，API 可能变。JDK 19+ preview。

### Compact Strings（JDK 9+ 默认）

JDK 9+ String 内部用 `byte[]` + coder flag：
- ASCII 字符串：Latin-1 编码，1 字节/字符（JDK 8 是 2 字节）
- 非 ASCII：UTF-16，2 字节/字符

**收益**：ASCII 字符串内存减半。JDK 17 默认开，无需配置。

### CDS / AppCDS

Class Data Sharing，启动加速：

```bash
# JDK 17：生成默认 CDS 归档
java -Xshare:dump

# 运行时使用
java -Xshare:on -jar app.jar

# AppCDS（自定义类）
java -XX:DumpLoadedClassList=classes.lst -jar app.jar
java -Xshare:dump -XX:SharedClassList=classes.lst -XX:SharedArchiveFile=app.jsa -jar app.jar
java -Xshare:on -XX:SharedArchiveFile=app.jsa -jar app.jar
```

**收益**：启动时间减少 30-50%，内存占用降低（多进程共享元数据）。

## 反模式

- ❌ 短跑应用调 `-XX:+TieredCompilation` 想要预热优化 → JIT 可能没生效，反而慢
- ❌ Code Cache 调太小 → 反优化
- ❌ 热路径用复杂继承层级 → 虚方法多态，不内联
- ❌ 大量反射 → JIT 难优化
- ❌ 在热路径 lambda 捕获变量 → 每次创建 Lambda 对象
- ❌ JDK 17 等 C2 自动向量化 → 不稳定，关键路径用 Vector API
- ❌ JDK 17 用 Lombok @Data → Records 编译器生成，无反射
- ❌ JDK 17 `instanceof` 链 + cast → sealed + pattern matching
- ❌ JDK 17 反射访问非 export 包不开 `--add-opens` → `InaccessibleObjectException`

## 参考文档

- OpenJDK HotSpot 文档：`https://openjdk.org/groups/hotspot/`
- JIT Watcher（JIT 可视化）：`https://github.com/AdoptOpenJDK/jitwatch`
- GraalVM：`https://www.graalvm.org/`
- Project Leyden（AOT 演进）：`https://openjdk.org/projects/leyden/`
