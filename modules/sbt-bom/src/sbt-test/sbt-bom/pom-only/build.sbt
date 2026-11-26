ThisBuild / organization       := "com.example"
ThisBuild / version            := "1.0.0"
ThisBuild / scalaVersion       := "3.3.7"
ThisBuild / crossScalaVersions := Seq("2.13.16", "3.3.7")

@transient lazy val assertPom = taskKey[Unit]("Assert pom-only modules are imported in the generated BOM pom")

def assertDependencies(dependencies: Seq[scala.xml.Node]): Unit = {
  val entries = dependencies.map { dependency =>
    (
      (dependency \ "groupId").text,
      (dependency \ "artifactId").text,
      (dependency \ "version").text,
      (dependency \ "type").text,
      (dependency \ "scope").text
    )
  }.toSet

  val expected = Set(
    ("io.netty", "netty-bom", "4.2.21.Final", "pom", "import"),
    ("com.google.guava", "guava", "33.4.0-jre", "", "")
  )

  assert(entries == expected, s"Unexpected BOM entries: $entries")
}

lazy val perScalaVersion = project
  .enablePlugins(BomPlugin)
  .settings(
    bomIncludeModules ++= Seq(
      ("io.netty"        % "netty-bom" % "4.2.21.Final").pomOnly(),
      "com.google.guava" % "guava"     % "33.4.0-jre"
    )
  )
  .settings(assertPom := {
    val _ = makePom.value

    assertDependencies(bomDependenciesListing.value \ "dependencies" \ "dependency")
  })

lazy val allScalaVersions = project
  .enablePlugins(BomPlugin)
  .settings(crossVersion := CrossVersion.disabled)
  .settings(
    bomIncludeModules ++= Seq(
      ("io.netty"        % "netty-bom" % "4.2.21.Final").pomOnly(),
      "com.google.guava" % "guava"     % "33.4.0-jre"
    )
  )
  .settings(assertPom := {
    val _ = makePom.value

    assertDependencies(bomDependenciesListing.value \ "dependencies" \ "dependency")
  })
