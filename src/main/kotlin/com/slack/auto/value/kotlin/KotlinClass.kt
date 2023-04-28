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

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.AnnotationSpec.UseSiteTarget.GET
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.KModifier.DATA
import com.squareup.kotlinpoet.KModifier.INTERNAL
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.NOTHING
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import javax.annotation.processing.Messager

@ExperimentalAvkApi
public data class KotlinClass(
  val packageName: String,
  val doc: String?,
  val name: String,
  val visibility: KModifier,
  val isRedacted: Boolean,
  val isParcelable: Boolean,
  val superClass: TypeName?,
  val interfaces: Map<TypeName, CodeBlock?>,
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
  val isTopLevel: Boolean,
  val children: Set<ClassName>
) {
  @Suppress("LongMethod", "ComplexMethod")
  public fun toTypeSpec(messager: Messager): TypeSpec {
    val typeBuilder =
      TypeSpec.classBuilder(name)
        .addModifiers(DATA)
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
      constructorBuilder.addModifiers(INTERNAL)
    }

    // TODO does parcelize care about these?
    val needsConstructorDefaultValues = classAnnotations.any { it.typeName == JSON_CLASS_CN }
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
                  .useSiteTarget(GET)
                  .addMember("%S", prop.funName)
                  .build()
              )
            }
            if (!isRedacted && prop.isRedacted && redactedClassName != null) {
              addAnnotation(redactedClassName)
            }
            if (prop.forcePropertyOverride) {
              addModifiers(OVERRIDE)
            }
            prop.doc?.let { addKdoc(it) }
          }
          .build()
      )

      val defaultCodeBlock =
        when {
          // Builders handle the defaults unless it's a Json class, in which case we need both!
          avkBuilder != null && !needsConstructorDefaultValues -> null
          else -> prop.defaultValue
        }
      constructorBuilder.addParameter(
        ParameterSpec.builder(prop.name, prop.type)
          .apply { defaultCodeBlock?.let { defaultValue(it) } }
          .build()
      )

      // Generate the original getter. We'll either deprecate it or we need to keep it
      // No need to generate if this uses get... syntax as Kotlin users would already be using
      // property accessors.
      if (!prop.usesGet) {
        typeBuilder.addFunction(
          FunSpec.builder(prop.funName)
            .apply {
              // Prop uses getter syntax, kotlin usages are fine
              if (prop.usesGet) {
                addAnnotation(AnnotationSpec.builder(JvmName::class).addMember("%S", "-").build())
              }
              if (prop.isOverride) {
                // It's from an interface/base class, so leave this as is
                addModifiers(OVERRIDE)
                addStatement("return ${prop.name}")
              } else {
                // Hide from Java, deprecate for Kotlin users
                addAnnotation(JvmSynthetic::class)
                addAnnotation(
                  AnnotationSpec.builder(JvmName::class).addMember("%S", "-${prop.name}").build()
                )
                addAnnotation(deprecatedAnnotation("Use the property", prop.name))
                addStatement("%N()", prop.funName)
                addStatement(
                  "TODO(%S)",
                  "Remove this function. Use the above line to auto-migrate."
                )
              }
            }
            .returns(prop.type)
            .build()
        )
      }
    }

    typeBuilder.primaryConstructor(constructorBuilder.build())

    superClass?.let { typeBuilder.superclass(it) }
    interfaces.forEach { (supertype, delegate) ->
      if (delegate == null) {
        typeBuilder.addSuperinterface(supertype)
      } else {
        typeBuilder.addSuperinterface(supertype, delegate)
      }
    }

    val companionObjectBuilder = TypeSpec.companionObjectBuilder().addProperties(staticConstants)

    if (staticCreators.isNotEmpty()) {
      companionObjectBuilder.addFunctions(staticCreators)
      constructorBuilder.addModifiers(INTERNAL)
    }

    if (withers.isNotEmpty()) {
      typeBuilder.addFunctions(withers)
    }

    if (remainingMethods.isNotEmpty()) {
      typeBuilder.addFunction(
        FunSpec.builder("placeholder")
          .apply {
            addComment(
              "TODO This is a placeholder to mention the following methods need to be moved manually over:"
            )
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

    val shouldIncludeCompanion =
      companionObjectBuilder.funSpecs.isNotEmpty() ||
        companionObjectBuilder.propertySpecs.isNotEmpty()
    if (shouldIncludeCompanion) {
      typeBuilder.addType(companionObjectBuilder.build())
    }

    return typeBuilder.build()
  }

  // Public for extension
  public companion object
}
