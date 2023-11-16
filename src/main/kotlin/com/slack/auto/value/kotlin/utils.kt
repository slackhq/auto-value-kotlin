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
@file:Suppress("TooManyFunctions")
@file:OptIn(DelicateKotlinPoetApi::class)

package com.slack.auto.value.kotlin

import com.google.auto.common.Visibility
import com.google.auto.common.Visibility.DEFAULT
import com.google.auto.common.Visibility.PRIVATE
import com.google.auto.common.Visibility.PROTECTED
import com.google.auto.common.Visibility.PUBLIC
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.CHAR
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.DelicateKotlinPoetApi
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.NOTHING
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.asTypeVariableName
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.TypeVariable
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal val NONNULL_ANNOTATIONS = setOf("NonNull", "NotNull", "Nonnull")

internal val PARCELIZE = ClassName("kotlinx.parcelize", "Parcelize")

internal val INTRINSIC_IMPORTS =
  setOf(
    "import java.lang.String",
    "import java.lang.CharSequence",
    "import java.lang.Boolean",
    "import java.lang.Byte",
    "import java.lang.Short",
    "import java.lang.Char",
    "import java.lang.Int",
    "import java.lang.Float",
    "import java.lang.Double",
    "import java.lang.Object",
    "import java.util.Map",
    "import java.util.List",
    "import java.util.Set",
    "import java.util.Collection"
  )

/** Regex used for finding references in javadoc links */
internal const val DOC_LINK_REGEX = "[0-9A-Za-z._]*"

internal const val MAX_PARAMS = 7

internal val JSON_CN = Json::class.asClassName()
internal val JSON_CLASS_CN = JsonClass::class.asClassName()

@OptIn(DelicateKotlinPoetApi::class)
@ExperimentalAvkApi
public fun TypeMirror.asSafeTypeName(): TypeName {
  return asTypeName().copy(nullable = false).normalize()
}

@Suppress("ComplexMethod")
@ExperimentalAvkApi
public fun TypeName.normalize(): TypeName {
  return when (this) {
    is ClassName -> {
      when (this) {
        ClassName("java.lang", "Boolean") -> BOOLEAN
        ClassName("java.lang", "Byte") -> BYTE
        ClassName("java.lang", "Short") -> SHORT
        ClassName("java.lang", "Character") -> CHAR
        ClassName("java.lang", "Integer") -> INT
        ClassName("java.lang", "Long") -> LONG
        ClassName("java.lang", "Float") -> FLOAT
        ClassName("java.lang", "Double") -> DOUBLE
        ClassName("java.lang", "String") -> STRING
        else -> this
      }
    }
    is ParameterizedTypeName -> {
      rawType.parameterizedBy(typeArguments.map { it.normalize() })
    }
    is TypeVariableName -> this
    else -> error("Unsupported type: $this")
  }
}

@ExperimentalAvkApi
public fun TypeElement.classAnnotations(): List<AnnotationSpec> {
  return annotationMirrors
    .map { AnnotationSpec.get(it) }
    .filterNot { (it.typeName as ClassName).packageName == "com.google.auto.value" }
    .filterNot { (it.typeName as ClassName).simpleName == "Metadata" }
    .map { spec ->
      if ((spec.typeName as ClassName).simpleName == "JsonClass") {
        // Strip the 'generator = "avm"' off of this if present
        spec.toBuilder().apply { members.removeIf { "avm" in it.toString() } }.build()
      } else {
        spec
      }
    }
}

@ExperimentalAvkApi
public fun TypeName.defaultPrimitiveValue(): CodeBlock =
  when (this) {
    BOOLEAN -> CodeBlock.of("false")
    CHAR -> CodeBlock.of("0.toChar()")
    BYTE -> CodeBlock.of("0.toByte()")
    SHORT -> CodeBlock.of("0.toShort()")
    INT -> CodeBlock.of("0")
    FLOAT -> CodeBlock.of("0f")
    LONG -> CodeBlock.of("0L")
    DOUBLE -> CodeBlock.of("0.0")
    UNIT,
    Void::class.asTypeName(),
    NOTHING -> {
      error("Parameter with void, Unit, or Nothing type is illegal")
    }
    else -> CodeBlock.of("null")
  }

@ExperimentalAvkApi
public fun deprecatedAnnotation(message: String, replaceWith: String): AnnotationSpec {
  return AnnotationSpec.builder(Deprecated::class)
    .addMember("message = %S", message)
    .addMember("replaceWith = %T(%S)", ReplaceWith::class, replaceWith)
    .build()
}

@Suppress("SpreadOperator")
@ExperimentalAvkApi
public fun FunSpec.Companion.copyOf(method: ExecutableElement): FunSpec.Builder {
  var modifiers: Set<Modifier> = method.modifiers

  val methodName = method.simpleName.toString()
  val funBuilder = builder(methodName).addModifiers(method.visibility)

  modifiers = modifiers.toMutableSet()
  modifiers.remove(Modifier.ABSTRACT)
  funBuilder.jvmModifiers(modifiers)

  method.typeParameters
    .map { it.asType() as TypeVariable }
    .map { it.asTypeVariableName() }
    .forEach { funBuilder.addTypeVariable(it) }

  funBuilder.returns(method.returnType.asSafeTypeName())
  funBuilder.addParameters(ParameterSpec.parametersWithNullabilityOf(method))
  if (method.isVarArgs) {
    funBuilder.parameters[funBuilder.parameters.lastIndex] =
      funBuilder.parameters.last().toBuilder().addModifiers(KModifier.VARARG).build()
  }

  if (method.thrownTypes.isNotEmpty()) {
    val throwsValueString = method.thrownTypes.joinToString { "%T::class" }
    funBuilder.addAnnotation(
      AnnotationSpec.builder(Throws::class)
        .addMember(throwsValueString, *method.thrownTypes.toTypedArray())
        .build()
    )
  }

  return funBuilder
}

@ExperimentalAvkApi
public fun ParameterSpec.Companion.parametersWithNullabilityOf(
  method: ExecutableElement
): List<ParameterSpec> = method.parameters.map(ParameterSpec.Companion::getWithNullability)

@ExperimentalAvkApi
public fun ParameterSpec.Companion.getWithNullability(element: VariableElement): ParameterSpec {
  val name = element.simpleName.toString()
  val isNullable =
    element.annotationMirrors.any {
      (it.annotationType.asElement() as TypeElement).simpleName.toString() == "Nullable"
    }
  val type = element.asType().asSafeTypeName().copy(nullable = isNullable)
  return builder(name, type).build()
}

/** Cleans up the generated doc and translates some html to equivalent markdown for Kotlin docs. */
@ExperimentalAvkApi
public fun cleanUpDoc(doc: String): String {
  // TODO not covered yet
  //  {@link TimeFormatter#getDateTimeString(SlackDateTime)}
  return doc
    .replace("<em>", "*")
    .replace("</em>", "*")
    .replace("<p>", "")
    // JavaParser adds a couple spaces to the beginning of these for some reason
    .replace("   *", " *")
    // {@code view} -> `view`
    .replace("\\{@code ($DOC_LINK_REGEX)}".toRegex()) { result: MatchResult ->
      val codeName = result.destructured
      "`${codeName.component1()}`"
    }
    // {@link Foo} -> [Foo]
    .replace("\\{@link ($DOC_LINK_REGEX)}".toRegex()) { result: MatchResult ->
      val foo = result.destructured
      "[${foo.component1()}]"
    }
    // {@link Foo#bar} -> [Foo.bar]
    .replace("\\{@link ($DOC_LINK_REGEX)#($DOC_LINK_REGEX)}".toRegex()) { result: MatchResult ->
      val (foo, bar) = result.destructured
      "[$foo.$bar]"
    }
    // {@linkplain Foo baz} -> [baz][Foo]
    .replace("\\{@linkplain ($DOC_LINK_REGEX) ($DOC_LINK_REGEX)}".toRegex()) { result: MatchResult
      ->
      val (foo, baz) = result.destructured
      "[$baz][$foo]"
    }
    // {@linkplain Foo#bar baz} -> [baz][Foo.bar]
    .replace("\\{@linkplain ($DOC_LINK_REGEX)#($DOC_LINK_REGEX) ($DOC_LINK_REGEX)}".toRegex()) {
      result: MatchResult ->
      val (foo, bar, baz) = result.destructured
      "[$baz][$foo.$bar]"
    }
    // Remove any trailing whitespace
    .replace("(?m)\\s+$".toRegex(), "")
    // Remove any leading space for tags
    .replace("^\\s+@".toRegex(), "")
    .trim()
}

@ExperimentalAvkApi
public fun FunSpec.Builder.withDocsFrom(
  e: Element,
  parseDocs: Element.() -> String?
): FunSpec.Builder {
  val doc = e.parseDocs() ?: return this
  return addKdoc(doc)
}

@ExperimentalAvkApi
public fun ProcessingEnvironment.isParcelable(element: TypeElement): Boolean {
  return elementUtils.getTypeElement("android.os.Parcelable")?.asType()?.let { parcelableClass ->
    element.interfaces.any { it.isClassOfType(typeUtils, parcelableClass) }
  }
    ?: false
}

private fun TypeMirror.isClassOfType(types: Types, other: TypeMirror?) =
  types.isAssignable(this, other)

@ExperimentalAvkApi
public val Element.visibility: KModifier
  get() =
    when (Visibility.effectiveVisibilityOfElement(this)!!) {
      PRIVATE -> KModifier.PRIVATE
      DEFAULT -> KModifier.INTERNAL
      PROTECTED -> KModifier.PROTECTED
      PUBLIC -> KModifier.PUBLIC
    }

@ExperimentalAvkApi
@OptIn(ExperimentalPathApi::class)
public fun TypeSpec.writeCleanlyTo(packageName: String, dir: String) {
  val file = File(dir).toPath()
  val outputPath = FileSpec.get(packageName, this).writeToLocal(file)
  val text = outputPath.readText()
  // Post-process to remove any kotlin intrinsic types
  // Is this wildly inefficient? yes. Does it really matter in our cases? nah
  var prevWasBlank = false
  outputPath.writeText(
    text
      .lineSequence()
      .filterNot { it in INTRINSIC_IMPORTS }
      .mapNotNull {
        if (it.trimStart().startsWith("public ")) {
          prevWasBlank = false
          val indent = it.substringBefore("public ")
          it.removePrefix(indent).removePrefix("public ").prependIndent(indent)
        } else if (it.isKotlinPackageImport) {
          // Ignore kotlin implicit imports
          null
        } else if (it.isBlank()) {
          if (prevWasBlank) {
            null
          } else {
            prevWasBlank = true
            it
          }
        } else {
          prevWasBlank = false
          it
        }
      }
      .joinToString("\n")
  )
}

/** Best-effort checks if the string is an import from `kotlin.*` */
@Suppress("MagicNumber")
private val String.isKotlinPackageImport: Boolean
  get() =
    startsWith("import kotlin.") &&
      // Looks like a class
      // 14 is the length of `import kotlin.`
      get(14).isUpperCase() &&
      // Exclude if it's importing a nested element
      '.' !in removePrefix("import kotlin.")

private fun FileSpec.writeToLocal(directory: Path): Path {
  require(Files.notExists(directory) || Files.isDirectory(directory)) {
    "path $directory exists but is not a directory."
  }
  var srcDirectory = directory
  if (packageName.isNotEmpty()) {
    for (packageComponent in packageName.split('.').dropLastWhile { it.isEmpty() }) {
      srcDirectory = srcDirectory.resolve(packageComponent)
    }
  }

  Files.createDirectories(srcDirectory)

  val outputPath = srcDirectory.resolve("$name.kt")
  OutputStreamWriter(Files.newOutputStream(outputPath), StandardCharsets.UTF_8).use { writer ->
    writeTo(writer)
  }
  return outputPath
}

@ExperimentalAvkApi
@Suppress("ReturnCount")
public fun Element.parseDocs(elements: Elements): String? {
  val doc = elements.getDocComment(this)?.trim() ?: return null
  if (doc.isBlank()) return null
  return cleanUpDoc(doc)
}
