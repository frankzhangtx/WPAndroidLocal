# TASK-VERSION-OVERFLOW-20261024 — WhatsNewAppVersionUtils 主次版本拼接溢出容错

- 目标分支：`trial/wordpress-150-overflow-20261024`
- planningHead：`8905542ab20c808da1eef77f7d6b56dda0634e07`
- 工作区策略：`isolatedWorktree`；提交策略：`autoCommit`（V8 `continuity.isolatedAutoIntegration: true`，通过聚焦测试、回归测试、`assembleDebug` 与独立 Review 后本地自动集成，**不推送远程**）
- 最大改动文件数：2
- 批准来源：可信委托 grant `wordpress-150-overflow-20261024-grant`（由 intake 以 `delegatedApproval` 记录并原子消费；本运行不请求人工单选审批）

## 1. 当前可观察行为

`libs/fluxc/src/main/java/org/wordpress/android/fluxc/utils/WhatsNewAppVersionUtils.kt` 当前实现：

```kotlin
object WhatsNewAppVersionUtils {
    fun versionNameToInt(appVersion: String): Int {
        val majorMinor = Regex("(\\d+\\.)(\\d+)").find(appVersion, 0)?.value

        majorMinor?.let {
            return it.replace(".", "").toInt()
        }
        return -1
    }
}
```

可观察结果：

| 输入 | 当前结果 |
| --- | --- |
| `"14.1.2"` | `141`（`"14.1"` → 去掉点 → `"141"`） |
| `"alpha-222"` | `-1`（无匹配） |
| `"2147483648.1"` | 抛出 `java.lang.NumberFormatException`（`"21474836481"` > `Int.MAX_VALUE` 2147483647） |
| `"1.2147483648"` | 抛出 `java.lang.NumberFormatException`（`"12147483648"` > `Int.MAX_VALUE`） |

该对象只有两个公开成员：`object WhatsNewAppVersionUtils` 与 `fun versionNameToInt(appVersion: String): Int`。现有可信测试 `libs/fluxc/src/test/java/org/wordpress/android/fluxc/utils/WhatsNewAppVersionUtilsTest.kt` 覆盖 `14.1.2 → 141` 与 `alpha-222 → -1`，本任务不改写它。

## 2. 目标可观察行为

单一行为变化：当正则匹配到的主次版本数字拼接后超出 `Int` 范围时，`versionNameToInt` 返回 `-1`，不再抛出 `NumberFormatException`。匹配规则（首个 `(\d+\.)(\d+)` 匹配、去掉点拼接）以及全部合法/非法输入的既有返回值保持不变。

实现方式：把 `toInt()` 改为 `toIntOrNull() ?: -1`。对仅含数字的拼接串，`toIntOrNull()` 与 `toInt()` 在 Int 范围内结果逐字一致，只在溢出时返回 `null`。

## 3. 验收条件

1. `versionNameToInt("2147483648.1")` 返回 `-1`，不抛异常；修复前该用例以 `java.lang.AssertionError` 在该测试方法内失败（测试用 `runCatching` 捕获异常，`result.getOrNull()` 为 `null`，`assertEquals(-1, null)` 断言失败）。
2. `versionNameToInt("1.2147483648")` 返回 `-1`，不抛异常；修复前同样以该测试方法内的 `java.lang.AssertionError` 失败。
3. 保持行为：新测试断言 `versionNameToInt("14.1.2") == 141`。
4. 保持行为：新测试断言 `versionNameToInt("alpha-222") == -1`。

## 4. 边界与用例表

| 输入 | 修复前 | 修复后 |
| --- | --- | --- |
| `2147483648.1` | `NumberFormatException` | `-1` |
| `1.2147483648` | `NumberFormatException` | `-1` |
| `14.1.2` | `141` | `141` |
| `alpha-222` | `-1` | `-1` |

- 前导零（如 `01.2`）在 `toInt` 与 `toIntOrNull` 下都按十进制解析，结果一致，不新增特殊处理。
- 正则只匹配数字，拼接串不可能带符号，因此不存在负数解析差异。
- 无匹配仍走原有 `return -1` 分支。

## 5. 精确改动文件

只允许下列两个文件，最大改动文件数 2；不新增其他文件、不删除文件：

- 生产代码（修改）：`libs/fluxc/src/main/java/org/wordpress/android/fluxc/utils/WhatsNewAppVersionUtils.kt`
- 测试代码（新建）：`libs/fluxc/src/test/java/org/wordpress/android/fluxc/utils/WhatsNewAppVersionOverflowTest.kt`

禁止修改：既有 `libs/fluxc/src/test/java/org/wordpress/android/fluxc/utils/WhatsNewAppVersionUtilsTest.kt`、`libs/fluxc/build.gradle`、`automation/**`、`scripts/automation/**`、`.opencode/**`、`opencode.json*`、`AGENTS.md`、Gradle/构建文件、`.automation-worktree-allowlist`、本计划文件之外的文档。

## 6. 实现步骤

### 步骤 1（RED）：新建聚焦测试类

创建 `libs/fluxc/src/test/java/org/wordpress/android/fluxc/utils/WhatsNewAppVersionOverflowTest.kt`，完整内容：

```kotlin
package org.wordpress.android.fluxc.utils

import org.junit.Test
import kotlin.test.assertEquals

class WhatsNewAppVersionOverflowTest {
    @Test
    fun `versionNameToInt returns -1 when the major version digits overflow Int`() {
        val result = runCatching { WhatsNewAppVersionUtils.versionNameToInt("2147483648.1") }

        assertEquals(-1, result.getOrNull())
    }

    @Test
    fun `versionNameToInt returns -1 when the minor version digits overflow Int`() {
        val result = runCatching { WhatsNewAppVersionUtils.versionNameToInt("1.2147483648") }

        assertEquals(-1, result.getOrNull())
    }

    @Test
    fun `versionNameToInt keeps converting a normal major and minor version`() {
        assertEquals(141, WhatsNewAppVersionUtils.versionNameToInt("14.1.2"))
    }

    @Test
    fun `versionNameToInt keeps returning -1 for a malformed version name`() {
        assertEquals(-1, WhatsNewAppVersionUtils.versionNameToInt("alpha-222"))
    }
}
```

要点：溢出用例在测试方法内用 `runCatching` 包住调用，再直接 `assertEquals(-1, result.getOrNull())`。旧实现抛出的 `NumberFormatException` 被 `runCatching` 捕获，`getOrNull()` 为 `null`，因此失败类型是 `java.lang.AssertionError` 而不是未捕获的 `NumberFormatException`，且失败位置就是各自的测试方法。

### 步骤 2：验证 RED

运行执行器管理的聚焦验证：`:libs:fluxc:testDebugUnitTest`，过滤 `org.wordpress.android.fluxc.utils.WhatsNewAppVersionOverflowTest`（执行器通过受控验证脚本运行，不手写 shell 链式命令）。

预期：
- `versionNameToInt returns -1 when the major version digits overflow Int` 失败，`java.lang.AssertionError`，origin 为该测试方法。
- `versionNameToInt returns -1 when the minor version digits overflow Int` 失败，`java.lang.AssertionError`，origin 为该测试方法。
- `versionNameToInt keeps converting a normal major and minor version` 与 `versionNameToInt keeps returning -1 for a malformed version name` 通过。
- 不得出现编译错误或夹具错误。

### 步骤 3（GREEN）：最小实现

只改 `WhatsNewAppVersionUtils.kt` 中拼接后的解析表达式：

```kotlin
package org.wordpress.android.fluxc.utils

object WhatsNewAppVersionUtils {
    fun versionNameToInt(appVersion: String): Int {
        val majorMinor = Regex("(\\d+\\.)(\\d+)").find(appVersion, 0)?.value

        majorMinor?.let {
            return it.replace(".", "").toIntOrNull() ?: -1
        }
        return -1
    }
}
```

正则、匹配偏移、去点逻辑、`-1` 失败返回与签名 `versionNameToInt(appVersion: String): Int` 全部不变。

### 步骤 4：验证 GREEN

再次运行同一聚焦过滤测试。预期四个用例全部通过，且无新增失败。

### 步骤 5：最终集成验证（候选提交）

在固定候选提交上重新执行目标测试，并附加：
- 回归测试类 `org.wordpress.android.fluxc.utils.WhatsNewAppVersionUtilsTest`（`:libs:fluxc:testDebugUnitTest` 过滤）——验证既有 `14.1.2 → 141`、`alpha-222 → -1` 未被破坏。
- `:libs:fluxc:assembleDebug`——通过 `verification.scope.tasks.assembleTasks` 显式批准的唯一构建任务。
- 独立 Review。

无 lint 任务、无设备/仪器化测试（`deviceTestsRequired: false`）。

## 7. 测试选择理由与依赖影响

- 只有两个改动路径：`:libs:fluxc` 的 `main` 生产源与 `test` 测试源，故仅选择 `:libs:fluxc:testDebugUnitTest` 的 `Debug` 变体。
- 影响传递：修改`:libs:fluxc` 的 Kotlin 源需要编译其 `:libs:fluxc-annotations`、`:libs:fluxc-processor` 依赖；执行器按任务图编译必要输入，不追加模块或全变体回退。
- `assemblyTasks` 仅 `:libs:fluxc:assembleDebug`，与 Debug 变体一致，不构建 Release 或应用模块。
- `regressionTests` 仅列既有 `WhatsNewAppVersionUtilsTest`，不追加其他模块或通配过滤。
- baseline `snapshot`：只记录 planningHead 的提交/配置/文件/测试定义元数据，不运行 Gradle，也不声称原测试已通过；新测试类在 baseline 中不存在，故 `inventory.emptyBaseline: allow`、`existingSkips: reject`。

## 8. 非目标

- 不改变版本匹配规则、正则表达式或首个匹配语义。
- 不改变无匹配输入返回 `-1` 的失败契约；不新增异常类型、日志或错误码。
- 不改变公开签名或返回类型。
- 不改写既有 `WhatsNewAppVersionUtilsTest.kt` 或任何既有测试。
- 不新增依赖、资源、Gradle/构建配置变更，不做无关重构。
- 不做设备/仪器化测试，不跑 lint，不新增文档（本计划除外）。
- 不推送远程，不做 `git push`。

## 9. 设备策略与残余风险

- 设备策略：不需要模拟器或真机；纯 JVM 单元测试。
- 残余风险：`toIntOrNull()` 在 Int 范围内与 `toInt()` 行为一致，溢出时返回 `-1`；原先可能依赖捕获 `NumberFormatException` 的外部调用方将改为收到 `-1`，这正是本需求要求的契约。快照内按文件名检索仅发现该工具类与其测试；公开签名与正常/非法输入返回值不变，调用方无需改动。
