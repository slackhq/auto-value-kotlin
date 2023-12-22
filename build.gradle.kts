/*
 * Copyright (C) 2021 Slack Technologies, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.diffplug.spotless.LineEnding
import io.gitlab.arturbosch.detekt.Detekt
import java.net.URI
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.9.22"
  id("org.jetbrains.dokka") version "1.9.10"
  id("com.google.devtools.ksp") version "1.9.20-1.0.14"
  id("com.diffplug.spotless") version "6.22.0"
  id("com.vanniktech.maven.publish") version "0.25.3"
  id("io.gitlab.arturbosch.detekt") version "1.23.4"
}

repositories { mavenCentral() }

pluginManager.withPlugin("java") {
  configure<JavaPluginExtension> { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

  project.tasks.withType<JavaCompile>().configureEach { options.release.set(8) }
}

mavenPublishing {
  publishToMavenCentral(automaticRelease = true)
  signAllPublications()
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_1_8)
    freeCompilerArgs.addAll(
      "-progressive",
      "-opt-in=com.slack.auto.value.kotlin.ExperimentalAvkApi"
    )
  }
}

tasks.withType<Detekt>().configureEach { jvmTarget = "1.8" }

kotlin { explicitApi() }

tasks.named<DokkaTask>("dokkaHtml") {
  outputDirectory.set(rootDir.resolve("docs/0.x"))
  dokkaSourceSets.configureEach {
    skipDeprecated.set(true)
    externalDocumentationLink { url.set(URI("https://square.github.io/moshi/1.x/moshi/").toURL()) }
  }
}

spotless {
  lineEndings = LineEnding.PLATFORM_NATIVE
  format("misc") {
    target("*.md", ".gitignore")
    trimTrailingWhitespace()
    endWithNewline()
  }
  val ktfmtVersion = "0.46"
  kotlin {
    target("**/*.kt")
    ktfmt(ktfmtVersion).googleStyle()
    trimTrailingWhitespace()
    endWithNewline()
    licenseHeaderFile("spotless/spotless.kt")
    targetExclude("**/spotless.kt")
  }
  kotlinGradle {
    ktfmt(ktfmtVersion).googleStyle()
    trimTrailingWhitespace()
    endWithNewline()
    licenseHeaderFile(
      "spotless/spotless.kt",
      "(import|plugins|buildscript|dependencies|pluginManagement|rootProject)"
    )
  }
}

tasks.withType<Test>().configureEach {
  jvmArgs(
    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
  )
}

val moshiVersion = "1.15.0"

dependencies {
  ksp("dev.zacsweers.autoservice:auto-service-ksp:1.1.0")
  implementation("com.squareup.moshi:moshi:$moshiVersion")
  implementation("com.google.auto.service:auto-service:1.1.1")
  implementation("com.squareup:kotlinpoet:1.15.1")
  implementation("com.squareup.okio:okio:3.6.0")
  implementation("com.google.auto.value:auto-value:1.10.4")
  implementation("com.google.auto.value:auto-value-annotations:1.10.4")
  testImplementation("junit:junit:4.13.2")
  testImplementation("com.google.truth:truth:1.1.5")
  testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.21")
  testImplementation("com.google.testing.compile:compile-testing:0.21.0")
  testImplementation("com.ryanharter.auto.value:auto-value-moshi-extension:1.1.0")
  testImplementation("com.ryanharter.auto.value:auto-value-parcel:0.2.9")
  testImplementation("com.gabrielittner.auto.value:auto-value-with:1.1.1")
  testImplementation("com.squareup.auto.value:auto-value-redacted:1.1.1")
  testImplementation("com.google.android:android:4.1.1.4")
}
