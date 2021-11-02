AutoValue Kotlin
================

auto-value-kotlin (AVK) is an [AutoValue](https://github.com/google/auto) extension + processor
that generates binary-and-source-compatible, equivalent Kotlin `data` classes.

The intended use of this project is to ease migration from AutoValue classes to Kotlin data classes
and should be used ad-hoc rather than continuously. The idea is that it does 95% of the work for you
while leaving obvious placeholders or TODOs in places that require manual adjustment.

## Installation

Add the dependency to kapt

```kotlin
dependencies {
  kapt("com.google.auto.value:auto-value:<version>")
  kapt("com.slack.auto.value:auto-value-kotlin:<version>")
}
```

Snapshots of the development version are available in [Sonatype's `snapshots` repository][snap].

Configure the following arguments in your build file

```kotlin
kapt {
  arguments {
    // Source dir to output files to. This should usually be the src/main/java or src/main/kotlin
    // path of the project you’re running this in.
    // REQUIRED
    arg("avkSrc", file("src/main/java"))

    // Colon-delimited string of simple class names to convert
    // OPTIONAL. By default, AVK will convert all AutoValue models on the compilation.
    arg("avkTargets", "ClassOne:ClassTwo")

    // Boolean option to ignore nested classes. By default, AVK will error out when it encounters
    // a nested non-AutoValue class as it has no means of safely converting the class since its
    // references are always qualified. This option can be set to true to make AVK just skip them
    // and emit a warning.
    // AVK will automatically convert nested AutoValue and enum classes along the way.
    // OPTIONAL. False by default.
    arg("avkIgnoreNested", "true")
  }
}
```

## Workflow

_Pre-requisites_
* Move any nested non-AutoValue/non-enum classes to top-level first (even if just temporarily for the migration).
  * You can optionally choose to ignore nested non-AutoValue classes or only specific targets per the configuration
    options detailed in the Installation section above.
* Ensure no classes outside of the original AutoValue class accesses its generated `AutoValue_` class.
* Clean once to clear up any generated file references.

Add the AVK configuration to your project's build file

Run the kapt task, such as `./gradlew :path:to:library:kaptReleaseKotlin` or `/gradlew :path:to:library:kaptKotlin`.

Now you'll see generated equivalent Kotlin files in the source set defined by `avkSrc`.

_Repeat the following for each generated file._
- Open the original AV file and change the class name to something else, so it doesn’t link in the IDE to existing places. For example: rename `class AuthModel` to `class AuthModelOld`.
![image](https://user-images.githubusercontent.com/1361086/137796491-8dc422a9-6d9b-4514-9c6c-758d51e6b216.png)
- Open the generated Kotlin file (i.e. `AuthModel.kt`). The file will look a little noisy, but this is by design to intelligently best-effort preserve Java and Kotlin interop.
    1. You shouldn’t need to edit any Java usages 🤞, only Kotlin usages to switch to the property accessors. The generated deprecated functions will have `@JvmName` annotations with funny values that hide them from Java callers.
- For any `@Deprecated` functions, these are functions that may have existing Kotlin usages that need to be updated. These come with `ReplaceWith` information to allow the IDE to auto-migrate these where possible. For each of these, you should…
    1. Put your cursor on the first line of the function (it looks like a recursive call)
    2. option+enter on it to bring up inspections, then click “Replace all usages in project”.
    3. Delete the function
![image](https://user-images.githubusercontent.com/1361086/137796511-a825ae37-ac75-49db-9356-6fefc9546e48.png)
- For any “placeholders”, these are cases where AVK couldn’t migrate them automatically. This includes final non-property methods, static factories/helpers, etc. For these, it’s best to copy them in the original AV file (i.e. from AuthModel.java) and paste them into the new Kotlin file. The IDE will automatically ask if you want to convert it to Kotlin and do most of the work for you.
![image](https://user-images.githubusercontent.com/1361086/137796529-566b1d7a-c3f8-4d45-8b9d-99dd2c9d5825.png)
- When done, delete the old Java file.
  - If you want to go the extra mile for review and have git recognize the file migration, you can rename it first with no changes, commit the rename, then commit the content changes in a separate commit.

When done with all the migrations, revert the added AVK configuration to your build file and be done with it!

## Supported features/interop

* Basic nullability
* Builders
* Static creators and static builders
* [auto-value-moshi](https://github.com/rharter/auto-value-moshi)
  * Converts to just using standard Moshi Kotlin code gen
* [auto-value-parcel](https://github.com/rharter/auto-value-parcel)
  * Converts to just using [Parcelize](https://developer.android.com/kotlin/parcelize).
* [auto-value-with](https://github.com/gabrielittner/auto-value-with)
* [auto-value-redacted](https://github.com/square/auto-value-redacted)
  * Only adds the redacted annotation to the property. Implementation is left to the consumer, we use [redacted-compiler-plugin](https://github.com/ZacSweers/redacted-compiler-plugin).

License
--------

    Copyright 2021 Slack Technologies, LLC

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


[snap]: https://oss.sonatype.org/content/repositories/snapshots/com/slack/auto/value/
