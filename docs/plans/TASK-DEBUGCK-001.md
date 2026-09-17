# TASK-DEBUGCK-001 — DebugCookie.decode 容错增强

- 目标分支：`feature-0602-cases-opencode-test`
- planningHead：`241c40ec6a9a0d519edfe34f709e0f7c2c0d0077`
- 工作区策略：`inPlaceExclusive`；提交策略：`autoCommit`（通过构建、全量单测与独立 Review 后自动本地提交并集成，不推送远程）
- 最大改动文件数：2（修改 1 个生产文件，新增 1 个测试文件）
- 设备测试：不需要

## 1. 当前可观察行为

`WordPress/src/main/java/org/wordpress/android/ui/debug/cookies/DebugCookie.kt` 是 Kotlin data class，字段为 `host: String`、`name: String`、`value: String?`，派生成员为 `key`、`oldRfcDomain`、`headerValue`、`toURI()`、`toHttpCookie()`。

planningHead 上的编解码实现逐字为：

```kotlin
fun encode(gson: Gson): String = gson.toJson(this)

companion object {
    fun decode(gson: Gson, encoded: String?): DebugCookie? = gson.fromJson(encoded, DebugCookie::class.java)
}
```

`decode` 把 Gson 的行为直接透出给调用方：

- 非法 JSON（例如 `not-json`）与非法 JSON 结构（例如 `[1,2]`）会抛出 Gson 解析异常。
- 可解析为对象但缺少 `host` / `name` 的 JSON（例如 `{}`）会返回 host/name 被反射置空的非空实例，调用方后续在 `toURI()` / `key` 上出错。

唯一生产调用方是 `DebugCookieManager.getAll()`：`preferences.all.values.mapNotNull { DebugCookie.decode(gson, it as? String?) }`，其 Gson 由 `GsonBuilder().serializeNulls().create()` 构造。

planningHead 上不存在任何 DebugCookie 测试，`WordPress/src/test/java/org/wordpress/android/ui/debug/cookies/` 目录不存在；同层 `ui/debug` 测试使用 JUnit4 + AssertJ。

## 2. 目标可观察行为

`decode` 变成容错入口：输入为 null、非法 JSON（文本或非对象结构）、或可解析为对象但 `host` / `name` 为 null 或空白时，一律返回 null，且不向调用方抛出解析异常。合法 Cookie 的 encode/decode、公开签名与派生成员完全不变。

## 3. 验收条件

1. `decode(gson, null)` 返回 null，且不抛出任何异常。
2. `decode(gson, "not-json")` 与 `decode(gson, "[1,2]")` 返回 null，解析异常不再传播。
3. `decode(gson, "{}")`、`decode(gson, "{\"name\":\"session\"}")`、`decode(gson, "{\"host\":\"example.com\"}")` 返回 null。
4. host/name 为空白（空串或空格）时返回 null。
5. 合法往返不变：value 为 `abc`、空串、null 时 `decode(gson, cookie.encode(gson))` 等于 cookie。
6. 固定 cookie 的 encode 输出与改动前基线逐字一致。
7. 固定 cookie 的 `key`、`oldRfcDomain`、`headerValue` 与改动前基线一致。
8. 新增测试在实现前捕获 RED，失败原因必须是 Gson 解析异常或非空断言失败，不能是编译错误或夹具错误；实现后全部 GREEN。
9. 聚焦过滤测试、独立 `./gradlew testDebugUnitTest`、`assembleDebug` 与独立 Reviewer 复核全部通过；`decode` 公开签名与返回类型不变。

## 4. 边界与判定规则

| 输入 | 目标结果 |
| --- | --- |
| `null` | `null` |
| `not-json` | `null` |
| `[1,2]` | `null` |
| `{}` | `null` |
| 仅含 name 字段的对象 | `null` |
| 仅含 host 字段的对象 | `null` |
| host 为 `""` 或 `" "` | `null` |
| name 为 `""` 或 `" "` | `null` |
| 合法 cookie（value 为 `abc` / `""` / null） | 与输入 cookie 相等 |

判定只使用必填字段的 null/空白检查，不引入 Gson 强类型校验。

## 5. 精确改动文件

最大改动文件数 2：

- 生产代码（修改）：`WordPress/src/main/java/org/wordpress/android/ui/debug/cookies/DebugCookie.kt`，只改 `companion object` 的 `decode`。
- 测试代码（新增）：`WordPress/src/test/java/org/wordpress/android/ui/debug/cookies/DebugCookieTest.kt`。

除这两个文件外不得新增、修改或删除任何文件，特别是 `DebugCookieManager.kt`、`DebugCookies*` UI 文件、`automation/**`、`.opencode/**` 与任何 `build.gradle*`。

## 6. 实现步骤

### 步骤 1（RED 与基线捕获）：新增 DebugCookieTest.kt

新建 `WordPress/src/test/java/org/wordpress/android/ui/debug/cookies/DebugCookieTest.kt`，内容如下（纯 JVM，无 Android/Robolectric/Mockito 依赖；Gson 构造与生产 `DebugCookieManager` 一致）：

```kotlin
package org.wordpress.android.ui.debug.cookies

import com.google.gson.GsonBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class DebugCookieTest {
    private val gson = GsonBuilder().serializeNulls().create()

    @Test
    fun `given null encoded string, when decoding, then returns null`() {
        assertThat(DebugCookie.decode(gson, null)).isNull()
    }

    @Test
    fun `given malformed json text, when decoding, then returns null`() {
        assertThat(DebugCookie.decode(gson, "not-json")).isNull()
    }

    @Test
    fun `given json array text, when decoding, then returns null`() {
        assertThat(DebugCookie.decode(gson, "[1,2]")).isNull()
    }

    @Test
    fun `given json object missing required fields, when decoding, then returns null`() {
        assertThat(DebugCookie.decode(gson, "{}")).isNull()
        assertThat(DebugCookie.decode(gson, "{\"name\":\"session\"}")).isNull()
        assertThat(DebugCookie.decode(gson, "{\"host\":\"example.com\"}")).isNull()
    }

    @Test
    fun `given json object with blank required fields, when decoding, then returns null`() {
        assertThat(DebugCookie.decode(gson, "{\"host\":\"\",\"name\":\"session\"}")).isNull()
        assertThat(DebugCookie.decode(gson, "{\"host\":\" \",\"name\":\"session\"}")).isNull()
        assertThat(DebugCookie.decode(gson, "{\"host\":\"example.com\",\"name\":\" \"}")).isNull()
    }

    @Test
    fun `given valid cookie, when encoding and decoding, then the cookie is preserved`() {
        listOf(
            DebugCookie("example.com", "session", "abc"),
            DebugCookie("example.com", "empty", ""),
            DebugCookie("example.com", "novalue", null)
        ).forEach { cookie ->
            assertThat(DebugCookie.decode(gson, cookie.encode(gson))).isEqualTo(cookie)
        }
    }

    @Test
    fun `given valid cookie, when encoding, then the json matches the captured baseline`() {
        val cookie = DebugCookie("example.com", "session", "abc")

        assertThat(cookie.encode(gson)).isEqualTo("{\"host\":\"example.com\",\"name\":\"session\",\"value\":\"abc\"}")
    }

    @Test
    fun `given valid cookie, when reading derived members, then the values match the captured baseline`() {
        val cookie = DebugCookie("example.com", "session", "abc")

        assertThat(cookie.key).isEqualTo("example.com_session")
        assertThat(cookie.oldRfcDomain).isEqualTo(".example.com")
        assertThat(cookie.headerValue).isEqualTo("session=abc")
    }
}
```

基线捕获要求：`given valid cookie, when encoding, then the json matches the captured baseline` 中的字符串字面量必须先在不改动生产代码的前提下运行确认。若基线运行显示实际输出与该字面量不同，就以基线实际输出逐字替换该字面量（这是唯一允许的准备修正），并在执行证据中记录改动前的实际值。同理记录 `key` / `oldRfcDomain` / `headerValue` 的基线值。

### 步骤 2：运行聚焦测试，记录实现前证据

命令：

```bash
./gradlew :WordPress:testWordpressDebugUnitTest --tests "org.wordpress.android.ui.debug.cookies.DebugCookieTest"
```

第一失败行为测试是 `given malformed json text, when decoding, then returns null`。预期 RED 原因：当前 `decode` 直接调用 `gson.fromJson`，Gson 对非法 JSON 抛出 `com.google.gson.JsonSyntaxException`，因此该用例以异常报错而不是断言失败。执行者必须记录实际抛出的异常类名作为证据（测试本身不断言异常类型）。

预期同一轮的实现前结果：

- 失败（change 用例）：`given malformed json text...`、`given json array text...`（Gson 解析异常）；`given json object missing required fields...`、`given json object with blank required fields...`（`isNull` 断言失败，当前返回非空实例）。
- 通过（preserve / observe 用例）：`given null encoded string...`、`given valid cookie, when encoding and decoding...`、`given valid cookie, when encoding...`、`given valid cookie, when reading derived members...`。

若 preserve 用例因为字面量不符而失败，按步骤 1 的基线捕获要求修正字面量后重跑一次，并记录基线输出。若失败原因是 Gson 无法实例化 DebugCookie 或环境/夹具问题，必须停止并报告阻塞，不得修改依赖、Gradle 配置或生产代码来绕过。

### 步骤 3（GREEN）：修改 DebugCookie.kt 的 decode

只改 `companion object` 的 `decode`，其余成员与 `encode` 逐字不动：

```kotlin
    companion object {
        fun decode(gson: Gson, encoded: String?): DebugCookie? = runCatching {
            val decoded = gson.fromJson(encoded, DebugCookie::class.java)
            val host: String? = decoded?.host
            val name: String? = decoded?.name
            if (decoded == null || host.isNullOrBlank() || name.isNullOrBlank()) null else decoded
        }.getOrNull()
    }
```

实现约束：

- 先用 `runCatching { ... }.getOrNull()` 包住 Gson 调用，保证解析异常不外泄。
- 必填字段改为可空局部变量后再用 `isNullOrBlank()` 判定，避免对可能被反射置空的字段直接调用非空成员。
- 保留 `fun decode(gson: Gson, encoded: String?): DebugCookie?` 的签名与返回类型，`value` 不参与校验。

### 步骤 4：验证 GREEN 与质量门

重跑步骤 2 的聚焦命令，8 个用例全部通过；随后运行 `./gradlew testDebugUnitTest`（独立命令）、`./gradlew assembleDebug`，并接受独立 Reviewer 复核。配置中 `lintEnabled` 为 false，本任务不运行 lint 任务。

## 7. 命令汇总

- 聚焦：`./gradlew :WordPress:testWordpressDebugUnitTest --tests "org.wordpress.android.ui.debug.cookies.DebugCookieTest"`
- 全量单测：`./gradlew testDebugUnitTest`（独立命令，不添加环境变量前缀）
- 构建：`./gradlew assembleDebug`

## 8. 非目标

- 不修改 `encode(gson)` 与合法 Cookie 的序列化输出。
- 不修改 `DebugCookieManager.kt`、`getAll/add/remove`、SharedPreferences 键名与其中的 Gson 构造。
- 不修改 `DebugCookie` 的公开签名、构造参数、`key` / `oldRfcDomain` / `headerValue` / `toURI()` / `toHttpCookie()` 实现。
- 不新增依赖，不做 Gradle 或构建配置变更，不新增仪器化或设备测试，不做无关重构与文档改动。
- 不引入超出必填字段空值判定的 Gson 强类型校验，也不对 Gson 的数值到字符串强制转换等宽松行为做断言。
- 不改变失败返回契约：失败一律返回 null，不新增异常类型或日志。

## 9. 设备测试策略

不需要设备或模拟器：`DebugCookie` 只依赖 Gson 与 `java.net`，新增测试是纯 JVM JUnit4 测试，`deviceTestsRequired` 为 false，不运行 `connectedDebugAndroidTest`。

## 10. 残余风险

- Gson 对 `{\"host\":1}` 等数值字段的宽松读取与测试 JDK 的反射实例化细节未在本任务中断言；这两点不属于已批准的行为变化范围。
- 已批准的 `TASK-STRICTDATE-002` 任务此前在同一分支使用过相同的 JVM 单测链路，可降低测试环境不确定性，但本任务的基线捕获步骤仍会给出独立证据。
