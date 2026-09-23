# AGENTS.md

Repository-specific rules for AI agents and automated contributors working in this project.
Humans should read [`.github/CONTRIBUTING.md`](.github/CONTRIBUTING.md) first; the rules below are the machine-enforced subset plus the guardrails that are not obvious from the code.

This is **JDA (Java Discord API)** — a published library, not an application. Almost every rule here exists to protect the public API contract for downstream consumers.

## Project facts

- Gradle 9.7.1 (wrapper), built and tested on a **JDK 25** toolchain.
- Published bytecode target is **JVM 25**: `javaVersion = JavaLanguageVersion.of(25)`, `options.release = 25`, and `verifyBytecodeVersion` asserts class-file major version **69**.
- ~208,000 LOC across ~1,218 Java files in `src/main`, plus ~91 test files.
- Split: `net.dv8tion.jda.api` (public, 805 files) vs `net.dv8tion.jda.internal` (405 files, excluded from Javadoc).
- Several Gradle modules: root, `buildSrc` (Kotlin, generates Java REST model sources via JavaPoet), `formatter-recipes` (Kotlin, OpenRewrite recipe), plus the `examples` source set.
- Consumer-facing docs live at <https://docs.jda.wiki> and the wiki at <https://jda.wiki>.

## Commands

| Task | Purpose |
|---|---|
| `./gradlew build` | Full build; run before opening a PR |
| `./gradlew test` | Main test suite (JUnit 5, Mockito, AssertJ, ArchUnit) |
| `./gradlew format` | Apply all formatters (Spotless + Error Prone patching + version catalog) |
| `./gradlew checkFormat` | Verify formatting without applying (`spotlessCheck` + `rewriteDryRun`) |
| `./gradlew check` | `checkFormat` + tests + `verifyBytecodeVersion` |
| `./gradlew updateTestSnapshots` | Regenerate snapshot test fixtures |
| `./gradlew generateApiModels` | Regenerate REST DTOs from the Discord OpenAPI spec |
| `./gradlew javadoc` | Build the published API docs |
| `./gradlew publishToMavenLocal` | Publish locally (used by JitPack and downstream testing) |

Always run `./gradlew format` before `./gradlew build`. A large share of CI failures in this repo are formatting or OpenRewrite discrepancies, not logic errors.

## Hard rules

### Bytecode target
Every compiled class in `main` must be **JVM 25** bytecode (major version 69). `verifyBytecodeVersion` runs automatically after `compileJava` and fails the build otherwise. The minimum runtime for consumers is Java 25. Do not lower `options.release` or `javaVersion`. When Kotlin is introduced to `src/main`, its `jvmTarget` must also be 25 — see `MIGRATION.md`.

### Warning-free compilation
`compileJava` uses `-Werror` with `-Xlint:all`. Only these are suppressed, each for a stated reason:
`-Xlint:-removal` (8 classes override `finalize()`, deprecated for removal but still supported), `-Xlint:-serial` (exceptions are not meant to be serialized), `-Xlint:-this-escape` (member calls in constructors for argument checks), `-Xlint:-try` (resources used as locks), `-Xlint:-varargs` (handled by `@SafeVarargs`).

Do not add blanket suppressions. If a new warning must be suppressed, add a narrow, commented `-Xlint` entry or fix the cause.

### Error Prone
Enabled for `main` and `test` (disabled entirely for `examples`). There is a curated `disable(...)` list in `build.gradle.kts`; do not extend it casually, and never `disableAllChecks` outside `examples`.

The list includes style checks that only become applicable at a higher language level (`PatternMatchingInstanceof`, `StatementSwitchToExpressionSwitch`, `StringConcatToTextBlock`, `UnnamedVariable`). These are syntax-conversion suggestions rather than defects, and converting the ~90 affected call sites is a deliberate follow-up, not a build fix. New code should still prefer modern syntax where it reads better.

### Nullability annotations (non-negotiable for public API)
The `net.dv8tion.jda.api` package maintains an enforced nullability contract, checked by `ArchUnitComplianceTest`:

- Every public method returning `RestAction` or `CompletableFuture` must be annotated `@CheckReturnValue` **and** `@Nonnull`.
- Every public method returning a non-primitive type must carry `@Nonnull`, `@Nullable`, `@Contract`, or `@UnknownNullability`.
- Non-primitive parameters must have a nullability annotation, except for the documented exclusions in the test.
- Methods returning primitives must **not** have nullability annotations.

Annotations are JSR-305 (`javax.annotation.Nonnull` / `javax.annotation.Nullable`, from `com.google.code.findbugs:jsr305`), **not** JetBrains annotations. `rewrite.yml` migrates stray JetBrains annotations to JSR-305, and the `MigrateToJavaxAnnotations` recipe enforces it. Use `net.dv8tion.jda.annotations.*` for project-specific annotations (`@Incubating`, `@DeprecatedSince`, `@ReplaceWith`, `@UnknownNullability`).

### API compatibility
This is a published library. Do not:
- change or reorder public method signatures, or change their nullability;
- rename public types, methods, or enum constants without `@Deprecated` + `@ReplaceWith`;
- widen or narrow visibility of public API;
- convert a public type in ways that alter its bytecode shape (see `MIGRATION.md` for the Kotlin-specific cases).

New public API requires documentation and, for features, a usage example in the PR description.

### Documentation
All public API methods and types must have Javadoc. Javadoc is validated (`Xdoclint:all,-missing`) and formatted by a custom OpenRewrite recipe, `net.dv8tion.jda.recipe.JavadocFormatter`, which `rewriteDryRun` enforces. Anything under `internal` is excluded from the published docs, so internal code does not need the same treatment.

`@Incubating` marks functionality that may change in a future release; use it for new API that is not yet stable.

### Formatting
- Palantir Java Format (2.84.0), Javadoc formatting disabled in Spotless because the OpenRewrite recipe owns it.
- Apache 2.0 license header from `gradle/copyright-header.txt` is applied to `src/**/*.java` — new files must include it.
- Import order: `""`, `java`, `javax`, `\#`.
- Line endings: CRLF for source (`.editorconfig`, `.gitattributes`); `gradlew` is LF.
- Kotlin files under `buildSrc`/`formatter-recipes` are covered by `spotlessKotlinGradle`.

### Build configuration
- The version catalog lives at `gradle/libs.versions.toml`; use `alias(libs...)`/`libs...` rather than hardcoding versions.
- `@pin` comments in the catalog mark versions held back deliberately (e.g. JUnit 6 requires Java 17+). Do not "upgrade" a pinned entry without understanding why it is pinned.
- Dependency versions are managed by `versionCatalogUpdate` with a stable-only selector.

## Workflow rules

- **One logical change per PR.** Per `CONTRIBUTING.md`, do not bundle unrelated refactors. During the Kotlin migration this means strictly one package per PR.
- **Reference relevant issues or API docs**, especially for new Discord features — link the upstream `discord-api-docs` PR.
- **Follow JDA's existing coding style even where it is unconventional.** Consistency beats local preference here.
- Run `./gradlew format` then `./gradlew build` before finishing. Never commit with `check` failing.
- Prefer editing existing files over creating new ones; do not leave temporary or duplicate `*_test`/`*_fix`/`*_old` files behind.

## Tests

- JUnit 5 (`org.junit.jupiter`), Mockito (as a `-javaagent`), AssertJ, ArchUnit.
- `ArchUnitComplianceTest` and `ComponentConsistencyComplianceTest` enforce the API contract. If they fail after your change, the change is almost certainly wrong — do not weaken or exclude the rule to make it pass.
- Snapshot tests exist (`AbstractSnapshotTest`); regenerate with `./gradlew updateTestSnapshots` only when the output change is intended and reviewed.
- Test sources are not the place for `-Werror` exemptions; write warning-free tests.

## Security and scope

- Do not commit credentials, tokens, or `publishing-keyring.gpg`. `.gitignore` already covers `config.json`, `tokens.json`, and `publishing-keyring.gpg`.
- Do not modify `.github/workflows/publish.yml` secrets handling or `nmcp` publishing configuration without explicit human direction; publishing is a release-managed process.
- Treat `formatter-recipes` and `buildSrc` as build infrastructure: changes there affect the whole build and every contributor.

## Migration in progress

See [`MIGRATION.md`](MIGRATION.md). During the Kotlin migration, additional rules apply:

- Preserve the public ABI for Java consumers; the ABI diff gate must pass.
- Keep JSR-305 nullability annotations on the public API boundary rather than relying on Kotlin's nullability.
- Convert from the leaves inward, one package per PR, never the large files (`Guild`, `MessageChannel`, `Message`, `EntityBuilder`, `JDA`) as single units.
- `src/main/java` and `src/main/kotlin` coexist during the transition; do not delete Java sources for a package until its Kotlin replacement has passed the full verification suite.

When a rule here conflicts with a plausible shortcut, the rule wins. If a rule seems wrong, raise it rather than working around it.
