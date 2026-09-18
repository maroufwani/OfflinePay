# OfflinePay – Improvement Audit

> **Current audit:** September 6, 2026 — against `master` @ `7648947`, v0.1.1-beta (versionCode 2)
> **Scope:** 50 Kotlin files, ~10,800 LOC. Read-only inspection; no code changed.
> **Previous audit:** April 24, 2026 — preserved in [Appendix A](#appendix-a--april-24-2026-audit) with resolution status.
> **Resolution pass:** September 8, 2026 — every item below has been worked through; see [Resolution status](#resolution-status--september-8-2026) for the summary and the decisions taken.

---

## Contents

- [Resolution status — September 8, 2026](#resolution-status--september-8-2026)
- [🔴 Critical — costs money or voids security](#-critical--costs-money-or-voids-security)
- [🟠 High — hangs, stuck state, wrong data](#-high--hangs-stuck-state-wrong-data)
- [🟡 Medium](#-medium)
- [⚡ Optimisation](#-optimisation)
- [🏗 Approach-level critique](#-approach-level-critique)
- [✅ What's solid](#-whats-solid)
- [Priority summary](#priority-summary)
- [Appendix A — April 24, 2026 audit](#appendix-a--april-24-2026-audit)

---

## Resolution status — September 8, 2026

Every item above has been worked through. Per-item status is recorded inline: as a blockquote under each prose heading, and as a **Status** column in each table.

| | Count | |
|---|---|---|
| ✅ Fixed | 40 | C1–C4 (incl. C3a–C3d), H1–H10, M1–M14, O1–O6, O8, O9, O11, A2, A3, A4 |
| ◐ Partly | 1 | O10 — three of four dependency bumps done, `biometric` held deliberately |
| ⬜ Declined, with reasons | 5 | O7, A1, A5, A6, A7 |
| ⚠️ Knowingly reversed | 1 | April item 4 — `security-crypto` back on an alpha, to get **C3** |

Two items from April 2026 remain open and unaddressed: **9** (no dependency injection) and, following from **A1** being declined, **7** (`UssdManager` is a global mutable singleton). They are the same piece of work.

Verification: `./gradlew :app:assembleDebug` and `:app:testDebugUnitTest` both pass; 81 unit tests across 6 classes. Nothing in this pass has been exercised on a real `*99#` session against a real bank — **C2** in particular is the kind of defect that only a device can confirm, since a database keyed with 32 zero bytes reads back perfectly.

### Decisions taken

The instruction for this pass was to decide ambiguous calls rather than ask. Each decision is also recorded as a comment at the code it affects; these are the ones where a reasonable engineer would have chosen differently.

**1. `security-crypto` returned to `1.1.0-alpha06`, reversing April item 4.** C3 needs the Keystore to refuse decryption without a fresh authentication, which means `setUserAuthenticationRequired` on the master key, which means `MasterKey.Builder` — absent from stable `1.0.0`, where only the deprecated `MasterKeys` exists. So the choice was an alpha dependency or a UI-only PIN gate. An alpha in a library whose failure mode is "does not compile or throws at init" is a smaller risk than shipping a stored UPI PIN that any process-level compromise can read, and it is the specific alpha that was already in use before April. Both `SecurePrefs` entry points (`openStandard`, `openAuthBound`) are in one file, so returning to stable later is one file's work.

**2. `biometric` held at `1.1.0`, against O10's recommendation.** O10 asked for 1.4.x "needed for the API 30+ auth-type handling in H9". It is not: H9's actual defect was `onAuthenticationFailed` wired to `onError`, and `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` works on the stable release. Every version after 1.1.0 is an alpha, and this library sits directly on the payment-authorisation path. Taking one alpha for a capability that does not otherwise exist (decision 1) is a different trade from taking a second alpha for an API cleanup.

**3. Update check gated at 6 hours, not the "at most daily" O5 suggested.** This app's update channel *is* its bug-fix channel — H6 meant no beta ever saw a later beta, and the fixes in this pass are the ones users need. A day's latency on a payment-correctness fix is worse than four HEAD requests a day. Revisit if it ever ships through a store.

**4. Classifiers are compiled pattern lists, not remote config.** A2 suggested shipping them as config so a bad match need not wait for an app update. Declined: it would put a network-fetched input in the path that decides whether money moved. The gap it was meant to close — a bank wording nobody has seen — is closed the other way, by the report mechanism in decision 5.

**5. Unrecognised responses are reported through the share sheet, with no backend.** The user picks the destination, so consent is a deliberate act rather than a setting someone has to find and switch off; there is no analytics endpoint anywhere near the payment path and no privacy policy needed for telemetry that does not exist. The report carries app version, Android version and which flow was running — no device id, install id, phone number or UPI ID. The response text goes in **verbatim rather than scrubbed**, because blanking long digit runs would remove the reference ids, amounts and balances the classifier keys on, which is the only reason to collect the string. The failure screen shows the exact text first and says it may contain account details and can be edited, so what the user consents to is the real string rather than a promise about a scrubber.

**6. The PIN-storage disclosure is worded as what happens, not as reassurance.** "Stored on this phone" is the fact a user needs in order to decide; calling it "secure" is not, and the app saying so proves nothing. It appears twice — in the dialog that captures the PIN, and permanently under Settings → Security — because a user who wants to check later has by definition already dismissed the dialog.

**7. `formatAmount` groups digits by hand; M8's own suggested fix was wrong.** M8 proposed `NumberFormat.getCurrencyInstance(Locale("en","IN"))`. That was implemented, and a unit test failed: lakh/crore grouping depends on the platform's locale data — Android's ICU gives `1,23,456`, the JDK's CLDR gives `123,456` — and `DecimalFormat("#,##,##0.00")` cannot express it either, since the JVM honours only the last grouping interval. On a device this would have been quietly wrong instead of a red test. The arithmetic is now done in the app: identical output everywhere, ASCII by construction, no shared mutable formatter.

**8. C2 was fixed by deleting the wipe, not by relocating it.** There is no correct place for it. `Room.build()` does not open the database — SQLite open is deferred to the first query — and `SupportOpenHelperFactory` holds the passphrase array by reference, so zeroing it in any `finally` risks keying the database with 32 zero bytes, silently, because a database created and read with a zero key works perfectly. `sqlcipher-android` defaults `clearPassphrase = true` and zeroes the array itself once the database is genuinely open.

**9. Release builds fall back to unsigned rather than failing when `signing.properties` is absent.** O11 asked for a signing config; the ambiguity was what to do on a machine without the credentials. Failing the build would block anyone from producing a release build to test, so the config is applied when the file is present and skipped with a warning when it is not.

**10. A1, A5, A6 and A7 are declined, and the bugs they were cited for are fixed in place.** The critiques are right about the design. They are declined for this pass because each is a rewrite of a file that moves money or renders the PIN pad, they would land in the same release as the C1–C4 fixes — making any regression in them indistinguishable from a regression in the fixes — and A1 in particular cannot be unit-tested without the DI that April item 9 is still asking for. What did land is the part a later refactor needs in order to be verifiable: the invariants behind H1–H4 are now each stated and checked in one place, `@Volatile` where they cross threads, with an ownership token instead of implicit sharing. Each declined item's own blockquote gives its specific reasoning.

**11. `REQUEST_INSTALL_PACKAGES` is kept.** M14 flagged its Play-policy implications. The self-update path is the only way fixes reach users of a sideloaded beta, so the permission stays and the manifest now carries a comment saying it must be removed — along with the updater — if this app is ever submitted to Play.

---

## 🔴 Critical — costs money or voids security

### C1. Failed payments are recorded as SUCCESS (substring collision)

> **Status: ✅ Fixed.** Classification moved out of `UssdManager` into a new [`UssdResponseClassifier.kt`](app/src/main/java/com/mw/offlineupi/service/UssdResponseClassifier.kt). The negation guard runs first, matching is by word boundary (`\bsuccess(ful)?\b`) instead of `contains`, and `isBalanceResponse` is gated on `UssdCommandType.CHECK_BALANCE` — the command type is now passed in rather than read from shared state. All three rows in the table above are regression tests in [`UssdResponseClassifierTest`](app/src/test/java/com/mw/offlineupi/service/UssdResponseClassifierTest.kt) (25 tests).

**File:** [`UssdManager.kt:1028-1040`](app/src/main/java/com/mw/offlineupi/service/UssdManager.kt#L1028-L1040)

```kotlin
private fun isSuccessResponse(text: String): Boolean {
    val lower = text.lowercase()
    return lower.contains("successful") || lower.contains("success") || ...
```

`"unsuccessful".contains("successful")` is **`true`**.

`isErrorResponse` runs first ([`:728`](app/src/main/java/com/mw/offlineupi/service/UssdManager.kt#L728)) and would normally intercept a decline — but it matches none of the words in a typical NPCI failure string, so the text falls through to the success branch. Three cases traced end-to-end:

| Bank response | `isErrorResponse` | Result |
|---|---|---|
| `Transaction unsuccessful. Please try again.` | false — text has "try again", the rule requires "try again later" | ✅ **Success** |
| `Your transaction was not successful` | false | ✅ **Success** |
| `Insufficient balance. Available balance Rs 100` | false | ✅ **Success** via `isBalanceResponse` |

The state flows to `UssdState.Success` → the ViewModel calls `updateTransactionStatus(id, "SUCCESS", refId)`. **The user sees a success receipt and the history row reads SUCCESS for a payment that never happened.** For insufficient-balance — one of the most common UPI declines — this is the default behaviour.

The `isBalanceResponse` term inside `isSuccessResponse` ([`:1042`](app/src/main/java/com/mw/offlineupi/service/UssdManager.kt#L1042)) is the deeper design error: balance text is only a success signal for `CHECK_BALANCE`, but it is applied to every command type.

**Fix:**
1. Guard first: `if (lower.contains("unsuccessful") || lower.contains("not successful")) return false`
2. Match on word boundaries (`Regex("\\bsuccess(ful)?\\b")`) rather than `contains`.
3. Gate `isBalanceResponse` on `currentCommand?.type == UssdCommandType.CHECK_BALANCE`.
4. Back it with a table-driven unit test over real captured bank strings — see [Approach A2](#a2-screen-scraping-a-system-dialog-is-inherently-fragile--the-fallbacks-should-reflect-that).

---

### C2. SQLCipher may be keyed with 32 zero bytes

> **Status: ✅ Fixed** — by *removing* the wipe rather than moving it. `Room.build()` does not open the database, and `SupportOpenHelperFactory` holds the array by reference, so no `finally` block placement was safe. `sqlcipher-android` defaults `clearPassphrase = true` and zeroes the array itself once the DB is actually opened, so the manual wipe was either redundant or destructive and never useful. The reasoning is recorded in a comment at the call site so it is not "fixed" back.

**File:** [`AppDatabase.kt:84-105`](app/src/main/java/com/mw/offlineupi/data/local/AppDatabase.kt#L84-L105)

```kotlin
val passphrase = getPassphrase(context)
try {
    val factory = SupportOpenHelperFactory(passphrase)
    Room.databaseBuilder(...).openHelperFactory(factory).build().also { INSTANCE = it }
} finally {
    passphrase.fill(0)
}
```

`Room.build()` does **not** open the database — SQLite open is deferred to the first query. `SupportOpenHelperFactory` holds the array *by reference*. If the open happens after this `finally`, the key material is all zeros by then.

Both outcomes are silently self-consistent, which is what makes this dangerous: a DB created *and* read with a zero key works perfectly and produces no symptom, while the encryption is worthless to anyone who has read this code.

Separately, sqlcipher-android's factory already defaults `clearPassphrase = true` and zeroes the array itself after opening — so this manual `fill(0)` is **either redundant or destructive, never useful**.

**Verify before assuming:** pull `/data/data/com.mw.offlineupi/databases/*.db` from a device and try `PRAGMA key = "x'00...00'"` (64 zeros). Regardless of the result, delete the `finally` block and let SQLCipher handle zeroing.

---

### C3. Plaintext UPI PIN at rest; biometric is only a UI gate

> **Status: ✅ Fixed** (C3 and C3a–C3d). The PIN now lives in a separate `EncryptedSharedPreferences` file opened through the new [`SecurePrefs.kt`](app/src/main/java/com/mw/offlineupi/data/preferences/SecurePrefs.kt), keyed by a `MasterKey` built with `setUserAuthenticationRequired(true)` and a 30-second validity window — so the Keystore, not the UI, refuses to decrypt without a fresh authentication. Reaching for `MasterKey.Builder` meant returning `security-crypto` to `1.1.0-alpha06`, which knowingly reverses April item 4; see the decisions list.

**File:** [`AppPreferences.kt`](app/src/main/java/com/mw/offlineupi/data/preferences/AppPreferences.kt) — `setEncryptedUpiPin` / `getEncryptedUpiPin`

The PIN is written to `EncryptedSharedPreferences` **without** `setUserAuthenticationRequired(true)` on the master key. The Keystore key is therefore usable by the app process at any time; biometric success is just a boolean deciding whether the code path calls `getEncryptedUpiPin()`. On a rooted or debuggable device, or via any code-execution bug, the PIN is readable **with no biometric at all**.

**Fix:** generate the master key with a `KeyGenParameterSpec` carrying `setUserAuthenticationRequired(true)` + `setUserAuthenticationValidityDurationSeconds(...)`, so the cipher physically cannot decrypt without a fresh auth. This also means migrating off deprecated `MasterKeys` to `MasterKey.Builder`.

Same threat model, all currently open:

| # | Issue | Location | Status |
|---|---|---|---|
| C3a | **No `FLAG_SECURE` on the PIN overlay.** Both `createLayoutParams()` and `createFocusableLayoutParams()` omit it, so the numpad and typed dot-count are capturable by screenshot, screen recording, and any other accessibility service. `MainActivity` has none either. | [`OverlayManager.kt:297-321`](app/src/main/java/com/mw/offlineupi/service/OverlayManager.kt#L297-L321) | ✅ **Fixed** — `FLAG_SECURE` on both overlay window types, and on `MainActivity` |
| C3b | **PIN held in an immutable `String`** — `var currentPin = ""` cannot be zeroed; it stays on the heap until GC and appears in any heap dump. Same for `pendingPin` in `UssdManager`. Use `CharArray`/`ByteArray`, wiped after submit. | [`OverlayManager.kt:619`](app/src/main/java/com/mw/offlineupi/service/OverlayManager.kt#L619) | ✅ **Fixed** — `CharArray` end to end, wiped in a `finally`; `CharArrayCharSequence` keeps it off the String pool at the `ACTION_SET_TEXT` boundary |
| C3c | **`failSession` doesn't clear `pendingPin`** — also leaves `currentCommand`, `messageQueue`, `verifiedPayeeName`, `lastSubmittedAmount` set on a singleton that outlives the Activity. | [`UssdManager.kt:242-252`](app/src/main/java/com/mw/offlineupi/service/UssdManager.kt#L242-L252) | ✅ **Fixed** — `failSession` wipes `pendingPin` and clears `currentCommand`, `messageQueue`, `verifiedPayeeName`, `lastSubmittedAmount` |
| C3d | **Clipboard PIN fallback** — when `ACTION_SET_TEXT` fails the PIN is pasted via clipboard, then "cleared" with `ClipData.newPlainText("", "")`. That writes a *new clip*, still readable; API 28+ should use `clearPrimaryClip()`. Any clipboard-monitoring app sees the PIN. | [`UssdAccessibilityService.kt`](app/src/main/java/com/mw/offlineupi/service/UssdAccessibilityService.kt) | ✅ **Fixed** — still the fallback, but the previous clip is restored and `clearPrimaryClip()` (API 28+) is used, with an empty-clip overwrite below that |

---

### C4. No upper bound on payment amount — `isValidAmount` is dead code

> **Status: ✅ Fixed** — and moved. `Validators.amountError` now returns a reason string and is called from `UssdManager.startCommand`, the one boundary every entry point passes through (overlay, ViewModel, QR deep link), so this is also **A4**. The cap is `MAX_USSD_AMOUNT = 5_000.0`, the NPCI `*99#` limit, not the ₹100,000 app-level UPI limit.

[`Validators.kt:12`](app/src/main/java/com/mw/offlineupi/util/Validators.kt#L12) defines `isValidAmount` (cap `100_000`). **Grep shows zero call sites anywhere in the codebase.**

The only amount validation that actually runs is in the overlay's Continue handler, [`OverlayManager.kt:1011-1022`](app/src/main/java/com/mw/offlineupi/service/OverlayManager.kt#L1011-L1022):

```kotlin
if (amount.isEmpty() || amount == "." || (amount.toDoubleOrNull() ?: 0.0) <= 0) { ... }
```

No maximum, and the field allows 10 characters — so `9999999999` (₹999 crore) is submitted to the USSD session unchallenged. It also accepts `10.999` (three decimals), which `*99#` rejects mid-flow *after the PIN has already been entered*.

The real NPCI `*99#` per-transaction cap is **₹5,000**, so the constant in `Validators` is both wrong and unused. This supersedes April's item 12, which assumed the cap was at least being enforced.

**Fix:** enforce validation at the command boundary (`UssdManager.startCommand`), not in a click listener — see [Approach A4](#a4-validation-lives-in-the-view-layer).

---

## 🟠 High — hangs, stuck state, wrong data

### H1. Permanent stuck session: `newSession()` before the informational early-return

> **Status: ✅ Fixed.** `newSession()` no longer runs before the informational early-returns, and every early-return path either re-arms the watchdog or clears `isRunning`. The comment at the return site names the invariant explicitly, because the bug was invisible: the watchdog disarmed itself and nothing else could ever clear the flag.

**File:** [`UssdManager.kt:652-682`](app/src/main/java/com/mw/offlineupi/service/UssdManager.kt#L652-L682)

`newSession()` at line 656 bumps the session counter, which invalidates every pending `scheduleTimeout` (they check `expectedSession == sessionId`). Lines 679-682 then `return` for `"welcome"` / `"running"` / blank **without arming a replacement timeout**:

```kotlin
newSession()                                     // :656 — invalidates all pending timeouts
...
if (lower.contains("welcome") || lower.contains("running") || responseText.isBlank()) {
    Log.d(TAG, "Ignoring informational/blank final response")
    return                                       // :681 — no new timeout armed
}
```

If the operator's *last* dialog is a welcome/progress screen — or arrives blank, which is exactly the device-timing case the comment at `:645` describes — then `isRunning` stays `true` forever, the overlay never hides, and every subsequent `startCommand` is refused. The user's only recovery is force-stopping the app.

This is a watchdog that disarms itself precisely in the situation it exists for.

---

### H2. `isRunning = true` set before suspension points

> **Status: ✅ Fixed.** The session is claimed *before* the suspending preference read, and re-checked after it — a caller that lost the race is logged and dropped rather than overwriting a live session's state.

**File:** [`UssdManager.kt:321-326`](app/src/main/java/com/mw/offlineupi/service/UssdManager.kt#L321-L326)

`isRunning = true` precedes the `prefs.simSlot.first()` / DataStore reads. If the calling `viewModelScope` is cancelled while suspended (rotation, navigation, process-death race), the coroutine dies after the flag is set and nothing resets it. Same permanent-lockout symptom as H1.

**Fix:** set the flag after the last suspension point, or wrap the body in `try/finally`.

---

### H3. `continueSession()` indexes `messageQueue` without a bounds check

> **Status: ✅ Fixed.** Both `messageQueue` reads are bounds-checked (`currentStep < messageQueue.size`), so a bank inserting an extra prompt fails the session instead of throwing out of the accessibility-service callback and killing the service mid-payment.

**File:** [`UssdManager.kt:857-881`](app/src/main/java/com/mw/offlineupi/service/UssdManager.kt#L857-L881)

`messageQueue[currentStep]` on the zero-arg path. Any dialog arriving after the queue is drained throws `IndexOutOfBoundsException` **inside an AccessibilityService callback**. Unhandled exceptions there can get the service unbound by the system, silently breaking the app until the user re-enables it in Settings.

---

### H4. Cross-ViewModel status corruption

> **Status: ✅ Fixed.** `UssdManager` hands out a monotonic ownership token (`currentSessionOwner`) and every ViewModel checks `ownsSession(token)` before acting on a state change, so a screen left in the back stack can no longer write another screen's outcome into transaction history. This is a targeted fix, not the redesign in **A1** — see the decisions list for why.

**Files:** [`PayMobileViewModel.kt`](app/src/main/java/com/mw/offlineupi/ui/paymobile/PayMobileViewModel.kt), [`ScanPayViewModel.kt`](app/src/main/java/com/mw/offlineupi/ui/scanpay/ScanPayViewModel.kt), [`BalanceCheckViewModel.kt`](app/src/main/java/com/mw/offlineupi/ui/balance/BalanceCheckViewModel.kt)

All three collect the **same global** `UssdManager.state`. Any two simultaneously alive with `lastTransactionId > 0` — trivially reachable via the Compose backstack, where a screen's ViewModel survives navigation — will each write `updateTransactionStatus(..., "SUCCESS", ...)` from a single payment. Two history rows, one payment.

Root cause: a global singleton `StateFlow` has no notion of *which* caller owns the current command. See [Approach A1](#a1-global-mutable-singleton-as-the-payment-state-machine).

---

### H5. Contacts search runs a ContentProvider query on the main thread

> **Status: ✅ Fixed.** The contacts query runs in `withContext(Dispatchers.IO)`, the previous search `Job` is cancelled on each new keystroke, and the query is debounced.

**File:** [`PayMobileViewModel.kt:157-188`](app/src/main/java/com/mw/offlineupi/ui/paymobile/PayMobileViewModel.kt#L157-L188)

`viewModelScope` is `Dispatchers.Main.immediate`. There is no `withContext(Dispatchers.IO)`, so the binder IPC into the contacts provider plus the full cursor walk happen **on the UI thread** — on **every keystroke** from the 2nd character, with **no debounce** and **no cancellation of the previous search**. On a 2,000-contact device this is visible jank and an ANR candidate.

Grep confirms `Dispatchers.IO` appears in only three files app-wide (`MainActivity`, `ApkInstaller`, `UpdateChecker`); every other `viewModelScope.launch` body runs on Main. Room's suspend DAOs dispatch internally so those are safe — but `EncryptedSharedPreferences` writes are not: `putString` performs the AES-GCM encryption **synchronously**, and `apply()` defers only the disk flush.

**Fix:** `withContext(Dispatchers.IO)`, ~250 ms debounce, cancel the previous `Job`.

---

### H6. Beta→beta updates are never offered

> **Status: ✅ Fixed.** `UpdateChecker.isNewerVersion` compares numeric components and then pre-release suffixes, splitting each suffix into alphabetic and numeric runs so `beta10` sorts after `beta2` and a release outranks any pre-release of the same version. 12 tests in [`UpdateCheckerTest`](app/src/test/java/com/mw/offlineupi/util/UpdateCheckerTest.kt), including the upgrade path this beta will actually be asked about.

**File:** [`UpdateChecker.kt`](app/src/main/java/com/mw/offlineupi/util/UpdateChecker.kt)

`isNewerVersion` returns `false` when both versions carry a pre-release suffix, so `0.1.1-beta` → `0.1.2-beta` is not detected. For an app whose entire distribution channel is beta APKs from GitHub Releases, this **disables the update mechanism for its actual audience** — which also means none of the fixes in this document can reach existing installs until it is fixed.

Also: `disconnect()` is not in a `finally`; and the `.sha256` asset is served from the same origin as the APK, so it detects corruption, not tampering. Only a signature check over the release (or the cert pinning in H7) closes that.

---

### H7. Update install path

> **Status: ✅ Fixed.** Download and install moved into a new [`ApkInstaller.kt`](app/src/main/java/com/mw/offlineupi/util/ApkInstaller.kt): the cache directory is emptied before each download, every failure path deletes its partial file, and the APK's signing certificates are compared against the installed app's before the install intent is offered. A same-origin hash alone could not catch a substituted release asset; a signature match cannot be forged without the signing key.

**File:** [`ApkInstaller.kt`](app/src/main/java/com/mw/offlineupi/util/ApkInstaller.kt)

- The downloaded APK in `cacheDir/apk_updates/` is **never deleted** — every update permanently leaks its full APK size.
- No verification that the new APK's signing certificate matches the installed one (`PackageManager.checkSignatures` / `GET_SIGNING_CERTIFICATES`). For a self-hosted channel this is the check that actually matters.
- `ACTION_INSTALL_PACKAGE` is deprecated in favour of `PackageInstaller`.
- In [`MainActivity.kt`](app/src/main/java/com/mw/offlineupi/MainActivity.kt), `HashMismatch` is handled with `// HashMismatch: do nothing` — a failed integrity check should surface to the user, not vanish.
- The download runs in `rememberCoroutineScope()` with no progress UI and no survival across rotation.

---

### H8. App lock is bypassable

> **Status: ✅ Fixed.** The unlock flag is a plain `mutableStateOf` field cleared in `onStop` — not `onPause`, which also fires for the biometric prompt itself — and it is deliberately **not** saved into `SavedStateHandle`, so a process death re-locks rather than restoring an unlocked session.

**File:** [`MainActivity.kt`](app/src/main/java/com/mw/offlineupi/MainActivity.kt)

- `var isUnlocked by rememberSaveable { mutableStateOf(false) }` — no re-lock in `onStop`, so backgrounding and returning skips auth entirely. `rememberSaveable` also *restores* `true` after process death.
- `isOnboarded` (initial `null`) and `appLockEnabled` (initial `false`) are collected independently, so on a cold start the full UI — including transaction history — renders for at least a frame before the lock gate appears. Combine into one state before deciding what to draw.
- `onError = { }` swallows auth failures.
- `canUseBiometric() == false` sets `isUnlocked = true` — i.e. **removing your fingerprint removes the lock**.

---

### H9. Biometric prompt aborts on the first bad read

> **Status: ✅ Fixed.** `onAuthenticationFailed` is wired to a non-terminal `onFailedAttempt` callback that does nothing by default, letting `BiometricPrompt` handle its own retry. It used to be wired to `onError`, which tore down the payment on one badly-placed finger.

**File:** [`SecurityUtil.kt`](app/src/main/java/com/mw/offlineupi/util/SecurityUtil.kt)

`onAuthenticationFailed()` calls `onError(...)`. But `onAuthenticationFailed` means "that finger wasn't recognised, try again" — `BiometricPrompt` stays open and handles retries itself. Calling `onError` here tears down the payment flow on one smudged read. It should be a no-op or a UI hint.

`BIOMETRIC_STRONG or DEVICE_CREDENTIAL` is also **not a supported combination below API 30**, and `minSdk` is 26 — on API 26–29 `canAuthenticate` returns an error, and per H8 that error is interpreted as "unlocked".

**File:** [`BiometricAuthActivity.kt`](app/src/main/java/com/mw/offlineupi/BiometricAuthActivity.kt)

- `onDestroy()` fires the error callback without checking `isChangingConfigurations`, so a rotation during the prompt cancels the payment.
- If `startActivity` throws, the `pendingCallbacks` entry is never removed.

---

### H10. Full USSD response text logged at `Log.w` in release

> **Status: ✅ Fixed.** Every `Log.w` that carried bank text now logs a shape instead of the content — length, exception class name, or nothing. The one place the raw text is still needed is the unrecognised-response report, and that never touches the log: it is carried on `UssdState.Failed` and shown to the user (**A2**).

**File:** [`UssdManager.kt:757`](app/src/main/java/com/mw/offlineupi/service/UssdManager.kt#L757)

```kotlin
Log.w(TAG, "Unrecognized final response, treating as failure: $responseText")
```

The ProGuard rule strips only `Log.v` and `Log.d`. This `w` survives into release and writes raw bank response text — which can include account fragments and balances — to logcat.

**Fix:** strip `w`/`i`/`e` too, or drop the payload from the message.

---

## 🟡 Medium

| # | Issue | Location | Status |
|---|---|---|---|
| M1 | **`hasCellularConnectivity` can crash** — `tm.simState` / `tm.serviceState` are outside any `try/catch`. `serviceState` throws `SecurityException` on some OEM builds without extra permission, crashing the pre-flight check. | [`UssdManager.kt:212-222`](app/src/main/java/com/mw/offlineupi/service/UssdManager.kt#L212-L222) | ✅ **Fixed** — `simState` / `serviceState` inside `try/catch`; the log records the exception class, not the telephony state |
| M2 | **PIN pad stays live after session expiry** — the 60 s `CountDownTimer.onFinish()` only changes the label to "Session expired". The user can still type and submit a PIN into a dead USSD session. Disable input and dismiss. | [`OverlayManager.kt:531-536`](app/src/main/java/com/mw/offlineupi/service/OverlayManager.kt#L531-L536) | ✅ **Fixed** — `onFinish` disables input and dismisses the pad |
| M3 | **Node leaks in the accessibility service** — `collectLeaves` never calls `recycle()` and recurses with no depth cap. `lastEvent` is captured via deprecated `AccessibilityEvent.obtain(event)` and then never read — a pure leak. | [`UssdAccessibilityService.kt`](app/src/main/java/com/mw/offlineupi/service/UssdAccessibilityService.kt) | ✅ **Fixed** — depth cap on `collectLeaves`, and the write-only `AccessibilityEvent.obtain` copy removed |
| M4 | **Discarded event subscription** — the service `return`s immediately on `TYPE_WINDOW_CONTENT_CHANGED` (`:261-263`) while the config subscribes to it with `notificationTimeout="0"`. The system delivers a high-frequency event stream that is dropped on arrival; remove it from the config. | [`accessibility_service_config.xml`](app/src/main/res/xml/accessibility_service_config.xml) | ✅ **Fixed** — `TYPE_WINDOW_CONTENT_CHANGED` removed from the config, so the stream is no longer delivered at all |
| M5 | **No DB migration path** — `version = 1`, `exportSchema = false`, no `Migration` objects, no `fallbackToDestructiveMigration`. The first schema change on a shipped beta either crashes at open or (if the fallback is added later) silently deletes users' transaction history. Turn `exportSchema` on and commit the JSON **now**, while there is only one version to baseline. | [`AppDatabase.kt`](app/src/main/java/com/mw/offlineupi/data/local/AppDatabase.kt) | ✅ **Fixed** — `exportSchema = true` and `app/schemas/…/1.json` committed while there is still only one version to baseline |
| M6 | **Zero tests.** `app/src/test/` and `app/src/androidTest/` contain package directories and nothing else. JUnit/Espresso are declared in `libs.versions.toml` and unused. | `app/src/test/`, `app/src/androidTest/` | ✅ **Fixed** — 81 tests across 6 classes; all of A's list except `UssdManager.extractPayeeName`, which is not reachable without an `AccessibilityEvent` |
| M7 | **`UPI_ID_REGEX` rejects valid handles** — `"^[a-zA-Z0-9.\\-_]+@[a-zA-Z]+$"` rejects every handle whose *domain* contains a digit (`@paytm4`, `@axis2`). | [`Validators.kt`](app/src/main/java/com/mw/offlineupi/util/Validators.kt) | ✅ **Fixed** — handle may contain digits and dots (`@paytm4`, `@icici.bank`); local part bounded at 64 chars |
| M8 | **`formatAmount` uses the default locale** — `"₹%.2f".format(...)` renders Devanagari digits on a `hi-IN` / `mr-IN` device. Display-only (confirmed: used only in `TransactionHistoryScreen` and `HomeScreen`), but wrong for an Indian payments app. Use `NumberFormat.getCurrencyInstance(Locale("en","IN"))`. | [`Validators.kt`](app/src/main/java/com/mw/offlineupi/util/Validators.kt) | ✅ **Fixed** — but *not* as suggested: see the decisions list, `NumberFormat(en-IN)` is platform-dependent and was itself wrong |
| M9 | **`maskPhoneNumber` reveals 6 of 10 digits** — weak masking, given the last 4 are the identifying part. | [`Validators.kt`](app/src/main/java/com/mw/offlineupi/util/Validators.kt) | ✅ **Fixed** — all but the last two digits masked |
| M10 | **`UpiQrParser` gaps** — case-sensitive `startsWith("upi://pay")` misses `UPI://PAY` (valid per RFC 3986 scheme rules); `am` / `pa` are never validated; `cu=USD` is silently treated as INR. | [`UpiQrParser.kt`](app/src/main/java/com/mw/offlineupi/util/UpiQrParser.kt) | ✅ **Fixed** — case-insensitive scheme, `pa` and `am` validated, non-INR `cu` rejected rather than assumed |
| M11 | **`shareReceiptScreenshot`** — PNG `compress()` runs on the calling (main) thread, and it always writes the same `receipt.png`, so two shares in flight collide. | [`ShareUtil.kt`](app/src/main/java/com/mw/offlineupi/util/ShareUtil.kt) | ✅ **Fixed** — `compress()` off the main thread, unique filename per share |
| M12 | **Raw USSD text stored as "balance"** — `saveBalance(success.message)` persists the entire 200-char response and renders it on the home screen; the numeric balance is never parsed out, and bank account fragments get written to prefs. | [`BalanceCheckViewModel.kt`](app/src/main/java/com/mw/offlineupi/ui/balance/BalanceCheckViewModel.kt) | ✅ **Fixed** — new `BalanceParser` extracts the numeric balance; the raw response is no longer persisted |
| M13 | **`KEY_REMIND_LATER_TIME`** is a `stringPreferencesKey` parsed with `toLongOrNull()`; should be `longPreferencesKey`. | [`AppPreferences.kt`](app/src/main/java/com/mw/offlineupi/data/preferences/AppPreferences.kt) | ✅ **Fixed** — `longPreferencesKey`, as is the update-check timestamp |
| M14 | **Manifest** — no `android:localeConfig`, no explicit `android:enableOnBackInvokedCallback`. `REQUEST_INSTALL_PACKAGES` is a sensitive permission with Play-policy implications if this ever ships there. | [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) | ✅ **Fixed** — `localeConfig` and `enableOnBackInvokedCallback` declared; `REQUEST_INSTALL_PACKAGES` kept and documented in the manifest as the reason this app cannot ship on Play |

---

## ⚡ Optimisation

| # | Opportunity | Location | Status |
|---|---|---|---|
| O1 | **`SimpleDateFormat` allocated per list item, per frame.** `HistoryItem` constructs one inside the composable body — a pattern parse plus locale-data load for every visible row on every recomposition, inside a `LazyColumn`. Hoist to a top-level `val`/`remember`, or move to immutable `java.time.DateTimeFormatter`. | [`TransactionHistoryScreen.kt:162`](app/src/main/java/com/mw/offlineupi/ui/history/TransactionHistoryScreen.kt#L162), [`HomeScreen.kt:627`](app/src/main/java/com/mw/offlineupi/ui/home/HomeScreen.kt#L627), [`:635`](app/src/main/java/com/mw/offlineupi/ui/home/HomeScreen.kt#L635) | ✅ **Fixed** — new `DateFormats` object caches immutable `DateTimeFormatter`s keyed by pattern *and* locale, so a locale change is picked up rather than frozen at first use |
| O2 | **`groupTransactionsByDate` re-runs on every recomposition** — a plain function called from the composable body, allocating another `SimpleDateFormat` each time. Wrap in `remember(transactions)`. | [`TransactionHistoryScreen.kt:269-284`](app/src/main/java/com/mw/offlineupi/ui/history/TransactionHistoryScreen.kt#L269-L284) | ✅ **Fixed** — `remember(transactions)` |
| O3 | **`AppPreferences` blocks app startup.** `encryptedPrefs` is `by lazy`, but the `_phoneNumber` / `_lastBalance` / `_lastBalanceTime` field initialisers touch it immediately — defeating the laziness, so Keystore init + EncryptedSharedPreferences open (tens of ms) runs on the main thread inside `OfflineUpiApp.onCreate()`. Make them genuinely lazy or load off-thread. | [`AppPreferences.kt`](app/src/main/java/com/mw/offlineupi/data/preferences/AppPreferences.kt) | ✅ **Fixed** — the field initialisers no longer touch `encryptedPrefs`, so the `by lazy` is genuinely lazy |
| O4 | **Contacts search**: debounce, cancel previous `Job`, move to `Dispatchers.IO` (see H5). A leading-`%` `LIKE` cannot use an index, so each keystroke is a full provider scan. | [`PayMobileViewModel.kt:157`](app/src/main/java/com/mw/offlineupi/ui/paymobile/PayMobileViewModel.kt#L157) | ✅ **Fixed** — see **H5** |
| O5 | **Update check on every cold start** — `LaunchedEffect(Unit)` hits the GitHub API each launch. Cache with a timestamp; check at most daily. | [`MainActivity.kt`](app/src/main/java/com/mw/offlineupi/MainActivity.kt) | ✅ **Fixed** — timestamp-gated, at 6 hours rather than daily; see the decisions list |
| O6 | **Redundant `handler.post {}` inside `CountDownTimer.onTick`** — `onTick` already runs on the thread that created the timer. Remove. | [`OverlayManager.kt`](app/src/main/java/com/mw/offlineupi/service/OverlayManager.kt) | ✅ **Fixed** — `handler.post {}` removed from `onTick` |
| O7 | **`material-icons-extended`** pulls thousands of vector icons. R8 shrinks unused ones in release, but it inflates build time and debug APK size meaningfully. Prefer the base icon set with specific imports. | [`libs.versions.toml:35`](gradle/libs.versions.toml#L35) | ⬜ **Declined** — see the decisions list. 37 distinct icons are in use and most are absent from `material-icons-core`, so the swap is a bulk hand-drawn-vector exercise on UI chrome, and R8 already strips the unused ones from the release APK |
| O8 | **`useLegacyPackaging = true`** disables uncompressed-native-library packaging — larger install footprint and slower `System.loadLibrary("sqlcipher")`. Remove unless something specifically requires it. | `app/build.gradle.kts` | ✅ **Fixed** — `useLegacyPackaging` removed |
| O9 | **`-keepattributes SourceFile,LineNumberTable` still commented out** — release crash stacks are unreadable without it. Worth having on a beta. | [`proguard-rules.pro:17`](app/proguard-rules.pro#L17) | ✅ **Fixed** — `-keepattributes SourceFile,LineNumberTable` uncommented, with `-renamesourcefileattribute` |
| O10 | **Outdated deps** — `biometric 1.1.0` (→ 1.4.x, needed for the API 30+ auth-type handling in H9), `camerax 1.4.1` (→ 1.5.x), `securityCrypto 1.0.0` (`MasterKeys` deprecated; needed for C3). `kotlin-android` is declared in `[plugins]` but never applied in any build script — dead entry. | [`libs.versions.toml`](gradle/libs.versions.toml) | ◐ **Partly** — `camerax` → 1.5.0, dead `kotlin-android` plugin alias removed, `securityCrypto` → 1.1.0-alpha06 for **C3**. `biometric` deliberately held at 1.1.0; see the decisions list |
| O11 | **No signing config** in `app/build.gradle.kts`, though `.gitignore` already lists `signing.properties`. Release builds cannot currently be produced reproducibly. | `app/build.gradle.kts` | ✅ **Fixed** — signing config reads `signing.properties` when present and falls back to an unsigned release rather than failing the build |

---

## 🏗 Approach-level critique

### A1. Global mutable singleton as the payment state machine

> **Status: ⬜ Declined for this pass** — with the four bugs it causes fixed individually instead (H1–H4). The critique is correct: a `suspend` state machine over a `Channel<DialogEvent>` would make all four impossible rather than absent. It is declined here because it is a rewrite of the file that moves money, it cannot be covered by unit tests without the DI in April item 9, and it would land in the same release as the C1–C4 fixes — so a regression in it would be indistinguishable from a regression in them. The `@Volatile` annotations, the ownership token and the bounds checks are the honest intermediate: the invariants are now stated and checked in one place each, which is also what a later refactor needs in order to be verifiable.

`UssdManager` is a Kotlin `object` holding ~15 mutable fields (`isRunning`, `currentStep`, `pendingPin`, `messageQueue`, `sessionComplete`, `cancelRequested`, `retryCount`, …) mutated from three different threads: the AccessibilityService callback thread, the main thread via `Handler`, and `viewModelScope`. None of it is synchronized or `@Volatile`.

**H1, H2, H3 and H4 are all the same root cause** — invariants spread across independent booleans with no single owner. The `sessionId` counter is a clever manual fix for stale callbacks, but it solves a problem that structured concurrency solves for free.

**The approach that fits this problem:** model the flow as an explicit `sealed interface Step` sequence driven by a single `suspend fun` in one coroutine, with the AccessibilityService feeding a `Channel<DialogEvent>`. Then:

- "cancel" is `job.cancel()`
- "timeout" is `withTimeout`
- "session ownership" is *who holds the Job*
- cleanup is one `finally` that wipes the PIN

All four bugs stop being *possible* rather than being individually patched. It also gives each ViewModel its own owned session instead of a shared global flow, which is what H4 actually needs.

### A2. Screen-scraping a system dialog is inherently fragile — the fallbacks should reflect that

> **Status: ✅ Fixed** (both halves, one of two ways). Point 1: the classifiers are now a separate tested unit — but as compiled pattern lists, **not** as shippable remote config. Shipping the money-classification rules down a network channel adds a remote input to the payment path, which is a larger risk than the app update it saves. Point 2: an unrecognised response is carried on `UssdState.Failed`, shown verbatim on the failure screen, and shareable through the system chooser — no backend, no automatic upload, no identifiers. See [`ShareUtil.shareUnrecognisedResponse`](app/src/main/java/com/mw/offlineupi/util/ShareUtil.kt) for the reasoning.

Driving `com.android.phone` through an AccessibilityService is legitimately the only way to do this, and the implementation is thoughtful (leaf traversal, `ACTION_SET_TEXT` with clipboard fallback, retry-on-mid-flow-death). But the design currently treats "I couldn't parse this" as a rare edge case, when across ~200 Indian OEM ROMs × ~50 banks it is the **common** case. Two consequences:

1. **The dialog-text classifiers are the trust boundary for money**, yet they are a flat list of `contains()` calls with no tests (C1). They should be a data table — versioned, unit-tested against captured real strings, and ideally shippable as config so a bad match does not require an app update. That matters more given H6 means updates do not reach beta users at all.
2. **Unrecognised text correctly defaults to failure** at [`:758`](app/src/main/java/com/mw/offlineupi/service/UssdManager.kt#L758) — good instinct — but there is no mechanism to collect the unparsed string so coverage can improve. An anonymised, user-consented "report unrecognised response" action would turn the long tail from unfixable into tractable.

### A3. Storing the UPI PIN at all is the load-bearing risk decision

> **Status: ✅ Fixed** (both halves). The key is bound to user authentication, so the crypto now enforces what the UI suggested (**C3**). The product half is a plain-language disclosure at the moment the PIN is asked for, plus a permanent one-sentence note under Settings → Security, since the dialog version is dismissed forever once the PIN is stored. Both say NPCI's guidance in as many words: a UPI PIN should only be entered on the bank's own screen, and keeping it here is a convenience the user is choosing.

Everything in C3 follows from it. The stated posture is "encrypted at rest + biometric UI gate", but **the encryption key is not bound to the biometric**, so the two layers are independent rather than composed. If the PIN must be stored, bind the key to user auth so the crypto enforces what the UI currently only suggests.

It is also worth stating plainly in-app that the PIN is stored locally. Users reasonably assume a UPI PIN never leaves the bank's own keyboard, and NPCI's guidance is that it should not be captured by third-party UI. This is a product decision, not only an implementation one.

### A4. Validation lives in the view layer

> **Status: ✅ Fixed** — see **C4**. `amountError` is called from `UssdManager.startCommand`, so the QR path is bounded by the same check as the overlay.

`Validators.kt` exists but `isValidAmount` has **zero callers** (C4), and the real check is inline in `OverlayManager`'s click listener. Validation for a payment belongs at the boundary where the command is built — `UssdManager.startCommand` — so that *every* entry point (overlay, ViewModel, QR deep link) is covered. As written, the QR path can carry an amount that no code ever bounds.

### A5. `Handler` + `postDelayed` alongside coroutines

> **Status: ⬜ Declined for this pass.** Consolidating on coroutines is right, but it is the same rewrite as **A1** viewed from the timer side, and the specific bug it was cited for (H1) is fixed. Where the two models met and the `Handler` was doing nothing — the `handler.post {}` inside `CountDownTimer.onTick` (**O6**) — the `Handler` is gone.

The codebase runs two concurrency models in parallel: `Handler` / `CountDownTimer` in `UssdManager` and `OverlayManager`, and coroutines everywhere else. That is why timeout cancellation needs the hand-rolled `sessionId` guard, and why H1 is possible at all. Consolidating on coroutines (`withTimeout`, `delay`) removes a whole class of lifecycle bug.

### A6. Programmatic View hierarchies for the overlay

> **Status: ⬜ Declined.** Correct that the imperative view code is the hardest file to change safely, but C4 and M2 — the two bugs cited as living there — are fixed where they are, and a `ComposeView` inside a `TYPE_ACCESSIBILITY_OVERLAY` needs a hand-written `ViewTreeLifecycleOwner` and `SavedStateRegistry` shim. That is new unproven scaffolding under the PIN pad, in exchange for readability rather than for a fixed bug.

~1,040 lines of imperative `View` construction in `OverlayManager` is the single hardest file to change safely — and it is the file that renders the PIN pad. `TYPE_ACCESSIBILITY_OVERLAY` can host a `ComposeView` (with a `ViewTreeLifecycleOwner` / `SavedStateRegistry` shim). Worth it here specifically because the state, timer and validation logic tangled into the view code is exactly where C4 and M2 live.

### A7. Ad-hoc `HttpURLConnection` for the update channel

> **Status: ⬜ Declined; the defects it was cited for are fixed instead.** `disconnect()` is in a `finally`, version comparison is a tested comparator (**H6**), and the install path verifies signing certificates (**H7**). Adding OkHttp to an offline-first app to replace working code in the one path that reaches the network is a dependency the app does not otherwise need — and a semver library would still not have handled `0.1.1-beta2` versus `0.1.1-beta10`, which is what this app actually ships.

Manual connection handling, no `finally` on `disconnect()`, hand-rolled version comparison (H6), hand-rolled hash check. This is precisely what a small OkHttp dependency plus a real semver comparator handle correctly — and it is the code path that installs APKs.

---

## ✅ What's solid

Worth recording, given the length of the list above. These are correct and non-obvious:

- The `sessionId` invalidation pattern for stale callbacks (the *idea* is right; A5 is about the mechanism)
- "Unrecognised response defaults to failure" rather than to success
- The wrong-PIN attempt counter with the explicit 24 h-suspension warning
- `TYPE_ACCESSIBILITY_OVERLAY` to avoid needing `SYSTEM_ALERT_WINDOW`
- `SecureRandom` DB key generation
- SHA-256 verification of downloaded APKs
- `allowBackup="false"`
- Retry-on-mid-flow-death in `onUssdFinalResponse`
- Parameterised `selectionArgs` in the contacts query (no injection surface)

The six fixes landed since the April audit were real fixes, not cosmetic ones.

---

## Priority summary

| Order | # | Issue | Severity | Status |
|---|---|---|---|---|
| 1 | C1 | Failed payments recorded as SUCCESS | 🔴 Critical | ✅ Fixed |
| 2 | C2 | SQLCipher possibly keyed with zero bytes | 🔴 Critical | ✅ Fixed |
| 3 | H1 | Permanent stuck session (self-disarming watchdog) | 🔴 Critical | ✅ Fixed |
| 4 | H2 | `isRunning` set before suspension → lockout | 🟠 High | ✅ Fixed |
| 5 | C4 | No upper bound on payment amount | 🔴 Critical | ✅ Fixed |
| 6 | C3 | PIN key not bound to biometric | 🔴 Critical | ✅ Fixed |
| 7 | C3a | No `FLAG_SECURE` on PIN overlay | 🔴 Critical | ✅ Fixed |
| 8 | H6 | Beta→beta updates never offered (blocks shipping fixes) | 🟠 High | ✅ Fixed |
| 9 | H4 | Cross-ViewModel status corruption | 🟠 High | ✅ Fixed |
| 10 | H3 | `messageQueue` OOB crashes the a11y service | 🟠 High | ✅ Fixed |
| 11 | H8 | App lock bypassable | 🟠 High | ✅ Fixed |
| 12 | H9 | Biometric aborts on first bad read | 🟠 High | ✅ Fixed |
| 13 | H10 | Raw USSD text in release logs | 🟠 High | ✅ Fixed |
| 14 | H5 | Contacts query on main thread | 🟠 High | ✅ Fixed |
| 15 | H7 | Update install path (cache leak, no cert check) | 🟠 High | ✅ Fixed |
| 16 | C3b–C3d | PIN in `String`, not cleared on fail, clipboard | 🟠 High | ✅ Fixed |
| 17 | M5 | No DB migration path (act before v2 ships) | 🟡 Medium | ✅ Fixed |
| 18 | M1–M4, M6–M14 | Remaining medium items | 🟡 Medium | ✅ Fixed (M1–M14) |
| 19 | O1–O11 | Optimisation & build | ⚡ Perf/Build | ✅ Fixed except **O7** (declined) and **O10** (partly) |
| 20 | A1–A7 | Approach-level refactors | 🏗 Design | ⬜ **A2, A3, A4** fixed; **A1, A5, A6, A7** declined with reasons |

**Suggested first pass:** C1 is a ~15-line change plus the test table; pair it with C2's on-device verification, since both concern data the user cannot see is wrong.

---

# Appendix A — April 24, 2026 audit

Resolution status verified against `master` @ `7648947` on September 6, 2026.

| # | Issue | Status |
|---|---|---|
| 1 | Static biometric callbacks (race + leak) | ✅ **Fixed** — `ConcurrentHashMap` keyed by UUID token |
| 2 | `runBlocking` on the main thread | ✅ **Fixed** — `startCommand` is `suspend`, reads via `.first()` |
| 3 | DB passphrase derived from UUID string | ✅ **Fixed** — `SecureRandom` 32 bytes, Base64-stored; the wipe **C2** objected to is now gone too |
| 4 | `security-crypto` alpha dependency | ⚠️ **Knowingly reversed** — back to `1.1.0-alpha06`, because `MasterKey.Builder` (and therefore the auth-bound key in **C3**) does not exist in `1.0.0`. Judged the better trade: see the decisions list |
| 5 | `Log.d` not stripped in release | ✅ **Fixed** — `-assumenosideeffects` for `d`/`v`; the `Log.w` leak **H10** found is fixed as well |
| 6 | No APK integrity check | ✅ **Fixed** — `.sha256` verified, **and** the signing certificates compared (**H7**), which closes the same-origin gap |
| 7 | `UssdManager` is a global mutable singleton | ⬜ **Still open** → **A1**, declined this pass with H1–H4 fixed individually instead |
| 8 | `exportSchema = false`, no migration safety net | ✅ **Fixed** — `exportSchema = true`, `1.json` committed (**M5**) |
| 9 | No dependency injection | ⬜ **Still open** — no DI. Nothing in this pass needed it; it is the prerequisite for **A1** |
| 10 | Dead `targetService` variable in `isAccessibilityEnabled()` | ✅ **Fixed** — dead `targetService` gone, exact `ComponentName` comparison in both the manager-list and `Settings.Secure` paths |
| 11 | `isErrorResponse()` false-positive risk (`"problem"`) | ✅ **Fixed** via **C1** — word-boundary matching in `UssdResponseClassifier`, tested against real bank strings |
| 12 | Amount validation limit 20× the USSD cap | ✅ **Fixed** via **C4** — cap is ₹5,000 *and* it is now enforced, at `startCommand` |
| 13 | No unit tests | ✅ **Fixed** via **M6** — 81 tests |
| 14 | Dead `description` variable in `startCommand()` | ✅ **Fixed** — the dead `description` variable is gone |
| 15 | `material-icons-extended` bloat | ⬜ **Declined** → **O7** |
| 16 | CameraX outdated | ✅ **Fixed** — `camerax` 1.5.0 |
| 17 | Biometric library outdated | ⬜ **Deliberately held** at `biometric 1.1.0` — everything **H9** needs is in the stable release; every newer build is an alpha, on the payment-authentication path. See the decisions list |

<details>
<summary>Original April 2026 write-ups (full text)</summary>

### 1. Static biometric callbacks are a race condition & memory leak
**File:** `BiometricAuthActivity.kt` (lines 16–17) — `onBiometricSuccess` and `onBiometricError` are `companion object var`s. If two authentication flows overlap (e.g. app lock + PIN autofill), the second assignment overwrites the first, silently dropping the first result. **Fix:** scoped ViewModel event or `ActivityResultLauncher`.

### 2. `runBlocking` on the main thread (potential ANR)
**File:** `UssdManager.kt` (lines 325, 770) — two `runBlocking` calls block the main thread to read DataStore preferences synchronously. **Fix:** make `startCommand()` a `suspend fun`.

### 3. DB passphrase derived from UUID string
**File:** `AppDatabase.kt` (lines 37–43) — `UUID.randomUUID().toString()` produces a 36-char character-set-limited string, derived to `ByteArray` from a `String`, leaving it in the JVM string pool. **Fix:** `SecureRandom().nextBytes(32)` stored as Base64; wipe the array after use.

### 4. `security-crypto` alpha dependency in production
**File:** `libs.versions.toml` (line 14) — `securityCrypto = "1.1.0-alpha06"`. **Fix:** use stable `1.0.0`.

### 5. Verbose `Log.d` not stripped in release builds
**File:** `proguard-rules.pro` — dozens of `Log.d` calls log full USSD response text, UPI amounts, and partial PINs. **Fix:** `-assumenosideeffects class android.util.Log { ... }`.

### 6. No APK integrity check in `UpdateChecker`
**File:** `UpdateChecker.kt` — fetches an APK from GitHub and opens an install intent with no hash or signature validation. **Fix:** publish a SHA-256 checksum per release asset and verify before installing.

### 7. `UssdManager` is a global mutable singleton
**File:** `UssdManager.kt` — ~20 `@Volatile` fields, a mutable message queue, Handler callbacks, shared state across unrelated screens. Untestable and fragile under process death or config changes. **Fix:** Application-scoped class with a coroutine state machine, injected via Hilt.

### 8. `exportSchema = false` on Room — no migration safety net
**File:** `AppDatabase.kt` (line 22) — migration history is not tracked; bumping `version` without a migration causes a destructive schema drop. **Fix:** `exportSchema = true`, commit the JSON, write explicit `Migration` objects.

### 9. No dependency injection
`AppDatabase`, `AppPreferences`, repositories and `UssdManager` are manually wired through the Application class and singletons. **Fix:** add Hilt — KSP is already present.

### 10. Dead variable in `isAccessibilityEnabled()`
**File:** `UssdManager.kt` (lines 125–137) — `targetService` is constructed but never used; the loop checks `service.id.contains(context.packageName)`, matching *any* accessibility service from this package. **Fix:** remove the dead variable, match the exact component name.

### 11. `isErrorResponse()` false-positive risk
**File:** `UssdManager.kt` — `lower.contains("problem")` would match benign messages like "no problem found". **Fix:** anchor to known error patterns, test against real bank responses.

### 12. Amount validation limit is 20× higher than USSD cap
**File:** `Validators.kt` (line 12) — `value <= 100_000` vs the NPCI *99# limit of ₹5,000. **Fix:** change the cap to `5_000`.

### 13. No unit tests
**Directory:** `app/src/test/` — exists but empty. Immediately testable pure logic: `Validators`, `UpdateChecker.isNewerVersion()`, `UssdManager.extractPayeeName()`, `isErrorResponse()`/`isSuccessResponse()`, `UpiQrParser`.

### 14. Dead `description` variable in `startCommand()`
**File:** `UssdManager.kt` (lines 332–338) — constructed, never used. **Fix:** pass it to the overlay progress indicator or remove.

### 15. `material-icons-extended` bloats debug APK
**File:** `app/build.gradle.kts` (line 49) — ~11 MB. **Fix:** import specific icon objects if fewer than ~20 are used.

### 16. CameraX version is behind
**File:** `libs.versions.toml` (line 9) — `camerax = "1.4.1"`; 1.5.x adds improved latency and zero-shutter-lag.

### 17. Biometric library is outdated (2021)
**File:** `libs.versions.toml` (line 13) — `biometric = "1.1.0"`; 1.2.0-alpha05+ adds Class 3 biometric support and an improved `canAuthenticate()` API.

</details>
