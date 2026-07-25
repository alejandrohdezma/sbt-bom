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
import scala.xml.Node
import scala.xml.Utility

import sbt._

import munit.FunSuite

class BillOfMaterialsSuite extends FunSuite {

  test("artifactId applies the module's cross-version to its name") {
    val modules = List(
      module.cross(CrossVersion.binary), module.cross(CrossVersion.disabled), module.cross(CrossVersion.full),
      module.cross(CrossVersion.for3Use2_13), module.cross(CrossVersion.constant("special"))
    )

    val obtained = modules.map(BillOfMaterials.artifactId(_, "3.3.7"))

    val expected = List("lib_3", "lib", "lib_3.3.7", "lib_2.13", "lib_special")

    assertEquals(obtained, expected)
  }

  test("artifactId uses the binary version on Scala 2") {
    val obtained = BillOfMaterials.artifactId(module.cross(CrossVersion.binary), "2.13.16")

    assertEquals(obtained, "lib_2.13")
  }

  test("perScalaVersion renders a cross-built module suffixed for the Scala version") {
    val obtained = BillOfMaterials.perScalaVersion(
      module.cross(CrossVersion.binary),
      "3.3.7",
      List("2.13.16", "3.3.7")
    )

    val expected =
      "<dependency><groupId>com.example</groupId><artifactId>lib_3</artifactId><version>1.0.0</version></dependency>"

    assertEquals(render(obtained), expected)
  }

  test("perScalaVersion renders a comment when the module is not available for the Scala version") {
    val obtained = BillOfMaterials.perScalaVersion(
      module.cross(CrossVersion.binary),
      "3.3.7",
      List("2.13.16")
    )

    assertEquals(render(obtained), "<!-- lib is not available for Scala 3 -->")
  }

  test("perScalaVersion always renders modules that are not cross-built") {
    val obtained = BillOfMaterials.perScalaVersion(
      module.cross(CrossVersion.disabled),
      "3.3.7",
      List("2.13.16")
    )

    val expected =
      "<dependency><groupId>com.example</groupId><artifactId>lib</artifactId><version>1.0.0</version></dependency>"

    assertEquals(render(obtained), expected)
  }

  test("allScalaVersions renders one entry per Scala version") {
    val obtained = BillOfMaterials.allScalaVersions(module.cross(CrossVersion.binary), List("2.13.16", "3.3.7"))

    val expected = List(
      "<dependency><groupId>com.example</groupId><artifactId>lib_2.13</artifactId><version>1.0.0</version></dependency>",
      "<dependency><groupId>com.example</groupId><artifactId>lib_3</artifactId><version>1.0.0</version></dependency>"
    )

    assertEquals(obtained.toList.map(render), expected)
  }

  test("allScalaVersions applies the full cross-version per Scala version") {
    val obtained = BillOfMaterials.allScalaVersions(module.cross(CrossVersion.full), List("2.13.16", "3.3.7"))

    val expected = List(
      "<dependency><groupId>com.example</groupId><artifactId>lib_2.13.16</artifactId><version>1.0.0</version></dependency>",
      "<dependency><groupId>com.example</groupId><artifactId>lib_3.3.7</artifactId><version>1.0.0</version></dependency>"
    )

    assertEquals(obtained.toList.map(render), expected)
  }

  test("allScalaVersions collapses modules that are not cross-built into a single entry") {
    val obtained = BillOfMaterials.allScalaVersions(module.cross(CrossVersion.disabled), List("2.13.16", "3.3.7"))

    val expected = List(
      "<dependency><groupId>com.example</groupId><artifactId>lib</artifactId><version>1.0.0</version></dependency>"
    )

    assertEquals(obtained.toList.map(render), expected)
  }

  test("dependencyManagement wraps the provided entries") {
    val entries = BillOfMaterials.allScalaVersions(module.cross(CrossVersion.binary), List("2.13.16", "3.3.7"))

    val obtained = BillOfMaterials.dependencyManagement(entries)

    val expected =
      "<dependencyManagement><dependencies>" +
        "<dependency><groupId>com.example</groupId><artifactId>lib_2.13</artifactId><version>1.0.0</version></dependency>" +
        "<dependency><groupId>com.example</groupId><artifactId>lib_3</artifactId><version>1.0.0</version></dependency>" +
        "</dependencies></dependencyManagement>"

    assertEquals(render(obtained), expected)
  }

  /** The artifact used as input on every test. */
  def module: ModuleID = ModuleID("com.example", "lib", "1.0.0")

  /** Renders a node as a whitespace-free string for stable comparisons. */
  def render(node: Node): String = node match {
    case elem: Elem => Utility.trim(elem).toString()
    case other      => other.toString()
  }

}
