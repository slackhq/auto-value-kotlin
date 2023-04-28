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
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URL

plugins {
  kotlin("jvm") version "1.5.31"
  id("org.jetbrains.dokka") version "1.5.31"
  id("com.google.devtools.ksp") version "1.8.21-1.0.11"
  id("com.diffplug.spotless") version "6.0.0"
  id("com.vanniktech.maven.publish") version "0.18.0"
  id("io.gitlab.arturbosch.detekt") version "1.18.1"
}

repositories {
  mavenCentral()
}

pluginManager.withPlugin("java") {
  configure<JavaPluginExtension> {
    toolchain {
      languageVersion.set(JavaLanguageVersion.of(17))
    }
  }

  project.tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
  }
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    jvmTarget = "1.8"
    @Suppress("SuspiciousCollectionReassignment")
    freeCompilerArgs += listOf("-progressive", "-Xopt-in=kotlin.RequiresOptIn", "-Xopt-in=com.slack.auto.value.kotlin.ExperimentalAvkApi")
  }
}

tasks.withType<Detekt>().configureEach {
  jvmTarget = "1.8"
}

kotlin {
  explicitApi()
}

tasks.named<DokkaTask>("dokkaHtml") {
  outputDirectory.set(rootDir.resolve("docs/0.x"))
  dokkaSourceSets.configureEach {
    skipDeprecated.set(true)
    externalDocumentationLink {
      url.set(URL("https://square.github.io/moshi/1.x/moshi/"))
    }
  }
}

spotless {
  format("misc") {
    target("*.md", ".gitignore")
    trimTrailingWhitespace()
    endWithNewline()
  }
  val ktlintVersion = "0.41.0"
  val ktlintUserData = mapOf("indent_size" to "2", "continuation_indent_size" to "2")
  kotlin {
    target("**/*.kt")
    ktlint(ktlintVersion).userData(ktlintUserData)
    trimTrailingWhitespace()
    endWithNewline()
    licenseHeaderFile("spotless/spotless.kt")
    targetExclude("**/spotless.kt")
  }
  kotlinGradle {
    ktlint(ktlintVersion).userData(ktlintUserData)
    trimTrailingWhitespace()
    endWithNewline()
    licenseHeaderFile("spotless/spotless.kt", "(import|plugins|buildscript|dependencies|pluginManagement|rootProject)")
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

val moshiVersion = "1.12.0"
dependencies {
  ksp("dev.zacsweers.autoservice:auto-service-ksp:1.0.0")
  implementation("com.squareup.moshi:moshi:$moshiVersion")
  implementation("com.google.auto.service:auto-service:1.0")
  implementation("com.squareup:kotlinpoet:1.10.1")
  implementation("com.squareup.okio:okio:3.0.0")
  implementation("com.google.auto.value:auto-value:1.8.2")
  implementation("com.google.auto.value:auto-value-annotations:1.8.2")
  testImplementation("junit:junit:4.13.2")
  testImplementation("com.google.truth:truth:1.1.3")
  testImplementation("org.jetbrains.kotlin:kotlin-test:1.5.31")
  testImplementation("com.google.testing.compile:compile-testing:0.19")
  testImplementation("com.ryanharter.auto.value:auto-value-moshi-extension:1.1.0")
  testImplementation("com.ryanharter.auto.value:auto-value-parcel:0.2.9")
  testImplementation("com.gabrielittner.auto.value:auto-value-with:1.1.1")
  testImplementation("com.squareup.auto.value:auto-value-redacted:1.1.1")
  testImplementation("com.google.android:android:4.1.1.4")
}
