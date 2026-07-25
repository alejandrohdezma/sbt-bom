ThisBuild / organization       := "com.example"
ThisBuild / version            := "1.0.0"
ThisBuild / scalaVersion       := "3.3.7"
ThisBuild / crossScalaVersions := Seq("3.3.7")

@transient lazy val assertPom = taskKey[Unit]("Assert the generated BOM pom")

lazy val bom = project
  .enablePlugins(BomPlugin)
  .settings(
    bomIncludeModules := Seq(
      "com.example"  %% "binary-lib"   % "1.0.0",
      ("com.example"  % "full-lib"     % "1.0.0").cross(CrossVersion.full),
      ("com.example" %% "compat-lib"   % "1.0.0").cross(CrossVersion.for3Use2_13),
      ("com.example"  % "constant-lib" % "1.0.0").cross(CrossVersion.constant("special")),
      "com.example"   % "java-lib"     % "1.0.0"
    )
  )
  .settings(assertPom := {
    val _       = makePom.value
    val listing = bomDependenciesListing.value

    val entries = (listing \ "dependencies" \ "dependency").map(node => (node \ "artifactId").text).toSet

    val expected = Set("binary-lib_3", "full-lib_3.3.7", "compat-lib_2.13", "constant-lib_special", "java-lib")

    assert(entries == expected, s"Unexpected BOM artifact ids: $entries")
  })
