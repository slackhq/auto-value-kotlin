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
package com.slack.auto.value.kotlin

import com.google.auto.value.processor.AutoValueProcessor
import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertThat
import com.google.testing.compile.CompilationSubject
import com.google.testing.compile.CompilationSubject.compilations
import com.google.testing.compile.Compiler.javac
import com.google.testing.compile.JavaFileObjects.forSourceString
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.tools.JavaFileObject

class AutoValueKotlinExtensionTest {
  @JvmField
  @Rule
  val tmpFolder: TemporaryFolder = TemporaryFolder.builder()
    .assureDeletion()
    .build()

  private lateinit var srcDir: File

  @Before
  fun setup() {
    srcDir = tmpFolder.newFolder("src/main/java")
  }

  // TODO
  //  avm
  //  withers
  //  parcelable
  //  nullable primitives
  //  nullable/primitive/collection defaults
  //  autoBuild?
  //  getter/setter syntax (what about mixed?)
  //  error: nested
  //  error: void, Unit, Nothing

  @Test
  fun smokeTest() {
    val result = compile(
      forSourceString(
        "test.Example",
        """
          package test;

          import com.google.auto.value.AutoValue;
          import java.util.List;
          import org.jetbrains.annotations.Nullable;

          @AutoValue
          abstract class Example {
            private static final String STRING_CONSTANT = "hello";
            private static final int INT_CONSTANT = 3;

            abstract String value();

            @Nullable
            abstract String nullableValue();

            abstract List<String> collection();

            @Nullable
            abstract List<String> nullableCollection();

            abstract boolean aBoolean();
            abstract char aChar();
            abstract byte aByte();
            abstract short aShort();
            abstract int aInt();
            abstract float aFloat();
            abstract long aLong();
            abstract double aDouble();

            boolean isNullableValuePresent() {
              return nullableValue() != null;
            }

            abstract Builder toBuilder();

            static Builder builder() {
              return null;
            }

            @AutoValue.Builder
            abstract static class Builder {
              abstract Builder value(String value);
              abstract Builder nullableValue(@Nullable String value);
              abstract Builder collection(List<String> value);
              abstract Builder nullableCollection(@Nullable List<String> value);
              abstract Builder aBoolean(boolean value);
              abstract Builder aChar(char value);
              abstract Builder aByte(byte value);
              abstract Builder aShort(short value);
              abstract Builder aInt(int value);
              abstract Builder aFloat(float value);
              abstract Builder aLong(long value);
              abstract Builder aDouble(double value);
              Builder defaultValue() {
                return value("hello");
              }
              abstract Example build();
            }
          }
        """.trimIndent()
      )
    )

    result.succeeded()
    val generated = File(srcDir, "test/Example.kt")
    assertThat(generated.exists()).isTrue()
    assertThat(generated.readText())
      //language=Kotlin
      .isEqualTo(
        """
        package test

        import kotlin.Boolean
        import kotlin.Byte
        import kotlin.Char
        import kotlin.Deprecated
        import kotlin.Double
        import kotlin.Float
        import kotlin.Int
        import kotlin.Long
        import kotlin.Nothing
        import kotlin.ReplaceWith
        import kotlin.Short
        import kotlin.String
        import kotlin.Suppress
        import kotlin.jvm.JvmName
        import kotlin.jvm.JvmStatic
        import kotlin.jvm.JvmSynthetic

        data class Example internal constructor(
          @get:JvmName("value")
          val `value`: String,
          @get:JvmName("nullableValue")
          val nullableValue: String?,
          @get:JvmName("collection")
          val collection: List<String>,
          @get:JvmName("nullableCollection")
          val nullableCollection: List<String>?,
          @get:JvmName("aBoolean")
          val aBoolean: Boolean,
          @get:JvmName("aChar")
          val aChar: Char,
          @get:JvmName("aByte")
          val aByte: Byte,
          @get:JvmName("aShort")
          val aShort: Short,
          @get:JvmName("aInt")
          val aInt: Int,
          @get:JvmName("aFloat")
          val aFloat: Float,
          @get:JvmName("aLong")
          val aLong: Long,
          @get:JvmName("aDouble")
          val aDouble: Double
        ) {
          @JvmSynthetic
          @JvmName("-value")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("value")
          )
          fun `value`(): String {
            `value`()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-nullableValue")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("nullableValue")
          )
          fun nullableValue(): String? {
            nullableValue()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-collection")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("collection")
          )
          fun collection(): List<String> {
            collection()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-nullableCollection")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("nullableCollection")
          )
          fun nullableCollection(): List<String>? {
            nullableCollection()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-aBoolean")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("aBoolean")
          )
          fun aBoolean(): Boolean {
            aBoolean()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-aChar")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("aChar")
          )
          fun aChar(): Char {
            aChar()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-aByte")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("aByte")
          )
          fun aByte(): Byte {
            aByte()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-aShort")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("aShort")
          )
          fun aShort(): Short {
            aShort()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-aInt")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("aInt")
          )
          fun aInt(): Int {
            aInt()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-aFloat")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("aFloat")
          )
          fun aFloat(): Float {
            aFloat()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-aLong")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("aLong")
          )
          fun aLong(): Long {
            aLong()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-aDouble")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("aDouble")
          )
          fun aDouble(): Double {
            aDouble()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          fun placeholder(): Nothing {
            // TODO This is a placeholder to mention the following methods need to be moved manually over:
            //    boolean isNullableValuePresent(...)
            TODO()
          }

          internal fun toBuilder(): Builder =
              Builder(value = value, nullableValue = nullableValue, collection = collection, nullableCollection = nullableCollection, aBoolean = aBoolean, aChar = aChar, aByte = aByte, aShort = aShort, aInt = aInt, aFloat = aFloat, aLong = aLong, aDouble = aDouble)

          @Suppress("LongParameterList")
          internal class Builder internal constructor(
            private var `value`: String? = null,
            private var nullableValue: String? = null,
            private var collection: List<String>? = null,
            private var nullableCollection: List<String>? = null,
            private var aBoolean: Boolean = false,
            private var aChar: Char = 0.toChar(),
            private var aByte: Byte = 0.toByte(),
            private var aShort: Short = 0.toShort(),
            private var aInt: Int = 0,
            private var aFloat: Float = 0f,
            private var aLong: Long = 0L,
            private var aDouble: Double = 0.0
          ) {
            internal fun `value`(`value`: String): Builder = apply { this.`value` = `value` }

            internal fun nullableValue(`value`: String?): Builder = apply { this.nullableValue = `value` }

            internal fun collection(`value`: List<String>): Builder = apply { this.collection = `value` }

            internal fun nullableCollection(`value`: List<String>?): Builder =
                apply { this.nullableCollection = `value` }

            internal fun aBoolean(`value`: Boolean): Builder = apply { this.aBoolean = `value` }

            internal fun aChar(`value`: Char): Builder = apply { this.aChar = `value` }

            internal fun aByte(`value`: Byte): Builder = apply { this.aByte = `value` }

            internal fun aShort(`value`: Short): Builder = apply { this.aShort = `value` }

            internal fun aInt(`value`: Int): Builder = apply { this.aInt = `value` }

            internal fun aFloat(`value`: Float): Builder = apply { this.aFloat = `value` }

            internal fun aLong(`value`: Long): Builder = apply { this.aLong = `value` }

            internal fun aDouble(`value`: Double): Builder = apply { this.aDouble = `value` }

            internal fun build(): Example =
                Example(`value` = `value` ?: error("value == null"), nullableValue = nullableValue, collection = collection ?: error("collection == null"), nullableCollection = nullableCollection, aBoolean = aBoolean, aChar = aChar, aByte = aByte, aShort = aShort, aInt = aInt, aFloat = aFloat, aLong = aLong, aDouble = aDouble)
 
            fun placeholder(): Nothing {
              // TODO This is a placeholder to mention the following methods need to be moved manually over:
              //    test.Example.Builder defaultValue(...)
              TODO()
            }
          }

          companion object {
            private const val STRING_CONSTANT: String = "hello"

            private const val INT_CONSTANT: Int = 3

            @JvmStatic
            internal fun builder(): Builder {
              TODO("Replace this with the implementation from the source class")
            }
          }
        }

        """.trimIndent()
      )
  }

  @Test
  fun creators() {
    val result = compile(
      forSourceString(
        "test.Example",
        """
          package test;

          import com.google.auto.value.AutoValue;

          @AutoValue
          abstract class Example {

            abstract String value();

            static Example create(String value) {
              return null;
            }
          }
        """.trimIndent()
      )
    )

    result.succeeded()
    val generated = File(srcDir, "test/Example.kt")
    assertThat(generated.exists()).isTrue()
    assertThat(generated.readText())
      .isEqualTo(
        """
          package test

          import kotlin.Deprecated
          import kotlin.ReplaceWith
          import kotlin.String
          import kotlin.jvm.JvmName
          import kotlin.jvm.JvmStatic
          import kotlin.jvm.JvmSynthetic

          data class Example(
            @get:JvmName("value")
            val `value`: String
          ) {
            @JvmSynthetic
            @JvmName("-value")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("value")
            )
            fun `value`(): String {
              `value`()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            companion object {
              @JvmStatic
              @JvmName("-create")
              @Deprecated(
                message = "Use invoke()",
                replaceWith = ReplaceWith("test.Example(value)")
              )
              internal fun create(`value`: String): Example {
                create(value)
                TODO("Remove this function. Use the above line to auto-migrate.")
              }

              @JvmStatic
              @JvmName("create")
              internal operator fun invoke(`value`: String): Example = Example(value = value)
            }
          }
        """.trimIndent()
      )
  }

  private fun compile(vararg sourceFiles: JavaFileObject): CompilationSubject {
    val compilation = javac()
      .withOptions("-A${AutoValueKotlinExtension.OPT_SRC}=${srcDir.absolutePath}")
      .withProcessors(AutoValueProcessor(listOf(AutoValueKotlinExtension())))
      .compile(*sourceFiles)
    return assertAbout(compilations())
      .that(compilation)
  }
}
