/*
 * Copyright 2026 Alejandro Hernández <https://github.com/alejandrohdezma>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alejandrohdezma.sbt.bom

import scala.xml.Elem

import sbt.Keys._
import sbt._

/** Generates "Bill of Materials" (BOM) poms.
  *
  * The BOM's `<dependencyManagement>` section pins versions for other projects in the build (`bomIncludeProjects`) and
  * for external artifacts (`bomIncludeModules`). Only settings are read from the included projects, so generating or
  * publishing the BOM never compiles them.
  *
  * The BOM project's own `crossVersion` selects between two modes:
  *
  *   - `CrossVersion.binary` (the default): one BOM per Scala version. Under each `++<scala-version>` pass the BOM
  *     artifact gets that Scala suffix and lists entries suffixed for that Scala version only. Included projects that
  *     don't cross-build for the running Scala version are skipped (a comment is left in their place).
  *   - `CrossVersion.disabled`: a single unsuffixed BOM listing every Scala variant of each entry — projects according
  *     to their own `crossScalaVersions`, external modules according to the BOM project's.
  *
  * A successor to Lightbend's `sbt-bill-of-materials` plugin (same keys), cross-published for sbt 1 and sbt 2.
  */
object BomPlugin extends AutoPlugin {

  override def trigger = noTrigger

  object autoImport {

    /** Projects whose published coordinates are pinned in the BOM's `<dependencyManagement>`. Read through each
      * project's `projectID` (so `moduleName` overrides are honoured and the projects are never compiled) and suffixed
      * with the relevant Scala version(s) when cross-built.
      */
    val bomIncludeProjects = settingKey[Seq[ProjectReference]] {
      "Projects to include in the Bill of Materials pom"
    }

    /** External artifacts pinned in the BOM's `<dependencyManagement>`, alongside `bomIncludeProjects`. Cross-built
      * `ModuleID`s are suffixed with the relevant Scala version(s) following their cross-version; Java ones are emitted
      * as declared.
      */
    val bomIncludeModules = settingKey[Seq[ModuleID]] {
      "External artifacts to include in the Bill of Materials pom"
    }

    /** The generated `<dependencyManagement>` section. Appended to `pomExtra` by default; override or inspect it to
      * customise the generated pom.
      */
    val bomDependenciesListing = settingKey[Elem] {
      "The generated `<dependencyManagement>` section added to the BOM pom"
    }

  }

  import autoImport._

  override def projectSettings = Seq(
    publishMavenStyle            := true,
    autoScalaLibrary             := false,
    packageBin / publishArtifact := false,
    packageSrc / publishArtifact := false,
    packageDoc / publishArtifact := false,
    bomIncludeProjects           := Nil,
    bomIncludeModules            := Nil,
    bomDependenciesListing       := dependencyManagement.value,
    pomExtra                     := pomExtra.value :+ bomDependenciesListing.value
  )

  /** The `<dependencyManagement>` block for the BOM pom. Concatenates each `bomIncludeProjects` entry's `projectID`
    * with `bomIncludeModules` and renders them through `BillOfMaterials`, either once for the current Scala version or
    * once per Scala version depending on the BOM project's `crossVersion`. A `settingDyn` because it fans out to
    * per-project `projectID`/`crossScalaVersions` settings.
    */
  private val dependencyManagement: Def.Initialize[Elem] = Def.settingDyn {
    val currentScalaVersion   = scalaVersion.value
    val bomCrossScalaVersions = crossScalaVersions.value

    val singleBom =
      CrossVersion(crossVersion.value, currentScalaVersion, scalaBinaryVersion.value).isEmpty

    bomIncludeProjects.value
      .map(project => Def.setting((project / projectID).value -> (project / crossScalaVersions).value))
      .join
      .apply(bomIncludeModules.value.map(_ -> bomCrossScalaVersions) ++ _)
      .apply(_.flatMap { case (module, scalaVersions) =>
        if (singleBom) BillOfMaterials.allScalaVersions(module, scalaVersions)
        else List(BillOfMaterials.perScalaVersion(module, currentScalaVersion, scalaVersions))
      })
      .apply(BillOfMaterials.dependencyManagement)
  }

}
