---
name: verify-and-ship
description: Build, test, install and launch the app on the connected phone, check for crashes, then commit in separate feature / test / docs commits and update docs/STATUS.md. Use at the end of every coding task in this project, or when asked to run the app, "ลองรันบนเครื่อง", commit, or ship.
---

# Verify and ship (this project's definition of done)

Run these in order. **Stop at the first failure** and fix it - never commit past a red step.

## 1. Build and test

```bash
./gradlew.bat assembleDebug -q 2>&1 | grep -E "^e:" ; echo "exit ${PIPESTATUS[0]}"
./gradlew.bat testDebugUnitTest -q 2>&1 | grep -E "^e:|FAILED" ; echo "exit ${PIPESTATUS[0]}"
```

`JAVA_HOME` comes from `.claude/settings.json`. If Gradle still picks the VS Code JRE
(`jlink` missing), export `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` first.

Count what ran - a suite that silently did not compile is not "green":

```bash
grep -ho 'tests="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml | awk -F'"' '{s+=$2} END {print s}'
```

## 2. Install, launch, check for crashes (when a phone is connected)

```bash
ADB="/c/Users/Acer/AppData/Local/Android/Sdk/platform-tools/adb.exe"
"$ADB" devices -l
./gradlew.bat installDebug -q
"$ADB" logcat -c
"$ADB" shell am start -n th.ac.kmutnb.prachin.map/.MainActivity
"$ADB" shell pidof th.ac.kmutnb.prachin.map        # a pid means it is still alive
"$ADB" logcat -d -b crash | tail -30                # must be empty
```

- Do **not** tap, swipe or wake the phone unless the user asks - it is their phone and
  they test by hand. A screenshot (`exec-out screencap -p`) is fine; a black one means the
  screen is off: say so, do not wake it.
- Room schema changed? Launching over the old install is the migration test - look for
  `IllegalStateException` / `Migration didn't properly handle` in logcat.

## 3. Commit in parts (CLAUDE.md "วิธีทำงานร่วมกัน")

- Separate commits: **feature**, then **test**, then **docs**. One feature per commit.
- Chain with `&&` / `[ "${PIPESTATUS[0]}" = 0 ] &&` so a failing step cannot commit.
- Never commit `.idea/`, `ForAiReadDontCommit/` (both gitignored) or `.claude/settings.local.json`.
- End every message with the `Co-Authored-By` line from the session's attribution reminder.
- Branch: work on the feature branch; never squash or rebase; merge to master with `--no-ff` only when asked.

## 4. Docs

- `docs/STATUS.md`: add the new commits to the current set, update "ยังไม่ได้ทำ / พัง".
  Keep it short - older sets live in `docs/HISTORY.md`.
- `CLAUDE.md` only when a rule or an important file changes.
- `docs/KHAOYAI_APP.md` §2.1 when a proposal item (§2.3.x) changes status.

## 5. Tell the user (in Thai)

What changed and why, what was verified (build / N tests / installed / crash log), what was
**not** verified (e.g. "ยังไม่ได้กดทดสอบทีละหน้า"), and anything cut or left half done.
