# TASK-STRICTDATE-002 — WeeklyRoundupUtils 严格日历日期校验

- 目标分支：`feature-0602-cases-opencode-test`
- planningHead：`36ea67920c3b99b61a99f391d61ec9a7574f6bb2`
- 工作区策略：`inPlaceExclusive`；提交策略：`autoCommit`（通过构建、全量单测与独立 Review 后自动本地提交并集成，不推送远程）
- 最大改动文件数：2
- 说明：本计划范围与已批准的 `TASK-STRICTDATE-001` 方案完全一致，仅任务标识与计划路径不同；原任务 ID 因历史条目 `TASK-STRICTDATE-001@1` 已处于 ABORTED 终态而不可复用。

## 1. 当前可观察行为

`WordPress/src/main/java/org/wordpress/android/workers/weeklyroundup/WeeklyRoundupUtils.kt` 是 Kotlin `object`，暴露两个公开入口：

- `fun parseStandardDate(date: String): LocalDate?`
- `fun parseWeekPeriodDate(date: String): LocalDate?`

它们分别使用 `DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)` 与 `DateTimeFormatter.ofPattern("yyyy'W'MM'W'dd", Locale.ROOT)`，并统一经

```kotlin
runCatching { LocalDate.parse(date, formatter) }
    .onFailure { AppLog.e(T.NOTIFS, "Weekly Roundup – Couldn't parse date: $date", it) }
    .getOrNull()
```

返回 `LocalDate?`。两个 formatter 都没有指定 resolver style，因此使用 JDK 默认的 `ResolverStyle.SMART`。SMART 会把超出当月长度的“日”夹取为该月最后一天，于是：

- `parseStandardDate("2021-02-30")` 返回 `LocalDate.of(2021, 2, 28)`
- `parseWeekPeriodDate("2021W02W30")` 返回 `LocalDate.of(2021, 2, 28)`
- `parseStandardDate("2023-02-29")` 返回 `LocalDate.of(2023, 2, 28)`
- `parseStandardDate("2021-04-31")` 返回 `LocalDate.of(2021, 4, 30)`

月份越界（`2021-13-01`）、日越界（`2021-08-00`）与格式不匹配（`invalid`）当前已经返回 `null`。

唯一调用方 `WeeklyRoundupRepository.getLastWeekPeriodData()` 依赖返回 `null` 表示解析失败：`parseStandardDate(...) ?: return null` 会让 `fetchWeeklyRoundupData(site)` 返回 `null`。该链路行为在本任务中保持不变。

## 2. 目标可观察行为

两个入口改用严格日历校验：不存在的日历日期不再被夹取修正，一律返回 `null`；合法输入、返回类型、公开签名与日志行为完全不变。

## 3. 验收条件

1. `parseStandardDate("2021-02-30")` 返回 `null`。
2. `parseWeekPeriodDate("2021W02W30")` 返回 `null`。
3. 非闰年：`parseStandardDate("2023-02-29")` 与 `parseWeekPeriodDate("2023W02W29")` 返回 `null`；闰年：`parseStandardDate("2024-02-29")` 与 `parseWeekPeriodDate("2024W02W29")` 返回 `LocalDate.of(2024, 2, 29)`。
4. 30 天月越界日返回 `null`：`2021-04-31`、`2021-06-31`、`2021W04W31`、`2021W06W31`。
5. 既有的越界输入仍返回 `null`：`2021-13-01`、`2021-00-10`、`2021-08-00`、`2021W13W01`、`2021W00W10`、`2021W08W00`。
6. 合法输入与边界值不变：`2021-08-10`、`2021W08W10`、`2021-01-01`、`2021-12-31` 返回原有 `LocalDate`；`"invalid"` 与空串返回 `null`。
7. 新增回归测试在实现前捕获 RED，失败原因必须是“返回了被夹取的日期而不是 null”，不能是编译错误或夹具错误；实现后全部 GREEN。
8. 聚焦测试与完整门禁通过：`:WordPress:testWordpressDebugUnitTest`（过滤 `org.wordpress.android.workers.weeklyroundup.WeeklyRoundupUtilsTest`）、独立命令 `./gradlew testDebugUnitTest`、`assembleDebug`，以及独立 Reviewer 复核。
9. 公开签名 `parseStandardDate(date: String): LocalDate?` 与 `parseWeekPeriodDate(date: String): LocalDate?`、返回类型、`AppLog.e(T.NOTIFS, ...)` 拒绝日志和 `WeeklyRoundupRepository` 的 null 处理链路均不变。

## 4. 边界用例

| 输入 | 目标结果 |
| --- | --- |
| `2021-02-30` / `2021W02W30` | `null` |
| `2023-02-29` / `2023W02W29` | `null`（非闰年） |
| `2024-02-29` / `2024W02W29` | `LocalDate.of(2024, 2, 29)` |
| `2021-04-31`、`2021-06-31` / `2021W04W31`、`2021W06W31` | `null` |
| `2021-13-01`、`2021-00-10`、`2021-08-00` / `2021W13W01`、`2021W00W10`、`2021W08W00` | `null` |
| `2021-01-01`、`2021-12-31`、`2021-08-10` / `2021W08W10` | 原有 `LocalDate` |
| `invalid`、空串 | `null` |

## 5. 精确改动文件

只允许修改下列两个文件，不新增、不删除任何文件，最大改动文件数 2：

- 生产代码：`WordPress/src/main/java/org/wordpress/android/workers/weeklyroundup/WeeklyRoundupUtils.kt`
- 测试代码：`WordPress/src/test/java/org/wordpress/android/workers/weeklyroundup/WeeklyRoundupUtilsTest.kt`

## 6. 实现步骤

### 步骤 1（RED）：在 WeeklyRoundupUtilsTest.kt 追加失败测试

保留现有 3 个测试不动，在 `WeeklyRoundupUtilsTest` 类内追加下列测试方法。所需导入已存在（`assertThat`、`Test`、`LocalDate`）。

```kotlin
    @Test
    fun `parsing standard date with non-existent day returns null`() {
        assertThat(WeeklyRoundupUtils.parseStandardDate("2021-02-30")).isNull()
    }

    @Test
    fun `parsing week period date with non-existent day returns null`() {
        assertThat(WeeklyRoundupUtils.parseWeekPeriodDate("2021W02W30")).isNull()
    }

    @Test
    fun `parsing non-existent day in 30 day month returns null`() {
        assertThat(WeeklyRoundupUtils.parseStandardDate("2021-04-31")).isNull()
        assertThat(WeeklyRoundupUtils.parseStandardDate("2021-06-31")).isNull()
        assertThat(WeeklyRoundupUtils.parseWeekPeriodDate("2021W04W31")).isNull()
        assertThat(WeeklyRoundupUtils.parseWeekPeriodDate("2021W06W31")).isNull()
    }

    @Test
    fun `parsing non-leap year february 29 returns null`() {
        assertThat(WeeklyRoundupUtils.parseStandardDate("2023-02-29")).isNull()
        assertThat(WeeklyRoundupUtils.parseWeekPeriodDate("2023W02W29")).isNull()
    }

    @Test
    fun `parsing leap day returns correct value`() {
        assertThat(WeeklyRoundupUtils.parseStandardDate("2024-02-29")).isEqualTo(LocalDate.of(2024, 2, 29))
        assertThat(WeeklyRoundupUtils.parseWeekPeriodDate("2024W02W29")).isEqualTo(LocalDate.of(2024, 2, 29))
    }

    @Test
    fun `parsing out of range month or day returns null`() {
        assertThat(WeeklyRoundupUtils.parseStandardDate("2021-13-01")).isNull()
        assertThat(WeeklyRoundupUtils.parseStandardDate("2021-00-10")).isNull()
        assertThat(WeeklyRoundupUtils.parseStandardDate("2021-08-00")).isNull()
        assertThat(WeeklyRoundupUtils.parseWeekPeriodDate("2021W13W01")).isNull()
        assertThat(WeeklyRoundupUtils.parseWeekPeriodDate("2021W00W10")).isNull()
        assertThat(WeeklyRoundupUtils.parseWeekPeriodDate("2021W08W00")).isNull()
    }

    @Test
    fun `parsing year boundary dates returns correct value`() {
        assertThat(WeeklyRoundupUtils.parseStandardDate("2021-01-01")).isEqualTo(LocalDate.of(2021, 1, 1))
        assertThat(WeeklyRoundupUtils.parseStandardDate("2021-12-31")).isEqualTo(LocalDate.of(2021, 12, 31))
    }

    @Test
    fun `parsing empty date returns null`() {
        assertThat(WeeklyRoundupUtils.parseStandardDate("")).isNull()
        assertThat(WeeklyRoundupUtils.parseWeekPeriodDate("")).isNull()
    }
```

### 步骤 2：验证 RED

运行聚焦过滤测试。预期结果：`parsing standard date with non-existent day returns null`、`parsing week period date with non-existent day returns null`、`parsing non-existent day in 30 day month returns null`、`parsing non-leap year february 29 returns null` 四个测试失败，失败原因是 `expected null but was 2021-02-28` / `2023-02-28` / `2024-04-30` 之类的非 null 断言失败；`parsing leap day returns correct value`、`parsing out of range month or day returns null`、`parsing year boundary dates returns correct value`、`parsing empty date returns null` 以及原有 3 个测试为 GREEN。不得出现编译错误或夹具错误。

### 步骤 3（GREEN）：修改 WeeklyRoundupUtils.kt

只改两处 pattern 常量与两个 formatter 的构造，其余逻辑（`safelyParseDate`、`AppLog`、`getOrNull`、公开签名）逐字不动：

```kotlin
import java.time.format.ResolverStyle

object WeeklyRoundupUtils {
    private const val STANDARD_DATE_PATTERN = "uuuu-MM-dd"
    private const val WEEK_PERIOD_DATE_PATTERN = "uuuu'W'MM'W'dd"

    private val standardFormatter = DateTimeFormatter
        .ofPattern(STANDARD_DATE_PATTERN, Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)

    private val weekPeriodFormatter = DateTimeFormatter
        .ofPattern(WEEK_PERIOD_DATE_PATTERN, Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)
```

理由：`ResolverStyle.STRICT` 下 `yyyy`（year-of-era）缺少 era 字段时连合法日期也无法解析，因此必须使用同义且对 4 位年份输入完全等价的 proleptic year `uuuu`。这保持“现有合法输入格式不变”的约束。

### 步骤 4：验证 GREEN

重新运行步骤 2 的聚焦命令，`WeeklyRoundupUtilsTest` 全部用例（原有 3 个 + 新增 9 个）通过。

### 步骤 5：完整质量门

按 `AGENTS.md` 要求以独立命令运行 `./gradlew testDebugUnitTest`（不加环境变量前缀、不包裹脚本），通过后运行 `assembleDebug`。全量单测与构建通过后交由独立 Reviewer 复核；`autoCommit` 策略下由执行器完成本地提交与集成，不推送远程。

## 7. 验证命令

- 聚焦：`./gradlew :WordPress:testWordpressDebugUnitTest --tests "org.wordpress.android.workers.weeklyroundup.WeeklyRoundupUtilsTest"`
- 全量：`./gradlew testDebugUnitTest`（独立命令）
- 构建：`./gradlew assembleDebug`

## 8. 允许与禁止路径

允许：上述两个文件。禁止：`automation/**`、`.opencode/**`、`.automation-plugin/**`、`scripts/automation/**`、`opencode.json`、`opencode.jsonc`、`AGENTS.md`、`.automation-worktree-allowlist`、`gradle/**`、`gradlew`、`gradlew.bat`、`settings.gradle(.kts)`、`build.gradle(.kts)`、`**/build.gradle(.kts)`、`WordPress/build.gradle`、`libs/*/build.gradle`、`docs/plans/TASK-STRICTDATE-002.md`，以及 `WeeklyRoundupRepository.kt` 与同包其他文件。

## 9. 非目标

- 不改变接受的日期文本格式、`Locale.ROOT`、公开签名或返回类型
- 不改变拒绝解析时的日志内容、级别或 channel
- 不修改 `WeeklyRoundupRepository.kt`、`WeeklyRoundupRepositoryTest.kt` 或同包其他文件
- 不引入 ISO week（`YYYY-'W'ww`）语义，不新增格式支持
- 不新增依赖、不改 Gradle/构建配置、不重构无关代码、不新增文档
- 不新增异常类型、不上抛异常、不改变失败返回 `null` 的契约

## 10. 设备测试策略与残余风险

设备测试：不需要。本改动是纯 JVM `java.time` 解析逻辑，不涉及 Android framework、资源、权限或 UI，`connectedDebugAndroidTest` 不运行。

残余风险：

- `uuuu` 替换 `yyyy` 属 STRICT 解析的必要调整；对 1..9999 的 4 位年份输入无观察差异，超长或负年份输入本就因定宽 4 位而失败，不受影响。
- 若上游 stats payload 存在依赖“夹取修正”的历史脏数据，行为会从“返回近似日期”变为“返回 null”，从而该站点本轮不生成周报。这是需求明确要求的方向，已由验收条件 1–4 锁定。

## 11. 批准记录

- 人类已通过 OpenCode 单选问答批准方案（`方案确认` → `批准方案，生成计划和任务合同。`），并在换用新任务 ID 后再次确认同一范围。
- 本次以新任务 ID `TASK-STRICTDATE-002` 封存，原因：`TASK-STRICTDATE-001` 已存在终态 ABORTED 执行记录，任务 ID 不可复用。
- 本条记录不是执行批准，执行由 `TASK-STRICTDATE-002` 的合同复核（`合同确认`）授权。
