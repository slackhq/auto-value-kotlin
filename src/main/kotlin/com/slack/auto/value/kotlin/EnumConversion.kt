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
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DelicateKotlinPoetApi
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import javax.annotation.processing.Messager
import javax.lang.model.element.ElementKind.ENUM
import javax.lang.model.element.ElementKind.ENUM_CONSTANT
import javax.lang.model.element.TypeElement
import javax.lang.model.util.ElementFilter
import javax.lang.model.util.Elements
import javax.tools.Diagnostic.Kind.ERROR

/**
 * Simple utility to convert enums from Java to Kotlin.
 *
 * Can handle nested enums but will error out when encountering anything else.
 */
@ExperimentalAvkApi
public object EnumConversion {
  @Suppress("ReturnCount", "ComplexMethod")
  @OptIn(DelicateKotlinPoetApi::class)
  public fun convert(
    elements: Elements,
    messager: Messager,
    element: TypeElement
  ): Pair<ClassName, TypeSpec>? {
    val className = element.asClassName()
    val docs = element.parseDocs(elements)
    return className to
      TypeSpec.enumBuilder(className.simpleName)
        .addAnnotations(element.classAnnotations())
        .addModifiers(element.visibility)
        .apply {
          docs?.let { addKdoc(it) }
          var isMoshiSerialized = false
          for (field in ElementFilter.fieldsIn(element.enclosedElements)) {
            if (field.kind == ENUM_CONSTANT) {
              val annotations =
                field.annotationMirrors.map {
                  if (it.annotationType.asTypeName() == JSON_CN) {
                    isMoshiSerialized = true
                  }
                  AnnotationSpec.get(it)
                }
              addEnumConstant(
                field.simpleName.toString(),
                TypeSpec.anonymousClassBuilder()
                  .addAnnotations(annotations)
                  .apply { field.parseDocs(elements)?.let { addKdoc(it) } }
                  .build()
              )
            }
          }

          if (isMoshiSerialized && annotations.none { it.typeName == JSON_CLASS_CN }) {
            addAnnotation(
              AnnotationSpec.builder(JSON_CLASS_CN).addMember("generateAdapter = false").build()
            )
          }

          for (nestedType in ElementFilter.typesIn(element.enclosedElements)) {
            if (nestedType.kind == ENUM) {
              convert(elements, messager, nestedType)?.second?.let(::addType)
            } else {
              messager.printMessage(
                ERROR,
                "Nested types in enums can only be other enums",
                nestedType
              )
              return null
            }
          }

          for (method in ElementFilter.methodsIn(element.enclosedElements)) {
            if (method.simpleName.toString() in setOf("values", "valueOf")) continue
            messager.printMessage(ERROR, "Cannot convert nested enums with methods", method)
            return null
          }
        }
        .build()
  }
}
