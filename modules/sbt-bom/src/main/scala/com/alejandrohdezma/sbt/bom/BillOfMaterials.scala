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

import scala.xml.Comment
import scala.xml.Elem
import scala.xml.Node

import sbt._

/** Renders Bill of Materials pom sections from sbt modules. Pure data-in/XML-out functions, kept separate from
  * `BomPlugin` so the rendering rules can be unit-tested without an sbt build.
  */
@SuppressWarnings(Array("scalafix:DisableSyntax.noXml"))
private[bom] object BillOfMaterials {

  /** Renders the `<dependencyManagement>` section wrapping the provided `<dependency>` entries. */
  def dependencyManagement(entries: Seq[Node]): Elem =
    <dependencyManagement>
      <dependencies>
        {entries}
      </dependencies>
    </dependencyManagement>

  /** Returns the module with the revision of the override for the same organization and name, if any. Overrides match
    * regardless of cross-version, so `"org" %% "lib" % "1.0.0"` replaces the revision of every Scala variant of `lib`.
    */
  def overridden(module: ModuleID, overrides: Seq[ModuleID]): ModuleID =
    overrides
      .map(o => (o.organization, o.name) -> o.revision)
      .toMap
      .get((module.organization, module.name))
      .fold(module)(module.withRevision)

  /** Drops repeated entries and fails when two `<dependency>` entries pin the same `groupId:artifactId` differently
    * (another version or other exclusions), since a consumer could not tell which one applies.
    */
  def uniquePerArtifact(entries: Seq[Node]): Seq[Node] = {
    val unique = entries.distinct

    val conflicts = unique.collect { case entry @ Elem(_, "dependency", _, _, _*) => entry }
      .groupBy(entry => s"${(entry \ "groupId").text}:${(entry \ "artifactId").text}")
      .collect {
        case (artifact, pins) if pins.size > 1 =>
          val versions = pins.map(pin => (pin \ "version").text).distinct

          if (versions.size > 1) s"$artifact is pinned to ${versions.mkString(", ")}"
          else s"$artifact is pinned to ${versions.mkString} with different exclusions"
      }
      .toList
      .sorted

    if (conflicts.nonEmpty)
      sys.error(
        conflicts.mkString("The BOM pins the same artifact more than once:\n  ", "\n  ", "\n") +
          "Choose a version with `dependencyOverrides` on the BOM project, or remove all but one of them from " +
          "`bomIncludeModules` or `bomIncludeProjects`."
      )

    unique
  }

  /** Renders a module as a single `<dependency>` entry for the provided Scala version.
    *
    * Cross-built modules get their artifact name suffixed for that Scala version (following the module's own
    * cross-version: binary, full, constant...). A cross-built module whose `crossScalaVersions` contains no version
    * with a matching binary version is rendered as an XML comment instead of an entry, since no artifact exists for it.
    * Modules that are not cross-built are always rendered verbatim.
    */
  def perScalaVersion(module: ModuleID, scalaVersion: String, crossScalaVersions: Seq[String]): Node = {
    val binaryVersion = CrossVersion.binaryScalaVersion(scalaVersion)

    val available = crossScalaVersions.map(CrossVersion.binaryScalaVersion).contains(binaryVersion)

    CrossVersion(module.crossVersion, scalaVersion, binaryVersion) match {
      case Some(_) if !available =>
        Comment(s" ${module.name} is not available for Scala $binaryVersion ")

      case cross =>
        val name = cross.fold(module.name)(_(module.name))

        dependency(module.organization, name, module.revision, exclusions(module, scalaVersion))
    }
  }

  /** Renders a module as one `<dependency>` entry per Scala version. Versions producing the same artifact name and
    * exclusions collapse into a single entry, so a module that is not cross-built is rendered exactly once.
    */
  def allScalaVersions(module: ModuleID, scalaVersions: Seq[String]): Seq[Node] =
    scalaVersions
      .map(scalaVersion => (artifactId(module, scalaVersion), exclusions(module, scalaVersion)))
      .distinct
      .map { case (name, excluded) => dependency(module.organization, name, module.revision, excluded) }

  /** Returns the module's artifact name for the provided Scala version, applying the module's cross-version suffix
    * (none for modules that are not cross-built).
    */
  def artifactId(module: ModuleID, scalaVersion: String): String =
    CrossVersion(module.crossVersion, scalaVersion, CrossVersion.binaryScalaVersion(scalaVersion))
      .fold(module.name)(rename => rename(module.name))

  /** Renders the module's `<exclusions>` block for the provided Scala version, or nothing when it has none.
    *
    * Each `InclExclRule` becomes an `<exclusion>`, with its cross-version applied to the artifact name so that the
    * excluded coordinates match the Scala version of the entry holding them. Unset organizations and names become
    * Maven's `*` wildcard. An intransitive module is rendered as a single `*`/`*` exclusion, Maven's way of expressing
    * that nothing should be pulled transitively, which also subsumes any rule the module declares.
    */
  private def exclusions(module: ModuleID, scalaVersion: String): Seq[Node] = {
    val rules =
      if (!module.isTransitive) Seq(exclusion("*", "*"))
      else
        module.exclusions.map { rule =>
          val cross = CrossVersion(rule.crossVersion, scalaVersion, CrossVersion.binaryScalaVersion(scalaVersion))

          val name = rule.name match {
            case "" | "*" => "*"
            case name     => cross.fold(name)(rename => rename(name))
          }

          exclusion(if (rule.organization.isEmpty) "*" else rule.organization, name)
        }

    if (rules.isEmpty) Nil else Seq(<exclusions>{rules}</exclusions>)
  }

  private def exclusion(groupId: String, artifactId: String): Elem =
    <exclusion>
      <groupId>{groupId}</groupId>
      <artifactId>{artifactId}</artifactId>
    </exclusion>

  private def dependency(groupId: String, artifactId: String, version: String, exclusions: Seq[Node]): Elem =
    <dependency>
      <groupId>{groupId}</groupId>
      <artifactId>{artifactId}</artifactId>
      <version>{version}</version>{exclusions}
    </dependency>

}
