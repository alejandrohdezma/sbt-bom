ThisBuild / organization := "com.example"
ThisBuild / version      := "1.0.0"

@transient lazy val assertPom = taskKey[Unit]("Assert the generated BOM pom")

lazy val library = project.settings(crossVersion := CrossVersion.disabled, autoScalaLibrary := false)

lazy val bom = project
  .enablePlugins(BomPlugin)
  .settings(crossVersion := CrossVersion.disabled)
  .settings(bomIncludeProjects := Seq(library))
  .settings(bomIncludeModules := Seq("com.google.guava" % "guava" % "33.4.0-jre"))
  .settings(assertPom := {
    val _       = makePom.value
    val listing = bomDependenciesListing.value

    val entries = (listing \ "dependencies" \ "dependency")
      .map(node => ((node \ "groupId").text, (node \ "artifactId").text, (node \ "version").text))
      .toSet

    val expected = Set(
      ("com.google.guava", "guava", "33.4.0-jre"),
      ("com.example", "library", "1.0.0")
    )

    assert(entries == expected, s"Unexpected BOM entries: $entries")

    val pomPath = (makePom / artifactPath).value.toString

    assert(pomPath.contains("bom-1.0.0.pom"), s"BOM pom should not be suffixed, got: $pomPath")
  })
