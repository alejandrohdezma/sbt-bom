ThisBuild / organization       := "com.example"
ThisBuild / version            := "1.0.0"
ThisBuild / scalaVersion       := "2.13.16"
ThisBuild / crossScalaVersions := Seq("2.13.16", "3.3.7")

@transient lazy val assertPom = taskKey[Unit]("Assert the generated BOM pom for the current Scala version")

lazy val core = project

lazy val legacy = project.settings(crossScalaVersions := Seq("2.13.16"))

lazy val bom = project
  .enablePlugins(BomPlugin)
  .settings(bomIncludeProjects := Seq(core, legacy))
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

    val expected = scalaBinaryVersion.value match {
      case "2.13" =>
        Set(
          ("org.typelevel", "cats-core_2.13", "2.13.0"),
          ("com.google.guava", "guava", "33.4.0-jre"),
          ("com.example", "core_2.13", "1.0.0"),
          ("com.example", "legacy_2.13", "1.0.0")
        )
      case _ =>
        Set(
          ("org.typelevel", "cats-core_3", "2.13.0"),
          ("com.google.guava", "guava", "33.4.0-jre"),
          ("com.example", "core_3", "1.0.0")
        )
    }

    assert(entries == expected, s"Unexpected BOM entries for Scala ${scalaBinaryVersion.value}: $entries")

    val pomPath = (makePom / artifactPath).value.toString

    assert(
      pomPath.contains(s"bom_${scalaBinaryVersion.value}-1.0.0.pom"),
      s"BOM pom should be suffixed with the Scala version, got: $pomPath"
    )

    if (scalaBinaryVersion.value == "3") {
      assert(
        listing.toString.contains("legacy is not available for Scala 3"),
        "Missing comment for the unavailable project"
      )
    }
  })
