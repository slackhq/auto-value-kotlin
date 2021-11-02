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

import java.io.File

public class Options(optionsMap: Map<String, String>) {

  public val srcDir: File = optionsMap[OPT_SRC]?.let { File(it) } ?: error("Missing src dir option")

  public val targets: Set<String> = optionsMap[OPT_TARGETS]?.splitToSequence(":")
    ?.toSet()
    ?: emptySet()

  public val ignoreNested: Boolean = optionsMap[OPT_IGNORE_NESTED]?.toBooleanStrict() ?: false

  public companion object {
    public const val OPT_SRC: String = "avkSrc"
    public const val OPT_TARGETS: String = "avkTargets"
    public const val OPT_IGNORE_NESTED: String = "avkIgnoreNested"

    internal val ALL = setOf(OPT_SRC, OPT_TARGETS, OPT_IGNORE_NESTED)
  }
}
