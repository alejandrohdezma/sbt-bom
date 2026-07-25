ThisBuild / organization       := "com.example"
ThisBuild / version            := "1.0.0"
ThisBuild / scalaVersion       := "2.13.16"
ThisBuild / crossScalaVersions := Seq("2.13.16", "3.3.7")

@transient lazy val assertPom = taskKey[Unit]("Assert the generated BOM pom")

lazy val core = project

lazy val bom = project
  .enablePlugins(BomPlugin)
  .settings(crossVersion := CrossVersion.disabled)
  .settings(bomIncludeProjects := Seq(core))
  .settings(
    bomIncludeModules := Seq(
      "org.typelevel"    %% "cats-core" % "2.13.0",
      "com.google.guava"  % "guava"     % "33.4.0-jre"
    )
  )
  .settings(assertPom := {
    val _       = makePom.value
    val listing = bomDependenciesListing.value

    val entries = (listing \ "dependencies" \ "dependency")
      .map(node => ((node \ "groupId").text, (node \ "artifactId").text, (node \ "version").text))
      .toSet

    val expected = Set(
      ("org.typelevel", "cats-core_2.13", "2.13.0"),
      ("org.typelevel", "cats-core_3", "2.13.0"),
      ("com.google.guava", "guava", "33.4.0-jre"),
      ("com.example", "core_2.13", "1.0.0"),
      ("com.example", "core_3", "1.0.0")
    )

    assert(entries == expected, s"Unexpected BOM entries: $entries")

    val pomPath = (makePom / artifactPath).value.toString

    assert(pomPath.contains("bom-1.0.0.pom"), s"BOM pom should not be suffixed, got: $pomPath")
  })
