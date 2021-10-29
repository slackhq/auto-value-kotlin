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

import com.google.auto.common.MoreElements
import com.google.auto.common.MoreElements.isAnnotationPresent
import com.google.auto.value.AutoValue
import com.google.auto.value.extension.AutoValueExtension
import com.google.auto.value.extension.AutoValueExtension.BuilderContext
import com.slack.auto.value.kotlin.AvkBuilder.BuilderProperty
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DelicateKotlinPoetApi
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeVariableName
import com.squareup.kotlinpoet.joinToCode
import com.squareup.moshi.Json
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.NestingKind
import javax.lang.model.util.ElementFilter
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic

public class AutoValueKotlinExtension : AutoValueExtension() {

  public companion object {
    // Options
    public const val OPT_SRC: String = "avkSrc"
    public const val OPT_TARGETS: String = "avkTargets"
    public const val OPT_IGNORE_NESTED: String = "avkIgnoreNested"
  }

  internal val collectedKclassees = ConcurrentHashMap<ClassName, KotlinClass>()
  internal val collectedEnums = ConcurrentHashMap<ClassName, TypeSpec>()
  private lateinit var elements: Elements
  private lateinit var types: Types

  override fun getSupportedOptions(): Set<String> {
    return setOf(OPT_SRC, OPT_TARGETS, OPT_IGNORE_NESTED)
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
    return withDocsFrom(e) { parseDocs(elements) }
  }

  @Suppress("DEPRECATION", "LongMethod", "ComplexMethod", "NestedBlockDepth", "ReturnCount")
  override fun generateClass(
    context: Context,
    className: String,
    classToExtend: String,
    isFinal: Boolean
  ): String? {
    val options = Options(context.processingEnvironment().options)

    val ignoreNested =
      context.processingEnvironment().options[OPT_IGNORE_NESTED]?.toBoolean() ?: false

    if (options.targets.isNotEmpty() && context.autoValueClass().simpleName.toString() !in options.targets) {
      return null
    }

    val avClass = context.autoValueClass()

    val isTopLevel = avClass.nestingKind == NestingKind.TOP_LEVEL
    if (!isTopLevel) {
      val isParentAv = isAnnotationPresent(
        MoreElements.asType(avClass.enclosingElement),
        AutoValue::class.java
      )
      if (!isParentAv) {
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
      }
    }

    // Check for non-builder nested classes, which cannot be converted with this
    val nonBuilderNestedTypes = ElementFilter.typesIn(avClass.enclosedElements)
      .filterNot { nestedType ->
        context.builder()
          .map { nestedType == it.builderType() }
          .orElse(false)
      }

    val (enums, nonEnums) = nonBuilderNestedTypes.partition { it.kind == ElementKind.ENUM }

    val (nestedAvClasses, remainingTypes) = nonEnums.partition { isAnnotationPresent(it, AutoValue::class.java) }

    if (remainingTypes.isNotEmpty()) {
      remainingTypes.forEach {
        context.processingEnvironment().messager
          .printMessage(
            Diagnostic.Kind.ERROR,
            "Cannot convert non-autovalue nested classes to Kotlin safely. Please move this to top-level first.",
            it
          )
      }
      return null
    }

    for (enumType in enums) {
      val (cn, spec) = EnumConversion.convert(
        elements,
        context.processingEnvironment().messager,
        enumType
      ) ?: continue
      collectedEnums[cn] = spec
    }

    val classDoc = avClass.parseDocs(elements)

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
          doc = method.parseDocs(elements)
        )
      }

    val classAnnotations = avClass.classAnnotations().toMutableList()
    val isClassRedacted = classAnnotations.anyRedacted() ||
      // If all the properties are redacted, redacted the class?
      properties.values.all { it.isRedacted }

    val isParcelable = context.processingEnvironment().isParcelable(avClass)

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
      avkBuilder = AvkBuilder.from(builder, propertyTypes) { parseDocs(elements) }

      builderFactories += builder.builderMethods()
      builderFactorySpecs += builder.builderMethods()
        .map {
          FunSpec.copyOf(it)
            .withDocsFrom(it)
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

            field.parseDocs(elements)?.let { addKdoc(it) }
          }
          .build()
      }

    val superclass = avClass.superclass.asSafeTypeName()
      .takeUnless { it == ClassName("java.lang", "Object") }

    val kClass = KotlinClass(
      packageName = context.packageName(),
      doc = classDoc,
      name = avClass.simpleName.toString(),
      visibility = avClass.visibility,
      isRedacted = isClassRedacted,
      isParcelable = isParcelable,
      superClass = superclass,
      interfaces = avClass.interfaces.associate { it.asSafeTypeName() to null },
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
      staticConstants = staticConstants,
      isTopLevel = isTopLevel,
      children = nestedAvClasses
        .mapTo(LinkedHashSet()) { it.asClassName() }
        .plus(collectedEnums.keys)
    )

    collectedKclassees[context.autoValueClass().asClassName()] = kClass

    return null
  }
}

private fun AvkBuilder.Companion.from(
  builderContext: BuilderContext,
  propertyTypes: Map<String, TypeName>,
  parseDocs: Element.() -> String?
): AvkBuilder {
  // Setters
  val props = builderContext.setters().entries.map { (prop, setters) ->
    val type = propertyTypes.getValue(prop)
    BuilderProperty(
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
    visibility = builderContext.builderType().visibility,
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
