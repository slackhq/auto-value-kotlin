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
@file:OptIn(DelicateKotlinPoetApi::class)

package com.slack.auto.value.kotlin

import com.google.auto.service.AutoService
import com.google.auto.value.extension.AutoValueExtension
import com.slack.auto.value.kotlin.AutoValueKotlinExtension.AvkBuilder.Companion
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
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.asTypeVariableName
import com.squareup.kotlinpoet.joinToCode
import com.squareup.moshi.Json
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.NestingKind
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.TypeVariable
import javax.lang.model.util.ElementFilter
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val NONNULL_ANNOTATIONS = setOf(
  "NonNull",
  "NotNull",
  "Nonnull"
)

// Options
private const val OPT_OUTPUT_DIR = "avkSrc"
private const val OPT_TARGETS = "avkTargets"
private const val OPT_IGNORE_NESTED = "avkIgnoreNested"

private val PARCELIZE = ClassName("kotlinx.parcelize", "Parcelize")
private val INTRINSIC_IMPORTS = setOf(
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
private const val DOC_LINK_REGEX = "[0-9A-Za-z._]*"

private const val MAX_PARAMS = 7

private val JSON_CN = Json::class.asClassName()

private fun TypeMirror.asSafeTypeName(): TypeName {
  return asTypeName().copy(nullable = false).normalize()
}

@Suppress("ComplexMethod")
private fun TypeName.normalize(): TypeName {
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

@AutoService(AutoValueExtension::class)
public class AutoValueKotlinExtension : AutoValueExtension() {

  private lateinit var elements: Elements
  private lateinit var types: Types

  override fun getSupportedOptions(): Set<String> {
    return setOf(OPT_OUTPUT_DIR, OPT_TARGETS, OPT_IGNORE_NESTED)
  }

  override fun incrementalType(processingEnvironment: ProcessingEnvironment): IncrementalExtensionType {
    // This is intentionally not incremental, we are generating into source sets directly
    return IncrementalExtensionType.UNKNOWN
  }

  override fun applicable(context: Context): Boolean {
    this.elements = context.processingEnvironment().elementUtils
    this.types = context.processingEnvironment().typeUtils
    // If this is run, we are generating info
    return true
  }

  private fun FunSpec.Builder.withDocsFrom(e: Element): FunSpec.Builder {
    return withDocsFrom(e) { parseDocs() }
  }

  @Suppress("ReturnCount")
  private fun Element.parseDocs(): String? {
    val doc = elements.getDocComment(this)?.trim() ?: return null
    if (doc.isBlank()) return null
    return cleanUpDoc(doc)
  }

  @Suppress("DEPRECATION", "LongMethod", "ComplexMethod", "NestedBlockDepth", "ReturnCount")
  override fun generateClass(
    context: Context,
    className: String,
    classToExtend: String,
    isFinal: Boolean
  ): String? {
    val targetClasses = context.processingEnvironment().options[OPT_TARGETS]
      ?.splitToSequence(":")
      ?.toSet()
      ?: emptySet()

    val ignoreNested =
      context.processingEnvironment().options[OPT_IGNORE_NESTED]?.toBoolean() ?: false

    if (targetClasses.isNotEmpty() && context.autoValueClass().simpleName.toString() !in targetClasses) {
      return null
    }

    val avClass = context.autoValueClass()

    if (avClass.nestingKind != NestingKind.TOP_LEVEL) {
      val diagnosticKind = if (ignoreNested) {
        Diagnostic.Kind.WARNING
      } else {
        Diagnostic.Kind.ERROR
      }
      context.processingEnvironment().messager
        .printMessage(
          diagnosticKind,
          "Cannot convert nested classes to Kotlin safely. Please move this to top-level first.",
          avClass
        )
      return null
    }

    // Check for non-builder nested classes, which cannot be converted with this
    val nonBuilderNestedTypes = ElementFilter.typesIn(avClass.enclosedElements)
      .filterNot { nestedType ->
        context.builder()
          .map { nestedType == it.builderType() }
          .orElse(false)
      }

    if (nonBuilderNestedTypes.isNotEmpty()) {
      nonBuilderNestedTypes.forEach {
        context.processingEnvironment().messager
          .printMessage(
            Diagnostic.Kind.ERROR,
            "Cannot convert nested classes to Kotlin safely. Please move this to top-level first.",
            it
          )
      }
      return null
    }

    val classDoc = avClass.parseDocs()

    var redactedClassName: ClassName? = null

    fun MutableList<AnnotationSpec>.anyRedacted(): Boolean {
      return removeIf {
        val type = it.typeName
        if (type !is ClassName) return@removeIf false
        (type.simpleName == "Redacted")
          .also { wasRedacted ->
            if (wasRedacted) {
              redactedClassName = type
            }
          }
      }
    }

    val properties = context.properties()
      .entries
      .associate { (propertyName, method) ->
        val annotations = method.annotationMirrors
          .map { AnnotationSpec.get(it) }
          .filter { spec ->
            if (spec.typeName == JSON_CN) {
              // Don't include `@Json` if the name value is the same as the property name as it's
              // redundant
              method.getAnnotation(Json::class.java)!!.name != propertyName
            } else {
              true
            }
          }
          .toMutableList()
        val isNullable =
          annotations.removeIf { (it.typeName as ClassName).simpleName == "Nullable" }
        annotations.removeIf {
          val simpleName = (it.typeName as ClassName).simpleName
          simpleName in NONNULL_ANNOTATIONS
        }
        val isAnOverride =
          annotations.removeIf { (it.typeName as ClassName).simpleName == "Override" }
        val isRedacted = annotations.anyRedacted()
        propertyName to PropertyContext(
          name = propertyName,
          funName = method.simpleName.toString(),
          type = method.returnType.asSafeTypeName().copy(nullable = isNullable),
          annotations = annotations,
          isOverride = isAnOverride,
          isRedacted = isRedacted,
          visibility = if (Modifier.PUBLIC in method.modifiers) KModifier.PUBLIC else KModifier.INTERNAL,
          doc = method.parseDocs()
        )
      }

    val classAnnotations = avClass.classAnnotations().toMutableList()
    val isClassRedacted = classAnnotations.anyRedacted() ||
      // If all the properties are redacted, redacted the class?
      properties.values.all { it.isRedacted }

    val parcelableClass = elements
      .getTypeElement("android.os.Parcelable")
      .asType()

    val isParcelable = avClass.interfaces.any { it.isClassOfType(types, parcelableClass) }

    val propertyMethods = context.properties().values.toSet()

    val toBuilderMethods = mutableListOf<ExecutableElement>()
    val toBuilderFunSpecs = mutableListOf<FunSpec>()
    val builderFactories = mutableListOf<ExecutableElement>()
    val builderFactorySpecs = mutableListOf<FunSpec>()
    val witherSpecs = mutableListOf<FunSpec>()
    var avkBuilder: AvkBuilder? = null
    if (context.builder().isPresent) {
      val builder = context.builder().get()
      toBuilderMethods += builder.toBuilderMethods()
      val propsList = properties.values.map {
        CodeBlock.of("%1L·=·%1L", it.name)
      }
      toBuilderFunSpecs += builder.toBuilderMethods()
        .map {
          // Assume it's just one with no params
          FunSpec.copyOf(it)
            .withDocsFrom(it)
            .addStatement(
              "return·%T(%L)",
              builder.builderType().asClassName(),
              propsList.joinToCode(",·")
            )
            .build()
        }
      // Note we don't use context.propertyTypes() here because it doesn't contain nullability
      // info, which we did capture
      val propertyTypes = properties.mapValues { it.value.type }
      avkBuilder = Companion.from(builder, propertyTypes) { parseDocs() }

      builderFactories += builder.builderMethods()
      builderFactorySpecs += builder.builderMethods()
        .map {
          FunSpec.copyOf(it)
            .withDocsFrom(it)
            .addModifiers(avkBuilder.visibility)
            .addStatement("TODO(%S)", "Replace this with the implementation from the source class")
            .build()
        }
    }

    val allMethods = ElementFilter.methodsIn(avClass.enclosedElements)
      .toMutableSet()

    val staticCreators = mutableListOf<ExecutableElement>()
    val staticCreatorSpecs = mutableListOf<FunSpec>()
    allMethods
      .filter { Modifier.STATIC in it.modifiers }
      .filter { types.asElement(it.returnType) == avClass }
      .forEach { staticCreator ->
        allMethods.remove(staticCreator)
        staticCreators += staticCreator
        val spec = FunSpec.copyOf(staticCreator)
          .withDocsFrom(staticCreator)
          .apply {
            if (parameters.size > MAX_PARAMS) {
              addAnnotation(
                AnnotationSpec.builder(Suppress::class)
                  .addMember("%S", "LongParameterList")
                  .build()
              )
            }
          }
          .build()
        val isFullCreator = spec.parameters.map { it.type } == properties.values.map { it.type }
        if (isFullCreator) {
          // Add deprecated copy of the original, hide from java
          val parameterString = spec.parameters.joinToString(", ") { it.name }
          staticCreatorSpecs += spec.toBuilder()
            .addAnnotation(
              AnnotationSpec.builder(JvmName::class)
                .addMember("%S", "-${spec.name}")
                .build()
            )
            .addAnnotation(
              deprecatedAnnotation(
                "Use invoke()",
                "${spec.returnType}($parameterString)"
              )
            )
            .addStatement("%N(%L)", spec.name, parameterString)
            .addStatement("TODO(%S)", "Remove this function. Use the above line to auto-migrate.")
            .build()
          // Expose the original creator for java
          val propsList = spec.parameters.map {
            CodeBlock.of("%1L·=·%1L", it.name)
          }
          staticCreatorSpecs += spec.toBuilder(name = "invoke")
            .addAnnotation(
              AnnotationSpec.builder(JvmName::class)
                .addMember("%S", spec.name)
                .build()
            )
            .addModifiers(KModifier.OPERATOR)
            .addStatement(
              "return·%T(%L)",
              avClass.asClassName(),
              propsList.joinToCode(",·")
            )
            .build()
        } else {
          staticCreatorSpecs += spec.toBuilder()
            .addStatement("TODO()")
            .build()
        }
      }

    // Recognize auto-value-with functions
    val avType = context.autoValueClass().asType().asSafeTypeName()
    allMethods
      .filter { it.simpleName.toString().startsWith("with") }
      .forEach { method ->
        val name = method.simpleName.toString()
        val propertyName = name.removePrefix("with")
          .replaceFirstChar { it.lowercase(Locale.US) }
        val prop = properties[propertyName] ?: return@forEach
        // Match return type
        val isValidReturnType = method.returnType.asSafeTypeName() == avType
        // Check if it's handled by auto-value-with
        val isAutoValueWith = Modifier.ABSTRACT in method.modifiers
        // Match param type
        val isMatchingSignature =
          method.parameters.size == 1 && method.parameters[0].asType().asSafeTypeName() == prop.type
        val isValidWith = isAutoValueWith || (isValidReturnType && isMatchingSignature)
        if (isValidWith) {
          allMethods -= method
          witherSpecs += FunSpec.builder(name)
            .addParameter(prop.name, prop.type)
            .returns(avType)
            .apply {
              // If we have a builder, use it rather than copy() to pick up any normalization
              // or checks it performs in building.
              val toBuilderFun = toBuilderFunSpecs.find { it.parameters.isEmpty() }
              if (toBuilderFun != null) {
                val builderMethodName = context.builder().get()
                  .setters().getValue(prop.name).first().simpleName.toString()
                addStatement(
                  "return %N().%L(%N).build()",
                  toBuilderFun,
                  builderMethodName,
                  prop.name
                )
              } else {
                addStatement("return copy(%1N = %1N)", prop.name)
              }
            }
            .build()
        }
      }

    // Get methods not defined in properties
    val remainingMethods = allMethods
      .asSequence()
      .filterNot { it in propertyMethods }
      .filterNot { it in toBuilderMethods }
      .filterNot { it in staticCreators }
      .filterNot { it in builderFactories }
      .map { "${it.modifiers.joinToString(" ")} ${it.returnType} ${it.simpleName}(...)" }
      .toList()

    // Look for static final fields
    val staticConstants = ElementFilter.fieldsIn(avClass.enclosedElements)
      .filter { Modifier.STATIC in it.modifiers }
      .map { field ->
        val type = field.asType().asSafeTypeName()
        PropertySpec.builder(field.simpleName.toString(), type)
          .apply {
            val visibility = when {
              Modifier.PRIVATE in field.modifiers -> KModifier.PRIVATE
              Modifier.PUBLIC !in field.modifiers -> KModifier.INTERNAL
              else -> null
            }
            visibility?.let { addModifiers(it) }

            field.constantValue?.let {
              addModifiers(KModifier.CONST)
              if (it is String) {
                initializer("%S", it)
              } else {
                // Best-effort. We could be more precise with this, but it's noisy and annoying
                initializer("%L", it)
              }
            } ?: run {
              initializer("TODO()")
            }

            field.parseDocs()?.let { addKdoc(it) }
          }
          .build()
      }

    val superclass = avClass.superclass.asSafeTypeName()
      .takeUnless { it == ClassName("java.lang", "Object") }

    val outputDir =
      context.processingEnvironment().options[OPT_OUTPUT_DIR] ?: error("Missing output dir option")

    KotlinClass(
      packageName = context.packageName(),
      doc = classDoc,
      name = avClass.simpleName.toString(),
      visibility = if (Modifier.PUBLIC in avClass.modifiers) KModifier.PUBLIC else KModifier.INTERNAL,
      isRedacted = isClassRedacted,
      isParcelable = isParcelable,
      superClass = superclass,
      interfaces = avClass.interfaces.map { it.asSafeTypeName() },
      typeParams = avClass.typeParameters.map { it.asTypeVariableName() },
      properties = properties,
      avkBuilder = avkBuilder,
      toBuilderSpecs = toBuilderFunSpecs,
      builderFactories = builderFactorySpecs,
      staticCreators = staticCreatorSpecs,
      withers = witherSpecs,
      remainingMethods = remainingMethods,
      classAnnotations = avClass.classAnnotations(),
      redactedClassName = redactedClassName,
      staticConstants = staticConstants
    ).writeTo(outputDir, context.processingEnvironment().messager)

    return null
  }

  private fun TypeMirror.isClassOfType(types: Types, other: TypeMirror?) =
    types.isAssignable(this, other)

  private data class PropertyContext(
    val name: String,
    val funName: String,
    val type: TypeName,
    val annotations: List<AnnotationSpec>,
    val isOverride: Boolean,
    val isRedacted: Boolean,
    val visibility: KModifier,
    val doc: String?
  ) {
    val usesGet = name.startsWith("get") || funName.startsWith("get")
  }

  private data class AvkBuilder(
    val name: String,
    val doc: String?,
    val visibility: KModifier,
    val builderProps: List<BuilderProp>,
    val buildFun: FunSpec?,
    val autoBuildFun: FunSpec,
    val remainingMethods: List<String>,
    val classAnnotations: List<AnnotationSpec>
  ) {

    @Suppress("LongMethod")
    fun createType(messager: Messager): TypeSpec {
      val builder = TypeSpec.classBuilder(name)
        .addModifiers(visibility)
        .addAnnotations(classAnnotations)

      val constructorBuilder = FunSpec.constructorBuilder()
        .addModifiers(KModifier.INTERNAL)

      @Suppress("MagicNumber")
      if (builderProps.size >= MAX_PARAMS) {
        builder.addAnnotation(
          AnnotationSpec.builder(Suppress::class)
            .addMember("%S", "LongParameterList")
            .build()
        )
      }

      val propsToCreateWith = mutableListOf<CodeBlock>()

      for ((propName, type, setters) in builderProps) {
        // Add param to constructor
        val defaultValue = if (type.isNullable) {
          CodeBlock.of("null")
        } else {
          type.defaultPrimitiveValue()
        }
        val useNullablePropType = defaultValue.toString() == "null"
        val param = ParameterSpec.builder(propName, type.copy(nullable = useNullablePropType))
          .defaultValue(defaultValue)
          .build()
        constructorBuilder.addParameter(param)

        // Add private properties
        val propSpec = PropertySpec.builder(propName, param.type)
          .addModifiers(KModifier.PRIVATE)
          .mutable()
          .initializer("%N", param.name)
          .build()
        builder.addProperty(propSpec)

        // Add build() assignment block
        val extraCheck = if (type.isNullable || !useNullablePropType) {
          CodeBlock.of("")
        } else {
          CodeBlock.of("·?:·error(%S)", "${propSpec.name} == null")
        }
        propsToCreateWith += CodeBlock.of("%1N·=·%1N%2L", propSpec, extraCheck)

        for (setter in setters) {
          if (setter.parameters.size != 1) {
            messager.printMessage(
              Diagnostic.Kind.WARNING,
              "Setter with surprising params: ${setter.name}"
            )
          }
          val setterSpec = setter.toBuilder()
            // Assume this is a normal setter
            .addStatement("return·apply·{·this.%N·= %N }", propSpec, setter.parameters[0])
            .build()
          builder.addFunction(setterSpec)
        }
      }

      builder.primaryConstructor(constructorBuilder.build())

      // TODO Build fun?

      // AutoBuild
      builder.addFunction(
        autoBuildFun.toBuilder()
          .addStatement(
            "return·%T(%L)",
            autoBuildFun.returnType!!,
            propsToCreateWith.joinToCode(",·")
          )
          .build()
      )

      if (remainingMethods.isNotEmpty()) {
        builder.addFunction(
          FunSpec.builder("placeholder")
            .apply {
              addComment("TODO This is a placeholder to mention the following methods need to be moved manually over:")
              for (remaining in remainingMethods) {
                addComment("  $remaining")
              }
            }
            .addStatement("TODO()")
            .returns(NOTHING)
            .build()
        )
      }

      return builder.build()
    }

    data class BuilderProp(val name: String, val type: TypeName, val setters: Set<FunSpec>)

    companion object {
      fun from(
        builderContext: BuilderContext,
        propertyTypes: Map<String, TypeName>,
        parseDocs: Element.() -> String?
      ): AvkBuilder {
        // Setters
        val props = builderContext.setters().entries.map { (prop, setters) ->
          val type = propertyTypes.getValue(prop)
          BuilderProp(
            prop,
            type,
            setters.mapTo(LinkedHashSet()) {
              FunSpec.copyOf(it)
                .withDocsFrom(it, parseDocs)
                .build()
            }
          )
        }

        val builderMethods = builderContext.setters().values.flatten()
          .plus(builderContext.autoBuildMethod())
          .toMutableSet()

        if (builderContext.buildMethod().isPresent) {
          builderMethods += builderContext.buildMethod().get()
        }

        // TODO propertyBuilders

        val remainingMethods =
          ElementFilter.methodsIn(builderContext.builderType().enclosedElements)
            .asSequence()
            .filterNot { it in builderMethods }
            .filterNot { it == builderContext.autoBuildMethod() }
            .map { "${it.modifiers.joinToString(" ")} ${it.returnType} ${it.simpleName}(...)" }
            .toList()

        return AvkBuilder(
          name = builderContext.builderType().simpleName.toString(),
          doc = builderContext.builderType().parseDocs(),
          visibility = if (Modifier.PUBLIC in builderContext.builderType().modifiers) {
            KModifier.PUBLIC
          } else {
            KModifier.INTERNAL
          },
          builderProps = props,
          buildFun = builderContext.buildMethod()
            .map {
              FunSpec.copyOf(it)
                .withDocsFrom(it, parseDocs)
                .build()
            }
            .orElse(null),
          autoBuildFun = FunSpec.copyOf(builderContext.autoBuildMethod())
            .withDocsFrom(builderContext.autoBuildMethod(), parseDocs)
            .build(),
          remainingMethods = remainingMethods,
          classAnnotations = builderContext.builderType().classAnnotations()
        )
      }
    }
  }

  private data class KotlinClass(
    val packageName: String,
    val doc: String?,
    val name: String,
    val visibility: KModifier,
    val isRedacted: Boolean,
    val isParcelable: Boolean,
    val superClass: TypeName?,
    val interfaces: List<TypeName>,
    val typeParams: List<TypeVariableName>,
    val properties: Map<String, PropertyContext>,
    val avkBuilder: AvkBuilder?,
    val toBuilderSpecs: List<FunSpec>,
    val staticCreators: List<FunSpec>,
    val withers: List<FunSpec>,
    val builderFactories: List<FunSpec>,
    val remainingMethods: List<String>,
    val classAnnotations: List<AnnotationSpec>,
    val redactedClassName: ClassName?,
    val staticConstants: List<PropertySpec>,
  ) {
    @Suppress("LongMethod", "ComplexMethod")
    @OptIn(ExperimentalPathApi::class)
    fun writeTo(dir: String, messager: Messager) {
      val typeBuilder = TypeSpec.classBuilder(name)
        .addModifiers(KModifier.DATA)
        .addAnnotations(classAnnotations)
        .addTypeVariables(typeParams)

      if (doc != null) {
        typeBuilder.addKdoc(doc)
      }

      if (isRedacted && redactedClassName != null) {
        typeBuilder.addAnnotation(redactedClassName)
      }

      if (isParcelable) {
        typeBuilder.addAnnotation(PARCELIZE)
      }

      val constructorBuilder = FunSpec.constructorBuilder()

      if (avkBuilder != null) {
        constructorBuilder.addModifiers(KModifier.INTERNAL)
      }

      for ((_, prop) in properties) {
        typeBuilder.addProperty(
          PropertySpec.builder(prop.name, prop.type)
            .initializer(prop.name)
            .addAnnotations(prop.annotations)
            .apply {
              if (!prop.usesGet && !prop.isOverride) {
                // Prop doesn't use getter syntax, use JvmName for java consumers
                addAnnotation(
                  AnnotationSpec.builder(JvmName::class)
                    .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
                    .addMember("%S", prop.funName)
                    .build()
                )
              }
              if (!isRedacted && prop.isRedacted && redactedClassName != null) {
                addAnnotation(redactedClassName)
              }
              prop.doc?.let { addKdoc(it) }
            }
            .build()
        )

        constructorBuilder.addParameter(prop.name, prop.type)

        // Generate the original getter. We'll either deprecate it or we need to keep it
        // No need to generate if this uses get... syntax as Kotlin users would already be using
        // property accessors.
        if (!prop.usesGet) {
          typeBuilder.addFunction(
            FunSpec.builder(prop.funName)
              .apply {
                // Prop uses getter syntax, kotlin usages are fine
                if (prop.usesGet) {
                  addAnnotation(
                    AnnotationSpec.builder(JvmName::class)
                      .addMember("%S", "-")
                      .build()
                  )
                }
                if (prop.isOverride) {
                  // It's from an interface/base class, so leave this as is
                  addModifiers(KModifier.OVERRIDE)
                  addStatement("return ${prop.name}")
                } else {
                  // Hide from Java, deprecate for Kotlin users
                  addAnnotation(JvmSynthetic::class)
                  addAnnotation(
                    AnnotationSpec.builder(JvmName::class)
                      .addMember("%S", "-${prop.name}")
                      .build()
                  )
                  addAnnotation(deprecatedAnnotation("Use the property", prop.name))
                  addStatement("%N()", prop.funName)
                  addStatement("TODO(%S)", "Remove this function. Use the above line to auto-migrate.")
                }
              }
              .returns(prop.type)
              .build()
          )
        }
      }

      typeBuilder.primaryConstructor(constructorBuilder.build())

      superClass?.let { typeBuilder.superclass(it) }
      typeBuilder.addSuperinterfaces(interfaces)

      val companionObjectBuilder = TypeSpec.companionObjectBuilder()
        .addProperties(staticConstants)

      if (staticCreators.isNotEmpty()) {
        companionObjectBuilder.addFunctions(staticCreators)
        constructorBuilder.addModifiers(KModifier.INTERNAL)
      }

      if (withers.isNotEmpty()) {
        typeBuilder.addFunctions(withers)
      }

      if (remainingMethods.isNotEmpty()) {
        typeBuilder.addFunction(
          FunSpec.builder("placeholder")
            .apply {
              addComment("TODO This is a placeholder to mention the following methods need to be moved manually over:")
              for (remaining in remainingMethods) {
                addComment("  $remaining")
              }
            }
            .addStatement("TODO()")
            .returns(NOTHING)
            .build()
        )
      }

      if (avkBuilder != null) {
        typeBuilder.addFunctions(toBuilderSpecs)
        if (builderFactories.isNotEmpty()) {
          companionObjectBuilder.addFunctions(builderFactories)
        }
        typeBuilder.addType(avkBuilder.createType(messager))
      }

      val shouldIncludeCompanion = companionObjectBuilder.funSpecs.isNotEmpty() ||
        companionObjectBuilder.propertySpecs.isNotEmpty()
      if (shouldIncludeCompanion) {
        typeBuilder.addType(companionObjectBuilder.build())
      }

      val file = File(dir).toPath()
      val outputPath = FileSpec.get(packageName, typeBuilder.build())
        .writeToLocal(file)
      val text = outputPath.readText()
      // Post-process to remove any kotlin intrinsic types
      // Is this wildly inefficient? yes. Does it really matter in our cases? nah
      outputPath.writeText(
        text
          .lineSequence()
          .filterNot { it in INTRINSIC_IMPORTS }
          .map {
            if (it.trimStart().startsWith("public ")) {
              val indent = it.substringBefore("public ")
              it.removePrefix(indent).removePrefix("public ").prependIndent(indent)
            } else {
              it
            }
          }
          .joinToString("\n")
      )
    }

    private fun FileSpec.writeToLocal(directory: Path): Path {
      require(Files.notExists(directory) || Files.isDirectory(directory)) {
        "path $directory exists but is not a directory."
      }
      var outputDirectory = directory
      if (packageName.isNotEmpty()) {
        for (packageComponent in packageName.split('.').dropLastWhile { it.isEmpty() }) {
          outputDirectory = outputDirectory.resolve(packageComponent)
        }
      }

      Files.createDirectories(outputDirectory)

      val outputPath = outputDirectory.resolve("$name.kt")
      OutputStreamWriter(
        Files.newOutputStream(outputPath),
        StandardCharsets.UTF_8
      ).use { writer -> writeTo(writer) }
      return outputPath
    }
  }
}

private fun TypeElement.classAnnotations(): List<AnnotationSpec> {
  return annotationMirrors
    .map {
      @Suppress("DEPRECATION")
      AnnotationSpec.get(it)
    }
    .filterNot { (it.typeName as ClassName).packageName == "com.google.auto.value" }
    .filterNot { (it.typeName as ClassName).simpleName == "Metadata" }
    .map { spec ->
      if ((spec.typeName as ClassName).simpleName == "JsonClass") {
        // Strip the 'generator = "avm"' off of this if present
        spec.toBuilder()
          .apply {
            members.removeIf { "avm" in it.toString() }
          }
          .build()
      } else {
        spec
      }
    }
}

private fun TypeName.defaultPrimitiveValue(): CodeBlock =
  when (this) {
    BOOLEAN -> CodeBlock.of("false")
    CHAR -> CodeBlock.of("0.toChar()")
    BYTE -> CodeBlock.of("0.toByte()")
    SHORT -> CodeBlock.of("0.toShort()")
    INT -> CodeBlock.of("0")
    FLOAT -> CodeBlock.of("0f")
    LONG -> CodeBlock.of("0L")
    DOUBLE -> CodeBlock.of("0.0")
    UNIT, Void::class.asTypeName(), NOTHING -> {
      throw IllegalStateException("Parameter with void, Unit, or Nothing type is illegal")
    }
    else -> CodeBlock.of("null")
  }

private fun deprecatedAnnotation(message: String, replaceWith: String): AnnotationSpec {
  return AnnotationSpec.builder(Deprecated::class)
    .addMember("message = %S", message)
    .addMember("replaceWith = %T(%S)", ReplaceWith::class, replaceWith)
    .build()
}

@Suppress("DEPRECATION", "SpreadOperator")
private fun FunSpec.Companion.copyOf(method: ExecutableElement): FunSpec.Builder {
  var modifiers: Set<Modifier> = method.modifiers

  val methodName = method.simpleName.toString()
  val funBuilder = builder(methodName)

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
    funBuilder.parameters[funBuilder.parameters.lastIndex] = funBuilder.parameters.last()
      .toBuilder()
      .addModifiers(KModifier.VARARG)
      .build()
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

private fun ParameterSpec.Companion.parametersWithNullabilityOf(method: ExecutableElement): List<ParameterSpec> =
  method.parameters.map(ParameterSpec.Companion::getWithNullability)

@Suppress("DEPRECATION")
private fun ParameterSpec.Companion.getWithNullability(element: VariableElement): ParameterSpec {
  val name = element.simpleName.toString()
  val isNullable =
    element.annotationMirrors.any { (it.annotationType.asElement() as TypeElement).simpleName.toString() == "Nullable" }
  val type = element.asType().asSafeTypeName().copy(nullable = isNullable)
  return builder(name, type)
    .jvmModifiers(element.modifiers)
    .build()
}

/** Cleans up the generated doc and translates some html to equivalent markdown for Kotlin docs. */
private fun cleanUpDoc(doc: String): String {
  // TODO not covered yet
  //  {@link TimeFormatter#getDateTimeString(SlackDateTime)}
  return doc.replace("<em>", "*")
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
    .replace("\\{@linkplain ($DOC_LINK_REGEX) ($DOC_LINK_REGEX)}".toRegex()) { result: MatchResult ->
      val (foo, baz) = result.destructured
      "[$baz][$foo]"
    }
    // {@linkplain Foo#bar baz} -> [baz][Foo.bar]
    .replace("\\{@linkplain ($DOC_LINK_REGEX)#($DOC_LINK_REGEX) ($DOC_LINK_REGEX)}".toRegex()) { result: MatchResult ->
      val (foo, bar, baz) = result.destructured
      "[$baz][$foo.$bar]"
    }
    // Remove any trailing whitespace
    .replace("(?m)\\s+$".toRegex(), "")
    // Remove any leading space for tags
    .replace("^\\s+@".toRegex(), "")
    .trim()
}

private fun FunSpec.Builder.withDocsFrom(
  e: Element,
  parseDocs: Element.() -> String?
): FunSpec.Builder {
  val doc = e.parseDocs() ?: return this
  return addKdoc(doc)
}
