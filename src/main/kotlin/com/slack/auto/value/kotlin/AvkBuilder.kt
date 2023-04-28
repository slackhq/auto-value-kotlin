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
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.KModifier.INTERNAL
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.NOTHING
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode
import javax.annotation.processing.Messager
import javax.tools.Diagnostic.Kind.WARNING

@ExperimentalAvkApi
public data class AvkBuilder(
  val name: String,
  val doc: String?,
  val visibility: KModifier,
  val builderProps: List<BuilderProperty>,
  val buildFun: FunSpec?,
  val autoBuildFun: FunSpec,
  val remainingMethods: List<String>,
  val classAnnotations: List<AnnotationSpec>
) {

  @Suppress("LongMethod")
  public fun createType(messager: Messager): TypeSpec {
    val builder =
      TypeSpec.classBuilder(name).addModifiers(visibility).addAnnotations(classAnnotations)

    val constructorBuilder = FunSpec.constructorBuilder().addModifiers(INTERNAL)

    @Suppress("MagicNumber")
    if (builderProps.size >= MAX_PARAMS) {
      builder.addAnnotation(
        AnnotationSpec.builder(Suppress::class).addMember("%S", "LongParameterList").build()
      )
    }

    val propsToCreateWith = mutableListOf<CodeBlock>()

    for ((propName, type, setters) in builderProps) {
      // Add param to constructor
      val defaultValue =
        if (type.isNullable) {
          CodeBlock.of("null")
        } else {
          type.defaultPrimitiveValue()
        }
      val useNullablePropType = defaultValue.toString() == "null"
      val param =
        ParameterSpec.builder(propName, type.copy(nullable = useNullablePropType))
          .defaultValue(defaultValue)
          .build()
      constructorBuilder.addParameter(param)

      // Add private properties
      val propSpec =
        PropertySpec.builder(propName, param.type)
          .addModifiers(PRIVATE)
          .mutable()
          .initializer("%N", param.name)
          .build()
      builder.addProperty(propSpec)

      // Add build() assignment block
      val extraCheck =
        if (type.isNullable || !useNullablePropType) {
          CodeBlock.of("")
        } else {
          CodeBlock.of("·?:·error(%S)", "${propSpec.name} == null")
        }
      propsToCreateWith += CodeBlock.of("%1N·=·%1N%2L", propSpec, extraCheck)

      for (setter in setters) {
        if (setter.parameters.size != 1) {
          messager.printMessage(WARNING, "Setter with surprising params: ${setter.name}")
        }
        val setterSpec =
          setter
            .toBuilder()
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
      autoBuildFun
        .toBuilder()
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

    return builder.build()
  }

  @ExperimentalAvkApi
  public data class BuilderProperty(
    val name: String,
    val type: TypeName,
    val setters: Set<FunSpec>
  )

  // Public for extension
  public companion object
}
