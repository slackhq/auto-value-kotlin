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

import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertThat
import com.google.testing.compile.CompilationSubject
import com.google.testing.compile.CompilationSubject.compilations
import com.google.testing.compile.Compiler
import com.google.testing.compile.Compiler.javac
import com.google.testing.compile.JavaFileObjects.forSourceString
import java.io.File
import javax.tools.JavaFileObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@Suppress("LongMethod", "LargeClass", "MaxLineLength")
class AutoValueKotlinExtensionTest {
  @JvmField
  @Rule
  val tmpFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

  private lateinit var srcDir: File

  @Before
  fun setup() {
    srcDir = tmpFolder.newFolder("src/main/java")
  }

  @Test
  fun smokeTest() {
    val result =
      compile(
        forSourceString(
          "test.Example",
          """
          package test;

          import android.os.Parcelable;
          import com.google.auto.value.AutoValue;
          import com.slack.auto.value.kotlin.Redacted;
          import com.squareup.moshi.Json;
          import com.squareup.moshi.JsonClass;
          import java.util.List;
          import com.google.common.collect.ImmutableList;
          import org.jetbrains.annotations.Nullable;

          @JsonClass(generateAdapter = true, generator = "avm")
          @AutoValue
          abstract class Example implements Parcelable {
            private static final String STRING_CONSTANT = "hello";
            private static final int INT_CONSTANT = 3;

            @Json(name = "_value")
            abstract String value();

            @Nullable
            abstract String nullableValue();

            abstract List<String> collection();

            @Nullable
            abstract List<String> nullableCollection();

            abstract ImmutableList<String> requiredBuildableCollection();

            abstract boolean aBoolean();
            abstract char aChar();
            abstract byte aByte();
            abstract short aShort();
            abstract int aInt();
            abstract float aFloat();
            abstract long aLong();
            abstract double aDouble();

            @Redacted
            abstract String redactedString();

            boolean isNullableValuePresent() {
              return nullableValue() != null;
            }

            abstract Builder toBuilder();

            static Builder builder() {
              return null;
            }

            enum ExampleEnum {
              ENUM_VALUE,
              @Redacted
              ANNOTATED_ENUM_VALUE
            }

            @AutoValue.Builder
            abstract static class Builder {
              abstract Builder value(String value);
              abstract Builder nullableValue(@Nullable String value);
              abstract Builder collection(List<String> value);
              abstract Builder nullableCollection(@Nullable List<String> value);
              abstract Builder requiredBuildableCollection(ImmutableList<String> value);
              abstract ImmutableList.Builder<String> requiredBuildableCollectionBuilder();
              abstract Builder aBoolean(boolean value);
              abstract Builder aChar(char value);
              abstract Builder aByte(byte value);
              abstract Builder aShort(short value);
              abstract Builder aInt(int value);
              abstract Builder aFloat(float value);
              abstract Builder aLong(long value);
              abstract Builder aDouble(double value);
              abstract Builder redactedString(String value);
              Builder defaultValue() {
                return value("hello");
              }
              abstract Example build();
            }
          }
        """
            .trimIndent()
        )
      )

    result.succeeded()
    val generated = File(srcDir, "test/Example.kt")
    assertThat(generated.exists()).isTrue()
    assertThat(generated.readText())
      // language=Kotlin
      .isEqualTo(
        """
        package test

        import android.os.Parcelable
        import com.google.common.collect.ImmutableList
        import com.slack.auto.`value`.kotlin.Redacted
        import com.squareup.moshi.Json
        import com.squareup.moshi.JsonClass
        import kotlin.jvm.JvmName
        import kotlin.jvm.JvmStatic
        import kotlin.jvm.JvmSynthetic
        import kotlinx.parcelize.Parcelize

        @JsonClass(generateAdapter = true)
        @Parcelize
        data class Example internal constructor(
          @Json(name = "_value")
          @get:JvmName("value")
          val `value`: String,
          @get:JvmName("nullableValue")
          val nullableValue: String? = null,
          @get:JvmName("collection")
          val collection: List<String>,
          @get:JvmName("nullableCollection")
          val nullableCollection: List<String>? = null,
          @get:JvmName("requiredBuildableCollection")
          val requiredBuildableCollection: ImmutableList<String>,
          @get:JvmName("aBoolean")
          val aBoolean: Boolean = false,
          @get:JvmName("aChar")
          val aChar: Char = 0.toChar(),
          @get:JvmName("aByte")
          val aByte: Byte = 0.toByte(),
          @get:JvmName("aShort")
          val aShort: Short = 0.toShort(),
          @get:JvmName("aInt")
          val aInt: Int = 0,
          @get:JvmName("aFloat")
          val aFloat: Float = 0f,
          @get:JvmName("aLong")
          val aLong: Long = 0L,
          @get:JvmName("aDouble")
          val aDouble: Double = 0.0,
          @get:JvmName("redactedString")
          @Redacted
          val redactedString: String,
        ) : Parcelable {
          @JvmSynthetic
          @JvmName("-value")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("value"),
          )
          fun `value`(): String {
            `value`()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-nullableValue")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("nullableValue"),
          )
          fun nullableValue(): String? {
            nullableValue()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-collection")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("collection"),
          )
          fun collection(): List<String> {
            collection()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-nullableCollection")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("nullableCollection"),
          )
          fun nullableCollection(): List<String>? {
            nullableCollection()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-requiredBuildableCollection")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("requiredBuildableCollection"),
          )
          fun requiredBuildableCollection(): ImmutableList<String> {
            requiredBuildableCollection()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-aBoolean")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("aBoolean"),
          )
          fun aBoolean(): Boolean {
            aBoolean()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-aChar")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("aChar"),
          )
          fun aChar(): Char {
            aChar()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-aByte")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("aByte"),
          )
          fun aByte(): Byte {
            aByte()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-aShort")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("aShort"),
          )
          fun aShort(): Short {
            aShort()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-aInt")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("aInt"),
          )
          fun aInt(): Int {
            aInt()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-aFloat")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("aFloat"),
          )
          fun aFloat(): Float {
            aFloat()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-aLong")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("aLong"),
          )
          fun aLong(): Long {
            aLong()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-aDouble")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("aDouble"),
          )
          fun aDouble(): Double {
            aDouble()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          @JvmSynthetic
          @JvmName("-redactedString")
          @Deprecated(
            message = "Use the property",
            replaceWith = ReplaceWith("redactedString"),
          )
          fun redactedString(): String {
            redactedString()
            TODO("Remove this function. Use the above line to auto-migrate.")
          }

          fun placeholder(): Nothing {
            // TODO This is a placeholder to mention the following methods need to be moved manually over:
            //    boolean isNullableValuePresent(...)
            TODO()
          }

          internal fun toBuilder(): Builder =
              Builder(value = value, nullableValue = nullableValue, collection = collection, nullableCollection = nullableCollection, requiredBuildableCollection = requiredBuildableCollection, aBoolean = aBoolean, aChar = aChar, aByte = aByte, aShort = aShort, aInt = aInt, aFloat = aFloat, aLong = aLong, aDouble = aDouble, redactedString = redactedString)

          @Suppress("LongParameterList")
          internal class Builder internal constructor(
            private var `value`: String? = null,
            private var nullableValue: String? = null,
            private var collection: List<String>? = null,
            private var nullableCollection: List<String>? = null,
            private var requiredBuildableCollection: ImmutableList<String>? = null,
            private var aBoolean: Boolean = false,
            private var aChar: Char = 0.toChar(),
            private var aByte: Byte = 0.toByte(),
            private var aShort: Short = 0.toShort(),
            private var aInt: Int = 0,
            private var aFloat: Float = 0f,
            private var aLong: Long = 0L,
            private var aDouble: Double = 0.0,
            private var redactedString: String? = null,
          ) {
            private var requiredBuildableCollectionBuilder: ImmutableList.Builder<String>? = null

            internal fun `value`(`value`: String): Builder = apply { this.`value` = `value` }

            internal fun nullableValue(`value`: String?): Builder = apply { this.nullableValue = `value` }

            internal fun collection(`value`: List<String>): Builder = apply { this.collection = `value` }

            internal fun nullableCollection(`value`: List<String>?): Builder =
                apply { this.nullableCollection = `value` }

            internal fun requiredBuildableCollectionBuilder(): ImmutableList.Builder<String> {
              if (requiredBuildableCollectionBuilder == null) {
                requiredBuildableCollectionBuilder = ImmutableList.builder()
                if (requiredBuildableCollection != null) {
                  requiredBuildableCollectionBuilder.addAll(requiredBuildableCollection)
                  requiredBuildableCollection = null
                }
              }
              return requiredBuildableCollectionBuilder
            }

            internal fun requiredBuildableCollection(`value`: ImmutableList<String>): Builder {
              check(requiredBuildableCollectionBuilder == null) {
                "Cannot set requiredBuildableCollection after calling requiredBuildableCollectionBuilder()"
              }
              this.requiredBuildableCollection = `value`
              return this
            }

            internal fun aBoolean(`value`: Boolean): Builder = apply { this.aBoolean = `value` }

            internal fun aChar(`value`: Char): Builder = apply { this.aChar = `value` }

            internal fun aByte(`value`: Byte): Builder = apply { this.aByte = `value` }

            internal fun aShort(`value`: Short): Builder = apply { this.aShort = `value` }

            internal fun aInt(`value`: Int): Builder = apply { this.aInt = `value` }

            internal fun aFloat(`value`: Float): Builder = apply { this.aFloat = `value` }

            internal fun aLong(`value`: Long): Builder = apply { this.aLong = `value` }

            internal fun aDouble(`value`: Double): Builder = apply { this.aDouble = `value` }

            internal fun redactedString(`value`: String): Builder = apply { this.redactedString = `value` }

            internal fun build(): Example {
              if (requiredBuildableCollectionBuilder != null) {
                this.requiredBuildableCollection = requiredBuildableCollectionBuilder.build()
              } else if (this.requiredBuildableCollection == null) {
                this.requiredBuildableCollection = ImmutableList.of()
              }
              return Example(`value` = `value` ?: error("value == null"), nullableValue = nullableValue, collection = collection ?: error("collection == null"), nullableCollection = nullableCollection, requiredBuildableCollection = requiredBuildableCollection ?: error("requiredBuildableCollection == null"), aBoolean = aBoolean, aChar = aChar, aByte = aByte, aShort = aShort, aInt = aInt, aFloat = aFloat, aLong = aLong, aDouble = aDouble, redactedString = redactedString ?: error("redactedString == null"))
            }

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

          @JsonClass(generateAdapter = false)
          internal enum class ExampleEnum {
            ENUM_VALUE,
            @Redacted
            ANNOTATED_ENUM_VALUE,
          }
        }

        """
          .trimIndent()
      )
  }

  @Test
  fun creators() {
    val result =
      compile(
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
        """
            .trimIndent()
        )
      )

    result.succeeded()
    val generated = File(srcDir, "test/Example.kt")
    assertThat(generated.exists()).isTrue()
    assertThat(generated.readText())
      .isEqualTo(
        """
          package test

          import kotlin.jvm.JvmName
          import kotlin.jvm.JvmStatic
          import kotlin.jvm.JvmSynthetic

          data class Example(
            @get:JvmName("value")
            val `value`: String,
          ) {
            @JvmSynthetic
            @JvmName("-value")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("value"),
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
                replaceWith = ReplaceWith("test.Example(value)"),
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

        """
          .trimIndent()
      )
  }

  @Test
  fun wither() {
    val result =
      compile(
        forSourceString(
          "test.Example",
          """
          package test;

          import com.google.auto.value.AutoValue;

          @AutoValue
          abstract class Example {

            abstract String value();

            abstract String withValue();
          }
        """
            .trimIndent()
        )
      )

    result.succeeded()
    val generated = File(srcDir, "test/Example.kt")
    assertThat(generated.exists()).isTrue()
    assertThat(generated.readText())
      .isEqualTo(
        """
          package test

          import kotlin.jvm.JvmName
          import kotlin.jvm.JvmSynthetic

          data class Example(
            @get:JvmName("value")
            val `value`: String,
            @get:JvmName("withValue")
            val withValue: String,
          ) {
            @JvmSynthetic
            @JvmName("-value")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("value"),
            )
            fun `value`(): String {
              `value`()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-withValue")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("withValue"),
            )
            fun withValue(): String {
              withValue()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            fun withValue(`value`: String): Example = copy(`value` = `value`)
          }

        """
          .trimIndent()
      )
  }

  @Test
  fun defaultsInConstructor() {
    val result =
      compile(
        forSourceString(
          "test.Example",
          """
          package test;

          import com.google.auto.value.AutoValue;
          import java.util.List;
          import org.jetbrains.annotations.Nullable;

          @AutoValue
          abstract class Example {

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

            abstract Boolean aBooleanReference();
            abstract Character aCharReference();
            abstract Byte aByteReference();
            abstract Short aShortReference();
            abstract Integer aIntReference();
            abstract Float aFloatReference();
            abstract Long aLongReference();
            abstract Double aDoubleReference();

            abstract Boolean aNullableBooleanReference();
            abstract Character aNullableCharReference();
            abstract Byte aNullableByteReference();
            abstract Short aNullableShortReference();
            abstract Integer aNullableIntReference();
            abstract Float aNullableFloatReference();
            abstract Long aNullableLongReference();
            abstract Double aNullableDoubleReference();

          }
        """
            .trimIndent()
        )
      )

    result.succeeded()
    val generated = File(srcDir, "test/Example.kt")
    assertThat(generated.exists()).isTrue()
    assertThat(generated.readText())
      .isEqualTo(
        """
          package test

          import kotlin.jvm.JvmName
          import kotlin.jvm.JvmSynthetic

          data class Example(
            @get:JvmName("value")
            val `value`: String,
            @get:JvmName("nullableValue")
            val nullableValue: String? = null,
            @get:JvmName("collection")
            val collection: List<String>,
            @get:JvmName("nullableCollection")
            val nullableCollection: List<String>? = null,
            @get:JvmName("aBoolean")
            val aBoolean: Boolean = false,
            @get:JvmName("aChar")
            val aChar: Char = 0.toChar(),
            @get:JvmName("aByte")
            val aByte: Byte = 0.toByte(),
            @get:JvmName("aShort")
            val aShort: Short = 0.toShort(),
            @get:JvmName("aInt")
            val aInt: Int = 0,
            @get:JvmName("aFloat")
            val aFloat: Float = 0f,
            @get:JvmName("aLong")
            val aLong: Long = 0L,
            @get:JvmName("aDouble")
            val aDouble: Double = 0.0,
            @get:JvmName("aBooleanReference")
            val aBooleanReference: Boolean = false,
            @get:JvmName("aCharReference")
            val aCharReference: Char = 0.toChar(),
            @get:JvmName("aByteReference")
            val aByteReference: Byte = 0.toByte(),
            @get:JvmName("aShortReference")
            val aShortReference: Short = 0.toShort(),
            @get:JvmName("aIntReference")
            val aIntReference: Int = 0,
            @get:JvmName("aFloatReference")
            val aFloatReference: Float = 0f,
            @get:JvmName("aLongReference")
            val aLongReference: Long = 0L,
            @get:JvmName("aDoubleReference")
            val aDoubleReference: Double = 0.0,
            @get:JvmName("aNullableBooleanReference")
            val aNullableBooleanReference: Boolean = false,
            @get:JvmName("aNullableCharReference")
            val aNullableCharReference: Char = 0.toChar(),
            @get:JvmName("aNullableByteReference")
            val aNullableByteReference: Byte = 0.toByte(),
            @get:JvmName("aNullableShortReference")
            val aNullableShortReference: Short = 0.toShort(),
            @get:JvmName("aNullableIntReference")
            val aNullableIntReference: Int = 0,
            @get:JvmName("aNullableFloatReference")
            val aNullableFloatReference: Float = 0f,
            @get:JvmName("aNullableLongReference")
            val aNullableLongReference: Long = 0L,
            @get:JvmName("aNullableDoubleReference")
            val aNullableDoubleReference: Double = 0.0,
          ) {
            @JvmSynthetic
            @JvmName("-value")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("value"),
            )
            fun `value`(): String {
              `value`()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-nullableValue")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("nullableValue"),
            )
            fun nullableValue(): String? {
              nullableValue()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-collection")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("collection"),
            )
            fun collection(): List<String> {
              collection()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-nullableCollection")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("nullableCollection"),
            )
            fun nullableCollection(): List<String>? {
              nullableCollection()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aBoolean")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aBoolean"),
            )
            fun aBoolean(): Boolean {
              aBoolean()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aChar")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aChar"),
            )
            fun aChar(): Char {
              aChar()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aByte")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aByte"),
            )
            fun aByte(): Byte {
              aByte()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aShort")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aShort"),
            )
            fun aShort(): Short {
              aShort()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aInt")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aInt"),
            )
            fun aInt(): Int {
              aInt()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aFloat")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aFloat"),
            )
            fun aFloat(): Float {
              aFloat()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aLong")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aLong"),
            )
            fun aLong(): Long {
              aLong()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aDouble")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aDouble"),
            )
            fun aDouble(): Double {
              aDouble()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aBooleanReference")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aBooleanReference"),
            )
            fun aBooleanReference(): Boolean {
              aBooleanReference()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aCharReference")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aCharReference"),
            )
            fun aCharReference(): Char {
              aCharReference()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aByteReference")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aByteReference"),
            )
            fun aByteReference(): Byte {
              aByteReference()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aShortReference")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aShortReference"),
            )
            fun aShortReference(): Short {
              aShortReference()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aIntReference")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aIntReference"),
            )
            fun aIntReference(): Int {
              aIntReference()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aFloatReference")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aFloatReference"),
            )
            fun aFloatReference(): Float {
              aFloatReference()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aLongReference")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aLongReference"),
            )
            fun aLongReference(): Long {
              aLongReference()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aDoubleReference")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aDoubleReference"),
            )
            fun aDoubleReference(): Double {
              aDoubleReference()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aNullableBooleanReference")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aNullableBooleanReference"),
            )
            fun aNullableBooleanReference(): Boolean {
              aNullableBooleanReference()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aNullableCharReference")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aNullableCharReference"),
            )
            fun aNullableCharReference(): Char {
              aNullableCharReference()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aNullableByteReference")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aNullableByteReference"),
            )
            fun aNullableByteReference(): Byte {
              aNullableByteReference()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aNullableShortReference")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aNullableShortReference"),
            )
            fun aNullableShortReference(): Short {
              aNullableShortReference()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aNullableIntReference")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aNullableIntReference"),
            )
            fun aNullableIntReference(): Int {
              aNullableIntReference()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aNullableFloatReference")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aNullableFloatReference"),
            )
            fun aNullableFloatReference(): Float {
              aNullableFloatReference()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aNullableLongReference")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aNullableLongReference"),
            )
            fun aNullableLongReference(): Long {
              aNullableLongReference()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            @JvmSynthetic
            @JvmName("-aNullableDoubleReference")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("aNullableDoubleReference"),
            )
            fun aNullableDoubleReference(): Double {
              aNullableDoubleReference()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }
          }

        """
          .trimIndent()
      )
  }

  @Test
  fun nestedClasses() {
    val result =
      compile(
        forSourceString(
          "test.Outer",
          """
          package test;

          import com.google.auto.value.AutoValue;

          @AutoValue
          abstract class Outer {

            abstract String outerValue();

            @AutoValue
            abstract static class Inner {

              abstract String value();

              abstract String withValue();
            }
          }
        """
            .trimIndent()
        )
      )

    result.succeeded()
    val generated = File(srcDir, "test/Outer.kt")
    assertThat(generated.exists()).isTrue()
    assertThat(generated.readText())
      .isEqualTo(
        """
          package test

          import kotlin.jvm.JvmName
          import kotlin.jvm.JvmSynthetic

          data class Outer(
            @get:JvmName("outerValue")
            val outerValue: String,
          ) {
            @JvmSynthetic
            @JvmName("-outerValue")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("outerValue"),
            )
            fun outerValue(): String {
              outerValue()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            data class Inner(
              @get:JvmName("value")
              val `value`: String,
              @get:JvmName("withValue")
              val withValue: String,
            ) {
              @JvmSynthetic
              @JvmName("-value")
              @Deprecated(
                message = "Use the property",
                replaceWith = ReplaceWith("value"),
              )
              fun `value`(): String {
                `value`()
                TODO("Remove this function. Use the above line to auto-migrate.")
              }

              @JvmSynthetic
              @JvmName("-withValue")
              @Deprecated(
                message = "Use the property",
                replaceWith = ReplaceWith("withValue"),
              )
              fun withValue(): String {
                withValue()
                TODO("Remove this function. Use the above line to auto-migrate.")
              }

              fun withValue(`value`: String): Inner = copy(`value` = `value`)
            }
          }

        """
          .trimIndent()
      )
  }

  @Test
  fun nestedError_outerNonAuto() {
    val result =
      compile(
        forSourceString(
          "test.Outer",
          """
          package test;

          import com.google.auto.value.AutoValue;

          class Outer {

            @AutoValue
            abstract static class Inner {

              abstract String value();

              abstract String withValue();
            }
          }
        """
            .trimIndent()
        )
      )

    result.failed()
    result.hadErrorContaining(
      "Cannot convert nested classes to Kotlin safely. Please move this to top-level first."
    )
  }

  @Test
  fun nestedError_innerNonAuto() {
    val result =
      compile(
        forSourceString(
          "test.Outer",
          """
          package test;

          import com.google.auto.value.AutoValue;

          @AutoValue
          abstract class Outer {

            abstract String value();

            abstract String withValue();

            static class Inner {

            }
          }
        """
            .trimIndent()
        )
      )

    result.failed()
    result.hadErrorContaining(
      "Cannot convert non-autovalue nested classes to Kotlin safely. Please move this to top-level first."
    )
  }

  @Test
  fun nestedWarning() {
    val result =
      compile(
        forSourceString(
          "test.Outer",
          """
          package test;

          import com.google.auto.value.AutoValue;

          class Outer {

            @AutoValue
            abstract static class Inner {

              abstract String value();

              abstract String withValue();
            }
          }
        """
            .trimIndent()
        )
      ) {
        withOptions(listOf(compilerSrcOption(), "-A${Options.OPT_IGNORE_NESTED}=true"))
      }

    result.succeeded()
    result.hadWarningContaining(
      "Cannot convert nested classes to Kotlin safely. Please move this to top-level first."
    )
  }

  @Test
  fun targets() {
    val result =
      compile(
        forSourceString(
          "test.Example",
          """
          package test;

          import com.google.auto.value.AutoValue;

          @AutoValue
          abstract class Example {
            abstract String value();
          }
        """
            .trimIndent()
        ),
        forSourceString(
          "test.IgnoredExample",
          """
          package test;

          import com.google.auto.value.AutoValue;

          @AutoValue
          abstract class IgnoredExample {
            abstract String value();
          }
        """
            .trimIndent()
        )
      ) {
        withOptions(listOf(compilerSrcOption(), "-A${Options.OPT_TARGETS}=Example"))
      }

    result.succeeded()
    assertThat(File(srcDir, "test/Example.kt").exists()).isTrue()
    assertThat(File(srcDir, "test/IgnoredExample.kt").exists()).isFalse()
  }

  @Test
  fun getters() {
    val result =
      compile(
        forSourceString(
          "test.Example",
          """
          package test;

          import com.google.auto.value.AutoValue;

          @AutoValue
          abstract class Example {
            abstract String getValue();
          }
        """
            .trimIndent()
        )
      )

    result.succeeded()
    val generated = File(srcDir, "test/Example.kt")
    assertThat(generated.exists()).isTrue()
    assertThat(generated.readText())
      .isEqualTo(
        """
          package test

          data class Example(
            val `value`: String,
          )

        """
          .trimIndent()
      )
  }

  // Regression test for https://github.com/slackhq/auto-value-kotlin/issues/16
  @Test
  fun nonJsonClassesDoNotGetDefaults() {
    val result =
      compile(
        forSourceString(
          "test.Example",
          """
          package test;

          import com.google.auto.value.AutoValue;
          import org.jetbrains.annotations.Nullable;

          @AutoValue
          abstract class Example {
            @Nullable
            abstract String name();

            @AutoValue.Builder
            interface Builder {
              Builder name(@Nullable String name);
              Example build();
            }
          }
        """
            .trimIndent()
        )
      )

    result.succeeded()
    val generated = File(srcDir, "test/Example.kt")
    assertThat(generated.exists()).isTrue()
    assertThat(generated.readText())
      .isEqualTo(
        """
          package test

          import kotlin.jvm.JvmName
          import kotlin.jvm.JvmSynthetic

          data class Example internal constructor(
            @get:JvmName("name")
            val name: String?,
          ) {
            @JvmSynthetic
            @JvmName("-name")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("name"),
            )
            fun name(): String? {
              name()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            internal class Builder internal constructor(
              private var name: String? = null,
            ) {
              internal fun name(name: String?): Builder = apply { this.name = name }

              internal fun build(): Example = Example(name = name)
            }
          }

        """
          .trimIndent()
      )
  }

  // Regression test for https://github.com/slackhq/auto-value-kotlin/issues/16
  @Test
  fun jsonClassesWithBuildersGetDefaults() {
    val result =
      compile(
        forSourceString(
          "test.Example",
          """
          package test;

          import com.google.auto.value.AutoValue;
          import com.squareup.moshi.JsonClass;
          import org.jetbrains.annotations.Nullable;

          @JsonClass(generateAdapter = true, generator = "avm")
          @AutoValue
          abstract class Example {
            @Nullable
            abstract String name();

            @AutoValue.Builder
            interface Builder {
              Builder name(@Nullable String name);
              Example build();
            }
          }
        """
            .trimIndent()
        )
      )

    result.succeeded()
    val generated = File(srcDir, "test/Example.kt")
    assertThat(generated.exists()).isTrue()
    assertThat(generated.readText())
      .isEqualTo(
        """
          package test

          import com.squareup.moshi.JsonClass
          import kotlin.jvm.JvmName
          import kotlin.jvm.JvmSynthetic

          @JsonClass(generateAdapter = true)
          data class Example internal constructor(
            @get:JvmName("name")
            val name: String? = null,
          ) {
            @JvmSynthetic
            @JvmName("-name")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("name"),
            )
            fun name(): String? {
              name()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }

            internal class Builder internal constructor(
              private var name: String? = null,
            ) {
              internal fun name(name: String?): Builder = apply { this.name = name }

              internal fun build(): Example = Example(name = name)
            }
          }

        """
          .trimIndent()
      )
  }

  // Mixed of get and non-get means everything is treated as non-get
  // TODO maybe we should still use simple syntax for getters since kotlin property syntax will
  //  still kick in?
  @Test
  fun gettersMixed() {
    val result =
      compile(
        forSourceString(
          "test.Example",
          """
          package test;

          import com.google.auto.value.AutoValue;

          @AutoValue
          abstract class Example {
            abstract String getValue();
            abstract String nonGetValue();
          }
        """
            .trimIndent()
        )
      )

    result.succeeded()
    val generated = File(srcDir, "test/Example.kt")
    assertThat(generated.exists()).isTrue()
    assertThat(generated.readText())
      .isEqualTo(
        """
          package test

          import kotlin.jvm.JvmName
          import kotlin.jvm.JvmSynthetic

          data class Example(
            val getValue: String,
            @get:JvmName("nonGetValue")
            val nonGetValue: String,
          ) {
            @JvmSynthetic
            @JvmName("-nonGetValue")
            @Deprecated(
              message = "Use the property",
              replaceWith = ReplaceWith("nonGetValue"),
            )
            fun nonGetValue(): String {
              nonGetValue()
              TODO("Remove this function. Use the above line to auto-migrate.")
            }
          }

        """
          .trimIndent()
      )
  }

  // Regression test for https://github.com/slackhq/auto-value-kotlin/issues/15
  @Test
  fun enumsAreNotShared() {
    val result =
      compile(
        forSourceString(
          "test.Example",
          """
          package test;

          import com.google.auto.value.AutoValue;

          @AutoValue
          abstract class Example {
            abstract String getValue();
            abstract String nonGetValue();

            enum ExampleEnum {
              INSTANCE
            }
          }
        """
            .trimIndent()
        ),
        forSourceString(
          "test.Example2",
          """
          package test;

          import com.google.auto.value.AutoValue;

          @AutoValue
          abstract class Example2 {
            abstract String getValue();
            abstract String nonGetValue();

            enum Example2Enum {
              INSTANCE
            }
          }
        """
            .trimIndent()
        ),
      )

    result.succeeded()
  }

  private fun compilerSrcOption(): String {
    return "-A${Options.OPT_SRC}=${srcDir.absolutePath}"
  }

  private fun compile(
    vararg sourceFiles: JavaFileObject,
    compilerBody: Compiler.() -> Compiler = { this }
  ): CompilationSubject {
    val compilation =
      javac()
        .withOptions(compilerSrcOption())
        .withProcessors(AutoValueKotlinProcessor())
        .let(compilerBody)
        .compile(*sourceFiles)
    return assertAbout(compilations()).that(compilation)
  }
}
