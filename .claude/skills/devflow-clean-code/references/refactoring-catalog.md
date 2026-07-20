# 重构目录：异味识别与手法

> 配套 `devflow-clean-code`。每条含：识别特征 → 操作步骤 → before/after。所有重构在绿灯上进行，小步，每步跑测试。示例为 C/C++，原则通用。

## 1. Extract Function（提取函数）

**识别**：函数超过一屏；体内有空行/注释分隔的"段落"；同一函数里混着流程和细节两个抽象层。

**步骤**：① 选定一个意图完整的段落；② 以它的**意图**（不是机制）命名新函数；③ 段落用到的局部变量变参数、产生的结果变返回值；④ 替换原段落为调用；⑤ 跑测试。

```c
/* before */
void sensor_poll(void) {
    /* debounce: 连续 3 次相同读数才接受 */
    raw = adc_read(CH0);
    if (raw == last_raw) { stable_cnt++; } else { stable_cnt = 0; }
    last_raw = raw;
    if (stable_cnt < 3) return;
    ...后续处理...
}

/* after：注释消失，变成函数名 */
void sensor_poll(void) {
    if (!debounced_read(CH0, &raw)) return;
    ...后续处理...
}
```

## 2. 卫语句替代嵌套（Replace Nested Conditional with Guard Clauses）

**识别**：≥3 层缩进；else 链里藏着错误返回；主逻辑在最深处。

**步骤**：① 把每个否定条件改写为"检查失败立即返回"；② 从最外层逐个剥离；③ 主逻辑回到零缩进；④ 跑测试。

（完整示例见 `devflow-clean-code` §控制流。）

## 3. 提取解释变量 / 谓词函数（Introduce Explaining Variable / Predicate）

**识别**：if 条件要读两遍；条件里有 ≥3 个 && / ||；条件表达式带注释。

```c
/* before */
if ((e->flags & 0x04) && e->ts + ttl_ms < now_ms() && e->owner == self) { ... }

/* after */
bool is_expired_own_entry =
    entry_is_dirty(e) && entry_expired(e, now_ms()) && e->owner == self;
if (is_expired_own_entry) { ... }
```

条件在多处出现 → 升级为命名谓词函数。

## 4. 命名常量替换魔法数（Replace Magic Number with Named Constant）

**识别**：裸字面量参与逻辑判断或计算；同一个数字出现在多处且必须同步修改。

```c
/* before */
if (retry > 5) { ... }     vTaskDelay(200);

/* after：名字说含义，含义不变时值可变 */
enum { MAX_HANDSHAKE_RETRIES = 5 };
#define SENSOR_SETTLE_TIME_MS  200
```

注意：`0`、`1`、数组边界等自解释字面量不需要常量化；机械地全部替换是噪音。

## 5. 提取参数结构体（Introduce Parameter Object）

**识别**：同一组 3-4 个参数在多个函数签名里结伴出现；新增一个相关参数要改 N 个签名。

```c
/* before */
int wave_cfg(uint32_t freq_hz, uint16_t amp_mv, uint8_t duty_pct);
int wave_validate(uint32_t freq_hz, uint16_t amp_mv, uint8_t duty_pct);

/* after：这组数据获得了名字，将来加字段只改一处 */
typedef struct { uint32_t freq_hz; uint16_t amp_mv; uint8_t duty_pct; } wave_params_t;
int wave_cfg(const wave_params_t *p);
int wave_validate(const wave_params_t *p);
```

## 6. Move Function（搬移函数 / 治特性依恋）

**识别**：函数的实现大量读写另一个模块的数据/调用其内部函数，几乎不碰本模块的。

**步骤**：① 函数搬到数据所在模块；② 原位置留转发或直接改调用方；③ 检查搬移后原模块对目标模块的依赖是否可以删除；④ 跑测试。

## 7. 拆开关参数（Split Flag Parameter）

**识别**：布尔参数让函数走完全不同的路径；调用点 `f(x, true)` 读不出含义。

```c
/* before */
int store_write(record_t *r, bool sync);

/* after */
int store_write(record_t *r);           /* 异步，默认 */
int store_write_sync(record_t *r);      /* 阻塞直到落盘 */
```

## 8. 集中清理出口（Consolidate Cleanup，C）

**识别**：多资源获取的函数里，每个失败分支各自重复释放代码；新增资源时漏改某个分支。

```c
/* before：三个出错分支各自释放，已经漏了一处 */
int pipeline_start(void) {
    buf = malloc(BUF_SZ);
    if (buf == NULL) return ERR_NOMEM;
    if (timer_open(&t) != OK) { free(buf); return ERR_HW; }
    if (irq_attach(&irq) != OK) { timer_close(&t); return ERR_HW; }  /* 漏 free(buf) */
    ...
}

/* after：单一出口，释放顺序与获取相反，新增资源只改两处 */
int pipeline_start(void) {
    int rc = ERR_HW;
    uint8_t *buf = malloc(BUF_SZ);
    if (buf == NULL) return ERR_NOMEM;
    if (timer_open(&t) != OK) goto fail_timer;
    if (irq_attach(&irq) != OK) goto fail_irq;
    return OK;

fail_irq:   timer_close(&t);
fail_timer: free(buf);
    return rc;
}
```

支持确定性析构的语言通常用 RAII / scope guard 等惯用形态，不需要此模式；具体写法以适用语言规范为准。

## 9. 用查表替代分支链（Replace Conditional with Table）

**识别**：长 switch/else-if 链对同一个判别量做映射；每加一种取值要改多处分支。

```c
/* before */
const char *dtc_to_str(dtc_t d) {
    if (d == DTC_OVERVOLT) return "overvolt";
    else if (d == DTC_UNDERVOLT) return "undervolt";
    else if (d == DTC_OVERTEMP) return "overtemp";
    ...
}

/* after：数据就是数据 */
static const char *const k_dtc_names[] = {
    [DTC_OVERVOLT]  = "overvolt",
    [DTC_UNDERVOLT] = "undervolt",
    [DTC_OVERTEMP]  = "overtemp",
};
```

分支体不是简单映射而是各自不同的复杂行为 → 考虑函数指针表或多态；但先确认变化轴真实存在（`devflow-design` §抽象纪律）。

## 10. 删除死代码（Remove Dead Code）

**识别**：`#if 0`；编译器报 unused；grep 无调用方的函数；永真/永假条件；"先留着"的旧实现。

**步骤**：① 确认确实不可达（小心条件编译、链接脚本、宏拼接、外部 ABI 调用方）；② 整块删除，不留注释尸体；③ 提交信息注明删了什么、为何确认无引用。

## 11. 拆大接口 / 隐藏实现（Interface Segregation）

**识别**：公共头文件暴露内部结构体字段、私有宏或一组调用方只用其中一小部分的大而全 API；改内部实现会迫使无关调用方重编译或修改。

**步骤**：① 按调用方实际需要分组 API；② 把内部字段改为不透明句柄或移入 `.c` / detail 命名空间；③ 删除调用方不需要的 include；④ 跑构建和相关测试。

```c
/* before：调用方被迫知道所有字段 */
typedef struct {
    uint32_t raw;
    uint32_t filtered;
    uint8_t calibration_state;
} sensor_t;
int sensor_read(sensor_t *s, uint32_t *value);

/* after：契约只暴露调用方需要的事 */
typedef struct sensor sensor_t;
int sensor_read(const sensor_t *s, uint32_t *value);
```

## 12. 在真实边界引入适配层（Dependency Inversion）

**识别**：高层流程直接调用硬件寄存器、协议栈、文件系统或第三方库；测试必须 mock 内部纯逻辑才能绕开底层细节。

**步骤**：① 确认边界真实存在（硬件、外部组件、第三方库、跨进程/跨组件）；② 用最小端口函数表达高层需要的能力；③ 底层适配实现端口；④ 高层只依赖端口契约；⑤ 跑测试。

不要为了 DIP 创建没有第二个真实实现、也不跨所有权边界的接口。那是单实现抽象，回 `devflow-design` 的抽象纪律处理。

## 13. 性能相关重构（Performance-Aware Refactoring）

**识别**：热路径里出现 N+1 查询/调用、循环内重复解析或分配、无界读取、资源生命周期不清；或者评审/测试/基准已有延迟、内存、吞吐证据。

**步骤**：① 先写清楚证据：热路径、数据规模、复杂度或资源风险；② 优先选择让意图更清楚的结构（批量接口、缓存边界、合适的数据结构、集中资源管理）；③ 跑功能测试；④ 有性能基准时补跑基准或记录为何 N/A。

示例：

```c
/* before：每个元素都跨边界读取一次 */
for (size_t i = 0; i < count; ++i) {
    read_sensor_config(ids[i], &configs[i]);
}

/* after：边界能力表达真实需求，调用次数和错误处理都更清楚 */
rc = read_sensor_configs(ids, count, configs);
if (rc != OK) return rc;
```

不要为了性能引入晦涩技巧、手写缓存或复杂并发，除非有热路径/基准/资源上限证据。没有证据时，保持简单可读并登记观察项。

## 不要做的"重构"

- **绿灯之外的重构**：测试不全绿时改结构 = 蒙眼搬家
- **跨任务边界的大重构**：超出当前任务触碰范围的结构调整 → 登记，走 `devflow-design`
- **提取长得像但知识不同的代码**：两段相似代码服务不同业务规则时，合并会制造"改一处炸另一处"的耦合
- **重命名风暴**：一次提交里大面积改名混入行为变更，diff 不可审
- **无证据微优化**：没有热路径、复杂度或资源证据，却牺牲表达力换取想象中的性能
