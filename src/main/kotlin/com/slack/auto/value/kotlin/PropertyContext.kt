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
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeName

@ExperimentalAvkApi
public data class PropertyContext(
  val name: String,
  val funName: String,
  val type: TypeName,
  val annotations: List<AnnotationSpec>,
  val isOverride: Boolean,
  /** Forces the property to be marked with the override modifier too. */
  val forcePropertyOverride: Boolean = false,
  val isRedacted: Boolean,
  val visibility: KModifier,
  val doc: String?
) {
  public val usesGet: Boolean = name.startsWith("get") || funName.startsWith("get")

  // Public for extension
  public companion object
}
