ThisBuild / organization       := "com.example"
ThisBuild / version            := "1.0.0"
ThisBuild / scalaVersion       := "3.3.7"
ThisBuild / crossScalaVersions := Seq("2.13.16", "3.3.7")

@transient lazy val assertPom = taskKey[Unit]("Assert dependencyOverrides choose the version of conflicting entries")

lazy val bom = project
  .enablePlugins(BomPlugin)
  .settings(crossVersion := CrossVersion.disabled)
  .settings(
    bomIncludeModules := Seq(
      "com.google.protobuf" % "protobuf-java" % "4.36.1",
      "com.google.protobuf" % "protobuf-java" % "4.33.6",
      "com.example"        %% "cross-lib"     % "1.0.0",
      "com.example"         % "java-lib"      % "1.0.0"
    ),
    dependencyOverrides ++= Seq(
      "com.google.protobuf" % "protobuf-java" % "4.36.1",
      "com.example"        %% "cross-lib"     % "2.0.0"
    )
  )
  .settings(assertPom := {
    val _       = makePom.value
    val listing = bomDependenciesListing.value

    val entries = (listing \ "dependencies" \ "dependency")
      .map(dependency => (dependency \ "artifactId").text -> (dependency \ "version").text)
      .toList

    val expected = List(
      "protobuf-java"  -> "4.36.1",
      "cross-lib_2.13" -> "2.0.0",
      "cross-lib_3"    -> "2.0.0",
      "java-lib"       -> "1.0.0"
    )

    assert(entries == expected, s"Unexpected BOM entries: $entries")
  })
