# Lessons — SOMA Shades 3 driver

## Hubitat: preference `defaultValue` is not applied until Save Preferences is clicked

**Symptom:** Driver declares `input name: "logEnable", defaultValue: true` and UI shows the toggle as ON, but code reading `logEnable` gets `null`. Any `if (logEnable) …` gate is therefore skipped → no logs, no events, looks like the driver is dead.

**Root cause:** Hubitat only writes preference values to the settings store the first time the user clicks **Save Preferences**. The `defaultValue` attribute is a UI hint for what the checkbox *shows*, not a guarantee of what the stored value is.

**First debug step** when a driver seems inert on a fresh install: ask the user to hit **Save Preferences** once. Don't start patching gating logic until you've confirmed that isn't the cause.

**If writing defensively from the start:** coerce nulls to the declared default in a helper, e.g. `isDebug() { logEnable == null ? true : (logEnable == true) }`.

## Hubitat sandbox: no `private static final` at script top level

**Symptom:** Driver fails to save with `Modifier 'private' not allowed here` / `Modifier 'static' not allowed here` on a script-level constant declaration.

**Root cause:** Hubitat runs drivers as a top-level Groovy script, not a class. `private` and `static` are class-member modifiers and are rejected on script-level declarations by the sandbox compiler.

**Fix:** Use `@Field static final Type NAME = value` with `import groovy.transform.Field` at the top of the file. `@Field` hoists the variable into the generated script class so `static final` is legal. This is the standard idiom in every Hubitat driver for compile-time constants.
