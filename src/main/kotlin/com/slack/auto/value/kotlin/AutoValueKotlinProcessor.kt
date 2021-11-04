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

import com.google.auto.service.AutoService
import com.google.auto.value.AutoValue
import com.google.auto.value.extension.AutoValueExtension
import com.google.auto.value.processor.AutoValueProcessor
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeSpec
import okio.Buffer
import okio.blackholeSink
import okio.buffer
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.Writer
import java.net.URI
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedOptions
import javax.lang.model.SourceVersion
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.Modifier
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.NestingKind
import javax.lang.model.element.NestingKind.TOP_LEVEL
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic.Kind
import javax.tools.Diagnostic.Kind.ERROR
import javax.tools.FileObject
import javax.tools.JavaFileManager.Location
import javax.tools.JavaFileObject
import javax.tools.JavaFileObject.Kind.CLASS
import javax.tools.JavaFileObject.Kind.OTHER

@SupportedOptions(Options.OPT_SRC, Options.OPT_IGNORE_NESTED, Options.OPT_TARGETS)
@AutoService(Processor::class)
public class AutoValueKotlinProcessor : AbstractProcessor() {

  private val collectedClasses: MutableMap<ClassName, KotlinClass> = ConcurrentHashMap<ClassName, KotlinClass>()
  private val collectedEnums: MutableMap<ClassName, TypeSpec> = ConcurrentHashMap<ClassName, TypeSpec>()
  private lateinit var options: Options

  override fun init(processingEnv: ProcessingEnvironment) {
    super.init(processingEnv)
    options = Options(processingEnv.options)
  }

  override fun getSupportedAnnotationTypes(): Set<String> {
    return setOf(AutoValue::class.java.canonicalName)
  }

  override fun getSupportedSourceVersion(): SourceVersion {
    return SourceVersion.latest()
  }

  @Suppress("ReturnCount")
  override fun process(
    annotations: Set<TypeElement>,
    roundEnv: RoundEnvironment
  ): Boolean {
    val avClasses = roundEnv.getElementsAnnotatedWith(AutoValue::class.java)
    if (avClasses.isNotEmpty()) {
      processAvElements(annotations, roundEnv, avClasses)
    }

    // We're done processing, write all our collected classes down
    if (roundEnv.processingOver()) {
      if (collectedClasses.isEmpty()) {
        processingEnv.messager.printMessage(ERROR, "No AutoValue classes found")
        return false
      }
      val srcDir =
        processingEnv.options[Options.OPT_SRC] ?: error("Missing src dir option")
      val roots = collectedClasses.filterValues { it.isTopLevel }
        .toMutableMap()
      for ((_, root) in roots) {
        val spec = composeTypeSpec(root)
        spec.writeCleanlyTo(root.packageName, srcDir)
      }
    }
    return false
  }

  private fun composeTypeSpec(kotlinClass: KotlinClass): TypeSpec {
    val spec = kotlinClass.toTypeSpec(processingEnv.messager)
    return spec.toBuilder()
      .apply {
        for (child in kotlinClass.children) {
          val enumChild = collectedEnums.remove(child)
          if (enumChild != null) {
            addType(enumChild)
            continue
          }
          val childKotlinClass = collectedClasses.remove(child)
            ?: error("Missing child class $child for parent ${kotlinClass.name}")
          addType(composeTypeSpec(childKotlinClass))
        }
      }
      .build()
  }

  private fun processAvElements(
    annotations: Set<TypeElement>,
    roundEnv: RoundEnvironment,
    avClasses: Set<Element>
  ) {
    if (options.targets.isNotEmpty() && avClasses.none { it.simpleName.toString() in options.targets }) {
      // Nothing to do this round
      return
    }

    // Load extensions ourselves
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    val extensions = try {
      ServiceLoader.load(AutoValueExtension::class.java, AutoValueExtension::class.java.classLoader).toList()
    } catch (e: Exception) {
      emptyList()
    }

    // Make our extension
    val avkExtension = AutoValueKotlinExtension(processingEnv.messager, options)

    // Create an in-memory av processor and run it
    val avProcessor = AutoValueProcessor(extensions + avkExtension)
    avProcessor.init(object : ProcessingEnvironment by processingEnv {
      override fun getMessager(): Messager = NoOpMessager

      override fun getFiler(): Filer = NoOpFiler
    })
    avProcessor.process(annotations, roundEnv)

    // Save off our extracted classes
    collectedClasses += avkExtension.collectedKclassees
    collectedEnums += avkExtension.collectedEnums
  }
}

private object NoOpMessager : Messager {
  override fun printMessage(kind: Kind?, msg: CharSequence?) {
    // Do nothing
  }

  override fun printMessage(
    kind: Kind,
    msg: CharSequence,
    element: Element?
  ) {
    // Do nothing
  }

  override fun printMessage(kind: Kind?, msg: CharSequence?, e: Element?, a: AnnotationMirror?) {
    // Do nothing
  }

  override fun printMessage(
    kind: Kind?,
    msg: CharSequence?,
    e: Element?,
    a: AnnotationMirror?,
    v: AnnotationValue?
  ) {
    // Do nothing
  }
}

@Suppress("TooManyFunctions")
private class NoOpJfo(
  private val name: String,
  private val kind: JavaFileObject.Kind = JavaFileObject.Kind.SOURCE
) : JavaFileObject {
  override fun toUri(): URI {
    return URI("/dev/null")
  }

  override fun getName(): String {
    return name
  }

  override fun openInputStream(): InputStream {
    return Buffer().inputStream()
  }

  override fun openOutputStream(): OutputStream {
    return blackholeSink().buffer().outputStream()
  }

  override fun openReader(ignoreEncodingErrors: Boolean): Reader {
    return openInputStream().reader()
  }

  override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence {
    return ""
  }

  override fun openWriter(): Writer {
    return openOutputStream().writer()
  }

  override fun getLastModified(): Long {
    return -1L
  }

  override fun delete(): Boolean {
    return false
  }

  override fun getKind(): JavaFileObject.Kind {
    return kind
  }

  override fun isNameCompatible(simpleName: String?, kind: JavaFileObject.Kind?): Boolean {
    return true
  }

  override fun getNestingKind(): NestingKind {
    return TOP_LEVEL
  }

  override fun getAccessLevel(): Modifier {
    return PUBLIC
  }
}

private object NoOpFiler : Filer {
  override fun createSourceFile(
    name: CharSequence,
    vararg originatingElements: Element?
  ): JavaFileObject {
    return NoOpJfo(name.toString())
  }

  override fun createClassFile(
    name: CharSequence,
    vararg originatingElements: Element?
  ): JavaFileObject {
    return NoOpJfo(name.toString(), CLASS)
  }

  override fun createResource(
    location: Location?,
    moduleAndPkg: CharSequence?,
    relativeName: CharSequence?,
    vararg originatingElements: Element?
  ): FileObject {
    return NoOpJfo(relativeName.toString(), OTHER)
  }

  override fun getResource(
    location: Location?,
    moduleAndPkg: CharSequence?,
    relativeName: CharSequence?
  ): FileObject {
    return NoOpJfo(relativeName.toString(), OTHER)
  }
}
