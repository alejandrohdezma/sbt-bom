ThisBuild / organization := "com.example"
ThisBuild / version      := "1.0.0"
ThisBuild / scalaVersion := "2.13.16"

@transient lazy val assertPom = taskKey[Unit]("Assert the generated BOM pom")

lazy val core = project.settings(moduleName := "core-library")

lazy val bom = project
  .enablePlugins(BomPlugin)
  .settings(bomIncludeProjects := Seq(core))
  .settings(assertPom := {
    val _       = makePom.value
    val listing = bomDependenciesListing.value

    val entries = (listing \ "dependencies" \ "dependency")
      .map(node => ((node \ "groupId").text, (node \ "artifactId").text, (node \ "version").text))
      .toSet

    assert(entries == Set(("com.example", "core-library_2.13", "1.0.0")), s"Unexpected BOM entries: $entries")
  })
