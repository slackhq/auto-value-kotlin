package com.slack.auto.value.kotlin

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DelicateKotlinPoetApi
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
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
  @OptIn(DelicateKotlinPoetApi::class)
  public fun convert(
    elements: Elements,
    messager: Messager,
    element: TypeElement
  ): Pair<ClassName, TypeSpec>? {
    val className = element.asClassName()
    val docs = element.parseDocs(elements)
    return className to TypeSpec.enumBuilder(className.simpleName)
      .addModifiers(element.visibility)
      .apply {
        docs?.let {
          addKdoc(it)
        }
        for (field in ElementFilter.fieldsIn(element.enclosedElements)) {
          if (field.kind == ENUM_CONSTANT) {
            val annotations = field.annotationMirrors
              .map { AnnotationSpec.get(it) }
            addEnumConstant(
              field.simpleName.toString(),
              TypeSpec.anonymousClassBuilder()
                .addAnnotations(annotations)
                .apply {
                  field.parseDocs(elements)?.let {
                    addKdoc(it)
                  }
                }
                .build()
            )
          }
        }

        for (nestedType in ElementFilter.typesIn(element.enclosedElements)) {
          if (nestedType.kind == ENUM) {
            convert(elements, messager, nestedType)?.second?.let(::addType)
          } else {
            messager.printMessage(ERROR, "Nested types in enums can only be other enums", nestedType)
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