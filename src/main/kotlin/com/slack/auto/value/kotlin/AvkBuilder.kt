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
import com.squareup.kotlinpoet.ParameterizedTypeName
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

  @Suppress("LongMethod", "CyclomaticComplexMethod")
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

    @Suppress("DestructuringDeclarationWithTooManyEntries")
    for (builderProp in builderProps) {
      val (propName, type, setters, propertyBuilder) = builderProp
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

      if (propertyBuilder != null) {
        val builderPropSpec =
          PropertySpec.builder(builderProp.builderPropName, propertyBuilder.returnType)
            .addModifiers(PRIVATE)
            .mutable()
            .initializer("null")
            .build()
        builder.addProperty(builderPropSpec)

        // Fill in the builder body
        // Example:
        //   if (reactionsBuilder$ == null) {
        //     reactionsBuilder$ = ImmutableList.builder();
        //   }
        //   return reactionsBuilder$;
        val rawType =
          if (propSpec.type is ParameterizedTypeName) {
            (propSpec.type as ParameterizedTypeName).rawType
          } else {
            propSpec.type
          }
        val nonNullType = rawType.copy(nullable = false)
        val funSpec =
          propertyBuilder
            .toBuilder()
            .beginControlFlow("if (%N == null)", builderPropSpec)
            .apply {
              addStatement("%N·= %T.builder()", builderPropSpec, nonNullType)
              if (setters.isNotEmpty()) {
                // Add the previous set value if one is present
                // if (files == null) {
                //  filesBuilder$ = ImmutableList.builder();
                // } else {
                //  filesBuilder$ = ImmutableList.builder();
                //  filesBuilder$.addAll(files);
                //  files = null;
                // }
                beginControlFlow("if (%N != null)", propSpec)
                // TODO hacky but works for our cases
                val addMethod =
                  if (type.toString().contains("Map")) {
                    "putAll"
                  } else {
                    "addAll"
                  }
                addStatement("%N.$addMethod(%N)", builderPropSpec, propSpec)
                addStatement("%N = null", propSpec)
                endControlFlow()
              }
            }
            .endControlFlow()
            .addStatement("return·%N", builderPropSpec)
            .build()
        builder.addFunction(funSpec)
      }

      // Add build() assignment block
      val extraCheck =
        if (type.isNullable || !useNullablePropType) {
          CodeBlock.of("")
        } else {
          CodeBlock.of("·?:·error(%S)", "${propSpec.name} == null")
        }
      propsToCreateWith += CodeBlock.of("%1N·=·%1N%2L", propSpec, extraCheck)

      for (setter in setters) {
        // TODO if there's a builder, check the builder is null first
        if (setter.parameters.size != 1) {
          messager.printMessage(WARNING, "Setter with surprising params: ${setter.name}")
        }
        val setterBlock = CodeBlock.of("this.%N·= %N", propSpec, setter.parameters[0])
        val setterSpec =
          setter
            .toBuilder()
            .apply {
              if (propertyBuilder != null) {
                // Need to check if the builder is null
                // if (reactionsBuilder$ != null) {
                //   throw new IllegalStateException("Cannot set reactions after calling
                // reactionsBuilder()");
                // }
                beginControlFlow("check(%N == null)", builderProp.builderPropName)
                addStatement(
                  "%S",
                  "Cannot set ${propSpec.name} after calling ${builderProp.builderPropName}()"
                )
                endControlFlow()
                addStatement("%L", setterBlock)
                addStatement("return·this")
              } else {
                // Assume this is a normal setter
                addStatement("return·apply·{·%L }", setterBlock)
              }
            }
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
        .apply {
          // For all builder types, we need to init or assign them first
          // Example:
          //   if (reactionsBuilder$ != null) {
          //     this.reactions = reactionsBuilder$.build();
          //   } else if (this.reactions == null) {
          //     this.reactions = ImmutableList.of();
          //   }
          for (builderProp in builderProps) {
            if (builderProp.builder != null) {
              beginControlFlow("if (%N != null)", builderProp.builderPropName)
              addStatement("this.%N = %N.build()", builderProp.name, builderProp.builderPropName)
              // property builders can never be nullable
              nextControlFlow("else if (this.%N == null)", builderProp.name)
              val rawType =
                if (builderProp.type is ParameterizedTypeName) {
                  builderProp.type.rawType
                } else {
                  builderProp.type
                }
              addStatement("this.%N = %T.of()", builderProp.name, rawType)
              endControlFlow()
            }
          }
        }
        .addStatement("return·%T(%L)", autoBuildFun.returnType, propsToCreateWith.joinToCode(",·"))
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
    val setters: Set<FunSpec>,
    val builder: FunSpec?,
  ) {
    val builderPropName: String = "${name}Builder"
  }

  // Public for extension
  public companion object
}
