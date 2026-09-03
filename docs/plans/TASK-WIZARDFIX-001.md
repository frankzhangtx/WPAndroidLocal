# TASK-WIZARDFIX-001 Implementation Plan

> **Goal:** Make `WizardManager.showNextStep()` preserve the last valid step index when navigation past the final step throws, and add a focused regression test.

**Observable behavior change (single):** When `showNextStep()` is invoked while `currentStep` is already the last valid index, it still throws `IllegalStateException("Invalid index.")`, but `currentStep` must remain at the last valid index instead of being left at an out-of-range value (`steps.size`). No other behavior changes.

**Current behavior (evidence: `WordPress/src/main/java/org/wordpress/android/util/wizard/WizardManager.kt:19-26`):**
```kotlin
@Suppress("UseCheckOrError")
fun showNextStep() {
    if (isIndexValid(++currentStepIndex)) {          // pre-increment mutates the field before validation
        _navigatorLiveData.value = steps[currentStepIndex]
    } else {
        throw IllegalStateException("Invalid index.")
    }
}
```
The pre-increment `++currentStepIndex` mutates `currentStepIndex` before `isIndexValid` runs. At the last step (index `steps.size - 1`) the field is advanced to `steps.size`, the guard fails, and the exception is thrown with `currentStep` left equal to `steps.size` (invalid).

**Desired behavior:** Compute the candidate index without mutating state; mutate `currentStepIndex` only when the candidate is valid.

**Architecture:** Minimal change confined to one method in `WizardManager.kt`. Consumers (`SiteCreationMainVM`, `RestoreViewModel`, `BackupDownloadViewModel`) persist `wizardManager.currentStep` into saved instance state; the corrupted out-of-range value would later make restore-time `setCurrentStepIndex()` throw. No consumer file changes are in scope.

**Tech stack:** Kotlin (JVM unit test), JUnit 4, AssertJ, Mockito. No Android device/emulator interaction.

## Global Constraints

- Allowed paths: only `WordPress/src/main/java/org/wordpress/android/util/wizard/WizardManager.kt` and `WordPress/src/test/java/org/wordpress/android/util/wizard/WizardManagerTest.kt`.
- Max changed files: 2.
- Do not touch orchestration infrastructure (`automation/**`, `scripts/automation/**`, `.opencode/**`, `opencode.json*`, `AGENTS.md`, Gradle/build files, `.automation-worktree-allowlist`) or any `showNextStep`/`currentStep` consumer files.
- No new public API; exception message text stays `"Invalid index."`.
- Focused test filter (RED evidence): `org.wordpress.android.util.wizard.WizardManagerTest` via `:WordPress:testWordpressDebugUnitTest`.
- No device tests.

## Acceptance Criteria

1. Invoking `showNextStep()` on the last step still throws `IllegalStateException`.
2. After that exception, `manager.currentStep == LAST_STEP_INDEX` (never `steps.size` or larger).
3. Repeated calls after the first failure each throw and leave `currentStep` stably at `LAST_STEP_INDEX`.
4. Success-path navigation is unchanged: each call from a valid state advances exactly one step and emits a `navigatorLiveData` event; no event is emitted on the throwing call.
5. The new focused test fails before the fix (RED evidence) and passes together with the rest of `WizardManagerTest` after the fix.
6. `:WordPress:testWordpressDebugUnitTest` passes.

## Edge Cases

- Initial state (`currentStepIndex == -1`): first `showNextStep()` advances to index 0 and emits — unchanged.
- Empty step list: `showNextStep()` throws and `currentStep` remains `-1` (previously it was left at `0`); there is no last valid step, so the default index is preserved consistently.
- Failure branch never writes to `navigatorLiveData` — unchanged from today.
- `isLastStep()` semantics unchanged: it is based on the stored index, which is now never corrupted by a failed `showNextStep()`.

## Human Approval

- **2026-09-03:** Proposal approved by the human operator via the OpenCode `方案确认` single-select question (option: `批准方案，生成计划和任务合同。`) before this plan and the task contract were created.

---

## Task 1: Preserve the last valid step when `showNextStep()` throws

**Files:**
- Modify: `WordPress/src/main/java/org/wordpress/android/util/wizard/WizardManager.kt:19-26` (`showNextStep`)
- Test: `WordPress/src/test/java/org/wordpress/android/util/wizard/WizardManagerTest.kt` (add one test method)

**Interfaces:**
- Consumes: existing `WizardManager<T : WizardStep>` public surface: `showNextStep()`, `currentStep: Int`, `setCurrentStepIndex(Int)` (test helper), `navigatorLiveData`.
- Produces: unchanged signatures; only internal state-retention semantics of `showNextStep()` change.

- [ ] **Step 1: Write the failing test (add to `WizardManagerTest`)**

Append inside class `WizardManagerTest` (after the existing `exception thrown on navigation to invalid index` test, using existing constants `LAST_STEP_INDEX` and helper `createWizardManager`):

```kotlin
@Test
fun `showNextStep past last step throws but keeps last valid step index`() {
    manager = createWizardManager(initialStepIndex = LAST_STEP_INDEX)
    try {
        manager.showNextStep()
        throw AssertionError("Expected IllegalStateException")
    } catch (expected: IllegalStateException) {
        assertThat(manager.currentStep).isEqualTo(LAST_STEP_INDEX)
    }
}
```

Notes: `AssertionError` is not an `IllegalStateException`, so a missing exception fails the test instead of being swallowed. `assertThat` is already imported from AssertJ at the top of the file.

- [ ] **Step 2: Run the focused test and capture RED**

Run: `./gradlew :WordPress:testWordpressDebugUnitTest --tests "org.wordpress.android.util.wizard.WizardManagerTest"`
Expected: FAIL — the new test fails because today `currentStep` is `LAST_STEP_INDEX + 1` after the exception; all other `WizardManagerTest` tests still pass.

- [ ] **Step 3: Implement the minimal fix**

In `WordPress/src/main/java/org/wordpress/android/util/wizard/WizardManager.kt`, replace the body of `showNextStep()` so the candidate index is validated before the field is mutated:

```kotlin
@Suppress("UseCheckOrError")
fun showNextStep() {
    val nextStepIndex = currentStepIndex + 1
    if (isIndexValid(nextStepIndex)) {
        currentStepIndex = nextStepIndex
        _navigatorLiveData.value = steps[currentStepIndex]
    } else {
        throw IllegalStateException("Invalid index.")
    }
}
```

No other methods, messages, or signatures change.

- [ ] **Step 4: Run the focused test to verify it passes**

Run: `./gradlew :WordPress:testWordpressDebugUnitTest --tests "org.wordpress.android.util.wizard.WizardManagerTest"`
Expected: PASS — new test passes; all existing `WizardManagerTest` tests still pass.

- [ ] **Step 5: Run the full module unit suite (quality gate)**

Run: `./gradlew :WordPress:testWordpressDebugUnitTest`
Expected: PASS (full WordPress unit suite).

No commit is created by the agent during execution; the deterministic orchestrator owns all Git mutations and creates exactly one combined integration commit after human acceptance.
