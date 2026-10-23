# Migrating JDA to Kotlin

Status: **Phase 1 complete. Phase 2 in progress — pilot (`SkuSnowflake`) and the full top level of `internal.utils` have landed and verified.**

Work is parked on branch `kotlin-migration` (fork `astatio/JDA`), reviewed via **draft PR #1**, whose base is the throwaway branch `kotlin-migration-base` (`399755a`, the last pre-migration upstream merge) chosen only to give the diff a meaningful base. Fork `master` already contains these commits; the PR is a review surface, not a merge candidate. Resume by reading `AGENTS.md`. All `internal.utils` subpackages (`requestbody`, `tuple`, `message`, `compress`, `concurrent`, `cache`, `config`, `localization`) are converted; only `tuple/package-info.java` remains Java, and the remaining `internal` work is the three hubs `JDAImpl`, `EntityBuilder`, and `GuildImpl`. On the `api` side the default-free snowflake leaves `StickerSnowflake` and `ForumTagSnowflake` are converted (batch 39); **an `api` interface with a `default` method is blocked** — Kotlin emits `DefaultImpls`/`access$…$jd` that ArchUnit rejects (§10) — and the `ISnowflake`/`UserSnowflake` hubs need that settled first.

This document describes an incremental, in-place migration of the JDA codebase from Java to Kotlin, while preserving the public API contract for Java consumers. It targets **JVM 25 bytecode** and the **latest stable Kotlin release**.

---

## 1. Scope of the change

Facts measured against `master` (JDA 6.7.0):

| Metric | Value |
|---|---|
| Main Java files / LOC | 1,218 files / ~208,000 LOC |
| — `net.dv8tion.jda.api` | 805 files |
| — `net.dv8tion.jda.internal` | 406 files |
| — `net.dv8tion.jda.annotations` | 6 files |
| Tests | 91 files / ~9,900 LOC (JUnit 5, Mockito, AssertJ, ArchUnit) |
| Examples / Java 8 compat suite | 5 files / 1 file |
| Public top-level types | ~1,118 (349 interfaces, 61 enums, 57 abstract classes) |
| `default` interface methods | ~1,468 |
| `static` interface methods | ~170 |
| Wildcard signatures (`? extends` / `? super`) | ~551 across 146 files |
| Generic (parameterized) types | ~420 |
| Files importing `javax.annotation` (JSR-305) | 959 |
| `@SafeVarargs` / files with varargs | 6 / 190 |
| Package-private top-level classes | 5 |

Relevant build facts:

- Gradle **9.7.1** (wrapper), builds on a **JDK 25** toolchain.
- Published bytecode target: **was** Java 8 at the time these metrics were taken (`libraryJavaVersion = 8`, `options.release = 8`, `verifyBytecodeVersion` asserting class-file major version **52**). Phase 0 has since raised this to JVM 25 — see §4.
- Publishing is Maven Central via `nmcp`, with `sources` + `javadoc` jars and four jar variants (`jar`, `shadowJar`, `noOpusJar`, `minimalJar`) plus artifact exclusion filters (opus/JNA/tink).
- Tooling is Java-only: Palantir formatter (Spotless), Error Prone (large disabled-check list), OpenRewrite recipes (`NeedBraces`, `NoFinalizedLocalVariables`, `JavadocFormatter`, `MigrateToJavaxAnnotations`) gated by `rewriteDryRun`.
- The REST model generator emits **Java** via Palantir JavaPoet into `net.dv8tion.jda.internal.generated.*Dto`, filtered by a JavaParser-based task.
- Kotlin is already declared in `gradle/libs.versions.toml` (`org.jetbrains.kotlin.jvm:2.4.20`) for `buildSrc`'s `kotlin-dsl`, but is not applied to the root project.
- `jda-ktx` is a separate downstream Kotlin library that consumes the published Java API.

---

## 2. Decisions and drivers

| Decision | Choice |
|---|---|
| Migration model | **Option C** — incremental, in-place, package by package, with Java interop |
| Bytecode target | **JVM 25** (class-file major version 69) |
| Kotlin version | **Latest stable** (2.4.x line; the version catalog already pins 2.4.20) |
| Java consumer compatibility | Preserve public **source and binary** shape for consumers running on Java 25 |
| Min runtime for consumers | **Java 25** (raised from Java 8 — a breaking change) |
| Generated DTOs | Stay Java for now (see §7) |

### 2.1 Why incremental

JDA is a library with a deliberately stable, heavily annotated public API and a large documented surface. A big-bang rewrite would put the whole API at risk in one step and make review and bisecting impossible. Converting package by package, gated by an automated ABI diff, keeps the library shippable across the whole migration.

### 2.2 Why JVM 25 changes the calculus

Raising the target to JVM 25 is itself a **breaking change**: consumers on Java 8–24 can no longer load the artifact. That change is independent of the language migration and should be shipped and announced on its own line before Kotlin conversion begins, so that any fallout is attributable to one change rather than two.

Benefits of the JVM 25 target for this migration:

- Kotlin's Java 8 support window no longer constrains the Kotlin version. We can track the latest stable Kotlin release without worrying about the compiler dropping `jvmTarget = 1.8`.
- The Java 8 compatibility source set and its hidden worst-case constraints disappear (see §6).
- Modern library APIs (`java.time`, records interop, pattern matching in any residual Java) are available to both languages.

Costs:

- Kotlin ≥ **2.3.0** is required to emit Java 25 bytecode; we will use the latest stable (2.4.x).
- Consumers must be on Java 25; this belongs in the release notes as a first-class breaking change.
- Gradle/Kotlin compatibility must be verified: the wrapper is on Gradle 9.7.1, and each Kotlin release declares a supported Gradle range. Confirm the chosen Kotlin patch supports Gradle 9.7.x, or pin the wrapper to a supported Gradle version, before starting.

---

## 3. Non-negotiable guardrails

Freeze these before Phase 1 and encode them in CI. Update `.github/CONTRIBUTING.md` and add an `AGENTS.md` capture of the build rules.

1. **Bytecode is JVM 25.** `verifyBytecodeVersion` expects major version **69**. Every compiled class, Kotlin and Java, must match. The task reads both `compileJava` and `compileKotlin` output; keep it that way, or converted classes stop being checked.
2. **Java consumers keep compiling and running on Java 25.** This requires:
   - `-jvm-default=enable` (the Kotlin 2.2+ name; formerly `-Xjvm-default=all-compatibility`) so interface `default` methods remain `default` in bytecode and Java implementors are unaffected.
   - `@JvmStatic` on every converted `companion object` member that was a `static` interface method (~170 sites).
   - `@JvmName` where Kotlin would otherwise mangle a name.
   - `@JvmOverloads` where Java had explicit overloads.
3. **Nullability stays expressed as JSR-305 annotations** (`@Nonnull`, `@Nullable`) on the public API. Do not rely on Kotlin's own nullability for the interop boundary; Kotlin's inserted `Intrinsics` checks would change runtime behavior for Java callers and break the ArchUnit contract.
4. **`@UnknownNullability` and `@Contract` must survive** as annotations because ArchUnit rules reference them explicitly.
5. **`sources` and `javadoc` jars keep being produced.** Dokka must reach parity before Javadoc is dropped.
6. **One logical change per PR**, per the existing contribution policy; here that means one package per PR.

---

## 4. Phased plan

### Phase 0 — Raise the bytecode target to JVM 25 (separate, shippable change)

**Done.** Executed in Java only, with no Kotlin applied:

- `libraryJavaVersion` / `exampleJavaVersion` collapsed into a single `javaVersion = JavaLanguageVersion.of(25)`.
- `options.release = 25` on `compileJava` (the `compileTestJava8Java` task no longer exists); the Javadoc `-release` option now uses `javaVersion`.
- `verifyBytecodeVersion` `expectedMajorVersion` changed from `52` to `69`.
- Java 8 compatibility machinery retired:
  - Removed the `testJava8` source set, `testJava8Implementation`/`testJava8RuntimeOnly` configurations, `java8Toolchain`, the `testJava8Compatibility` task, and its `check` dependency.
  - Deleted `src/test-java8` (including `MinimalJDABotTest`, whose `testCurrentJavaVersion` asserted a `1.8` runtime).
  - Removed the `junit-java8` and `junit-launcher-java8` catalog pins and the `junit-java8` bundle.
  - Removed the `-Xlint:-options` suppression, which existed only for `--release 8` notes.
- Updated `README.md`: minimum Java is now **Java 25**.
- Updated `AGENTS.md` bytecode, lint, command, and test rules to match.

CI workflows already ran JDK 25 exclusively, and `jitpack.yml` already selects `25-tem`, so no workflow changes were required.

Remaining verification for this phase: run `./gradlew build` on a JDK 25 host to confirm the full suite, `checkFormat`, and `verifyBytecodeVersion` pass with the new target. The build could not be executed in the environment where this change was authored.

**Verification now complete.** Built on Temurin 25.0.4 via SDKMAN. The first JVM 25 build failed under `-Werror` because raising the target made three Error Prone style checks newly applicable (`StatementSwitchToExpressionSwitch` ×76, `StringConcatToTextBlock` ×3, `UnnamedVariable` ×1) alongside a javac `removal` warning; these were suppressed rather than mechanically rewritten, to keep the bytecode change reviewable. `./gradlew check` then passed with **501 tests / 0 failures** and all **1,915 classes at major version 69**.

### Phase 1 — Kotlin toolchain skeleton (no files converted)

**Done.** Implemented as described below, with three deltas from the original sketch:

- The compiler flag was renamed. Kotlin 2.2 rejects `-Xjvm-default=all-compatibility` as a deprecated argument; the equivalent is now **`-jvm-default=enable`**. Confirmed against the compiler itself (`-X` help), which documents the mapping: `all-compatibility` → `enable`, `all` → `no-compatibility`, `disable` → `disable`. The semantics are unchanged, so converted interfaces still emit real `default` methods with `DefaultImpls` retained. Note there is no public typed Gradle DSL property for this in Kotlin 2.4.20 (`JvmDefaultMode` is under `internal.config`), so it remains a free compiler arg.
- `jvmToolchain(25)` is set on the `kotlin` block in addition to `jvmTarget`. Without a matching toolchain declaration, Gradle fails the build with "Inconsistent JVM Target Compatibility Between Java and Kotlin Tasks" — which is a useful second line of defense if the target is ever edited in one place only.
- `kotlin.stdlib.default.dependency=false` in `gradle.properties`. Applying the plugin would otherwise add `kotlin-stdlib` as an `implementation` dependency, changing the published POM for every downstream consumer. That is a public-ABI change, and none is warranted yet since no Kotlin type is public. Re-enable when the first Kotlin type joins the public API.

Additionally, beyond the original sketch:

- `verifyBytecodeVersion` now also reads `compileKotlin` output. It previously covered only `compileJava`, so the first converted class would have silently escaped the major-version gate. Verified by confirming the task actually enumerates Kotlin classes (not merely that it exits zero).
- `spotlessKotlin` uses `ktlint 1.6.0` and the shared `gradle/copyright-header.txt`. The header enforcement was confirmed to fail on a Kotlin file lacking it.

Apply the Kotlin plugin in `build.gradle.kts`: `alias(libs.plugins.kotlin)`.
- Add `src/main/kotlin`; the Kotlin plugin wires it into `sourceSets.main` automatically, compiling alongside `src/main/java`. (The directory is created by the first converted file; the wiring is already in place and was exercised via `src/test/kotlin`.)
- Configure the compiler:

  ```kotlin
  kotlin {
      jvmToolchain(25)

      compilerOptions {
          jvmTarget.set(JvmTarget.JVM_25)
          freeCompilerArgs.add("-jvm-default=enable") // renamed from -Xjvm-default=all-compatibility
          allWarningsAsErrors.set(true)
      }
  }
  ```

- Ensure mixed-source compilation ordering is correct (Kotlin compiles against the Java sources and the Java task sees Kotlin output) so that a Java class can reference a Kotlin class and vice versa within `main`.
- Extend Spotless with a Kotlin target (`ktlint` or `ktfmt`) that uses the existing `gradle/copyright-header.txt` license header and preserves the `GIT_ATTRIBUTES_FAST_ALLSAME` line-ending behavior. The `.editorconfig` already carries a full `[{*.gradle.kts,*.kts,*.kt}]` section to align with.
- Add `detekt` for the class of checks Error Prone provided, and add Dokka in parallel with `javadoc` rather than replacing it yet.
- Add `binary-compatibility-validator` (or `japicmp`/`revapi`) and check in a baseline from the last Phase-0 release.
- Add the migration gates to CI (§9).

Deliverable: Kotlin enabled, zero conversions, all gates green.

**Interop gate (permanent).** Rather than only asserting "Kotlin compiles", `src/test/kotlin` and `src/test/java` share one package with a Kotlin declaration that a Java test consumes. This keeps the three mechanics Phase 2 depends on load-bearing, so a regression fails a test rather than surfacing later in a converted file:

- `@JvmStatic` companion members must remain callable as `KotlinJavaInteropProbe.say(...)` from Java.
- A Kotlin interface body method must remain a real `default` method, so a Java lambda can implement the interface without overriding it.
- Kotlin must be able to call Java statics and honor their contracts (e.g. `Checks.notNull` throwing `IllegalArgumentException`).

Bytecode inspection confirms all three: the companion emits a genuine `public static String say(String)`, the interface method is `public default`, and `DefaultImpls` is retained. `./gradlew check` passes with **505 tests / 0 failures** (501 existing + 4 interop).

**Documentation.** Dokka is applied alongside javadoc, not instead of it; `javadocJar` still produces its jar, so nothing changes for consumers yet. Dokka is pinned to `2.1.0` because `2.2.0` and `2.3.0-Beta` both fail on JDK 25 (`Registry key javac.fresh.variables.for.captured.wildcards.only is not defined`, and `Missing extension point: com.intellij.java.expressionTypeNullabilityPatcher` respectively). `2.1.0` builds 20,599 HTML pages across the mixed source set. Note it exposes the V2 tasks (`dokkaGenerateHtml`/`dokkaGenerate`); the V1 `dokkaHtml` task errors out.

**Static analysis.** detekt `2.0.0-alpha.6` (stable `io.gitlab.arturbosch` line stops at `1.23.8`, an older major) covers Kotlin where Error Prone covers Java, and runs from `check`. `gradle/detekt.yml` is small and follows the same policy as the javac `-Xlint` suppressions: rules are disabled only with a stated reason (naming, since the public API keeps Java-style names; complexity thresholds, since converted types preserve their shape; comment rules, since the sources carry Javadoc). Verified that detekt fails the build on an introduced smell rather than silently passing.

**ABI gate.** `binary-compatibility-validator` cannot be used: version `0.18.2` (current stable) fails immediately with `Unsupported class file major version 69`, because its bundled ASM cannot read JVM 25 bytecode. Rather than leave the "keep the public API compatible" requirement unmet, the gate is implemented with the JDK's own `javap`, which always understands the classes the JDK produced:

- `buildSrc/.../PublicApi.kt` extracts the public surface of `net.dv8tion.jda.api.**` from bytecode and normalizes it. `Compiled from "X.java"` is dropped so converting a class to Kotlin does not read as an API change.
- `apiDump` writes the baseline to `api/JDA.api` (currently **900 classes, ~11.8k signature lines**). Review the diff before committing; a change there is a change to what Java consumers compile against.
- `apiCheck` runs from `check` and fails on a removed class or member. Additions pass, since Kotlin emits synthetic and `DefaultImpls`/`Companion` members that are not Java-visible API.

Both directions were verified rather than assumed: `apiCheck` passes against the untampered baseline, and fails with a precise message when a baseline entry has no counterpart (`member removed or changed in net.dv8tion.jda.api.entities.Message: ...`).

**Correction, found during Phase 2.** As first written, the gate was **silently blind to Kotlin output**, and Phase 1's "verified" claim above was wrong — the negative test had only ever exercised a Java class. Two independent defects:

1. `PublicApi.classNames` skipped any input that was not a directory (`if (!root.isDirectory) continue`). A `classes.from(compileKotlin.outputs.files)` FileTree resolves to loose `.class` **files**, not a directory, so every Kotlin class was dropped from the comparison.
2. Even with enumeration fixed, `javap` resolves a binary name only against **directory or jar** classpath entries. Passing loose `.class` files as the `-classpath` produced `class removed: ...SkuSnowflake`, because javap could not resolve it. The Java output was unaffected only because it happened to be handed over as a directory.

The fix is to feed both tasks the source set's **directory** outputs (`sourceSets.main.output.classesDirs`, i.e. `build/classes/java/main` + `build/classes/kotlin/main`) rather than a filtered file tree, and to teach `classNames` to accept loose class files as well as directories. `kotlinClasses` (the loose-file tree) is retained only because `verifyBytecodeVersion` legitimately wants per-file inputs and iterates files directly.

This is the failure mode to watch for on every future gate: a check that passes because it silently examined nothing. Negative tests must target a **Kotlin** class now that Kotlin output exists. Re-verified afterwards by injecting a bogus member into the baseline for the converted class and confirming a precise failure, and by confirming `apiCheck` then passes once restored.

Caveats worth knowing: `javap -public` reports `public`/`protected` members, so package-private and `internal` changes are out of scope; and generated/rewritten signatures are compared as-is, so a genuinely intentional API break needs `apiDump` plus an intentional, reviewed baseline diff.

**Phase 1 complete.** Kotlin enabled, zero production files converted, every gate runs from `./gradlew check`, and 505 tests pass / 0 failures.

### Phase 2 — Pilot conversion (`SkuSnowflake`)

The first production file, `net.dv8tion.jda.api.entities.SkuSnowflake`, was converted as a deliberate pilot to force every Phase-2 mechanic to prove itself before any further files move. It is an interface with a companion holding two `@JvmStatic` factories — the minimal shape that still exercises static method emission, nullability annotations, and `Companion` synthesis.

What the pilot established, each verified against real bytecode rather than assumed:

- **`@JvmStatic` factories keep static ABI.** Both `static SkuSnowflake fromId(long)` and `static SkuSnowflake fromId(String)` are present in the compiled interface, matching the baseline. A `public static final Companion` field and a `SkuSnowflake$Companion` class are added; both are permitted additions under the gate's additions-pass policy.
- **Nullability must be written explicitly.** A bare non-null Kotlin parameter emits only `org.jetbrains.annotations.NotNull`, which the compliance rules reject: they require `javax.annotation.*` on public parameters. Writing `@Nonnull` explicitly preserves the `RuntimeVisibleAnnotations` entry. This is the single most important mechanical rule for converted files, and is why the annotation is written out in `SkuSnowflake.kt` despite looking redundant.
- **`check` is green** with the converted class: 505 tests / 0 failures, all 8 ArchUnit compliance rules, `apiCheck`, `apiDump` parity, `detekt`, `spotlessKotlinCheck`, and `verifyBytecodeVersion`. The converted class is class-file major version **69**.
- **Both remaining gates were shown to actually see Kotlin output**, by negative test: removing `@Nonnull` from the converted method fails `testMethodsThatAcceptObjectShouldHaveNullabilityAnnotations()`, and injecting a bogus member into the baseline for the converted class fails `apiCheck` with `member removed or changed in ...SkuSnowflake`. Without that check, "check passes" would have been equally consistent with the gates examining nothing.
- **Packaging is unaffected.** The converted class appears in the main jar as `SkuSnowflake.class` + `SkuSnowflake$Companion.class`, `SkuSnowflake.kt` appears in the sources jar, and the javadoc jar still renders the class with its factory docs.

**New runtime dependency: `kotlin-stdlib`.** The pilot's most consequential discovery. Kotlin emits `kotlin/jvm/internal/Intrinsics.checkNotNullParameter` for parameter null checks, so the converted companion references stdlib from real code, not just metadata. Inspection confirmed the reference:

```
invokestatic  // Method kotlin/jvm/internal/Intrinsics.checkNotNullParameter:(Ljava/lang/Object;Ljava/lang/String;)V
```

`kotlin-stdlib` was *already* on the runtime classpath, but only **transitively through okhttp** — an accident of an unrelated dependency that could disappear on any okhttp upgrade, taking JDA's runtime with it. It is now declared explicitly as `api(libs.kotlin.stdlib)`. The `kotlin.stdlib.default.dependency=false` flag in `gradle.properties` remains, because it suppresses the plugin's *implicit* `implementation` edge; the explicit declaration is the reviewed, published replacement. This is the first published-POM change of the migration and belongs in the release notes.

Enum conversions of the **public `api` enums** remain blocked. Converting an enum leaks a public, non-synthetic `kotlin.enums.EnumEntries getEntries()`; six enums exist in `api` and none should be attempted until that is resolved (§10). **Correction, found during Phase 2:** the compliance gates (`ArchUnitComplianceTest`, the ABI baseline, `EnumComplianceTest`) are scoped to `net.dv8tion.jda.api.**`, so `internal` enums are outside them and convert cleanly. `ConfigFlag`, `ShardingConfigFlag`, `AudioEncryption`, `ConnectionStage`, `VoiceCode` (with its nested `Close`), and the nested `EventCache.Type`/`GuildSetupController.Status`/`GuildSetupNode.Type` are now Kotlin; the leaked `getEntries()` is real but only lands on `internal` types that nothing checks.

### Phase 2 — First leaf package (`internal.utils`)

With the pilot's mechanics proven, the first real batch was the 13 self-contained leaves of `net.dv8tion.jda.internal.utils`, walked leaves-inward so nothing depends on an unconverted sibling: `CacheConsumer`, `UnlockHook`, `ShutdownReason`, `ClockProvider`, `UnionUtil`, `FutureUtil`, `ResizingByteBuffer`, `EncodingUtil`, `EntityString`, `ClassWalker`, `ChainedClosableIterator`, `FallbackLogger`, `ContextRunnable`. Cases with in-package dependencies (`Checks`, `JDALogger`, `Helpers`, `IOUtil`, `PermissionUtil`, `SerializationUtil`, `ChannelUtil`) were deferred until those dependencies converted.

This package sits **outside** both gates: the ABI baseline (`api/JDA.api`) and the ArchUnit compliance rules only cover `net.dv8tion.jda.api.*`. That makes it the right place to establish conventions, but it also means the compiler and the test suite are the entire safety net — `apiCheck` passing here proves nothing about these files. Verification was therefore a full `test --rerun-tasks` (an ordinary `check` left `:test` UP-TO-DATE and would have tested stale classes), giving 505 tests / 0 failures, including `EntityStringTest`, which exercises the converted `EntityString` directly.

Conventions this batch fixed, each forced by a real failure:

- **`@JvmField` for static fields, `@JvmStatic` for static methods.** These are different mechanisms. `ShutdownReason.USER_SHUTDOWN` and friends are fields and need `@JvmField`; `ClockProvider.getClock`, `UnionUtil.safeUnionCast`, `FutureUtil.thenApplyCancellable`, the `EncodingUtil` helpers, and `ClassWalker.walk`/`range` are methods and need `@JvmStatic`. Using the wrong one either hides the member from Java or synthesizes into `Companion` and breaks every Java caller.
- **Objects replace static-only classes.** `ClockProvider`, `UnionUtil`, `FutureUtil`, and `EncodingUtil` were all static-only, and all subclassed from nowhere, so they became `object` declarations. Confirmed first with a subclass/implementer sweep across `src/` — zero hits for every class in the batch.
- **`open` only where inheritance exists.** `ShutdownReason` is kept `open` because its constructor is `public` and its field was `protected` in Java (widened to `val`, which is a superset); no other class needed `open`.
- **`java.util.Iterator.remove` must be overridden.** `ChainedClosableIterator` implements `ClosableIterator`, which extends `Iterator`. Java inherits the default `remove()`; Kotlin's `Iterator` does not, so the compiler demanded a declaration. It throws `UnsupportedOperationException`, matching Java's default.
- **`finalize()` is not an override in Kotlin.** `ChainedClosableIterator.finalize()` is declared as a plain `protected fun` carrying the same `@Deprecated` message. It is never invoked in tests, and `Object.finalize` is still present in JDK 25 (`javap` confirms), so the declaration remains valid; its removal is a separate decision from this migration.
- **Explicit typed constructor overloads, not `@JvmOverloads`.** `ContextRunnable` and `EntityString` keep their exact Java constructor and method sets as secondary constructors / overloaded `setType`, because overload resolution and the resulting ABI must match the original.
- **`@Nonnull` at the boundary.** Written explicitly even on internal code, so the annotation survives as `RuntimeVisibleAnnotations` and the files stay consistent with the pilot rule.

detekt is a source-level gate here (unlike the ABI gate, which is blind to `internal`), and it surfaced four findings, handled on merit rather than blanket-suppressed: the two magic numbers (`1.25` buffer growth, radix `16`) became named constants; `ReturnCount` on `hasNext` and `IteratorNotThrowingNoSuchElementException` on `ClassWalker.next` are suppressed with a written reason, because both flag control flow faithfully preserved from the Java original (`ClassWalker.next` does throw — `removeFirst()` on an empty deque raises `NoSuchElementException`; detekt cannot see through the deque).

### Phase 2 — Remaining `internal.utils` classes

The seven classes deferred above (`ChannelUtil`, `Checks`, `Helpers`, `IOUtil`, `JDALogger`, `PermissionUtil`, `SerializationUtil`) are now converted, completing the package's top level. Their Java counterparts were deleted in the same change.

The mechanics matched the leaf batch, with these specifics:

- **Statics.** `@JvmStatic` on every converted static method. `Checks`' `ALPHANUMERIC*` patterns were `public static final` fields in Java and stay Java-visible via `@JvmField val`.
- **Nullability.** `@Nonnull`/`@Nullable` written explicitly at the boundary, per the pilot rule. `Checks.notNull(argument: Any?, name: String?)` and the other guarded overloads keep their exact parameter shapes so Java overload resolution is unchanged.
- **Overload ambiguity.** `PermissionUtil`'s several `canInteract` / `getEffectivePermission` / `getExplicitPermission` overloads were preserving the Java distinction between `Member`/`User` and `GuildChannel`/`IPermissionContainer` receivers; where Kotlin erasure made two overloads collide, the helper was renamed (`checkPermissionInContainer`) rather than changing any public signature.
- **Deprecations.** `Helpers` moved off deprecated `HttpUrl.parse` to `url.toHttpUrlOrNull()`; `IOUtil` off the deprecated Okio call to `stream.source()`. Both are behavioral no-ops.
- **`-Werror`.** Redundant projections, unnecessary non-null assertions, annotation targets, and deprecation warnings were all fixed at the cause rather than suppressed.

detekt again ran as a real gate and surfaced **47 findings**, resolved on merit:

- **Magic numbers** (26, almost all in `IOUtil`'s byte-order helpers) became named constants — `MAX_REQUESTS_PER_HOST`, `IDLE_CONNECTIONS`, `KEEP_ALIVE_SECONDS`, `READ_AHEAD_LIMIT`, `BYTE_MASK`, `SHIFT_1_BYTE`/`SHIFT_2_BYTES`/`SHIFT_3_BYTES`, `LOWEST_BYTE_INDEX`, plus `MAX_SNOWFLAKE_LENGTH` and `MAX_CAUSE_DEPTH` in `Checks`/`Helpers`. Extracting is preferred over suppressing.
- **`ReturnCount`** (18) is suppressed inline with a written reason on the methods that are direct ports of branch-and-return Java control flow (`ChannelUtil.compare`, the `Helpers` predicates/equals, the `PermissionUtil` permission walks, `IOUtil.getBody`, `JDALogger.newFallbackLogger`, `SerializationUtil.pruneOneLevel`). Restructuring them would deviate from the faithful port and add review risk for no behavioral gain — the same rationale recorded for the leaf batch.
- **`TooGenericExceptionCaught`** (2) and **`PrintStackTrace`** (1) in `JDALogger` are suppressed with a reason: the reflective constructor probe catches `Throwable` exactly as the Java original did, and `getLazyString` deliberately catches `Exception` and writes the trace to a `StringWriter` so a failing lazy evaluation cannot itself throw from `toString()`. Narrowing either would be a behavior change.

Verification: `./gradlew check` is green — 505 tests / 0 failures, `apiCheck` (baseline unchanged), `verifyBytecodeVersion`, `spotlessCheck`, `rewriteDryRun`, and `detekt`. As with the leaf batch, `internal.utils` is outside the ABI baseline and the ArchUnit rules, so the compiler, the formatter, and the test suite are the entire safety net here; `apiCheck` passing proves nothing about these files.

### Phase 2 — `internal.utils` subpackages

The self-contained subpackages of `internal.utils` followed, walked leaves-inward so nothing depends on an unconverted sibling: `compress` (`Decompressor`, `ZlibDecompressor` — the interface and its only implementation), `tuple` (`Pair`, `ImmutablePair`, `MutablePair`, `MutableTriple`), `localization` (`LocalizationUtils`), and the `concurrent` leaves `CountingThreadFactory` and `concurrent.task.GatewayTask`. Their Java counterparts were deleted in the same change.

Faithfulness details forced by these files:

- **`Decompressor.LOG` was an interface field.** In Java the constant was `public static final` on the interface. Kotlin interfaces cannot hold fields, so `LOG` lives in the interface's `companion object` with `@JvmField`; `javap` shows it back on `Decompressor` itself, so `Decompressor.LOG` in Java and the interface's static initializer are unchanged.
- **`getMiddle` did not stay a property.** `MutablePair` and `MutableTriple` expose both public fields *and* JavaBean getters/setters on the same names. Modelling the fields as Kotlin `val`/`var` properties would synthesize `getLeft`/`setLeft` and then collide with the hand-written JavaBean methods, so the fields are plain `@JvmField` properties named `left`/`right`/`middle` and the getters/setters are declared separately as `getLeft()`, `setLeft()` etc. `javap` confirms the exact Java surface.
- **Nullable field types preserved.** `MutablePair`'s `left`/`right` and `MutableTriple`'s `middle` are nullable because callers set them to null; `getLeft`/`getRight` return the nullable type, matching Java's unannotated `L`/`R`. `ImmutablePair`'s fields stay non-null `L`/`R` like the Java original.
- **Generic bound.** `GatewayTask<T>` became `GatewayTask<T : Any>`. The Java class was unbounded, but its `get(): T` implements `Task<T>::get`, which Kotlin projects to `T & Any`; the bound is the minimum needed to keep the override well-formed and leaves every existing instantiation (`GatewayTask<Void>`, `GatewayTask<List<Member>>`, `GatewayTask<E : GenericEvent>`) valid.
- **Overloads, not `@JvmOverloads`.** `CountingThreadFactory` keeps its two- and three-argument constructors as real secondary constructors, per the standing rule; `javap` shows both.
- **`serialVersionUID`.** `Pair` implements `Serializable` and detekt wants a declared `serialVersionUID`, but the Java original declared none either, so the compiler-computed value is the compatible one. Declaring one would change serialization behaviour, so this single finding is suppressed with that reason written in place.

detekt surfaced three findings across the batch: the `serialVersionUID` above, and `TooGenericExceptionCaught` on `GatewayTask.onError`/`onSuccess`, where each callback's `catch (Throwable)` reproduces the Java original routing any throwable through the failure handler while rethrowing `Error`. Narrowing the catch would change behaviour, so both are suppressed with the reason inline.

Verification: `./gradlew check` is green and a forced `test --rerun-tasks` reports **505 tests / 0 failures**, matching the counts recorded for the earlier batches (`apiCheck` baseline unchanged, `verifyBytecodeVersion`, `spotlessCheck`/`rewriteDryRun`, and `detekt` all pass). The `internal` scope caveat still applies: these files are outside the ABI baseline and the ArchUnit rules.

The remaining `internal.utils` subpackage (`cache`) plus `tuple/package-info.java` were next, and are now converted (see below).

### Phase 2 — `internal.utils/requestbody` and `internal.utils/message`

The `requestbody` (`TypedBody`, `BufferedRequestBody`, `DataSupplierBody`, `JacksonRequestBody`) and `message` (`AbstractMessageBuilderMixin`, `MessageCreateBuilderMixin`, `MessageEditBuilderMixin`, `MessageUtil`) leaves followed; their Java counterparts were deleted in the same change. The remaining `internal.utils` subpackages were `config` and `cache`, both since converted.

These two packages are the first that are **consumed by retained Java code**, so the interface conversions were the ones where the Java compiler is an independent check on the Kotlin output rather than just a downstream reader:

- **Wildcards are not free.** Kotlin erases Java wildcards at the boundary, which the Java compiler then rejects:
  - `MessageRequest.setAllowedMentions` is declared in Java as the *invariant* `Collection<Message.MentionType>`. Kotlin emits `Collection<? extends Message.MentionType>` by default, so the Kotlin `override` did not actually override and every implementor (`MessageCreateActionImpl`, `ForumPostActionImpl`, …) failed to compile. Fixed with `@JvmSuppressWildcards` on the parameter.
  - `AbstractMessageBuilderMixin.getAttachments` returns `List<? extends AttachedFile>` in Java; Kotlin emitted the invariant `List<AttachedFile>`, which made `MessageCreateBuilderMixin`'s narrower `List<FileUpload>` an unrelated return type and broke the same implementors. Fixed with `@JvmWildcard` on the type argument.
  - Both are the same root cause seen from opposite sides: a Kotlin declaration that reads as an override may not be one after the `javac` boundary is applied. `javac` catching it is the point of keeping the consumers in Java.
- **`object` for static-only helpers.** `MessageUtil` was a static-only class with no subclasses (confirmed by sweep), so it became an `object` with `@JvmStatic` on both methods.
- **`writeTo` parameter name.** Overriding `RequestBody.writeTo` requires the parameter be named `sink`, not the descriptive `bufferedSink` used in the Java original; `-Werror` rejects the mismatch because it would break named-argument callers.
- **`finalize()` stayed a plain `protected fun`.** `BufferedRequestBody.finalize` carries the same `@Deprecated` message as `ChainedClosableIterator`'s and is likewise not an `override`; it is never invoked in tests but is still a valid declaration on JDK 25.
- **Deprecated Okio call replaced.** `Okio.buffer(source)` became the `source.buffer()` extension, matching how `IOUtil` was already updated.

Verification: `./gradlew check` green — 505 tests / 0 failures, `apiCheck` baseline unchanged, `verifyBytecodeVersion`, `spotlessCheck`/`rewriteDryRun`, and `detekt`. `javap` on the converted mixins shows the same default-method set as the Java originals, plus the Kotlin-generated `access$*$jd` static bridges (an implementation detail of interface bodies, absent from the Java version and not part of the source-level contract).

### Phase 2 — `internal.utils/config`

The `config` package (`AuthorizationConfig`, `MetaConfig`, `SessionConfig`, `ThreadingConfig` and the `sharding` subpackage: `EventConfig`, `PresenceProviderConfig`, `ShardingConfig`, `ShardingMetaConfig`, `ShardingSessionConfig`, `ThreadingProviderConfig`) followed. `ConfigFlag` and `ShardingConfigFlag` remain Java: they are enums, and AGENTS.md blocks enum conversion until the `EnumEntries getEntries()` leak is resolved.

- **`@JvmStatic` for the `static getDefault()` factories** keeps `ConfigClass.getDefault()` callable from the retained Java internals; without it the method only exists on `Companion`.
- **Nullable-throwing getters.** `ThreadingConfig`'s pooled executors are lazily initialised, so internally they are `var x: T?`. Java's `getRateLimitScheduler`/`getRateLimitElastic`/`getGatewayPool` were `@Nonnull` and effectively guaranteed non-null after `init()`, so the Kotlin getters return the non-null type using `!!`; only `getEventPool` (genuinely optional) returns a nullable type. This preserves the declared signature instead of widening it to `T?`.
- **Inheritance stayed `open`.** `ShardingMetaConfig extends MetaConfig` and `ShardingSessionConfig extends SessionConfig` are real Java-visible subclass edges, so both base classes must be `open`. `MetaConfig`'s methods had to drop the `open` markers again because nothing overrides them — `open` on an unoverridden method is not needed to preserve the ABI, and ktlint/compiler checks are clean without it.
- **Magic numbers extracted.** detekt flags literals in the `getDefault()` factories; `CONNECTION_TIMEOUT_MS`, `DEFAULT_MAX_RECONNECT_DELAY`, and `DEFAULT_LARGE_THRESHOLD` became named constants rather than suppressions, per the AGENTS.md rule.

Verification: `./gradlew check` green, 505 tests / 0 failures. The two subclass relationships were confirmed by `javap` (constructors and `super`-calls intact) and by a sweep for `extends <ConfigClass>` finding no further subclasses.

### Phase 2 — `internal.utils/cache`

The `cache` package (`ReadWriteLockCache`, `AbstractCacheView`, `SnowflakeCacheViewImpl`, `SortedSnowflakeCacheViewImpl`, `ChannelCacheViewImpl`, `SortedChannelCacheViewImpl`, `UnifiedCacheViewImpl`, `UnifiedChannelCacheView`, `MemberCacheViewImpl`, `ShardCacheViewImpl`) followed. This is the first batch whose classes are **instantiated directly by the retained Java tests** (`ChannelCacheViewTest`, `ChannelCacheViewTest`), so the Java compiler and the suite are the independent check on the conversion.

- **Parameter assertions had to be disabled to keep Java contracts.** `ChannelCacheView.ofType(Class<C>)` is declared `@Nonnull` in Java and validates with `Checks.notNull`, throwing `IllegalArgumentException`. Kotlin emits `Intrinsics.checkNotNullParameter` for the non-null parameter, so `ChannelCacheViewTest.testNullChannelInterfaceFilters` started failing with `NullPointerException` *before* `Checks.notNull` ran. The parameter cannot be widened to `Class<C>?`: Kotlin honors the Java `@Nonnull` and rejects a nullable `override` outright (verified with a minimal probe). The build therefore sets `-Xno-param-assertions`, which removes the intrinsic and restores the documented `IllegalArgumentException`. This is the second trap MIGRATION §5 predicted ("Kotlin's inserted null checks on non-null parameters … can turn previously legal Java calls into `NullPointerException`s") and it is now pinned by a permanent interop-gate test rather than left to review.
- **`kotlin-stdlib` is still required.** Removing the parameter assertions does not remove the stdlib dependency: the same classes reference `Intrinsics.checkNotNull`/`checkNotNullExpressionValue` for casts and platform types (14 references in `ChannelCacheViewImpl` alone), so the explicit `api(libs.kotlin.stdlib)` declaration stands.
- **Inner-class constructors keep their parameter contract.** `FilteredCacheView(type: Class<C>)` stays non-null and calls `Checks.notNull(type, "Type")` first, so a null reaches the check rather than an intrinsic; the property is assigned after the check instead of via a constructor `val`.
- **`@JvmField` on protected fields.** `caches`, `type`, and `filteredMaps` are `protected` fields the Java subclasses and tests read directly; `@JvmField` keeps them as fields rather than getter-only properties. `ShardCacheViewImpl`'s private `elements` does not need it.
- **Iterator cast.** `AbstractCacheView.iterator()` passes `ObjectArrayIterator(... as MutableIterator<T>)` because the Java original returned the raw iterator; the cast is the minimum needed to keep the `MutableIterator` return type.
- **detekt suppressions with reasons.** `ReturnCount` on `AbstractCacheView.getElementsByName` and `NestedBlockDepth` on `ShardCacheViewImpl.getElementsByName` reproduce the Java control flow, so they are suppressed inline rather than restructured, matching the AGENTS.md policy.

Verification: `./gradlew clean check` green — 506 tests / 0 failures, `apiCheck` baseline unchanged, `verifyBytecodeVersion`, `spotlessCheck`/`rewriteDryRun`, and `detekt`.

### Phase 2 — `internal.hooks` and `internal.modals`

The two remaining single-class packages followed. Both classes are `internal` (neither the ABI baseline nor `ArchUnitComplianceTest` covers them), so the compiler, detekt, and the suite are the only gates that see them.

- **`EventManagerProxy` keeps its nullable setter.** `setSubject(IEventManager?)` still substitutes a fresh `InterfacedEventManager` when given null, which is what `JDAImpl.setEventManager` relies on. Kotlin's parameter assertions would have thrown before the null check, but `-Xno-param-assertions` (added for the `cache` batch) already restores the Java behavior here too.
- **`EventManagerProxy`'s executor is nullable.** It is populated from `ThreadingConfig.getEventPool()`, which already returns `ExecutorService?`, so the field is declared nullable rather than assumed non-null.
- **detekt suppressions with reasons.** `SwallowedException` and `TooGenericExceptionCaught` on `handle`, and `TooGenericExceptionCaught` on `handleInternally`, reproduce the Java control flow: the original caught `RejectedExecutionException` without logging it (the warning message is the whole report) and caught broad `Exception`/`RuntimeException` deliberately so the event pool can never obstruct the socket handler. Narrowing either catch would change behavior, so they are suppressed inline rather than restructured, matching the AGENTS.md policy.
- **`ModalImpl` uses secondary constructors, not `@JvmOverloads`.** AGENTS.md forbids `@JvmOverloads` because it collapses the overload set; the `(DataObject)` and `(String, String, List)` constructors are declared separately.
- **Invariant list parameter.** The public constructor takes `components: @JvmSuppressWildcards List<ModalTopLevelComponentUnion>`. Without the annotation Kotlin emitted `List<? extends ModalTopLevelComponentUnion>` for a `val` property, which no longer matched the Java overload's erased signature; the annotation restores `List<ModalTopLevelComponentUnion>` exactly. The annotation has to sit on the type usage, not the value parameter — `@JvmSuppressWildcards` is not applicable to a value-parameter target, matching the existing use in `AbstractMessageBuilderMixin`.

Verification: `./gradlew clean check` green, `./gradlew test --rerun-tasks` reports 506 tests / 0 failures, `apiCheck` baseline unchanged.

### Phase 2 — `internal.components/tree` and `internal.components/utils`

These two packages are leaves of `internal.components` and are consumed by the retained Java `api.components.tree` interfaces (`ComponentTree`, `MessageComponentTree`, `ModalComponentTree`) and by `EntityBuilder`, so the Java compiler and the suite are the independent check again.

- **`AbstractComponentTree.components` keeps its field identity.** The Java field was `protected final List<E> components` and `ComponentTreeImpl`/`MessageComponentTreeImpl`/`ModalComponentTreeImpl` read it directly; `@JvmField` keeps it a field rather than a getter-only property.
- **Static factories keep static ABI.** `MessageComponentTreeImpl.of` and `ModalComponentTreeImpl.of` were `public static`; they are `@JvmStatic` members of the `companion object` so `MessageComponentTreeImpl.of(...)` is unchanged for the Java interfaces that call it.
- **`ComponentsUtil` became an `object`.** Every member was `static`, the constructor was implicit and never used, and there are no subclasses or implementors (checked with a repository-wide search for `new ComponentsUtil`/`extends ComponentsUtil`), so the `object` declaration is the correct shape. `@JvmStatic` on each member preserves the static call sites; the resulting `INSTANCE` field and private constructor are additions the gate permits. The one piece of state that looks mutable — `doReplace`'s local `newComponent` — is a local, not a field, so it stays inside the method.
- **`getComponentTreeTextContentLength` needed an explicit widening.** The Java original summed an `IntStream` with `.sum()`, which the compiler then widened to `long` at the return. Kotlin does not widen implicitly, so the `Int` result is converted with `.toLong()`; the return type stays `long`.
- **Variance annotations were dropped where Kotlin already infers them.** `Collection<out Component>` and `Collection<out MessageTopLevelComponent>` produced "projection is redundant" warnings under `-Werror`; the plain `Collection<Component>` still erases to `Collection<? extends Component>` in the signature because `Collection` is covariant in Kotlin. `javap` confirms the emitted descriptors match the Java originals.
- **`doReplace` keeps its unchecked cast.** The Java original cast each replacement back to `E` (`(E) newComponent`) under `@SuppressWarnings("unchecked")`; the Kotlin version carries `@Suppress("UNCHECKED_CAST")` at the cast site for the same reason, since users are not required to return unions.

Verification: `./gradlew clean check` green — 506 tests / 0 failures, `apiCheck` baseline unchanged, `verifyBytecodeVersion`, `spotlessCheck`/`rewriteDryRun`, and `detekt`.

### Phase 2 — `internal.components` base classes

`AbstractComponentImpl` and `UnknownComponentImpl` are the root of the `internal.components` hierarchy: 19 component implementations extend the former, so converting it is the prerequisite for the rest of the package.

- **`AbstractComponentImpl` stays an abstract `class`, not an `object`.** It has subclasses (19 of them) and carries instance state in those subclasses, so the "static-only class with no subclasses becomes an object" rule does not apply. The union-hook methods are non-final instance methods in the original and stay non-final here.
- **`toComponentType` stays `protected`.** It is the shared implementation behind the hooks and is called from subclasses, so its visibility and signature are preserved.
- **`UnknownComponentImpl.equals` uses `other`, not `o`.** Kotlin warns when an override's parameter name differs from the supertype's (`Any.equals(other)`), and the build is `-Werror`. Renaming is behavior-preserving; the identity check, the `instanceof` guard, and the `DataObject` comparison are unchanged.
- **`Objects.equals`/`Objects.hashCode` are retained.** The Java original delegated to them; keeping `java.util.Objects` rather than Kotlin's `==` on a platform type avoids introducing a nullability assumption about `data`.

Verification: `./gradlew clean check` green — 506 tests / 0 failures, `apiCheck` baseline unchanged, `javap` confirms the hook methods, `toComponentType`, and the full `withUniqueId` covariant-return set are unchanged.

### Phase 2 — `internal.components` selections

`SelectMenuImpl` plus its two concrete subclasses (`StringSelectMenuImpl`, `EntitySelectMenuImpl`). These are instantiated directly by the retained Java `EntitySelectMenu.Builder.build()`, `StringSelectMenu.Builder.build()`, and `ComponentDeserializer`, and `EntitySelectMenuImpl` is downcast to in `SelectMenuTests`, so the Java compiler and the suite are the independent check again.

- **`SelectMenuImpl`'s fields keep `protected` field identity.** `id`, `placeholder`, `uniqueId`, `minValues`, `maxValues`, `disabled`, and `required` are read directly by both subclasses, so they are `@JvmField protected`. `javap` shows all seven as `protected final` fields with the original types.
- **`EntitySelectMenuImpl`'s three fields had to drop to `private`.** Java declared `type`, `channelTypes`, and `defaultValues` as `protected`, but the class is `final` and no other source reads them; detekt's `ProtectedMemberInFinalClass` flags precisely that. The class is never subclassed (checked repository-wide), so `private` preserves every reachable behavior — it only removes the unreachable protected surface. This is a genuine faithfulness fix detekt caught, not a suppression.
- **`withUniqueId` downcasts the builder result.** The Java original wrote `(StringSelectMenuImpl) createCopy().setUniqueId(uniqueId).build()`; the builder is declared to return the `SelectMenu` interface, so the cast is retained as `as StringSelectMenuImpl` rather than reified through a generic.
- **Invariant list parameters again.** The `(..., List<SelectOption>, ...)` and `(..., List<DefaultValue>, ...)` constructors carry `@JvmSuppressWildcards` on the type usage so the erased descriptors match the Java overloads instead of widening to `List<? extends ...>`.
- **`Component.Type` is the right nested name.** `SelectMenu` declares no `Type` of its own; the menu types come from `Component.Type` (`STRING_SELECT`, `ROLE_SELECT`, …), which is what the Java originals used.
- **`hashCode`/`equals` stay hand-written.** `StringSelectMenuImpl` and `EntitySelectMenuImpl` compare against the *interface* (`other is StringSelectMenu`), not the implementation class, so Kotlin `data class` generation would change semantics; the manual implementations are preserved. `equals`'s parameter is named `other` for the same `-Werror` reason as `UnknownComponentImpl`.
- **`parseOptions` moved into the companion object.** It was `private static` in Java and is called only from the `DataObject` constructor; `private` in the companion keeps it out of the Java-visible surface.

Verification: `./gradlew clean check` green — 506 tests / 0 failures, `apiCheck` baseline unchanged, `verifyBytecodeVersion`, `spotlessCheck`/`rewriteDryRun`, and `detekt`. The ABI gate was also negatively tested at this point: adding a bogus member for the Kotlin `SkuSnowflake` class to `api/JDA.api` made `apiCheck` fail with "member removed or changed", confirming the gate really inspects Kotlin output rather than silently skipping it.

### Phase 2 — `internal.components` leaves

The remaining leaf implementations, converted in one batch: `ResolvedMediaImpl`, `replacer/TypedComponentReplacerImpl`, `textdisplay/TextDisplayImpl`, `checkbox/CheckboxImpl`, `separator/SeparatorImpl`, `mediagallery/{MediaGalleryImpl,MediaGalleryItemImpl,MediaGalleryItemFileUpload}`, `attachmentupload/AttachmentUploadImpl`, `thumbnail/{ThumbnailImpl,ThumbnailFileUpload}`, and `filedisplay/{FileDisplayImpl,FileDisplayFileUpload}`. All are instantiated directly by the retained Java `ComponentDeserializer` and by the API component interfaces' nested `Builder` classes, so the Java compiler remains the independent check.

- **`@JvmField` is only needed for `protected` fields, not `private` ones.** `AttachmentUploadImpl` declared six `protected final` fields in Java. The class is `final` and no other source reads them, so they became `private` (same reasoning as `EntitySelectMenuImpl`) and the `@JvmField` annotations were dropped — `private` fields are already emitted as plain fields, and the annotation would be redundant. `javap` confirms no protected surface remains and the public constructors/accessors are unchanged.
- **`@Unmodifiable` is not applicable to a Kotlin function.** `ResolvedMediaImpl.getFlags()` carried `org.jetbrains.annotations.Unmodifiable` on the Java method. Kotlin rejects that annotation on a `fun` ("not applicable to target 'member function'"); it is a type-use annotation here, and the interface `ResolvedMedia.getFlags()` already carries it. The override omits it. The return type stays `java.util.Set<ResolvedMediaFlag>`, and `Collections.unmodifiableSet` is called with an explicit type argument so Kotlin's `Set` (read-only) rather than `MutableSet` is inferred, matching the interface descriptor. Note that importing `java.util.Set` *shadows* `kotlin.collections.Set` and breaks the override — the plain Kotlin `Set` is correct.
- **Nested-enum constants import from the outer type.** `MAX_DESCRIPTION_LENGTH` lives on `Thumbnail` and `MediaGalleryItem`, and `MAX_ITEMS` on `MediaGallery`; the import is `...Thumbnail.MAX_DESCRIPTION_LENGTH` (not the `Companion` path, which does not exist for a Java interface constant).
- **`ThumbnailFileUpload`/`MediaGalleryItemFileUpload` keep the mutable-fallback semantics.** Both fall back to `file.getDescription()` when their own description is null. `toData()` must therefore call `getDescription()`, not read the field, or the emitted JSON would differ for a file whose description was set after construction — the Java original made the same call twice on purpose.
- **`detekt` caught two real deviations.** `ProtectedMemberInFinalClass` on `AttachmentUploadImpl` (above), and `ReturnCount` on `TypedComponentReplacerImpl.apply` — the Java body had three `return` statements, rewritten as a single `return if (...) ... else ...` rather than suppressed.

Verification: `./gradlew clean check` green — 506 tests / 0 failures, `apiCheck` baseline unchanged, `verifyBytecodeVersion`, `spotlessCheck`/`rewriteDryRun`, and `detekt`.

### Phase 2 — `internal.components` composites

The last eight Java files in the package: `actionrow/ActionRowImpl`, `buttons/ButtonImpl`, `checkboxgroup/CheckboxGroupImpl`, `container/ContainerImpl`, `label/LabelImpl`, `radiogroup/RadioGroupImpl`, `section/SectionImpl`, and `textinput/TextInputImpl`. `internal.components` is now 100% Kotlin (31 files). Every one is instantiated by the retained Java `ComponentDeserializer` and/or a nested `Builder` in the API interfaces, so the Java compiler and suite remain the independent check.

- **`doReplace` needs explicit type arguments where the finisher is nullable.** The Kotlin signature is `fun <R, E : Component> doReplace(..., finisher: Function<List<E>, R>)`. For a finisher that returns `null` — `LabelImpl.replace` and `SectionImpl.replace`, both of which turn "replacer removed the accessory" into a null — Kotlin cannot infer `R` as nullable from a lambda whose branches are `null`/`E`, so the call site writes `doReplace<SectionAccessoryComponentUnion?, SectionAccessoryComponentUnion>(...)`. The Java original relied on the erased `R` being `Object`. `SectionImpl.replace`'s content half is `doReplace<List<...Union>, ...Union>(...)` with `Function.identity()` as the finisher, which infers `R` from the finisher's return type rather than from the lambda.
- **Nullability has to be re-established after `Checks.notNull`.** `Checks.notNull` carries `@Contract("null, _ -> fail")`, but that is a JSR-305 contract for the *Java* compiler; Kotlin does not narrow through it. Where a value must be non-null afterwards the call site adds `!!` (e.g. `newChild!!`, `newAccessory!!`, `sku!!`). This preserves the exact behavior — `Checks.notNull` throws first, so the `!!` is unreachable — and is the same pattern already used in `PermissionUtil`.
- **`@Suppress` for a ported `TODO`.** `ActionRowImpl.checkIsValid` carried a genuine upstream `// TODO:` in Java. detekt's `ForbiddenComment` flags it, and deleting a real follow-up note to appease a linter would lose information, so the function carries `@Suppress("ForbiddenComment")` with a written reason, matching the inline-suppression policy.
- **The accent-colour mask became a named constant.** `ContainerImpl.toData` used the literal `0xFFFFFF`; detekt's `MagicNumber` flagged it. Extracted to `private const val ACCENT_COLOR_MASK` in the companion (a `const`, so it is inlined and adds no field), rather than suppressed.
- **`ButtonImpl.withUniqueId` reuses the interface default.** Java called `Button.super.withUniqueId(uniqueId)`. In Kotlin this is `super<Button>.withUniqueId(uniqueId) as ButtonImpl` — the qualified super form, because `Button` is one of several supertypes. The cast is safe: the interface default constructs a `ButtonImpl` and returns it after `checkValid()`.
- **`SectionImpl.validated` keeps its non-null accessory parameter.** The accessory overloads still take `SectionAccessoryComponent` (non-null), as in Java. The caller — `replace` — holds the nullable result of the accessory replacement, so it re-establishes non-nullness with `!!` after `Checks.notNull` rather than widening the parameter. Widening it would have changed the Java-visible signature and let a null slip past `Checks.notNull`'s contract.
- **`withUniqueId` overrides return the implementation type where Java did.** `ActionRowImpl`, `ButtonImpl`, `CheckboxGroupImpl`, `ContainerImpl`, `LabelImpl`, `RadioGroupImpl`, and `SectionImpl` declare the covariant `Impl` return, and the compiler emits the full bridge set (one per interface declaring the method) — `javap` confirms each bridge, matching the Java originals.

Verification: `./gradlew clean check` green — 506 tests / 0 failures, `apiCheck` baseline unchanged (`api/` has no diff), `verifyBytecodeVersion`, `spotlessCheck`/`rewriteDryRun`, and `detekt`. `javap` signature checks passed for all eight classes. With this, `internal.components`, `internal.components/tree`, `internal.components/utils`, `internal.utils`, `internal.utils/*`, `internal.hooks`, and `internal.modals` are Kotlin; the remaining `internal.*` work is `requests` (66), `handle` (64), `entities` (114), `managers` (29), `interactions` (26), and `JDAImpl` (`audio` and `generated` are now done).

### Phase 2 — `internal.utils` remaining files

Only one item in `internal.utils` is still Java:

- `tuple/package-info.java` — a package-level Javadoc file with no Kotlin equivalent; it documents the Apache Commons Lang provenance of the converted `tuple` classes and stays Java (or is dropped) until the package's documentation is re-homed.

(`config/flags/ConfigFlag.java` and `config/flags/ShardingConfigFlag.java` were the last two Java types here; they converted in batch 37 once the `internal`-scope enum exemption was confirmed.)

#### Conversion order

Order by dependency depth, not by importance:

1. `net.dv8tion.jda.annotations` (6 files — pure annotations, trivial warm-up).
2. `net.dv8tion.jda.internal.utils` (`Checks`, `JDALogger`, `EntityString`, `PermissionUtil`, `SerializationUtil`, compressors) — exercises reflection, statics, and generics on internal code where ABI risk is lowest.
3. Remaining `internal.*` packages: `requests`, `entities`, `hooks`, `managers`, `audio`, `handle`, `binary`.
4. `api.utils`, `api.requests`, `api.managers`.
5. `api.entities`, `api.events`, `api.components`, `api.interactions`, `api.audit`, `api.modals`, `api.audio`, `api.sharding`.

A package is "done" only when the ABI diff against the baseline is empty, or every residual difference is recorded in a reviewed, checked-in allowlist file with a rationale.

Do **not** treat the largest files as single units. Each of these is its own review cycle:

- `api/entities/Guild.java` (~6,800 LOC)
- `api/entities/channel/middleman/MessageChannel.java` (~3,800)
- `api/entities/Message.java` (~3,300)
- `internal/entities/EntityBuilder.java` (~2,800)
- `internal/entities/GuildImpl.java` (~2,500)
- `api/sharding/DefaultShardManagerBuilder.java` (~2,300)
- `api/JDA.java` (~2,200)
- `api/JDABuilder.java` (~1,800)

### Phase 2 — `internal.audio`

Seven of the ten files converted in one batch: `AudioConnection`, `AudioWebSocket`, `Decoder`, `AudioPacket`, `CryptoAdapter`, `DaveCryptoAdapter`, and `ConnectionRequest`. This is the first package where the converted classes are *not* all leaves — the retained Java is only the three enums (`AudioEncryption`, `ConnectionStage`, `VoiceCode`), which the plan defers, and the converted classes call into one another, so Java compilation of a package's remaining files is no longer an independent cross-check of the converted surface.

- **`@JvmField` is needed for `protected`/`internal` fields that another class in the package reads.** `ConnectionRequest.guildId`, `Decoder.ssrc`, `AudioConnection.udpSocket`, and `AudioWebSocket.{socket,encryption,crypto}` are read by a sibling class. In Kotlin a plain `internal var` emits `private` plus a mangled getter, so the field has to stay a real JVM field. The fields also keep their original `volatile` modifier (`@Volatile`) — dropping it would have been a silent concurrency regression, not just an ABI diff.
- **`@JvmName` preserves the original JVM name of an `internal` member.** Kotlin mangles `internal` to `foo$moduleName` (`getConnectionStatus$net_dv8tion_JDA`, `prepareReady$net_dv8tion_JDA`, ...). Annotating the declaration with `@JvmName("foo")` emits the unmangled name while the member stays `internal` (package-visible within the module) — this is what keeps the cross-class calls in the package compiling without widening the member to `protected`/`public`. The residual diff is then only a visibility widening (`protected` → `public`), with the name intact.
- **Cross-class same-package access is `internal`, not `protected`.** Java `protected` gives package access as a side effect; Kotlin `protected` does not. Members such as `AudioWebSocket.startConnection`, `AudioConnection.prepareReady`/`removeUserSSRC`/`updateUserSSRC`, and `Decoder.close` are called by siblings, so they became `internal`. Simple field accessors (`val`/`var`) do not need `@JvmName` — they are already unmangled — but functions do.
- **`open` is required where Java `protected` members would otherwise trip detekt.** `ProtectedMemberInFinalClass` fires on protected members of a final class. The originals are non-final, so `open class` is the faithful fix and keeps subclassing possible; suppressing instead would have narrowed the class for no reason. `open` is applied to `AudioConnection`, `AudioPacket`, `ConnectionRequest`, `Decoder`, `DaveCryptoAdapter`, and the two `CryptoAdapter` adapters.
- **`silenceBytes` was removed as dead code.** The Java original declared `static final ByteBuffer silenceBytes` but never read it. No consumer exists, so it is dropped rather than carried as an unused field.
- **The generated `Companion` field is an accepted addition.** Any Kotlin class with a `companion object` emits a public static `Companion` field and a `<clinit>`. Additions pass `apiCheck`, and the field is required for `AudioConnection.LOG` / `MAX_UINT_32` and the package's `const val` accessors to resolve from Java. Same documented allowance as the pilot.
- **detekt's 49 findings were resolved on merit, not wholesale suppression.** Most were `MagicNumber`, replaced with named `private const val`s; `catch (e: Exception)` blocks that genuinely rethrow or log-and-continue keep a narrow `@Suppress("TooGenericExceptionCaught", ...)` with the original comment preserved, and unnamed `catch (_: ...)` replaced the unused bindings (`SocketTimeoutException`, `SocketException`, `IOException`, `RejectedExecutionException`, `NoRouteToHostException`). `AES_GCM_Adapter` keeps its underscored name for bytecode compatibility, suppressed with a one-line reason matching the class-naming policy.
- **`internal` methods are public on the JVM, so the residual visibility diff is expected.** `AudioWebSocket`'s protected surface (`isReady`, `getAddress`, `getSecretKey`, `getSSRC`, `getConnectionStatus`, `send`/`send(int, Object)`, `startConnection`, `close`, `changeStatus`, `setAutoReconnect`) and its constructor, `Decoder`'s constructor and `close`, and the `AudioConnection$AudioData` accessors are all `public` on the JVM after conversion. All names are preserved; the widening is recorded as accepted because the affected types are `internal` to `net.dv8tion.jda.internal`, outside the checked `net.dv8tion.jda.api.**` baseline.
- **The anonymous `$1`/synthetic-lambda classes remain.** `CryptoAdapter$1`/`AudioWebSocket$1` were Java anonymous classes; Kotlin emits differently-named synthetic companions/lambda classes instead, reached by the same call sites. The three enums (`AudioEncryption`, `ConnectionStage`, `VoiceCode`) converted in batch 37.

Verification: `./gradlew check` green — 506 tests / 0 failures, `apiCheck` baseline unchanged, `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`.

### Phase 2 — `internal` enums (batch 37)

The remaining enums in `internal.utils.config.flags` (`ConfigFlag`, `ShardingConfigFlag`) and `internal.audio` (`AudioEncryption`, `ConnectionStage`, `VoiceCode` with its nested `Close`) are now Kotlin, closing out those two packages. This batch exists to settle the enum question from §4/§10.

- **The `getEntries()` leak is real, and scoped out.** `javap` confirms `kotlin.enums.EnumEntries getEntries()` is emitted `ACC_PUBLIC, ACC_STATIC` and non-synthetic on a converted `enum class`. It is exactly the leak §10 predicted. It is harmless here because every compliance gate that would flag it (`ArchUnitComplianceTest`, the `EnumComplianceTest`, the `api/JDA.api` baseline) imports `net.dv8tion.jda.api.**` only; `internal` enums are outside all of them. The public `api` enums stay blocked.
- **`values()`/`valueOf()` and the `$VALUES` field keep their JVM placement.** Kotlin emits both on the enum class itself, so the retained Java call sites (`ConfigFlag.getDefault()`, `AudioEncryption` reads in `CryptoAdapter`) resolve unchanged.
- **A former static method keeps its static call site.** `ConfigFlag.getDefault()` was `static` in Java; it becomes a `@JvmStatic` companion function so `ConfigFlag.getDefault()` still resolves from Java rather than through `ConfigFlag.Companion`.
- **`VoiceCode.Close`'s wire codes are enum constructor arguments.** detekt's `MagicNumber` flagged 15 of them; each value is already named by its constant, so there is no meaningful constant to extract. The rule is disabled for enum entries project-wide (`style.MagicNumber.ignoreEnums: true`) with the reason recorded in `gradle/detekt.yml`.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged (`api/` has no diff), `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`. `javap` confirms the enum constants, `values()`/`valueOf()`, and `ConfigFlag.getDefault()`'s static shape are unchanged apart from the accepted `getEntries()` addition on `internal` types.

### Phase 2 — `internal.generated`

All three files converted: `MaybeNull`, `MaybeNullSerializer`, and `MaybeNullDeserializer`. This package is *not* generator output — the REST-model generator writes to `build/generated/rest-api-models` (see the JavaPoet note above), so these are hand-written support classes that merely live under a `generated` package name. `MaybeNull` is the Jackson `@JsonSerialize`/`@JsonDeserialize` wrapper the generated DTOs use for fields that distinguish "absent" from "null".

- **The generic wrapper keeps its exact JVM shape.** `MaybeNull`'s `public T value()`, `isPresent()`, the static `<T> MaybeNull<T> empty()`, and `equals`/`hashCode`/`toString` all survive unchanged. The singleton moved from a private static `empty` field to a `private companion object` `EMPTY`, and `empty()` is `@JvmStatic` so Java still sees a static method on the class rather than on `Companion`.
- **`empty()` is `@JvmStatic`, not `@JvmField`.** It is a method, so the static-method rule applies. The backing `EMPTY` field stays private in the companion; the only new class-level member is the `access$getEMPTY$cp()` accessor Kotlin synthesises for the `@JvmStatic` body, plus the `Companion` field itself. Both are additions, which `apiCheck` passes.
- **The two Jackson handlers are `internal`, matching the Java package-private visibility.** `MaybeNullSerializer`/`MaybeNullDeserializer` were package-private classes; they are now `internal` (Java-visible, module-scoped), which is the same widening already accepted for the `internal.audio` members. The erased bridge methods Jackson relies on (`serialize(Object, ...)`, `deserialize(...)` returning `Object`) are emitted identically by Kotlin.
- **`equals` uses `other: Any?`.** Kotlin warns (`-Werror`) when an `equals` override names its parameter anything other than the supertype's `other`, so the ported body follows the existing `internal.utils.tuple.Pair` precedent rather than the Java parameter name `obj`.
- **Classes stay `final`.** None of the three has a subclass, so Kotlin's default finality is the faithful choice (same rule as the `final`-classes row in the idiom table).

Verification: `./gradlew check` green — 506 tests / 0 failures, `apiCheck` baseline unchanged, `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`.

### Phase 2 — `internal.interactions` leaves (batch 1)

The six classes that carry no interaction hierarchy were converted first, leaving the inheritance chain for batch 2. `ChannelInteractionPermissions`/`MemberInteractionPermissions` keep their JavaBean getters by declaring `val` properties (`memberId`/`permissions`/`channelId`) instead of private fields plus accessors. `UnmodifiableLocalizationMap.UNMODIFIABLE_CHECK` is a companion `@JvmField` so the static field stays on the class. `LocalizationMapper.fromFunction` is `@JvmStatic`; `TranslationContext` stays an `inner` class so it captures the mapper, and `forObjects` keeps the `java.util.function.Function`/`Consumer` parameter types rather than Kotlin function types so the erased signature is unchanged. `InteractionCallbackResponseImpl` maps the optional message with `optObject(...).map(...).orElse(null)`. The `RuntimeException` in `LocalizationMapper` is kept and `@Suppress`ed with a reason: narrowing it would change the exception contract callers see.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck`, `verifyBytecodeVersion`, `spotlessCheck`/`rewriteDryRun`, and `detekt` all pass. `javap` confirms every original public member is present; the remaining diffs are `final`/visibility and synthetic-lambda artifacts.

### Phase 2 — `internal.interactions` hierarchy (batch 2)

`InteractionImpl` and its descendants, plus `CommandDataImpl`, `FileTypesImpl`, and `InteractionHookImpl`.

- **Fields that a Java consumer reads as a field must be `@JvmField`.** Kotlin turns a `protected val` into a private field plus a getter, which both drops the field and collides with an interface getter of the same name (`Accidental override`). `token`, and the `protected` fields on the interaction bases and `ComponentInteractionImpl` (`customId`/`message`/`messageId`), are therefore `@JvmField`.
- **Reaching an interface member from a subclass body fails in Kotlin.** `super.getChannel()` in `ComponentInteractionImpl` is rejected with "Abstract member cannot be accessed directly", because `getChannel()` is also an `Interaction` interface member. A `protected getChannelChannel()` accessor was added to `InteractionImpl` as the port of `super.getChannel()`.
- **`CommandDataImpl` keeps its `EnumSet` fields.** `contexts`/`integrationTypes` still hold `EnumSet`s built through the unchanged `Helpers.copyEnumSet(Class, Collection)` static. detekt's `MagicNumber`/`ProtectedMemberInFinalClass` and the `@JvmField` rules drove the rest of the port: `options`, `name`, and `description` stay `@JvmField` `protected` fields, and the `of`/`EMPTY_AND_IMMUTABLE` statics are `@JvmStatic`/`@JvmField` on the respective companions.
- **`InteractionHookImpl` gains private constants.** detekt's `MagicNumber` requires the 10-second timeout, the 15-minute expiry, and the millisecond factor to be named constants; they are `private`, so they are not part of the ABI.
- **`StringSelectInteractionImpl.parseValues` is `private`.** detekt's `ProtectedMemberInFinalClass` forbids `protected` in a final class; the method had no other callers.
- **`FileTypesImpl` keeps a static `EMPTY_AND_IMMUTABLE` via `@JvmField` on the companion.** The private primary constructor is preserved, so the Kotlin compiler emits the same synthetic `DefaultConstructorMarker` overload the class already effectively hid; Java callers still use `empty()`/`fromArray`.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged, `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`. `javap` against the Java reference shows no removed public member — the diffs are `final` on methods, erasure-compatible wildcard bounds on `setContexts`/`setIntegrationTypes`, and synthetic lambdas/constants.

### Phase 2 — `internal.interactions.command` (batch 3)

`CommandImpl`, the payload/mixin pair, the command interaction chain, and the autocomplete interaction.

- **`CommandImpl`'s static members stay static.** `OPTIONS` and the three `Predicate` tests are companion `@JvmField`s, and `parseOptions` is `@JvmStatic`; `Command.Subcommand`/`SubcommandGroup` construct through `parseOptions` with `Command.Subcommand(this, it)` lambdas, so the public helper signature is unchanged.
- **`super.getChannel()` again cannot be reached.** `CommandInteractionPayloadImpl` and the context/autocomplete implementations use `InteractionImpl.getChannelChannel()` from batch 2 for the same interface-member reason.
- **`ContextInteractionImpl<T : Any>`.** The bound is required so the `@Nonnull getTarget(): T` override erases to a non-null reference; `parse` is a protected abstract member whose parameter is named `interactionData` to match the supertype (`allWarningsAsErrors` rejects the mismatch).
- **The `@Nullable Member getTargetMember()` contract is preserved explicitly.** Kotlin would otherwise emit non-null; the override carries `@Nullable`.
- **`replyChoices` keeps `Collection<Command.Choice>`.** Kotlin emits a declaration-site `? extends` wildcard, which is erasure-identical to the Java parameter.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged, `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`. `javap` against the Java reference shows only `final`, private helpers, and synthetic lambdas as differences.

`internal.interactions` is now fully Kotlin (all four batches: leaves, hierarchy, command, and the earlier response/localization helpers), as are `internal.entities.mixin` and `internal.entities.messages`.

### Phase 2 — `internal.entities.mixin` and `internal.entities.messages`

- **`MemberMixin`/`RoleMixin` are interfaces with default methods.** Converted in place; `-jvm-default=enable` keeps `getColors`/`createCopy`/`compareTo` as real `default` methods, so Java implementors of these (and of the API interfaces they extend) are unaffected. The `T : MemberMixin<T>`/`T : RoleMixin<T>` self-type bounds are preserved.
- **`RoleMixin.compareTo` renames its parameter to `other`.** Kotlin warns (`-Werror`) that a `compareTo` override names its parameter other than the `Comparable` supertype's; the body is otherwise verbatim, and `ReturnCount` is suppressed inline with a reason because the early-return control flow is unchanged from Java.
- **`MessageSearchResponseImpl.NotReadyImpl`/`ResultsImpl` stay nested `class`es.** The retained Java `MessageSearchActionImpl` constructs them as `MessageSearchResponseImpl.NotReadyImpl`/`.ResultsImpl`, which Kotlin's nested classes preserve. `getMessages` carries the `@Unmodifiable` type-use annotation on its return type — Kotlin rejects it on the function, and type usage is the equivalent target.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged, `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`. `EntityBuilder.createMessagePoll` and `MessageSearchActionImpl` remain Java callers, so Java compilation is an independent cross-check of both packages.

`internal.entities.emoji`, `internal.entities.sticker`, and `internal.entities.detached` are now Kotlin as well.

### Phase 2 — `internal.entities.emoji`, `internal.entities.sticker`, and `internal.entities.detached`

- **The `EmojiUnion` diamond is a supertype list.** `CustomEmojiImpl : CustomEmoji, EmojiUnion` (and the unicode/rich/application variants) keeps the exact interface set and the `getType()` erasure to `Emoji.Type`.
- **`CustomEmojiImpl.getFormatted` explicitly dispatches the interface default** with `super<CustomEmoji>.getFormatted()`. Kotlin otherwise emits a non-`default` method body, which would change the class shape relative to Java.
- **`StickerItemImpl`/`RichStickerImpl` keep protected mutable fields** (`name`/`tags`/`description`) via `@JvmField`, since the Kotlin subclasses and retained Java callers both read them directly.
- **`GuildStickerImpl.checkCreateOrManagePermissions(Guild)` stays a public static** through a companion `@JvmStatic`; the retained Java `GuildStickerManagerImpl` calls it as a class-level static.
- **`DetachedGuildImpl`'s ~145 throwing overrides were generated from the Java signatures.** The converter preserves each return type and nullability, then the JVM-facing details are corrected by hand: nested types are qualified as `Guild.Ban`/`Guild.MetaData`/`Guild.MFALevel`/…, `MemberFlag` as `Member.MemberFlag` for `DetachedMemberImpl`, primitives map to `Boolean`/`Int`/`Long`/`Unit`, and the parameter nullability of `createTemplate`, `moveVoiceMember`, `modifyNickname`, `ban`, `modifyMemberRoles`, and the `create*Channel` overloads matches the API interface exactly.
- **`DetachedGuildImpl(JDAImpl, long)` keeps the Java argument order.** `InteractionEntityBuilder` constructs it positionally as `new DetachedGuildImpl(api, guildId)`, so the primary constructor must stay `(JDAImpl, long)` even though Kotlin would prefer the id first.
- **`IDetachableEntityMixin.isDetachedBecauseCachedChannelIsObfuscated` suppresses `ReturnCount`** inline with a reason; the guard-chain control flow is verbatim from Java.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged, `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`. `EntityBuilder`, `InteractionEntityBuilder`, `GuildStickerManagerImpl`, and the emoji/sticker/detached handlers remain Java callers, so Java compilation is an independent cross-check.

### Phase 2 — `internal.entities` leaf types (batch 2)

The remaining self-contained `internal.entities` classes were converted: `ActivityImpl`, `RichPresenceImpl`, `ForumTagImpl`, `SoundboardSoundImpl`, `StageInstanceImpl`, and `EntitlementImpl`. `EntityBuilder` and `AbstractEntityBuilder` remain Java constructors of all six, so Java compilation is again the independent cross-check. `GuildWelcomeScreenImpl` was originally deferred on the belief that its nested `ChannelImpl` is ambiguous inside `EntityBuilder`; that was wrong — the two call sites are already fully qualified (`InviteImpl.ChannelImpl` at `EntityBuilder:2445`, `GuildWelcomeScreenImpl.ChannelImpl` at `:2531`), so it moved in batch 3 instead.

- **`ActivityImpl`'s four constructors have a shifted meaning and need hand-written secondaries.** The Java overloads `(name)` / `(name, url)` / `(name, url, type)` / `(name, state, url, type)` cannot be expressed with `@JvmOverloads` (it would emit two 2-argument constructors for the same signature). The primary is the six-argument form and the four protected secondaries delegate to it explicitly.
- **Nested Java types must be imported explicitly.** `Activity.ActivityType`/`Activity.Timestamps`, `RichPresence.Image`/`RichPresence.Party`, `StageInstance.PrivacyLevel`, and `Entitlement.EntitlementType` are not in scope through the enclosing import; each is imported by its qualified name. `Activity.MAX_ACTIVITY_STATE_LENGTH` is likewise reached as `Activity.MAX_ACTIVITY_STATE_LENGTH` rather than an unqualified constant.
- **Mutable bean fields stay `private var` with default values.** `ForumTagImpl` (`moderated`/`name`/`position`/`emoji`), `StageInstanceImpl` (`topic`/`privacyLevel`, where the interface marks both `@Nonnull`), and `RichPresenceImpl`'s optional images follow the `AutoModRuleImpl` precedent rather than `lateinit`, because tests construct them and read back defaults before the setters run; the properties are only read through the interface's `@Nonnull` accessors, so the field type may be nullable while the getter narrows with `!!`.
- **`RichPresenceImpl` keeps `open` and `@JvmField protected` state.** It is subclassed by nothing currently but mirrors `ActivityImpl`'s shape, and its `largeImage`/`smallImage` are derived in the body from the constructor keys.
- **`SoundboardSoundImpl.checkEditPermissions` reads `guild` after a `Checks.check`.** Kotlin smart-casts the field to non-null once the check guard is written in the same block; the explicit `!!` on the `delete`/`getManager` paths is retained to match the Java nullness contract, and the one in the permission throw was removed after `-Werror` flagged it as unnecessary.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged (901 classes), `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`. `internal.entities` remains outside the ABI baseline and the ArchUnit rules, so the compiler, the formatter, and the suite are the safety net.

### Phase 2 — `internal.entities` leaf types (batch 3)

Five more self-contained implementations: `GuildWelcomeScreenImpl` (with its nested `ChannelImpl`), `PermissionOverrideImpl`, `GuildVoiceStateImpl`, `ScheduledEventImpl`, and `ApplicationInfoImpl` (with nested `InstallParametersImpl`/`IntegrationTypeConfigurationImpl`). All references from retained Java are fully qualified, so Java compilation is the independent cross-check again.

- **`Route.compile` takes route parameters as `String`.** `DELETE_PERM_OVERRIDE`/`DELETE_SCHEDULED_EVENT` template `{...}` segments are filled with `String` args in the Kotlin signature, so `id.toString()` is required where Java's `javac` picked the `String` overload over `Object` (Kotlin defaults a `Long` literal to the looser overload and fails). The same applies to `compile(guild.id, ...)` on the voice-state routes.
- **`getPermissionHolder()` is `@Nullable` in the interface but was declared non-null in the Java impl.** Kotlin refuses to narrow; the override matches the interface with `IPermissionHolder?`. Because `role`/`member` are themselves `Role?`/`Member?`, the result is a genuine nullable.
- **`GuildVoiceStateImpl.getRequestToSpeak()` is a class-only accessor, not an override.** It is absent from the `GuildVoiceState` interface but is called by the retained Java `VoiceStateUpdateHandler`, so it stays a plain `fun` (and must not carry a nullability annotation — it returns a primitive).
- **`GuildVoiceStateImpl`'s cached `guild`/`member` shadow same-named Kotlin property accessors.** Both getters call an interface method named after the field, so the bodies use `getGuild()`/`getMember()` explicitly rather than the property syntax the converter would emit, which would recurse.
- **`ApplicationInfoImpl.setRequiredScopes` preserves the exact join semantics.** `String.join("+", scopes)` becomes `scopes.joinToString("+")`; the `bot`-appending branch and the mutable `scopes` field are byte-for-byte equivalent.
- **Nested `class`es remain nested.** `GuildWelcomeScreenImpl.ChannelImpl` and `ApplicationInfoImpl.InstallParametersImpl`/`IntegrationTypeConfigurationImpl` are `static` in Java and are referenced as `Outer.Inner` from `EntityBuilder` and `GuildWelcomeScreen`, which Kotlin's nested (non-`inner`) classes reproduce, including the package-private constructors and the `InstallParameters` `@Nullable` return.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged (901 classes), `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`. `javap` confirms the public/protected surface of all five classes and their nested types is unchanged.

### Phase 2 — `internal.entities` user hierarchy (batch 4)

`UserImpl` and `SelfUserImpl` converted together, because `SelfUserImpl` extends `UserImpl` and reads its `protected` fields. `UserImpl` stays `open` and `SelfUserImpl` stays `final` (Kotlin's default), matching the Java hierarchy.

- **`protected` state is `@JvmField protected`.** Kotlin's `protected` is subclass-only, whereas Java's is also package-visible. `SelfUserImpl` is the only subclass and reads `verified`/`mfaEnabled`/`applicationId` off `other`, so subclass access is sufficient; `@JvmField` reproduces the Java field instead of routing through synthetic getters. `MemberImpl` does *not* extend `UserImpl` — its `avatarId` is a distinct field, so nothing else reads the `UserImpl` state.
- **`SelfUserImpl.copyOf` is a `companion object` member with `@JvmStatic`.** Java calls `SelfUserImpl.copyOf(...)` from `DefaultShardManager`, so the companion method needs `@JvmStatic` to keep the static call site resolving.
- **A `companion object` constant on `UserImpl` would add public static API.** The legacy modulo-5 default-avatar constant was first written as a `companion object` `const`, which `javap` showed as a new `public static final int LEGACY_DEFAULT_AVATAR_COUNT` on the class. Moving it to a private top-level `const` puts it on the synthetic `UserImplKt` class instead, leaving the `UserImpl` surface unchanged.
- **Explicit `open` on `UserImpl.getPrivateChannel`.** The Java method is overridden by `SelfUserImpl` (to throw), so the Kotlin `fun` must be `open`; Kotlin members are final by default.
- **`Helpers.format` returns a `String` with a `short` vararg arg.** `discriminator.toInt()` is used at the call site because the Java `%04d` formatting accepts the boxed `Short` and Kotlin's `format(vararg Any?)` is stricter than the original implicit widening.
- **`getName()` (interface) is called where Java read the `name` field.** `EntityString.setName` requires a non-null `String`, and only the interface getter carries the `@Nonnull` contract that the field's genuine nullness lacks.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged (901 classes), `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`. `javap` confirms `UserImpl`'s field/getter/setter surface and `SelfUserImpl`'s static `copyOf` are unchanged.

### Phase 2 — `internal.entities` mentions hierarchy (batch 5)

`AbstractMentions`, `MessageMentionsImpl`, and `SelectMenuMentions` converted together. `InteractionMentions` was already Kotlin and subclassed `AbstractMentions`, so the abstract methods were already `open` and nullable — this batch only had to convert the Java side.

- **`processMentions` takes a collection factory instead of a `Collector`.** `Helpers.toUnmodifiableList()` is typed `Collector<T, *, List<T>>`; Kotlin cannot bind the star-projected accumulator, so every call failed to infer `A`. Passing `() -> C` (where `C : MutableCollection<T>`) and adding elements directly keeps inference working, with `Collections.unmodifiableList(...)` wrapping at the list call sites for the same runtime type as before.
- **`protected` fields stay `@JvmField protected`.** The `protected static toBag()/toMultiSet()` helpers are dropped in favor of inline factories; the class is sealed to its own hierarchy and the helpers only existed for the Java call sites being converted here, so no public or protected static API was removed that any subclass could observe. `getGuild$annotations()` appears as a synthetic static for the `@Nullable` on a `@JvmField protected val` — the only protected statics left are the compiler's nullability annotations.
- **The legacy `Bag`-returning methods are kept and marked `@Deprecated("")`.** They are deprecated in the public `Mentions` interface but must remain implemented; `@file:Suppress("DEPRECATION")` covers the `HashBag`/`BagUtils` usage rather than weakening the methods.
- **`ReturnCount` is suppressed inline with a reason** on the guard-heavy `getMembers`/`getRoles`/`getChannels`/`isMentioned`/`matchUser` methods, matching the established policy for faithfully-ported control flow.
- **`matchSlashCommand` group indices are named constants** (`SLASH_COMMAND_*_GROUP`) so detekt's `MagicNumber` does not fire on `matcher.group(2)`.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged (901 classes), `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`. `javap` confirms the public surface of all four classes is unchanged. Kotlin emits `List<? extends T>` for the `protected` cache fields (`mentionedUsers` etc.); these are internal, non-API, and only ever written and read through the class hierarchy, so no Java consumer can observe the difference.

### Phase 2 — `internal.entities` webhooks and widget (batch 6)

`AbstractWebhookClient`, `WebhookImpl`, and `WidgetImpl` (with its nested `MemberImpl`/`VoiceChannelImpl`/`VoiceStateImpl`). Remaining `internal.entities` Java is now `AbstractEntityBuilder`/`EntityBuilder`/`InteractionEntityBuilder`, `ReceivedMessage`, `GuildImpl`, `MemberImpl`, `RoleImpl`, and `InviteImpl`.

- **`getToken()` is `@Nullable` in the interface, so the impl returns `String?`.** The Java override was unannotated; Kotlin must follow the interface's JSR-305 `@Nullable`. `AbstractWebhookClient` therefore only *declares* the member and importantly makes `getJDA()` concrete, because Kotlin does not inherit a Java interface's `default` body into an override when the superclass also inherits it abstractly — the Java impl had `getJDA()` return `api` directly and that must stay.
- **`protected` fields are `@JvmField protected`** so the retained Java `IncomingWebhookClientImpl` subclass reads/writes `id`, `token`, and `api` as fields. `token` is a `protected var`, the rest `protected val`.
- **`WebhookImpl` is `open`, not `final`.** The Java class was non-final; this also satisfies detekt's `ProtectedMemberInFinalClass` style without narrowing.
- **Nested types are imported by qualified name.** `ChannelReference`/`GuildReference` are `Webhook.ChannelReference`/`Webhook.GuildReference`, and `Widget.Member`/`Widget.VoiceState` do not come into scope from the enclosing-type import.
- **`WidgetImpl.MemberImpl.hashCode` uses `widget.getId()`.** Kotlin has no `Long + String` overload: `' '` is a `Char`, so `widget.getId() + ' ' + id` compiles as character arithmetic, while `widget.id + ' ' + id` treats `' '` as a `Char` and fails to resolve. The explicit `getId()` keeps Java's string-concatenation semantics.
- **`WidgetImpl.MemberImpl.getEffectiveAvatarUrl(format)` returning the avatar hash (`avatar`), not its URL, is preserved** with a comment. It is an upstream quirk, not a conversion artifact, and must not be "fixed".
- **Nested-class helper methods (`setVoiceState`, `addMember`) are `internal`** so the outer class can reach them; Kotlin mangles the JVM names, but these were `private` in Java, so nothing observable widens on the public API.
- **The legacy modulo-5 default-avatar constant is a private top-level `const`.** As a `companion object` `const` it leaked onto `WidgetImpl` as a new `public static final int`; at file scope it lands privately on the synthetic `WidgetImplKt`.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged, `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, `detekt`. `javap` confirms `AbstractWebhookClient`'s protected fields and the full `sendMessage`/`editMessage`/`deleteMessageById` bridge set, `WebhookImpl`'s two constructors and all setters, and the four `WidgetImpl` nested classes' constructor/accessor surfaces are unchanged; `WidgetImpl` has no new public static.

### Phase 2 — `internal.entities.InviteImpl` (batch 7)

`InviteImpl` and its five nested POJO types (`ChannelImpl`, `GuildImpl`, `GroupImpl`, `InviteTargetImpl`, `EmbeddedApplicationImpl`). Remaining `internal.entities` Java: `AbstractEntityBuilder`/`EntityBuilder`/`InteractionEntityBuilder`, `ReceivedMessage`, `GuildImpl`, `MemberImpl`, `RoleImpl`.

- **The nested getters follow the *interface's* JSR-305 annotations, not the Java impl's.** Kotlin enforces nullability strictly where the Java impl could contradict its interface: `Group.getName()` is `@Nonnull` in `Invite.Group` although the Java `GroupImpl.getName` was unannotated (the builder always passes a possibly-empty, non-null name), and `Channel.getName()` is `@Nonnull` while the Java impl was unannotated. `GroupImpl.getIconId`/`getUsers` stay `@Nullable` because the builder really does pass `null` for a missing `recipients` array.
- **`ChannelImpl.getName`'s field is non-null**, which lets `EntityString.setName` (whose parameter is `@Nonnull`) be called without an assertion.
- **`InviteImpl.resolve` is a companion `@JvmStatic` member**, preserving the static call from the retained Java `EntityBuilder`/`Invite.resolve` path.
- **`ThrowsCount` and `ReturnCount` are suppressed inline with a reason** on `expand` (four precondition throws) and `getTargetEntity` (two guard returns), matching the established policy for faithfully-ported control flow.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged, `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, `detekt`. `javap` confirms the 15-argument constructor, all fields, `resolve`, and the five nested types' constructors and accessors are unchanged; the only additions are the synthetic `$Companion` field and lambda methods on the internal class.

### Phase 2 — `internal.entities.RoleImpl` (batch 8)

`RoleImpl` and its nested `RoleTagsImpl` (including the `RoleTagsImpl.EMPTY` constant, which the already-Kotlin `DetachedRoleImpl` imports). Remaining `internal.entities` Java: `AbstractEntityBuilder`/`EntityBuilder`/`InteractionEntityBuilder`, `ReceivedMessage`, `GuildImpl`, `MemberImpl`.

- **`RoleTagsImpl.EMPTY` is a `companion object` `@JvmField`.** As a plain `companion` property it becomes `getEMPTY()` and breaks the retained Kotlin `DetachedRoleImpl.EMPTY` reference and the JVM field `RoleImpl$RoleTagsImpl.EMPTY`; `@JvmField` (not `@JvmStatic`) is what keeps the field on the class. The declared type is the interface `RoleTags`, matching the Java `public static final RoleTags EMPTY`.
- **`RoleTags` is `Role.RoleTags`.** There is no top-level `net.dv8tion.jda.api.entities.RoleTags`; importing the bare name fails.
- **The `getPosition` exception message is assembled with `+`-prefixed continuation lines only where required.** A stray `+"..."` after a `+ "- ..."` line parses as unary plus, and the compiler reports an unresolved `unaryPlus` on `String`.
- **`ThrowsCount` and `ReturnCount` are suppressed inline with a reason** on `getPosition` and `delete` (precondition throws) and `canSync` (early returns), matching the established policy.
- **`name` is a nullable field with a non-null getter** (`name as String`), the same pattern as `DetachedRoleImpl`: the builder always sets it before publication, but the field must start null.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged, `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, `detekt`. `javap` confirms `getPosition`/`canSync`/`delete`, the fluent setters and `freezePosition`, the two `RoleTagsImpl` constructors, and the `EMPTY` static field are unchanged.

### Phase 2 — `internal.entities.MemberImpl` (batch 9)

`MemberImpl`, with `SelfMemberImpl` (already Kotlin) as its subclass. Remaining `internal.entities` Java: `AbstractEntityBuilder`/`EntityBuilder`/`InteractionEntityBuilder`, `ReceivedMessage`, `GuildImpl`.

- **The class is `open`, not final**, because `SelfMemberImpl : MemberImpl` already exists. This is the same rule as `WidgetImpl`/`WebhookImpl`, but here the subclass is real rather than defensive.
- **`MemberPresenceImpl` was already Kotlin and its properties are private.** The Java implementation reached `presence.getActivities()`, `presence.getOnlineStatus()`, and `presence.getClientStatus()`; from Kotlin, property syntax (`presence.activities`) resolves to the *private* backing field and fails, so the accessor methods are called explicitly.
- **`PermissionUtil.checkPermission` takes `vararg`, so call sites need spread.** The Kotlin `MemberImpl.hasPermission` forwards its own `vararg permissions` with `*permissions`; passing the array directly does not resolve.
- **`getPresence()` returns `MemberPresenceImpl?`** and `getVoiceState()` returns `GuildVoiceStateImpl?`, when the `Member` interface declares `@Nullable GuildVoiceState`; widening the return type is allowed and matches the Java covariant override.
- **`ThrowsCount`/`ReturnCount` are suppressed inline with a reason** on `canSync` (early returns), matching the established policy.
- **`getRoleSet()` returns `MutableSet<Role>`** (the live `roles` set), preserving the Java `Set<Role>` return used by the builders and handlers.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged, `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, `detekt`. `javap` confirms the constructor, the covariant `getGuild`/`getVoiceState` returns, all fluent setters, `getRoleSet`/`getBoostDateRaw`/`getTimeOutEndRaw`, and `equals`/`hashCode` are unchanged.

### Phase 2 — `internal.entities.ReceivedMessage` (batch 10)

`ReceivedMessage`, the largest `Message` implementation and the base of the `internal.entities` hierarchy used by `EntityBuilder`.

- **`@JvmField` is required on the protected fields.** A Kotlin `protected val id` would emit a `getId()` accessor that collides with the `Message`/`ISnowflake` defaults, and a plain property would change the field's JVM shape. `@JvmField` keeps every field exactly as Java declared it (including the mutable `webhook`/`altContent`/`strippedContent`/`invites`).
- **`didContentIntentWarning` is a companion `@JvmField`**, so the public static field that `Message.suppressContentIntentWarning()` writes to is preserved.
- **`Message.Attachment` is a nested type of `Message`**, not a top-level `entities.Attachment`.
- **`String.replaceAll` is not callable from Kotlin** on a `String` receiver; the regex replacement in `getContentDisplay` uses `Pattern.compile(...).matcher(tmp).replaceAll(...)`.
- **Deprecation diagnostics are suppressed explicitly.** The deprecated `Message.Interaction` in the constructor and the overriding `getInteraction()` need `@Suppress("DEPRECATION")` / `"OVERRIDE_DEPRECATION"`, and the class carries a file-level `@Suppress("DEPRECATION")` for the constructor parameter type.
- **`ThrowsCount`/`ReturnCount`/`ComplexCondition` are handled inline**: the multi-throw validators (`delete`, `suppressEmbeds`, `crosspost`) carry a reason comment, the early-return caches (`getContentStripped`, `getContentDisplay`, `getInvites`, `removeReaction`, `crosspost`) carry `ReturnCount`, and `checkIntent` extracts its boolean condition into a named local. `precision - 3` became a named `ELLIPSIS_LENGTH` constant rather than a suppression.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged, `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, `detekt`. `javap` confirms every protected field (same names, types, and `protected final`/`protected` modifiers), the `public static boolean didContentIntentWarning` field, the full-arity constructor, `withHook`, and the complete `Message`/`Formattable` method surface are unchanged.

### Phase 2 — `internal.entities.AbstractEntityBuilder` (batch 11)

`AbstractEntityBuilder`, the shared base of `EntityBuilder` and `InteractionEntityBuilder`, holding the `configure*` helpers that populate channels, members, and roles.

- **The class stays `abstract class`, not `object`**: it has two subclasses, so it is declared `open` via `abstract`.
- **The constructor is `protected constructor`** and `api` is a `@JvmField`, matching the Java `protected final JDAImpl api`. A plain `protected val` would have emitted a getter and changed the field's shape.
- **Every `protected` member is `open`.** Kotlin methods are final by default; leaving them final would have narrowed the inherited API available to `EntityBuilder`/`InteractionEntityBuilder` and flipped the class-file flags. This was caught by comparing `javap` output against the Java original, not by the compiler, because `internal` is outside the ABI gate.
- **`getJDA()` is `open`** for the same reason; it is an override of `AbstractEntityBuilder`'s own method surface used by subclasses.
- **`createRoleColors` moves to the companion with `@JvmStatic`**, preserving the Java static entry point.
- **Two latent nullability regressions from earlier batches were fixed here**, because this class is the first caller to exercise the null arguments:
  - `RoleMixin.setIcon`/`RoleImpl.setIcon`/`DetachedRoleImpl.setIcon` now take `RoleIcon?`. `configureRole` passes `null` when a role has neither an icon nor a unicode emoji — legal in the Java source (`role.setIcon(null)`), but a non-null Kotlin parameter inserted an `Intrinsics` check.
  - `MemberMixin.setNickname`/`setAvatarId`/`setBannerId` (and the `MemberImpl`/`DetachedMemberImpl` overrides) now take `String?`. `configureMember` passes `DataObject.getString(key, null)`, which is null for absent keys; the non-null parameter made `createMessageForUserAfterBan` fail with `NullPointerException: getString(...) must not be null`. This is exactly the failure mode §10 warns about, caught by a real test.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged, `verifyBytecodeVersion` (major 69), `spotlessApply`/`rewriteDryRun`, `detekt`. `javap` confirms the `protected final JDAImpl api` field, the `protected` constructor, and every `configure*`/`createForumTag`/`getJDA`/`createRoleColors` signature (visibility and non-final flags) match the Java original.

### Phase 2 — `internal.entities.InteractionEntityBuilder` (batch 12)

`InteractionEntityBuilder`, the `AbstractEntityBuilder` subclass that resolves interaction entities, falling back to detached implementations when the entity is not cached or the guild is detached.

- **Every public method is `final`**, matching the Java class: it is `public final class` with no subclass, so Kotlin's default finality is correct here (unlike its parent).
- **`createGroupChannel` returned `GroupChannelMixin` in my first draft; the Java signature is `GroupChannel`.** Caught by checking `javap` against the original — a good reminder that the mixin types are the concrete implementations, not the declared return types.
- **`createPrivateChannel` returns `PrivateChannel`**, not `PrivateChannelMixin`; the local is typed to the concrete detached impl only where it is constructed.
- **`member.interactionPermissions = ...` does not compile** because the backing property is private; the Java setter `member.setInteractionPermissions(...)` is used instead.
- **`DataObject.isEmpty` is a property in Kotlin**, so the recipient filter is `.filter { d -> !d.isEmpty }`.
- **`@Suppress("ReturnCount")` on `createThreadChannel`** with a reason comment: the port keeps the Java early-return branch that delegates to `EntityBuilder.createThreadChannel`, rather than restructuring the control flow.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged, `verifyBytecodeVersion` (major 69), `spotlessApply`/`rewriteDryRun`, `detekt`. `javap` confirms the class is `public final`, the three-argument constructor, and every `create*`/`getOrCreateGuild` return type and `final` flag match the Java original.

### Phase 2 — `internal.entities.channel.mixin.attribute` (batch 13)

The eleven attribute mixin interfaces, the first slice of the `channel` package. They are the `default`-method carriers behind every guild channel type.

- **Getters that convert to Kotlin properties must remain functions when they override a Java method.** `IPermissionContainerMixin.getPermissionOverrideMap()` and `IInteractionPermissionMixin.getInteractionPermissions()` become `val`s: Kotlin callers (converted in earlier batches) already use property syntax, and a `val` still emits the original `getXxx()` JVM method, so Java implementors and callers are unaffected. `IPostContainerMixin.getAvailableTagCache()` is **kept as a function** — it overrides the API method `IPostContainer.getAvailableTagCache()`, and a Kotlin `val` does not satisfy a Java method override, so the property form failed to compile (`'availableTagCache' overrides nothing`).
- **`@Suppress("TooGenericExceptionCaught")` on `retrieveWebhooks`** with a reason comment: the Java original catches `UncheckedIOException | NullPointerException`, which Kotlin cannot express as one multi-catch, so it is two `catch` blocks.
- **Magic `100` becomes `Channel.MAX_NAME_LENGTH`** rather than a detekt suppression, matching the value the API constant documents.
- **`ICategorizableChannelMixin.isSynced` needs `@Suppress("ReturnCount")`**, and its loop variable was renamed from `override` (a reserved modifier, which made the `for` header unparsable) to `parentOverride`.
- **`getPermissionOverrides` uses `java.util.Arrays.asList(*array)`**, not `array.asList()`, to keep the Java list semantics (fixed-size, mutable-through) instead of a Kotlin immutable copy.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged, `verifyBytecodeVersion` (major 69), `spotlessApply`/`rewriteDryRun`, `detekt`. `javap` confirms every `default` method body compiled to a real `default` method (not a `DefaultImpls`-only shape) and that all abstract setters/getters and generic bounds are identical to the Java interfaces.

### Phase 2 — `internal.entities.channel.mixin.concrete` (batch 14)

The ten concrete channel mixins (`Category`, `ForumChannel`, `MediaChannel`, `NewsChannel`, `GroupChannel`, `PrivateChannel`, `TextChannel`, `StageChannel`, `ThreadChannel`, `VoiceChannel`). These carry the `createCopy` default bodies and the per-type setters.

- **`rawSortOrder` is a `val`, not a function.** `IPostContainerMixin.getRawSortOrder(): Int` is JDA-internal with no API supertype, so the Kotlin property form is preferred (Kotlin callers use property syntax) and still emits `getRawSortOrder()`.
- **`getRawSortOrder()` is not a Java-method override**, unlike `getAvailableTagCache()` in batch 13 — the distinction is whether an API interface declares the getter. This is the rule that decides property-vs-function for every converted getter.
- **`SortOrder` must be qualified as `IPostContainer.SortOrder`**, because `SortOrder` is a nested type of the API interface, not a top-level type.
- **`ThreadChannel.AutoArchiveDuration` is nested in `ThreadChannel`**, matching the existing `AbstractEntityBuilder` usage.
- **`sendSoundboardSound` needs `@Suppress("ThrowsCount")`** with a reason comment: it keeps the Java method's four guard `throw`s rather than restructuring them.
- **`getName`/`retrieveUser` use `val user = user`** in `PrivateChannelMixin` to capture the nullable property once for the null check, mirroring the Java local.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged, `verifyBytecodeVersion` (major 69), `spotlessApply`/`rewriteDryRun`, `detekt`. `javap` confirms every `createCopy`/`canTalk`/`sendSoundboardSound`/`retrieveUser` body is a real `default` method and every abstract setter is unchanged; the only additions are Kotlin's synthetic `access$…$jd` default-compatibility bridges.

### Phase 2 — `internal.entities.channel.mixin` and `mixin.middleman` (batch 15)

The root `ChannelMixin` and the seven middleman mixins (`StandardGuildChannel`, `AudioChannel`, `GuildChannel`, `GuildMessageChannel`, `StandardGuildMessageChannel`, `MessageChannel`). These carry the permission-check hooks and most of the channel default bodies, so they are the hinge the concrete impls (next batches) build on.

- **`super<MessageChannelUnion>.method(...)` is the tool for the Java `MessageChannelUnion.super.method(...)` calls.** Kotlin cannot write `Interface.super.x()` for a non-direct supertype in this shape; the qualified `super<MessageChannelUnion>` spelling is the equivalent and produces the same `invokespecial` to the union default.
- **`getHistory()`/`getIterableHistory()` call `super<MessageChannelUnion>.history` / `.iterableHistory`** — Java called the interface methods `getHistory()`/`getIterableHistory()`, which Kotlin exposes as properties on the union; both still compile to the same JVM calls.
- **The vararg overrides keep the exact array shape.** `purgeMessagesById(vararg messageIds: Long)` and `sendMessageEmbeds(embed, vararg other)` preserve `long...` / `MessageEmbed...` in the descriptor; the forwarding call uses the spread operator `*other`.
- **`GuildChannelMixin.delete()` is the covariant bridge case.** `ChannelMixin.delete()` returns `RestAction<Void>` while `GuildChannelMixin.delete()` returns `AuditableRestAction<Void>`; Kotlin emits both the override and the synthetic bridge, matching the Java `javap` surface.
- **`@CheckReturnValue` and `@Nonnull` are carried on every override** so the ArchUnit contract is unchanged on the `api`-adjacent unions.
- **detekt findings handled inline**: `ReturnCount` on `purgeMessagesById`, and the two `100` magic numbers became private top-level `const val`s (`BULK_DELETE_CHUNK_SIZE`, `MAX_BULK_DELETE_IDS`, `MIN_BULK_DELETE_IDS`) rather than suppressions.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged, `verifyBytecodeVersion` (major 69), `spotlessApply`/`rewriteDryRun`, `detekt`. `javap` confirms every default body, the covariant `delete` bridge, the vararg descriptors, and all eight abstract hook signatures match the Java interfaces.

### Phase 2 — `internal.entities.channel` base impls (batch 16)

The four abstract channel base classes: `AbstractChannelImpl`, `AbstractGuildChannelImpl`, `AbstractStandardGuildChannelImpl`, `AbstractStandardGuildMessageChannelImpl`.

- **Protected mutable state is `@JvmField protected var`**, and the protected constructor parameters are copied into `@JvmField protected val` fields, preserving the Java field shape (`protected final long id`, `protected final JDAImpl api`, `protected String name`, `protected long parentCategoryId`, …). A plain `var` would have emitted a getter/setter pair and changed the layout.
- **`getName()` uses `name as String`**, matching the existing `ReceivedMessage`/`RoleImpl` convention for a nullable backing field with a non-null getter.
- **Kotlin emits default-method bridges for interface defaults (`checkCanAccess`, `checkCanManage`, `detachedException`).** Java subclasses that override those methods then trigger `-Xlint:overrides` (`overrides … ; overridden method is a bridge method`), which is fatal under `-Werror`. There is no `@SuppressWarnings` key that silences it (both `"overrides"` and `"all"` were tried), and the warning is not in scope for `-Xlint:-removal`-style global suppressions without weakening `-Xlint:all`. The fix is to declare **explicit overrides in the Kotlin base** (`override fun checkCanAccess() { super.checkCanAccess() }`), which makes Kotlin emit a real override with no bridge, so the Java subclass sees a normal virtual method. This is a migration-wide hazard: any Kotlin class exposing an interface default that a Java subclass overrides needs the explicit override.
- **`permissionOverrideMap` is a `val` with `get() = overrides`**, not an initialised property, so no second field is created next to the Java-shaped `overrides` field; the getter returns the same map instance.
- **`onPositionChange()` stays non-open `protected`**, matching the Java `protected final void`.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged, `verifyBytecodeVersion` (major 69), `spotlessApply`/`rewriteDryRun`, `detekt`. `javap` confirms the protected field names/types/modifiers, the constructors, `getGuild`/`compareTo`, the covariant `delete` bridge, and every `set*` return type match the Java classes; the only additions are the Kotlin generic bridges (`setFlags(int)GuildChannelMixin`, `delete()RestAction`).

### Phase 2 — `internal.entities.channel.concrete` non-thread impls (batch 17)

Eight concrete channel impls: `CategoryImpl`, `PrivateChannelImpl`, `TextChannelImpl`, `NewsChannelImpl`, `VoiceChannelImpl`, `StageChannelImpl`, `ForumChannelImpl`, `MediaChannelImpl` (the thread impls are left for the next batch).

- **`super<PrivateChannelMixin>.getName()` mirrors `PrivateChannelMixin.super.getName()`**, and `super<VoiceChannelMixin>.checkCanAccess()` / `super<StageChannelMixin>.checkCanAccess()` disambiguate the two inherited `checkCanAccess` defaults (`AudioChannelMixin` adds the VOICE_CONNECT check). `super.checkCanAccess()` alone does not compile when multiple supertypes supply an implementation.
- **`voiceState!!.channel`** is needed because `selfMember.voiceState` is nullable in the Kotlin view of `Member`; the original Java dereferenced it unguarded, so `!!` preserves the same NPE behaviour.
- **`Route.…compile(getId())`** keeps the routed string form; `compile(id)` (Long) is a different descriptor.
- **`ForumChannelImpl`/`MediaChannelImpl` keep their own `overrides`/`tagCache`/state fields** (they extend `AbstractGuildChannelImpl`, not the standard variant) and use the `@JvmField protected var defaultThreadSlowmode` field shape; `getRawLayout()` stays a plain method, `rawSortOrder` is the `IPostContainerMixin` `val`.
- **detekt handled inline**: `EmptyFunctionBlock` on the eight no-op `PrivateChannelImpl` permission hooks, and `ProtectedMemberInFinalClass` on `defaultThreadSlowmode` in the two final classes (kept protected for field-shape parity).
- **`@Nullable` on the two `getDefaultReaction()` overrides** documents the nullable backing field.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged, `verifyBytecodeVersion` (major 69), `spotlessApply`/`rewriteDryRun`, `detekt`. `javap` confirms `getName`/`equals`/`hashCode`/`canTalk`/`checkCanAccess`, `getPermissionOverrideMap`, the covariant `setPosition`/`setRegion`/`setUserLimit` returns and the `createCopy(Guild)` overloads.

### Phase 2 — `internal.entities.channel.concrete.detached` impls (batch 18)

Nine detached channel impls: `DetachedCategoryImpl`, `DetachedTextChannelImpl`, `DetachedNewsChannelImpl`, `DetachedGroupChannelImpl`, `DetachedPrivateChannelImpl`, `DetachedVoiceChannelImpl`, `DetachedStageChannelImpl`, `DetachedForumChannelImpl`, `DetachedMediaChannelImpl`. `DetachedThreadChannelImpl` is left with `ThreadChannelImpl` for the next batch.

- **`IInteractionPermissionMixin.interactionPermissions` is a `val`, and its backing field must not share the name.** A `private var interactionPermissions` plus `override val interactionPermissions` would collide, so the backing field is named `interactionPermissionsValue` and the override is `get() = interactionPermissionsValue!!` (matching the Java field-then-getter shape with `@Nonnull`).
- **`override val permissionOverrideMap: TLongObjectMap<…> get() = throw detachedException()`** models the Java `getPermissionOverrideMap()` that throws; the property form has the same descriptor.
- **Methods that only throw `detachedException()` are single-expression `= throw detachedException()`** (category creators, `follow`, `getManager`, `requestToSpeak`, `cancelRequestToSpeak`, `getStageInstance`, `modifyStatus`, `getMembers`).
- **`DetachedVoiceChannelImpl`/`DetachedStageChannelImpl` need an explicit `checkCanAccess()`** for the same two-default-supertype reason as the live voice/stage impls; here it throws the detached exception.
- **`DetachedPrivateChannelImpl` keeps its `@Nullable private val user`** and `super<PrivateChannelMixin>.getName()`; `DetachedGroupChannelImpl` keeps the full `MessageChannelMixin` no-op/throw hook set.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged, `verifyBytecodeVersion` (major 69), `spotlessApply`/`rewriteDryRun`, `detekt`. `javap` confirms `getInteractionPermissions`/`setInteractionPermissions` (with the mixin bridge), `getPermissionOverrideMap`, `getName`, and the covariant `setRegion`/`setUserLimit`.

### Phase 2 — `internal.entities.channel.concrete` thread impls (batch 19)

`ThreadChannelImpl` and `detached/DetachedThreadChannelImpl` complete the `concrete` package.

- **`private val type: ChannelType`, `private val threadMembers`, `private var parentChannel: IThreadContainerUnion?`** mirror the Java fields; `getParentChannel()` does `parentChannel!!.idLong` / `return parentChannel!!` where Java dereferenced the field unguarded (same NPE if unset).
- **`getAutoArchiveDuration()` is `= autoArchiveDuration!!`** because the field is nullable but the API contract is `@Nonnull`; the unset case was already an NPE in Java.
- **`getRawFlags()` reads the inherited `@JvmField protected var flags`** from `AbstractGuildChannelImpl`; `java.lang.Long.toUnsignedString(id)` keeps the exact static call.
- **`DeferredRestAction` uses a secondary-lambda form**; `RestActionImpl(jda, route) { resp, _ -> … }` preserves the original handler.
- **`setAppliedTags(LongStream)` keeps the `forEach` loop** (`tags.forEach { set.add(it) }`) rather than a method reference, to stay close to the Java.
- **`ThreadChannelImpl.checkCanManage()` is a real override** (the old Java comment about the Kotlin bridge is gone); `DetachedThreadChannelImpl` keeps it throwing `detachedException()` and uses the `interactionPermissionsValue` backing-field pattern.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged, `verifyBytecodeVersion` (major 69), `spotlessApply`/`rewriteDryRun`, `detekt`. `javap` confirms `getRawFlags`, `getThreadMemberView`, `setParentChannel`, `setAppliedTags(LongStream)`, `getArchiveTimestamp`, `getAppliedTagsSet` and `getAutoArchiveDuration`.

### Phase 2 — `internal.handle` create/update/delete handlers (batch 20)

The bulk of the handler package: 34 handlers covering application-command permissions, AutoMod, entitlements, audit-log entries, bans, guild create/sync, member add/update, role create/update, soundboard sounds, invites, message delete, reactions, ready, scheduled events, stage instances, threads, user update, and voice-channel status.

- **The guard-chain pattern is uniform:** each handler returns `null` on the first unsatisfied precondition. Java wrote these as sequential `if (...) return null;` statements; Kotlin keeps that exact control flow, and the 26 occurrences carry `@Suppress("ReturnCount")` with the reason "faithfully ported early-return guard chain from the Java original", per the inline-suppression policy for non-restructurable ported control flow.
- **`handleInternally` call sites take `content` as `DataObject`**, matching the base's abstract signature.
- **Handlers that previously extended `SocketHandler` through an intermediate Kotlin class keep the same superclass chain**; only the Java→Kotlin language changed, not the hierarchy.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged, `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`.

### Phase 2 — `internal.handle` message and guild handlers (batch 21)

Sixteen more handlers: channel create/delete, guild delete, emoji/sticker updates, member remove/chunk, role delete, message bulk delete/create/poll-vote/update, scheduled-event update, thread delete/update, and typing start.

- **The same `handleInternally(content): Long?` guard-chain port as batch 20**, with 16 `@Suppress("ReturnCount")` entries carrying the shared reason. `ThreadUpdateHandler` additionally carries `@Suppress("DEPRECATION", "ReturnCount")` because it fires the legacy thread events the Java original still dispatched.
- **`MessageCreateHandler` / `MessageUpdateHandler` keep the message-cache insertion and the "drop if ephemeral/unexpected type" early returns** verbatim; the ephemeral-message drop is preserved with its original comment.
- **`GuildStickersUpdateHandler` / `GuildEmojisUpdateHandler` keep the "cleanup old, then add new" two-pass loops** rather than restructuring into set operations, so the observable event ordering is unchanged.
- **Two adjacent Kotlin accessors were widened to the Java call sites converted here:** `RichCustomEmojiImpl.getRoleSet()` now returns `MutableSet<Role>` (was `Set<Role>`) and `GuildVoiceStateImpl.updateConnectedChannel` now takes `AudioChannel?` (was non-null). Both keep the same JVM descriptor/behaviour; they are `internal`, so no API change.
- **No public API is touched** — every converted type is `internal` and outside `apiCheck`/`ArchUnitComplianceTest`.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged, `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`.

### Phase 2 — `internal.handle` core and update handlers (batch 22)

The batch converts the `SocketHandler` base plus the remaining non-enum handlers: `ThreadMemberUpdateHandler`, `ThreadMembersUpdateHandler`, `VoiceChannelEffectSendHandler`, `VoiceServerUpdateHandler`, `InteractionCreateHandler`, `MessageReactionHandler`, `PresenceUpdateHandler`, `ChannelUpdateHandler`, `GuildUpdateHandler`, and `VoiceStateUpdateHandler`.

- **`SocketHandler`'s protected fields keep their exact JVM field shape.** The Java `protected final JDAImpl api` and `protected long responseNumber` are reproduced as `@JvmField protected val api` and `@JvmField protected var responseNumber` (a plain Kotlin property would emit a private field plus `getApi()`/`getResponseNumber()` accessors). The third field, `protected DataObject allContent`, is a computed non-null property over a private nullable `currentContent`: `handle()` assigns it before dispatch and releases it to `null` afterwards, and the non-null getter preserves the Java reads without forcing `!!` onto every handler call site. `CURRENT_EVENT` stays a real `public static final ThreadLocal` via `@JvmField` in the companion. All non-enum handlers were converted in the same batch, so no Java subclass reads these through a Kotlin getter.
- **`ChannelUpdateHandler.ObfuscationAwareUpdater` members the outer class calls are `internal`, not `private`.** Kotlin inner classes cannot see each other's `private` members, so `handleFlagsUpdate`, `handleTopic`, `handleSlowmode`, `handleNsfw`, `handleParentCategory`, `handlePosition`, `handleThreadContainer`, `handleAudioChannel`, `handlePostContainer`, `applyPermissions`, and `handleHideChildThreads` are `internal fun`. This is module-scoped, the same widening already accepted for other `internal` members.
- **The anonymous permission-override removal loop became a materialised copy.** Java used Trove's `forEachValue` with an early-`true` contract; Kotlin replaces it with `currentOverrides.valueCollection().toMutableList()` and a `for` loop, preserving the removal-driven iteration safely.
- **`threadView.remove(...)` needs an explicit type argument.** Kotlin cannot infer the generic `C` for the erased Java overload, so the call site writes `remove<Channel>(thread.getType(), thread.getIdLong())`; `guildThreadView.remove(thread)` infers from its argument.
- **Mixin property accessors replace Java getters on converted mixins** (`permissionOverrideMap`, `rawSortOrder`, `latestMessageIdLong`); the `ChatChannelMixin`/`MessageChannelMixin` cast is re-established at the `setLatestMessageIdLong` call site because `AbstractGuildChannelImpl` does not itself declare it.
- **`MessageReactionHandler` keeps the two-argument constructor** `(api, add)` and the nullable `List<Role?>` returned by the filtered role lookup, matching `EntityBuilder.updateMember`'s Java signature.
- **`PresenceUpdateHandler.parseActivities` catches broad `Exception`** and carries `@Suppress("TooGenericExceptionCaught")` with a written reason: the Java original deliberately logged and skipped any parse failure while still marking the activity list parsed.
- **`VoiceStateUpdateHandler` guards a nullable session id** before `setSessionId`, since `GuildVoiceStateImpl.setSessionId` is non-null in Kotlin; the Java original called it unguarded and only NPE'd if `session_id` was absent.
- **detekt handled inline**: the presence-parse catch and the ported `ChannelUpdateHandler` branch described above, both with reason comments.

`EventCache` (nested `Type` enum), `GuildSetupController` (nested `Status` enum plus `StatusListener`), and `GuildSetupNode` (nested `Type` enum) converted in batch 38.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged (`api/` has no diff), `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`. `test --rerun-tasks` confirms the forced run (506 tests, 0 failures).

### Phase 2 — `internal.handle` guild setup (batch 38)

`EventCache`, `GuildSetupController`, and `GuildSetupNode` — the last Java in `internal.handle` — are now Kotlin, so the package has no Java left. They are interlocked (`GuildSetupNode` takes a `GuildSetupController` and calls back into it), so all three converted together.

- **The nested `Type`/`Status` enums convert with the class.** They are `internal`-scoped, so the `getEntries()` leak is out of the gates' reach (batch 37).
- **`internal` members mangle, but every caller is now Kotlin in the same module.** Kotlin emits `addGuildForChunking$net_dv8tion_JDA`, `getIncompleteCount$net_dv8tion_JDA`, `handleCreate$net_dv8tion_JDA`, etc., for the members that were package-private. Their only callers are the sibling Kotlin `GuildSetupNode`/`GuildSetupController` and the Kotlin handlers, so the mangled JVM names are never observed; `internal` still compiles to `public` on the JVM, matching the accepted widening for `internal` types.
- **`StatusListener` is a `fun interface`, so the Java test can still mock it.** `AbstractSocketHandlerTest` mocks `GuildSetupController` with Mockito 5, which uses the inline mock maker and can mock the final Kotlin class. The listener default is a lambda-backed `StatusListener` that logs, replacing the Java anonymous class.
- **`GuildSetupNode` is `final`.** It was a non-final `class` in Java but is subclassed by nothing, and Kotlin's default matches.
- **`GuildSetupController` stays `open`.** The Java class was non-final; `javap` confirms the ACC_FINAL flag stays clear.
- **`EventCache`'s synchronized methods keep their `ACC_SYNCHRONIZED` shape.** `@Synchronized` on each method reproduces the Java `synchronized` modifier exactly.
- **detekt handled inline**: `ReturnCount` on `GuildSetupController.onDelete`, `GuildSetupNode.handleMemberChunk` (branch-and-return ports), `TooGenericExceptionCaught` on the status-listener catch (the Java original deliberately logged any listener exception), `EmptyFunctionBlock`/`UnusedParameter` on the intentionally-empty `GuildSetupNode.handleReady`, `ForbiddenComment` on the ported `TODO` (matching the `ActionRowImpl` precedent), and `MagicNumber` on the cached-event thresholds, extracted to `CACHE_EVENT_WARNING_THRESHOLD`/`CACHE_EVENT_WARNING_INTERVAL`. The default-constructed `IllegalStateException` passed to the warn log was given a message to satisfy `ThrowingExceptionsWithoutMessageOrCause`.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged (`api/` has no diff), `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`. The retained Java `ReadyEvent` calls `getGuildSetupController().getSetupNodes(Status.UNAVAILABLE)`, so Java compilation remains the independent cross-check of that public signature.

### Phase 2 — `api.entities` snowflake leaves (batch 39)

The first `api` package batch after the pilot: `ForumTagSnowflake` and `StickerSnowflake` are now Kotlin. They are the leaf snowflakes — interface plus two static `fromId` factories, the same shape as `SkuSnowflake` — so they sit inside both gates (the ABI baseline and ArchUnit) and the conversion is verified, not merely compiled.

- **`SoundboardSoundSnowflake` was attempted and reverted.** It is the same shape plus one `default getUrl()`, and that default is what breaks: converting it to a Kotlin interface default emits `SoundboardSoundSnowflake$DefaultImpls.getUrl(SoundboardSoundSnowflake)` and a synthetic `access$getUrl$jd(SoundboardSoundSnowflake)`, both `public`, and `ArchUnitComplianceTest` flags each as a public method whose non-primitive parameter/return lacks a nullability annotation (2 violations across the accept/return rules). Neither is Java-visible API and neither can carry `@Nonnull` (`DefaultImpls` is generated, and the synthetic accessor is not source-addressable), so an `api` interface with any default method is **blocked** until this is resolved. This is the interface analogue of the enum `getEntries()` leak, and it is why the batch is the two default-free leaves only.
- **`UserSnowflake` and `ISnowflake` are hubs, not leaves.** `ISnowflake` is implemented by ~200 types and `UserSnowflake` by `User`/`Member`; both are read through the synthesized `.id`/`.idLong`/`.defaultAvatarId` properties at ~280 Kotlin call sites. Converting either to a Kotlin interface drops those synthesized properties and fails compilation everywhere (confirmed: `ISnowflake` alone produced ~40+ unresolved-reference errors). They convert as their own dedicated batch once the interface-default question above is settled, and they will need their `getX` members written as Kotlin properties (or every call site moved to `getX()`) so the `getId()`/`getIdLong()` JVM methods and the call sites are preserved together.
- **`@JvmStatic` in the companion keeps the static ABI.** Both `fromId(long)` and `fromId(String)` remain `public static` on the interface, matching the baseline.
- **`@Nonnull` written explicitly**, per the pilot rule; the two converted interfaces carry no Kotlin-nullability-only annotations.
- **The ABI baseline gains only the accepted `Companion` artifacts.** `apiDump` adds `…$Companion` and the `Companion` field to the two entries (12 lines total), exactly the additions already accepted for `SkuSnowflake`; no member is removed, so the diff is intentional and recorded by re-running `apiDump` in this commit.

Verification: `./gradlew check --rerun-tasks` green — 506 tests / 0 failures, including the 8 `ArchUnitComplianceTest` rules; `apiCheck` passes with the 4 new Kotlin classes (`Companion` + `…$Companion` for each interface) and no removals; `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`. `javap` confirms `StickerSnowflake.fromId`/`ForumTagSnowflake.fromId` remain `public static` and the `default`/`Companion` split matches `SkuSnowflake`.

### Phase 2 — `internal.managers.channel.concrete` leaves (batch 23)

The eight leaf channel managers — `CategoryManagerImpl`, `ForumChannelManagerImpl`, `MediaChannelManagerImpl`, `NewsChannelManagerImpl`, `StageChannelManagerImpl`, `TextChannelManagerImpl`, `ThreadChannelManagerImpl`, `VoiceChannelManagerImpl` — are now Kotlin. Each is a thin subclass of the retained Java `ChannelManagerImpl` that only declares its type parameters and constructor, so `internal.managers.channel.ChannelManagerImpl` remains Java for now (it is the package's dependency hub).

- **The constructor parameter is left unnamed in the supertype-argument position** (`ChannelManagerImpl<Category, CategoryManager>(channel)`), matching the Java `super(channel)`.
- **`ChannelManagerImpl` stays Java**, so these leaves are still cross-checked by the Java compiler at their `super(...)` call sites — the same independent-check property that the entity leaves had against `EntityBuilder`.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged (`api/` has no diff), `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`.

### Phase 2 — `internal.managers` leaf managers (batch 24)

Eight manager leaves are now Kotlin: `AccountManagerImpl`, `ApplicationEmojiManagerImpl`, `CustomEmojiManagerImpl`, `DirectAudioControllerImpl`, `PresenceImpl`, `SoundboardSoundManagerImpl`, `StageInstanceManagerImpl`, `TemplateManagerImpl`. They all subclass the retained Java `ManagerBase` (or, for `DirectAudioControllerImpl`/`PresenceImpl`, have no superclass), so the Java hub keeps type-checking their `super(...)` invocations.

- **Java interface constants need a qualified reference in Kotlin.** `NAME`, `AVATAR`, `BANNER`, `ROLES`, `VOLUME`, `EMOJI`, `DESCRIPTION`, `TOPIC` are static fields on the `api.managers.*` interfaces; Kotlin does not inherit interface statics into the subclass scope, so every use is spelled `AccountManager.NAME` / `TemplateManager.DESCRIPTION` / … The JVM result is the same `getstatic`.
- **Trove-free state is a `@JvmField` Kotlin property.** `AccountManagerImpl`, `TemplateManagerImpl`, `ApplicationEmojiManagerImpl`, `CustomEmojiManagerImpl` keep their Java `protected` field shape (`protected final SelfUser selfUser`, `protected String name`, `protected final List<String> roles`, …) via `@JvmField protected val`/`var`. `CustomEmojiManagerImpl.roles` is a `MutableList<String>` (the Java field was `List<String>`, but the visible field type is erased), and `withLock(this.roles) { … }` carries the Java `withLock(list, …)` calls through `ManagerBase.withLock`.
- **`PresenceImpl` keeps its private field layout and static accessor.** `idle`/`activity`/`status` are private `var`s with the same types, `update()` is `protected fun` (still emitted as `protected final`), and `getGameJson` moves to `@JvmStatic @Suppress("SENSELESS_COMPARISON") fun getGameJson(activity: Activity?)` so the Java `PresenceImpl.getGameJson(...)` call in `ActivityTest` still resolves — the suppression guards the Java original's deliberate `getName() == null`/`getType() == null` checks against broken `Activity` implementations.
- **`setPresence(status, activity, idle)` resolves the nullable status once.** The Java reassigned the parameter (`if (status == OFFLINE || status == null) status = INVISIBLE;`); Kotlin parameters are immutable, so the resolved value lands in a `val resolved` before the field assignment. Same behaviour, same field values.
- **`CustomEmojiManagerImpl.setRoles` keeps the `Checks.check(role.getGuild() == getGuild(), …)` call**; `List::clear` method references became `{ it.clear() }` lambdas because Kotlin cannot pass a `MutableList<String>::clear` method reference where a `Consumer` is expected.
- **`AccountManagerImpl` `setAvatar`/`setBanner` take `Icon?`**, matching the `@Nullable` API declarations, and `finalizeData` emits `avatar?.getEncoding()` (the Java `avatar == null ? null : avatar.getEncoding()`).
- **detekt handled inline**: `ProtectedMemberInFinalClass` on the fields kept protected for field-shape parity (same suppression idiom as `ForumChannelImpl`/`MediaChannelImpl`), and magic numbers extracted to private top-level `const val`s (`USER_NAME_MAX_LENGTH`, `NAME_MAX_LENGTH`, `DESCRIPTION_MAX_LENGTH`, `TOPIC_MAX_LENGTH`, `NAME_MIN_LENGTH`) rather than suppressing `MagicNumber`.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged (`api/` has no diff), `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`. `javap` confirms `PresenceImpl.update` is `protected final`, the `setPresence` overload set is unchanged, `getGameJson` is `public static`, and the `AccountManagerImpl` protected field names/types match the Java class.

### Phase 2 — `internal.managers` hub and remaining leaves (batch 25)

`ManagerBase` — the package's dependency hub — plus three leaves, `GuildStickerManagerImpl`, `SelfMemberManagerImpl`, and `WebhookManagerImpl`, are now Kotlin. `ManagerBase` is the first converted class that **eighteen** other types both extend and read protected state from, eight of which are still Java (`ApplicationManagerImpl`, `AutoModRuleManagerImpl`, `GuildManagerImpl`, `GuildWelcomeScreenManagerImpl`, `PermOverrideManagerImpl`, `RoleManagerImpl`, `ScheduledEventManagerImpl`, `ChannelManagerImpl`), so the Java compiler is still the independent check on `set`, `shouldUpdate`, `withLock`, and `checkPermissions`.

- **`enablePermissionChecks` moves to the companion as a private static.** The Java `private static boolean` is read by the static accessors only; it stays private, so the field shape is preserved and the two accessors are `@JvmStatic` (methods, not fields). The `Manager` interface's own `setPermissionChecksEnabled`/`isPermissionChecksEnabled` statics keep resolving to `ManagerBase` unchanged.
- **`set` stays `@JvmField protected`.** It is written by every subclass and read by `shouldUpdate`/`reset`; a Kotlin property would emit `getSet`/`setSet` and break the Java subclasses (and `PermOverrideManagerImpl`'s `this.set |= …`).
- **`shouldUpdate`/`withLock` are implicitly final.** No subclass overrides either, so Kotlin's final default is faithful and matches the Java-bytecode `protected final` on the `javap` diff (the Java source declared them non-final, but nothing could override them without a warning-free subclass; the class-file flags are identical because no override exists).
- **`setCheck` takes `BooleanSupplier?`.** The interface parameter is `@Nullable` and the superclass method is unannotated/JDK-unannotated; declaring the override non-null caused an "inherited platform declarations clash" against `AuditableRestAction.setCheck(BooleanSupplier)` on every subclass, so the override follows the widest nullability.
- **`queue`'s null-success branch needs an unchecked cast.** `Consumer<in Void>` cannot accept `null` at the type level (`Void` is a class, not a nullable type here), so `success.accept(null)` is reached via `(success as Consumer<Any?>).accept(null)`, matching the Java `success.accept(null)`.
- **`complete` and `finalizeChecks` disambiguate `super`.** Both `AuditableRestActionImpl` and the `Manager`/`RestAction` interfaces declare them, so `super<AuditableRestActionImpl>` is required; the lambda was dropped in favour of `if (enablePermissionChecks) BooleanSupplier { checkPermissions() } else super.finalizeChecks()` because a bare method reference on a `protected open` member was not resolvable from the base's own companion-visible context.
- **`checkPermissions` is `protected open`, not `protected final`.** `CustomEmojiManagerImpl` and `TemplateManagerImpl` override it, so it must stay open (the compiler caught the final default immediately).
- **`GuildStickerManagerImpl.guild` is nullable.** The Java constructor stored the `Guild` field, but `GuildStickerImpl.getManager()` passes `getGuild()` and the sticker's guild is nullable (`@Nullable Guild getGuild()`); the manager's `checkPermissions` already guarded `if (guild != null)`, so the field is `Guild?` and the superclass argument uses `guild!!` (the constructor is only reached with a cached guild).
- **`GuildStickerManagerImpl.setTags(String...)` is inherited, not re-declared.** The Java class only overrode the `Collection<String>` overload; the varargs `setTags` stays the interface `default`, so the Kotlin class keeps the same single override.
- **`WebhookManagerImpl.setAvatar` keeps `Icon?`** and `finalizeData` emits `avatar?.getEncoding()`; `setChannel` casts the `IWebhookContainerUnion` from the interface's `getChannel()` to `GuildChannel` before `Checks.checkAccess`.
- **detekt handled inline**: `ProtectedMemberInFinalClass` on the `@JvmField protected` fields of `SelfMemberManagerImpl`/`WebhookManagerImpl` (field-shape parity, same idiom as batch 24), and magic numbers extracted to private top-level `const val`s throughout (`NAME_MIN_LENGTH`, `TAGS_MAX_LENGTH`, `SelfMember`/`Member` limits come from the API types).

Verification: `./gradlew check --rerun-tasks` green — 506 tests / 0 failures; `apiCheck` baseline unchanged (`api/` has no diff), `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`. `javap` confirms `ManagerBase.set` stays a `protected` field, the static accessors remain `public static`, `checkPermissions` is `protected` (non-final), and the three leaf classes' protected field names/types match their Java originals.

### Phase 2 — `internal.managers` stateful leaves (batch 26)

Three more `ManagerBase` subclasses are Kotlin: `AutoModRuleManagerImpl`, `GuildWelcomeScreenManagerImpl`, and `PermOverrideManagerImpl`. All three are stateful (they keep the mutable `set` bitmask plus their own protected fields), so their `set = set or FLAG` writes exercise the converted hub's `@JvmField protected set` from a Kotlin subclass for the first time.

- **Java interface constants stay qualified** (`AutoModRuleManager.NAME`, `GuildWelcomeScreenManager.CHANNELS`, `PermOverrideManager.ALLOWED`, …), same idiom as batch 24.
- **The protected field layout is preserved with `@JvmField`.** `AutoModRuleManagerImpl` keeps `guild`/`name`/`enabled`/`responses`/`exemptRoles`/`exemptChannels`/`triggerConfig`; `GuildWelcomeScreenManagerImpl` keeps `enabled`/`description`/`channels`; `PermOverrideManagerImpl` keeps `override`/`role`/`allowed`/`denied`. `responses` is `EnumMap<AutoModResponse.Type, AutoModResponse>?` (the Java field was initialised only by `setResponses`), and `exemptRoles`/`exemptChannels` are `MutableList<…>?` (Java `ArrayList` assigned from `new ArrayList<>(roles)`).
- **`AutoModRuleManagerImpl.setResponses`/`setExemptRoles`/`setExemptChannels` declare the invariant `Collection<…>`.** The interface declares `Collection<? extends …>`; Kotlin rejects a covariant parameter on an override, so the Kotlin source uses the base `Collection<AutoModResponse>` and the compiler still emits the `Collection<? extends AutoModResponse>` JVM signature (confirmed by `javap`), leaving the ABI and the `Collection<AutoModResponse>` test call sites unchanged.
- **`GuildWelcomeScreenManagerImpl.reset()` keeps its `super.reset(ENABLED | DESCRIPTION | CHANNELS)` call.** The Java `reset()` deliberately fanned out to the three fields rather than calling `super.reset()` (which clears every bit), preserving the same `set` bitmask.
- **`withLock` carries the `List::clear` and clear-then-`addAll` lambdas.** `clearWelcomeChannels` becomes `withLock(channels) { it.clear() }` (the Java `List::clear` method reference cannot be a `Consumer` in Kotlin), and `finalizeData`/`setWelcomeChannels` keep the locking blocks verbatim.
- **`PermOverrideManagerImpl.getPermissionOverride` reads the `permissionOverrideMap` property.** The already-converted `IPermissionContainerMixin` exposes `val permissionOverrideMap: TLongObjectMap<PermissionOverride>`; the call is `channel.permissionOverrideMap[override.getIdLong()]` rather than `getPermissionOverrideMap()`.
- **The raw `~permissions` becomes `permissions.inv()`** in `grant`/`deny`/`clear`; `allowed |= permissions` is `allowed or permissions`, `denied &= ~permissions` is `denied and permissions.inv()`. `allow`/`deny` are serialised as raw `long` (the Java `DataObject.put("allow", this.allowed)`).
- **detekt handled inline**: `ProtectedMemberInFinalClass` on the `@JvmField protected` fields (field-shape parity, same idiom as batches 24–25).

Verification: `./gradlew check --rerun-tasks` green — 506 tests / 0 failures; `apiCheck` baseline unchanged (`api/` has no diff), `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`. `javap` confirms all three classes' protected field names/types match their Java originals, `grant`/`deny`/`clear` keep the `PermOverrideManagerImpl` covariant return, and `finalizeData`/`checkPermissions` are `protected`.

### Phase 2 — `internal.managers` role and scheduled-event leaves (batch 27)

`RoleManagerImpl` and `ScheduledEventManagerImpl` are now Kotlin. Both are heavy `finalizeData`/`checkPermissions` overrides with a large protected field set and a mix of `@Nullable` inputs, so they exercise the converted hub's `shouldUpdate`/`set` and the `Helpers.toOffsetDateTime` return.

- **Java interface constants stay qualified** (`RoleManager.NAME`, `ScheduledEventManager.LOCATION`, …), same idiom as batch 24.
- **The nullable `setColors`/`setIcon` overrides match the API's `@Nullable`.** `RoleManager.setColors(@Nullable RoleColors)`, `setIcon(@Nullable Icon)`, and `setIcon(@Nullable String)` are nullable in the interface; declaring the Kotlin parameters non-null made the overrides fail ("overrides nothing") because the platform-nullable supertype is stricter, so the Kotlin parameters (and the `@Nullable` annotation) follow the interface exactly.
- **`RoleManagerImpl.setPermissions` keeps the missing-permission check.** `missingPerms &= ~selfPermissions` / `&= ~this.permissions` became `missingPerms and selfPermissions.inv()` / `and permissions.inv()`, and the `InsufficientPermissionException(getGuild(), permissionList.iterator().next())` construction is unchanged.
- **`setName` trims into a local.** The Java reassigned the parameter (`name = name.trim()`); Kotlin parameters are immutable, so the trimmed value lands in a `val trimmed` used for the emptiness/length checks and the field assignment.
- **`ScheduledEventManagerImpl`'s `entityType`/`status`/`startTime`/`endTime` are nullable fields.** They are only assigned by the corresponding setters, and `finalizeData` reads them under the matching `shouldUpdate` bit, so the dereferences carry `!!` exactly where the Java code relied on the bitmask.
- **`Helpers.toOffsetDateTime` returns `OffsetDateTime?`.** The Java never null-checked the result; Kotlin surfaces the nullability, so the two `setStartTime`/`setEndTime` locals and the `preChecks` end/start fallback use `!!` to preserve the original NPE-on-null behaviour.
- **The two `switch` statements become `when` expressions** (the status transition checks and the `entityType` serialisation), keeping the same branch bodies, exceptions, and `Checks.check` messages.
- **magic numbers extracted to a private top-level `const val`** (`MAX_YEARS_IN_FUTURE`, `NAME_MAX_LENGTH`) rather than suppressing `MagicNumber`; `ScheduledEvent.MAX_*` come from the API type.
- **detekt handled inline**: `ProtectedMemberInFinalClass` on the `@JvmField protected` fields (field-shape parity, same idiom as batches 24–26).

Verification: `./gradlew check --rerun-tasks` green — 506 tests / 0 failures; `apiCheck` baseline unchanged (`api/` has no diff), `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`. `javap` confirms both classes' protected field names/types match their Java originals, the `setIcon`/`setColors`/`setRole` overload set is unchanged, and `finalizeData`/`checkPermissions` are `protected`.

### Phase 2 — `ApplicationManagerImpl` (batch 28)

`ApplicationManagerImpl` is now Kotlin. Unlike the other managers it is refreshed per-JDA (its constructor takes a `JDA` directly rather than an entity), but it still inherits the hub's `set`/`reset` machinery.

- **Java interface constants stay qualified** (`ApplicationManager.DESCRIPTION`, `ApplicationManager.INTEGRATION_TYPES_CONFIG`, …), same idiom as batch 24.
- **The protected field layout is preserved with `@JvmField`**: `description`, `icon`, `coverImage`, `tags` (`MutableSet<String>?` for the Java `Set<String>`), `interactionsEndpointUrl`, `customInstallUrl`, `installParams`, and `integrationTypeConfig` (`MutableMap<IntegrationType, ApplicationManager.IntegrationTypeConfig>?`). The nested `IntegrationTypeConfig` is qualified because Kotlin does not inherit nested types into subclass scope.
- **`setIcon`/`setCoverImage` take `Icon?`**, matching the interface's `@Nullable` declarations (declaring them non-null made the overrides fail to resolve).
- **`finalizeData` serialises the nullable fields under their `shouldUpdate` bits**, using `icon?.getEncoding()` / `coverImage?.getEncoding()` (the Java ternaries) and `DataArray.fromCollection(tags!!)` / `integrationTypeConfig!!.forEach { … }` (the Java passed the fields, which the matching bit guarantees non-null).
- **`checkUrl` stays `protected`** (detekt's `ProtectedMemberInFinalClass` suppressed inline for member-shape parity, same idiom as the protected fields), and `handleSuccess` stays a `protected` override.

Verification: `./gradlew check --rerun-tasks` green — 506 tests / 0 failures; `apiCheck` baseline unchanged (`api/` has no diff), `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`. `javap` confirms all eight protected fields' names/types match the Java original and `checkUrl`/`handleSuccess`/`finalizeData` are `protected`.

### Phase 2 — `internal.requests.restaction.operator` (batch 29)

The whole `operator` package — `RestActionOperator`, `MapRestAction`, `MapErrorRestAction`, `FlatMapRestAction`, `FlatMapErrorRestAction`, `DelayRestAction`, and `CombineRestAction` — is now Kotlin, the first slice of `internal.requests`. The classes are only ever constructed from the retained Java `RestAction` interface, so Java compilation remains the independent cross-check of every constructor signature.

- **`RestActionOperator.doSuccess`/`doFailure` are `@JvmStatic` on a companion object.** The Java methods were `protected static`; because `CombineRestAction` (not a subclass) reads them through the same-package rule that Kotlin lacks, they are declared `internal` so the bytecode names become `doSuccess$net_dv8tion_JDA`/`doFailure$net_dv8tion_JDA`. Nothing outside the module can see them, so the widening is not observable.
- **The protected fields keep their Java shape with `@JvmField`** (`action`, `check`, `deadline`). A plain Kotlin property would have emitted `getAction()`/`getCheck()` accessors and changed the field layout; `javap` confirms `protected final RestAction<I> action`, `protected BooleanSupplier check`, and `protected long deadline`.
- **`handle` and `contextWrap` stay monomorphic ports of the Java lambdas.** `contextWrap` needed an explicit `Consumer<in Throwable>` local before `ContextException.here(...)`, because Kotlin inferred `Consumer<out Any>` from the nullable/default-failure branches and the Java signature is `Consumer<? super Throwable>`.
- **`fun fail(error: Throwable): Nothing` was rejected by `-Werror`.** A `Nothing` return used from `submit` made Kotlin's smart-cast complain that the recovered `T` value was never produced; the method returns `Unit` instead (it only ever throws), with `@Contract("_ -> fail")` kept from the Java original.
- **`then: RestAction<out T>? = map.apply(error)` pins the null branch.** `submit`/`complete` deliberately guard a `null` operand returned by a broken mapping function; Kotlin's platform-typed `map.apply(...)` was considered non-null and `-Werror` flagged `Condition is always 'false'`, so the local is explicitly nullable.
- **`CombineRestAction` gets a private `COMBINED_ACTION_COUNT` constant** for the `== 2` completion counter rather than a detekt `MagicNumber` suppression, matching the established policy.
- **detekt findings are all inline suppressions with reasons**: `TooGenericExceptionCaught` on `handle` and every error-mapping `queue`/`complete` (the Java original routes any throwable through the failure handler), `TooGenericExceptionThrown`/`SwallowedException` on the `fail` helpers and the `CompletionException` unwrap in `CombineRestAction`, and `ThrowsCount` on the multi-throw `complete`/`fail` bodies.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged (`api/` has no diff), `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`. `javap` against the deleted Java sources confirms every constructor, `queue`/`complete`/`submit`/`getJDA`/`setCheck`/`addCheck`/`getCheck`/`deadline` signature, the protected field names/types, and the covariant `? super`/`? extends` bounds are unchanged; the only additions are the `$Companion` field and synthetic lambda methods.

### Phase 2 — `internal.requests.restaction.pagination` (batch 30)

The whole `pagination` package — the abstract `PaginationActionImpl` base, the nested `ChainedConsumer`, and the ten concrete actions (`AuditLog`, `Ban`, `Entitlement`, `Message`, `PinnedMessage`, `PollVoters`, `Reaction`, `ScheduledEventMembers`, `ThreadChannel`, `ThreadMember`) — is now Kotlin. Every class is constructed from retained Java call sites (`GuildImpl`, `MessageChannelImpl`, `ReceivedMessage`, the managers, `PollVotersPaginationAction` reach-through, etc.), so Java compilation remains the independent cross-check of all constructor signatures.

- **The generic bound is `T : Any`, not `T`.** The API interface declares `getLast(): T` / `getFirst(): T` as `@Nonnull`, which Kotlin models as `T & Any`, so an unbounded `T` fails to override with `Return type ... is not a subtype`. `T : Any` makes the override legal and matches the Java declaration (`T extends Object`).
- **The supertype is `RestActionImpl<@JvmSuppressWildcards List<T>>`.** A bare Kotlin `List<T>` type argument compiles to `List<? extends T>` at the use site, which changes the inherited `handleSuccess(Response, Request<List<T>>)` erasure; the twelve subclasses then failed with `name clash ... neither overrides the other`. `@JvmSuppressWildcards` restores the invariant `List<T>` the Java original had.
- **`PaginationOrder` must be qualified as `PaginationAction.PaginationOrder`** — it is nested in the API interface, not a top-level type, so a bare `PaginationOrder` is unresolved inside the internal package.
- **All base-class fields keep their Java shape with `@JvmField`** (`cached`, `maxLimit`, `minLimit`, `limit`, `order`, `iteratorIndex`, `lastKey`, `last`, `useCache`) and the volatile ones keep `@Volatile`. A plain property would emit accessors and change the field layout the subclasses read.
- **`ChainedConsumer` is a `protected inner class`** so it keeps the `this$0` back-reference and the enclosing generic scope that the Java non-static inner class had. Its three fields plus `initial` are `@JvmField protected`; `initial` needs `@Suppress("ProtectedMemberInFinalClass")` because the class is `final` in bytecode (`javap` confirms `protected boolean initial` is retained).
- **`takeAsync` uses explicit `Consumer { ... }` lambdas for the failure callback** (`task.completeExceptionally`), preserving the Java `task::completeExceptionally` shape; `BiFunction` stays the parameter type of the private `takeAsync0`.
- **`getNextChunk` stays `public final` + `getRemainingCache`/`getIteratorIndex`/`updateIndex` stay `protected final`**, matching the Java modifiers exactly (Kotlin `fun` in a non-open class compiles to `final`). The abstract `getKey` is `protected abstract fun`.
- **`ReactionPaginationActionImpl` models the Java overload set without `@JvmOverloads`.** A private primary constructor `(reaction: MessageReaction?, jda, route)` backs four public secondary constructors, so `javap` shows the identical four public constructors, the private one, and `protected static final String getCode(MessageReaction)`.
- **`ScheduledEventMembersPaginationActionImpl` and the `JDA`-taking `ThreadChannel` action likewise use a private primary constructor** to run the `event.guild` lookups once while keeping the public constructor signature `(ScheduledEvent)`.
- **`MessagePaginationActionImpl.getChannel()` returns `channel as MessageChannelUnion`** — the field is typed `MessageChannel` to match the Java parameter, and the downcast is the same unchecked cast the Java original performed.
- **`PinnedMessagePaginationActionImpl.handleSuccess` sets `lastKey = getKey(pinnedMessage)`** rather than reading the freshly-assigned `last`, avoiding Kotlin's "smart cast to nullable" complaint while keeping the same value.
- **Page-size magic numbers are extracted to a private top-level `private const val PAGE_LIMIT`** per file (matching the established policy) rather than suppressed; the shared `1` minimum stays literal because detekt does not flag it.
- **detekt handled inline**: `TooGenericExceptionCaught` on every `handleSuccess` (the Java originals catch `Exception` or the `ParsingException | NullPointerException` pair and log-and-continue), `ProtectedMemberInFinalClass` on the fields kept protected for field-shape parity, and `ReturnCount` on `ThreadChannelPaginationActionImpl.getPaginationLastEvaluatedKey` (four guard returns kept verbatim from Java).

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged (`api/` has no diff), `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`. `javap` against the deleted Java sources confirms every constructor signature (including the four overloads of `ReactionPaginationActionImpl` and the two of `ScheduledEventMembersPaginationActionImpl`), the `protected`/`protected volatile`/`final` member modifiers, the `protected void handleSuccess(Response, Request<List<T>>)` erasure in all twelve classes, and the inherited `setCheck`/`timeout`/`deadline` bridge methods; the only additions are the synthetic `$annotations` helpers, kotlin `Function1` lambdas, and the `$Companion` on `ReactionPaginationActionImpl`.


### Phase 2 — `internal.requests.restaction` root actions (batch 31)

The root of `internal.requests.restaction` is now Kotlin: the `TriggerRestAction` base, `AuditableRestActionImpl`, the `AbstractWebhookMessageActionImpl` base with its four webhook-message actions, plus `WebhookActionImpl`, `StageInstanceActionImpl`, `TestEntitlementCreateActionImpl`, `SoundboardSoundCreateActionImpl`, `CommandListUpdateActionImpl`, and the `PermOverrideData` value type. Every class is constructed from retained Java call sites (`IncomingWebhookClientImpl`, `GuildImpl`, `MessageChannel`, `Member`, `StageChannel`, `JDAImpl`, the managers, `ChannelActionImpl`), so Java compilation remains the independent cross-check of all constructor signatures.

- **`AuditableRestActionImpl`, `TriggerRestAction`, and `WebhookActionImpl` are declared `open`.** All three are extended by retained Java or Kotlin subclasses (the manager hierarchy and the webhook actions), so Kotlin's default `final` would have broken the inheritance. `javap` confirms the class-file ACC_FINAL flag is clear and the `protected` field shapes (`reason`, `channel`/`name`/`avatar`) survive via `@JvmField`.
- **Multiple-supertype `super` qualification is mandatory in `AuditableRestActionImpl`.** `RestActionImpl` and `AuditableRestAction` both declare `timeout`/`deadline`, so the covariant overrides must call `super<RestActionImpl>.timeout(...)`; a bare `super` is a compile error. `javap` shows the four inherited `timeout`/`deadline`/`setCheck` bridge methods are unchanged.
- **`AbstractWebhookMessageActionImpl.setCheck`/`deadline` return `R`, and the concrete actions must re-override `deadline`.** `FluentRestAction` (via the API interface) and the base class both supply a `deadline`, so `WebhookMessageCreateActionImpl`, `WebhookMessageEditActionImpl`, `WebhookMessageDeleteActionImpl`, and `WebhookMessageRetrieveActionImpl` each declare `override fun deadline(timestamp: Long): X = super<AbstractWebhookMessageActionImpl>.deadline(timestamp)` to resolve the clash and keep the Java covariant return type.
- **`WebhookActionImpl.setCheck`/`timeout`/`deadline` narrow to `WebhookActionImpl`, not the interface.** The Java original returned the concrete type, so the Kotlin overrides do the same; the interface-returning bridges are still emitted by the compiler, matching the original bridge set.
- **`WebhookActionImpl.getChannel()` keeps both accessors.** The `@JvmField`-less primary-constructor parameter is a `private final` field with a `protected final getChannel()` accessor (Java had `protected final IWebhookContainer channel`), and the explicit `getChannel()` override returns `IWebhookContainerUnion` by casting the same field, exactly as the Java original.
- **`CommandListUpdateActionImpl` needs `RestActionImpl<@JvmSuppressWildcards List<Command>>`.** A bare Kotlin `List<Command>` type argument compiles to `List<? extends Command>` in the supertype signature, which changes the inherited `handleSuccess(Response, Request<List<Command>>)` erasure; the wildcard suppression restores the invariant `List<Command>` the Java original had.
- **`TriggerRestAction` reads `RestActionImpl.getDefaultFailure()` and `RestActionImpl.isPassContext()` explicitly.** Kotlin does not inherit Java statics into subclass scope, so the `wrapContext` helper calls them through the declaring class; the explicit `Consumer<in Throwable>` local is required because the nullable/default-failure branches otherwise infer `Consumer<out Any>`.
- **`PermOverrideData.ROLE_TYPE`/`MEMBER_TYPE` become `const val`.** The Java `public static final int` constants are exposed as `public static final int` fields in bytecode (confirmed by `javap`); a plain `val` would have added getters and tripped detekt's `MayBeConstant`. The `allow`/`deny` bitmasking (`deny & ~allow`) is preserved verbatim.
- **Numeric limits are extracted to private top-level constants** (`MAX_TOPIC_LENGTH`, `MAX_NAME_LENGTH`, `MAX_USERNAME_LENGTH`, plus the `MIN_VOLUME`/`MAX_VOLUME`/`DEFAULT_VOLUME` pair) rather than detekt `MagicNumber` suppressions, matching the established policy.
- **detekt handled inline**: `ReturnCount` on `AuditableRestActionImpl.finalizeHeaders` (four guard returns kept verbatim from Java).

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged (`api/` has no diff), `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`. `javap` against the deleted Java sources confirms every constructor signature (including the six `AuditableRestActionImpl` and six `TriggerRestAction` overloads), the `protected` field names/types, the covariant return types, the invariant `List<Command>` erasure, and the retained `protected`/`public` member modifiers; the only additions are the synthetic lambda methods and the `PermOverrideData`/`ReactionPaginationActionImpl`-style `$Companion` fields.



### Phase 2 — `internal.requests.restaction` root actions, second pass (batch 32)

The remaining root action classes are now Kotlin: `ChannelActionImpl`, `CommandCreateActionImpl`, `CommandEditActionImpl`, `ForumPostActionImpl`, `InviteActionImpl`, `MemberActionImpl`, `MessageCreateActionImpl`, `MessageEditActionImpl`, `MessageSearchActionImpl`, `PermissionOverrideActionImpl`, `RoleActionImpl`, `ScheduledEventActionImpl`, and `ThreadChannelActionImpl`.

- **`ForumPostActionImpl`'s constructor order is `(channel, name, builder)`.** The Kotlin primary constructor was reordered to match the original Java signature and the `IPostContainerMixin` call site updated; the retained Java test (`ThreadCreateActionTest`) is the cross-check.
- **Classes with protected or subclassed members stay `open`.** `RoleActionImpl`, `ScheduledEventActionImpl`, `ThreadChannelActionImpl`, `PermissionOverrideActionImpl`, and `ForumPostActionImpl` are `open` because retained Java/Kotlin subclasses extend them; Kotlin's default `final` would drop the ACC_FINAL-free class-file flag the ABI baseline expects.
- **`HTTP_ACCEPTED` is a private top-level constant** rather than a `MagicNumber` suppression, matching the established policy.
- **detekt handled inline**: `ThrowsCount` on `ChannelActionImpl.setBitrate` was resolved by extracting the argument validation into a `require`-based guard rather than suppressing the rule.

### Phase 2 — `internal.requests.restaction.order` (batch 33)

`OrderActionImpl`, `ChannelOrderActionImpl`, `CategoryOrderActionImpl`, and `RoleOrderActionImpl` are now Kotlin.

- **Generic bounds use definitely-non-nullable intersections.** The `OrderAction<T, M>` interface declares `@Nonnull` on `selectPosition(T)`, `moveBelow(T)`, `moveAbove(T)`, `swapPosition(T)`, and `getSelectedEntity()`, which Kotlin sees as `T & Any`. The overrides and the `getSelectedEntity()` return type therefore use `T & Any` (and `orderList.removeAt(...)` returns non-null); a plain `T` is a compile error, and a `T : Any` class bound breaks the interface's nullable-bounded `T`.
- **`orderList`, `ascendingOrder`, `selectedPosition`, `guild`, `bucket`, and `lockPermissions`/`parent` are `@JvmField protected`.** Java subclasses and the retained Java `RoleOrderActionImpl`/`ChannelOrderActionImpl` call sites read these fields directly, so the accessor-less field shape must survive.
- **`getChannelsOfType` is `@JvmStatic` in a companion** so the static helper stays visible to Java call sites in `CategoryOrderActionImpl` and the retained tests.
- **detekt handled inline**: `ThrowsCount` on `RoleOrderActionImpl.finalizeData` was resolved by extracting the non-owner permission checks into a private `checkOrderPermission` helper, keeping the ported control flow intact.

### Phase 2 — `internal.requests.restaction.interactions` (batch 34)

`InteractionCallbackImpl`, `DeferrableCallbackActionImpl`, `MessageEditCallbackActionImpl`, `ReplyCallbackActionImpl`, `ModalCallbackActionImpl`, and `AutoCompleteCallbackActionImpl` are now Kotlin.

- **Multiple-supertype `super` qualification is mandatory.** `RestActionImpl` and the `InteractionCallbackAction`/message-builder interfaces both contribute `setCheck`/`timeout`/`deadline`, and `InteractionCallbackImpl.queue`/`submit` collide with `RestActionImpl`, so every covariant override calls `super<RestActionImpl>...` or `super<DeferrableCallbackActionImpl>...` explicitly.
- **`InteractionCallbackImpl.tryAck()` is `protected fun`, not `protected final`.** Kotlin is `final` by default; `javap` confirms the ACC_FINAL shape matches the Java original and the covariant `queue`/`submit` overrides remain.
- **`MessageEditCallbackActionImpl`/`ReplyCallbackActionImpl` keep the builder mixins** (`MessageEditBuilderMixin`/`MessageCreateBuilderMixin`), and the try-with-resources bodies become `builder.build().use { ... }` so the `AutoCloseable` close semantics are preserved.
- **detekt handled inline**: `ReturnCount` and `UnusedParameter` on the `ErrorMapper` callback (`handleUnknownInteraction`) — the `response`/`request` parameters are required by the functional-interface signature but genuinely unused in the ported body.

### Phase 2 — `internal.requests` core actions and support classes (batch 35)

`RestActionImpl`, `ErrorMapper`, `CallbackContext`, `CompletedRestAction`, `DeferredRestAction`, `FunctionalCallback`, and `WebSocketCode` are now Kotlin, along with `IncomingWebhookClientImpl`, `MemberChunkManager`, `Requester`, `WebSocketSendingThread`, and `WebSocketClient`.

- **Kotlin default finality is overridden where the Java class was non-final.** `RestActionImpl`, `IncomingWebhookClientImpl`, and `WebSocketClient` are `open` (the class-file ACC_FINAL flag must stay clear; `WebSocketClient` has no subclass today but was a non-final `public class`); `WebSocketSendingThread` and `MemberChunkManager` are final.
- **`@JvmField` keeps the field shape for cross-class reads.** `WebSocketClient`'s `api`, `queueLock`, `executor`, `chunkSyncQueue`, `ratelimitQueue`, `queuedAudioConnections`, and `sentAuthInfo` are read directly by `WebSocketSendingThread`/`MemberChunkManager`, so they must remain real JVM fields (the visibility widens `protected` → `public`/`internal`, matching the accepted policy). The fields that need only a JVM name keep `@JvmField protected`.
- **`@JvmName` preserves the name of the two `internal` members that would otherwise mangle.** `WebSocketClient.send(DataObject, boolean)` and `getNextAudioConnectRequest()` are called from the sibling `WebSocketSendingThread`, so they stay `internal` with `@JvmName("send")`/`@JvmName("getNextAudioConnectRequest")` to avoid the `$net_dv8tion_JDA` suffix.
- **`send$net_dv8tion_JDA` is gone, `send(DataObject, boolean)` is back on the class.** The JVM name now matches the Java original exactly; the only remaining visibility widening is `protected` → `public`.
- **The two companion constants keep their `protected static final` shape.** `INVALIDATE_REASON` and `IDENTIFY_BACKOFF` were `protected static final` in Java; a `protected const val` in the companion makes `javap` show them as `protected static final` on `WebSocketClient` itself.
- **`WebSocketClient` declares `WebSocketListener` explicitly.** The Java class implemented `WebSocketListener` (via `WebSocketAdapter`); Kotlin's `WebSocketAdapter` supertype alone does not list it, so the explicit interface keeps the original class-file interface set.
- **Callback overrides keep their declared checked exceptions.** `onThreadStarted`, `handleCallbackError`, `onError`, and `onThreadCreated` carry `@Throws(Exception::class)`, and `onBinaryMessage` carries `@Throws(DataFormatException::class)`, matching the Java `throws` clauses so the generic override descriptors are unchanged.
- **`StartingNode`/`ReconnectNode` are `open inner`.** Java declared them `protected` and non-final; Kotlin's default `final`/`private` would drop the ACC_FINAL-free class flag, so they are `protected open inner class`.
- **detekt handled inline**: `SwallowedException` on `queueReconnect` and `reconnect` (the original logs a fixed message and shuts down without the cause), `LoopWithTooManyJumpStatements`/`ReturnCount`/`ThrowsCount` on faithfully ported control flow, and the long rate-limit log line split across a string concatenation.

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged (`api/` has no diff), `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`. `javap` against the deleted Java sources confirms every public member is still present, with only `final` on methods and the documented visibility widening as the residual diff.

### Phase 2 — `internal.managers` audio, guild, and channel hubs (batch 36)

`AudioManagerImpl`, `GuildManagerImpl`, and the `internal.managers.channel.ChannelManagerImpl` dependency hub are now Kotlin, joining the leaf managers and `ManagerBase` converted in batches 23–27. With this the `internal.managers` package tree has no Java left except what remains of the wider `internal.*` work.

- **Java interface constants are qualified in Kotlin, including inside method bodies.** `ChannelManager.NAME`, `GuildManager.BANNER`, and the rest are `static` fields on the `api.managers.*` interfaces; Kotlin does not bring them into the subclass scope. Every use — `reset` bit tests, `shouldUpdate` guards, `set |= …` assignments, and `finalizeData` — is spelled with the interface qualifier so the JVM `getstatic` is unchanged. A regex sweep over the ported bodies is not safe here: it rewrites occurrences inside string literals too, which is exactly the `checkFeature("GuildManager.BANNER")` bug caught by `GuildManagerTest.callEverySetter`.
- **An explicit `import java.util.Collection` shadows the inherited platform type and breaks the override.** With the import present, `Checks.noneNull(tags, …)` and the `Collection<ForumTagSnowflake>` override no longer line up — `Checks.noneNull` demands `kotlin.collections.Collection<*>?`, and a redundant `out` projection captures the element type so the wildcard no longer widens. Dropping the import (letting the inherited `java.util.Collection` win) and removing the `out` projection both restore the override.
- **`ChannelManagerImpl` stays `open`; its eight concrete subclasses remain thin Kotlin subclasses.** It has no `final` modifier in Java and is subclassed, so the class-file ACC_FINAL flag must stay clear. `T` cannot be declared `out`: it occurs in the invariant `ChannelManager<T, M>` supertype and in the mutable `channel` field, so `out` is rejected. `T` is a non-null platform type whose non-null bounds are expressed through the explicit `@Nonnull` on `getChannel()` and the `@Nonnull`/nullable parameter annotations on the setters, not through Kotlin nullability.
- **`TLongObjectHashMap.keySet()` returns `TLongSet`, whose `isEmpty()` is a method, not a property callsite.** The Kotlin port keeps `.isEmpty()` (`overridesRem.isEmpty()` etc.) so the trove stub is called directly; likewise `data.valueCollection()` is wrapped in `ArrayList(...)` because Kotlin's `MutableCollection` read type is not assignable to the `java.util.Collection<PermOverrideData>` return type.
- **`setAppliedTags`' `Collection<ForumTagSnowflake>` and `setAvailableTags`' `List<BaseForumTag>` use the inherited Java element types with no `out` projection**, matching `ThreadChannelManager`'s `Collection<? extends ForumTagSnowflake>` and `setAvailableTags(List<? extends BaseForumTag>)` overloads after erasure.
- **detekt handled inline**: `MagicNumber` on the bitrate floor is extracted to `MIN_BITRATE = 8000`; `ThrowsCount` is suppressed on `sync` and `setAppliedTags` (the multiple guards are ported verbatim from the Java originals); ktlint `property-naming` is suppressed on `AudioManagerImpl.CONNECTION_LOCK` because the uppercase name is the `public final` field shape.
- **`AudioConnection` reads the listener through the method.** `connectionListener` is `protected` in `AudioManagerImpl`, so the sibling `AudioConnection` accesses it via `getListenerProxy()`, which is how the Java class did it (`manager.listenerProxy` was never a field access in the original either across that visibility).

Verification: `./gradlew check` green — 506 tests / 0 failures; `apiCheck` baseline unchanged (`api/` has no diff), `verifyBytecodeVersion` (major 69), `spotlessCheck`/`rewriteDryRun`, and `detekt`. `javap` confirms `major version: 69` for `AudioManagerImpl`, `GuildManagerImpl`, and `ChannelManagerImpl`.

### Phase 3 — Tests (overlaps Phase 2)

Keep the safety net in Java as long as possible.

- Leave `ArchUnitComplianceTest`, `ComponentConsistencyComplianceTest`, and `SourceSets` in Java until the very end. ArchUnit operates on bytecode, so it keeps working across the transition, and its annotation rules are precisely the contract that Kotlin can silently break. Ensure `ClassFileImporter().importPackages("net.dv8tion.jda.api")` picks up Kotlin output as well as Java.
- Convert ordinary test files opportunistically, after the production package they cover.
- Convert or delete anything that no longer applies once `src/test-java8` is gone.
- Add a Java-only interop smoke test (§8) that is *never* converted — it is the standing proof that Java call sites still work.

### Phase 4 — Publishing, docs, CI

- Generate Dokka docs covering both Java and Kotlin sources. Either emit Dokka into the existing `build/docs/javadoc` path consumed by `docs.yml`, or update the workflow and the published `javadoc` jar. Do not remove Javadoc until Dokka covers the full public surface.
- Update `maven-publish`: the `sources` jar must include Kotlin files; add `kotlin-stdlib` as an `api` dependency once a Kotlin type appears in the public API.
- Re-validate all four jar variants with Kotlin present. Kotlin emits `@Metadata` and synthetic classes; confirm `minimalJar` minimization and `duplicatesStrategy = FAIL` still behave, and refresh artifact filters if Kotlin introduces packages.
- Re-verify `verifyBytecodeVersion` against Kotlin output (Kotlin must be told `jvmTarget = 25` and must not emit anything else).

### Phase 5 — Cleanup

- Remove `src/main/java` once empty; remove Java-only tooling (OpenRewrite Java recipes, the Error Prone configuration, the Palantir Java formatter block) once no Java remains.
- Freeze the accumulated ABI baseline as the new published contract.
- Decide `MigrateToJavaxAnnotations`' fate: if the public API standardizes on Kotlin/JetBrains nullability annotations after a major release, that recipe and the JSR-305 rule in §3.3 can be revisited deliberately, not accidentally.

---

## 5. Java → Kotlin idiom mapping

| Java idiom | Kotlin approach | ABI caveat |
|---|---|---|
| Interface `default` methods (~1,468) | Default implementations in interfaces | Needs `-jvm-default=enable` (formerly `-Xjvm-default=all-compatibility`) to keep `default` in bytecode for Java implementors |
| Static interface methods (~170) | `companion object` + `@JvmStatic` | Kotlin has no true interface statics; verify with the ABI diff |
| `@Nonnull` / `@Nullable` (959 files) | Keep the JSR-305 annotations; do not use Kotlin types at the boundary | Writing the annotation explicitly preserves `javax.annotation.*` in bytecode. Note that Kotlin still emits `Intrinsics.checkNotNullParameter` even when `@Nonnull` is present (verified in the pilot), so `kotlin-stdlib` becomes a runtime dependency regardless |
| `@UnknownNullability`, `@Contract` (13 sites) | Not expressible in Kotlin; retain as annotations | Referenced by ArchUnit rules |
| Wildcards `? extends` / `? super` (~551) | `out` / `in` variance | Some parameter wildcards are unrepresentable; use `@JvmSuppressWildcards` or explicit projections and document each exception |
| `public enum` with state (61) | `enum class` | `values()`/`valueOf()` placement in bytecode differs; Java call sites are fine, bytecode reflection may not be. Confirmed: Kotlin additionally leaks a public, non-synthetic `kotlin.enums.EnumEntries getEntries()`, which the compliance rules flag — blocked until resolved |
| Package-private top-level classes (5) | `internal` | `internal` is module-scoped and mangles function names; audit for same-package Java access before converting |
| Varargs (190 files, 6 `@SafeVarargs`) | `vararg` + `@SafeVarargs` | Generic varargs need `Array<out T>` / `@JvmSuppressWildcards` |
| Checked exceptions (`InterruptedException`, `JsonProcessingException`, `DataFormatException`, `GeneralSecurityException`, …) | Kotlin has none; add `@Throws` where Java callers must catch | Omitting `@Throws` silently changes the compiled signature |
| Static nested classes | Plain nested classes (static by default) | Behavioral match; use `inner` only if the Java type was a non-static inner class |
| `final` classes (17) | Kotlin classes are final by default | Good alignment; add `open` only where subclasses actually exist |
| Anonymous classes / SAM lambdas | Object expressions / SAM conversion | Compile-only; re-check Jackson and reflection paths |
| Reflection (`AnnotatedEventManager.getDeclaredMethods`, `JDALogger` `Class.forName`) | Works, but Kotlin emits synthetic/bridge methods | Filter synthetics in `AnnotatedEventManager`; verify the slf4j provider probe and `FallbackLogger` still resolve |
| Jackson (de)serialization | Keep Jackson; add `jackson-module-kotlin` only if Kotlin classes are serialized directly | New dependency only if actually needed |

Two runtime traps to test explicitly, because no static checker will catch them:

1. Kotlin's inserted null checks on non-null parameters.
2. Kotlin's `Intrinsics` checks on platform types.

Both can turn previously legal Java calls into `NullPointerException`s.

---

## 6. What the JVM 25 target removes from the work

Retiring Java 8 simplifies the migration materially:

- **No Java 8 API ceiling.** Kotlin may use `java.time` and other post-8 JDK APIs without the old `-release 8` restrictions.
- **No `src/test-java8` suite** to keep alive or port; the `MinimalJDABotTest` run-on-JDK-8 job goes away with Phase 0.
- **No `-Xlint:-options` / `-Xlint:-try` class of suppressions** carried over from the Java 8 era.
- **No Kotlin-version ceiling** imposed by upstream JVM-1.8 target deprecation. This is the main reason the migration is now tractable: the compiler and the bytecode target are no longer fighting each other.

---

## 7. Generated REST models

`buildSrc` generates `*Dto` sources with Palantir **JavaPoet**, and a **JavaParser** task filters them; the generator is already written in Kotlin but emits Java, and the output is added directly to `sourceSets.main`.

**Recommendation: keep generating Java.** Generated Java and hand-written Kotlin coexist in the same source set with no interop cost, and this avoids touching the codegen pipeline during the risky part of the migration.

Port to KotlinPoet only if a fully Java-free tree is a hard requirement after Phase 5. If that happens, it is a self-contained follow-up: swap the `JavaFile`/`TypeSpec` construction and decide whether the JavaParser filter stays (it can — it parses generated `.java` regardless of who wrote it).

---

## 8. Testing and verification strategy

Layered gates; each package must clear all of them before merge.

1. **ABI diff** (`binary-compatibility-validator`, or `japicmp`/`revapi`) against the last Phase-0 release. This is the single most important gate. Treat the baseline as an allowlist: any diff fails CI unless explicitly added and reviewed.
2. **Bytecode version** — `verifyBytecodeVersion` at major version 69, covering Kotlin and Java output.
3. **ArchUnit compliance** — unchanged; becomes the primary detector of annotations dropped during conversion.
4. **Behavioral suite** — the 91 existing test files. Watch `JDABuilderTest`, the event/socket handler tests, `DataObjectTest`/`JsonTest` (serialization), `PermissionUtilTest`, and `CryptoAdapterTest` especially.
5. **Java interop smoke test** — a small, permanently-Java module (reuse `src/examples`) compiled and run against the published artifact after every phase. This catches `@JvmStatic`, `@JvmName`, and default-method regressions that unit tests miss.
6. **Downstream canary** — build `jda-ktx` against the migrated artifact in CI. It is a real Kotlin consumer and will surface variance and `@JvmSuppressWildcards` mistakes.

---

## 9. CI changes

Extend `.github/workflows/validate.yml` (or add `migration.yml`):

- Kotlin compile + extended `checkFormat` (Spotless/Kotlin + detekt).
- ABI diff against the last Phase-0 tag.
- Dokka build, plus a diff of the public symbol index against the Javadoc index.
- jda-ktx downstream build (token/cache permitting, or nightly).

Keep `artifacts.yml`, `publish.yml`, `dependency_submission.yml`, and `docs.yml` behaviorally identical until the Phase 4 changes are deliberate.

---

## 10. Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| JVM 25 minimum breaks consumers | Certain | High | Ship in Phase 0 as its own announced breaking change; document prominently |
| Kotlin nullability changes Java runtime behavior | High | High | Keep JSR-305 annotations at the boundary; add NPE regression tests. **Confirmed in pilot**: a bare non-null parameter emits only `org.jetbrains.annotations.NotNull`, so `@Nonnull` must be written explicitly or the compliance rules fail. **Also confirmed in the `cache` batch**: `Intrinsics.checkNotNullParameter` preempted a documented `Checks.notNull` `IllegalArgumentException`; fixed globally with `-Xno-param-assertions` and pinned by an interop-gate test |
| Wildcard/variance mismatches (~551 sites) | High | Medium | Convert leaves first; per-package ABI diff; document each exception |
| Static interface methods lose their static-ness (~170) | Medium | High | `@JvmStatic` in companions; ABI diff verifies. **Confirmed in pilot**: both `fromId` overloads kept their static form |
| `default` methods stop being `default` | Medium | High | `-jvm-default=enable` (renamed from `-Xjvm-default=all-compatibility`); Java-implements-interface test |
| `kotlin-stdlib` enters every consumer's classpath | Certain | Medium | Release-note callout; declared `api` once a Kotlin type is public. **Occurred in pilot** — see Phase 2: stdlib is now a hard runtime dependency via `Intrinsics.checkNotNullParameter` |
| ABI gate silently examines nothing | Medium | High | **Occurred in Phase 1** — the gate dropped all Kotlin output. Negative tests must target a Kotlin class, not a Java one |
| Converting an enum leaks `EnumEntries getEntries()` | High | Medium | Confirmed: `getEntries()` is public and non-synthetic, and the compliance rules flag it. **Scoped, not resolved:** the gates import `net.dv8tion.jda.api.**` only, so `internal` enums convert cleanly (batches 37–38). The six `api` enums stay blocked until `getEntries()` is resolved |
| Converting an `api` interface with a `default` method leaks `DefaultImpls`/`access$…$jd` | High | Medium | **Confirmed in batch 39.** `SoundboardSoundSnowflake.getUrl()` as a Kotlin interface default emits `…$DefaultImpls.getUrl(…Snowflake)` and a synthetic `access$getUrl$jd(…Snowflake)`, both `public`; `ArchUnitComplianceTest` flags each as an un-annotated public method (2 violations). Neither is Java-visible and neither can carry `@Nonnull`. **Blocked:** any `api` interface with a default method stays Java, and the `ISnowflake`/`UserSnowflake` hubs depend on this being resolved. Default-free leaves (`StickerSnowflake`, `ForumTagSnowflake`) convert fine |
| Kotlin `Companion` field on a public interface | Low | Low | Additions-pass policy covers it; the field is initialized in `<clinit>` and confirmed resolvable |
| Javadoc site regression | High if Phase 4 rushed | Medium | Dokka parity gate before removing Javadoc |
| Gradle 9.7.1 vs Kotlin plugin support window | Medium | Medium | Verify the chosen Kotlin patch's supported Gradle range up front; pin the wrapper if needed |
| Shadow/minimal jar minimization breaks | Medium | Medium | Run all four jar tasks in CI every phase |
| Loss of Error Prone/OpenRewrite coverage | Certain | Low | Mirror rules in detekt; accept and document the gap |
| Reviewer fatigue and regression erosion | Medium | High | Enforce one package per PR |

---

## 11. Effort estimate

At roughly 300–500 LOC/day of review-quality conversion including tests and ABI fixing:

| Phase | Work |
|---|---|
| Phase 0 — JVM 25 target | 1–2 weeks |
| Phase 1 — Kotlin toolchain | 1–2 weeks |
| Phase 2 — Conversion | 8–14 months at one engineer; ~3–5 months with 3–4 engineers on non-overlapping packages |
| Phase 3 — Tests | 1–2 months (overlaps Phase 2) |
| Phase 4 — Publishing/docs/CI | 1–2 months |
| Phase 5 — Cleanup | 2–3 weeks |

**Total: roughly 6–12 engineer-months** to the last deleted Java file, spread across multiple releases, plus permanent maintenance overhead (Dokka, detekt, Kotlin version tracking, `kotlin-stdlib` on the consumer classpath).

Phase 0 is independently valuable and cheap; it should be treated as a deliverable regardless of what happens after it.

---

## 12. Open questions

1. Which release line carries the Java 25 minimum, and how much notice do consumers get?
2. ~~Is the version catalog's `2.4.20` the intended Kotlin target...~~ **Resolved.** `2.4.20` is a real, published Kotlin release and compiles the project cleanly on JDK 25 with Gradle 9.7.1, so it is the intended target. Nothing blocks on a downgrade to `2.4.10`. The catalog pin is shared with `formatter-recipes`, so moving it moves both.
3. Is a Kotlin-first companion module (rather than only upward conversion) wanted, to deliver value before the internals are converted?
4. Is dropping the Javadoc site for Dokka acceptable, and on what timeline?
5. Do generated DTOs stay Java indefinitely, or is KotlinPoet a stated end goal?
6. After the internals are Kotlin, does the public API keep JSR-305 nullability annotations (best interop) or migrate to Kotlin-native annotations in a future major (cleaner Kotlin, worse Java interop)?
