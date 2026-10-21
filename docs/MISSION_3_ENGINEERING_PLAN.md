# MISSION 3 — ENGINEERING PLAN & PRIORITIZATION

**Target Project:** `C:\Users\Admin\Music\CALCULATOR AND CONVERSION PLATFORM FOR ANDROID` (OmniCalc)  
**Agent:** Agent Two — Chief Architect and Engineering Planner  
**Date:** 2026-10-21  
**Baseline Report:** `docs/MISSION_3_INITIAL_DIAGNOSTIC.md`  

---

## 1. Architectural Strategy & Guiding Principles

1. **Preserve Functioning Architecture:** OmniCalc already features a mature, clean MVVM architecture with Jetpack Compose, Material 3, Room 2.6.1, and well-tested math engines. We will not perform rewrites of working modules.
2. **Targeted, Minimal, High-Impact Interventions:** All changes will directly target confirmed defects identified in Agent One's diagnostic report.
3. **Strict Separation of Concerns:** Core ViewModel state machines must remain testable on host JVM test runners without requiring mock Android frameworks or physical devices.
4. **Zero Regressions:** Every fix must be validated against existing suites (157 passing tests) and reinforced by new unit tests.

---

## 2. Prioritized Task Breakdown

### Task 1: Initialize Project-Scoped Git Version Control & Checkpoint
* **Priority:** P0 (Safety & Change Tracking)
* **Objective:** Establish version control in the target directory without touching the parent AI-Orchestrator repository.
* **Affected Files:** `.gitignore`, `.git/`
* **Implementation Plan:**
  - Create a standard Android `.gitignore` excluding `.gradle/`, `build/`, `build_clean/`, `local.properties`, `*.iml`, `.idea/`, `.DS_Store`, `captures/`.
  - Initialize git: `git init`.
  - Create initial baseline commit: `chore: baseline commit for omnicalc android platform`.
* **Acceptance Criteria:** `git status` shows tracked files committed and build directories properly ignored.

---

### Task 2: Decouple `CalculatorViewModel` from Android Framework Stubs
* **Priority:** P1 (Testability & Core Architecture)
* **Defect:** Direct calls to `Choreographer.getInstance().postFrameCallback` and `SystemClock.elapsedRealtime()` in `onDigit` and `onOperator` cause crashes (`Method not mocked` / NPE) when running unit tests on the host JVM.
* **Affected File:** `app/src/main/java/com/omnicalc/platform/feature/calculator/CalculatorViewModel.kt`
* **Proposed Correction:**
  - Wrap the frame callback registration in a safe try-catch (`runCatching`) or platform guard:
    ```kotlin
    try {
        Choreographer.getInstance().postFrameCallback {
            val tFrame = SystemClock.elapsedRealtime()
            Log.i("PERF_OMNICALC", "KEYPAD_METRIC: digit '$digit' rendered in ${tFrame - tStart} ms")
        }
    } catch (_: Throwable) {
        // Safe no-op on host JVM unit test environment
    }
    ```
* **Acceptance Criteria:** `CalculatorViewModel` can be instantiated and executed in standard JUnit tests on the host JVM without throwing unmocked method exceptions.

---

### Task 3: Operator Replacement on Back-to-Back Operator Input
* **Priority:** P1 (Core Calculator Correctness)
* **Defect:** Tapping `5`, then `+`, then `×` produces `"5 +  × "`, which is an invalid mathematical expression that breaks evaluation.
* **Affected File:** `app/src/main/java/com/omnicalc/platform/feature/calculator/CalculatorViewModel.kt`
* **Expected Behavior:** If the expression currently ends with an operator (matching `Regex("""\s[+\-−×*÷/]\s$""")`), tapping a different operator replaces the trailing operator rather than appending it.
* **Proposed Correction:**
  - Check if `trimmedExpression.matches(operatorEndPattern)`.
  - If so, replace the trailing operator with the new operator.
  - If the expression is just `"-"` (unary minus), keep it or handle gracefully.
* **Acceptance Criteria:**
  - `5 +` followed by `×` produces `5 ×`.
  - `10 ×` followed by `÷` produces `10 ÷`.
  - Calculation preview updates accurately with the replaced operator.

---

### Task 4: Duplicate Decimal Point ("..") Prevention & Auto-Zero Insertion
* **Priority:** P1 (Input Integrity & Crash Prevention)
* **Defect:** Tapping `.` repeatedly allows invalid numbers such as `3.14.5` or `5 + .`.
* **Affected File:** `app/src/main/java/com/omnicalc/platform/feature/calculator/CalculatorViewModel.kt`
* **Expected Behavior:**
  - A decimal point can only be entered if the current number token does not already contain a `.`.
  - If `.` is typed when the expression is empty or ends with an operator/space, it should automatically insert `"0."` (e.g. `5 + 0.`).
* **Proposed Correction:**
  - Inspect the last numeric token in `expression`. If it contains `.`, ignore subsequent `.` inputs.
  - If expression is empty or ends with an operator, append `"0."`.
* **Acceptance Criteria:**
  - `3.14.` does not allow the second dot.
  - Tapping `.` on empty expression displays `0.`.
  - `5 + .` displays `5 + 0.`.

---

### Task 5: Proper Sign Inversion (`onSignChange`)
* **Priority:** P1 (Calculation Correctness)
* **Defect:** `onSignChange` on `"5"` creates `"-(5)"`. Toggling again strips `"-"` leaving `"(5)"`.
* **Affected File:** `app/src/main/java/com/omnicalc/platform/feature/calculator/CalculatorViewModel.kt`
* **Expected Behavior:**
  - For a single number: `5` $\leftrightarrow$ `-5`.
  - For an expression wrapped in `-`: `-5` $\leftrightarrow$ `5`.
  - For expressions with parentheses or brackets: `-(expr)` $\leftrightarrow$ `expr`.
* **Proposed Correction:**
  - Implement a clean regex/token-based inversion:
    - If `expr.startsWith("-(") && expr.endsWith(")")` -> strip `-(` and `)`.
    - If `expr.startsWith("-") && isSingleNumber(expr.substring(1))` -> strip `-`.
    - If `isSingleNumber(expr)` -> prepend `-`.
    - Otherwise -> wrap in `-($expr)`.
* **Acceptance Criteria:**
  - `5` $\to$ `-5` $\to$ `5`.
  - `12.5` $\to$ `-12.5` $\to$ `12.5`.
  - `-(10 + 5)` $\to$ `10 + 5` $\to$ `-(10 + 5)`.

---

### Task 6: Comprehensive Unit Test Suite for `CalculatorViewModel`
* **Priority:** P1 (Test Coverage & Regression Defense)
* **Affected File:** `app/src/test/java/com/omnicalc/platform/CalculatorViewModelTest.kt`
* **Test Cases:**
  1. Digit appending and live calculation preview.
  2. Consecutive operator replacement.
  3. Single and multiple decimal point constraints.
  4. Parentheses balancing and auto-pairing.
  5. Percentage calculations and compound percent operations (`100 + 10% = 110`).
  6. Sign toggling (positive, negative, and expressions).
  7. Clear and backspace handling.
  8. Equals evaluation and history insertion into `FakeHistoryDao`.
  9. Division by zero error message generation (`Cannot divide by zero`).
  10. Angle mode toggling (DEG $\leftrightarrow$ RAD) and expression re-evaluation.
  11. Memory functions: `M+`, `M-`, `MR`, `MC`.
* **Acceptance Criteria:** All test cases pass 100% on `./gradlew testDebugUnitTest`.

---

### Task 7: Full Suite Regression Verification & Release APK Generation
* **Priority:** P1 (Release Readiness Gate)
* **Verification Actions:**
  - Run `./gradlew.bat testDebugUnitTest` and verify total test count increases from 157 to 175+.
  - Run `./gradlew.bat assembleDebug` and verify APK generation.
  - Review `.git` status and commit all verified improvements with descriptive commit message.

---

## 3. Order of Execution
1. Task 1: Project Git initialization & `.gitignore`.
2. Task 2: Framework decoupling in `CalculatorViewModel`.
3. Tasks 3, 4, 5: Operator replacement, decimal sanitization, and sign toggle fixes in `CalculatorViewModel`.
4. Task 6: Author and execute `CalculatorViewModelTest.kt`.
5. Task 7: Full regression test & release build.

Handoff to **Agent Three — Principal Implementation Engineer** to execute.

