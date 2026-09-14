ThisBuild / organization       := "com.example"
ThisBuild / version            := "1.0.0"
ThisBuild / scalaVersion       := "3.3.7"
ThisBuild / crossScalaVersions := Seq("2.13.16", "3.3.7")

@transient lazy val assertPom = taskKey[Unit]("Assert the generated BOM pom carries the modules' exclusions")

lazy val bom = project
  .enablePlugins(BomPlugin)
  .settings(crossVersion := CrossVersion.disabled)
  .settings(
    bomIncludeModules := Seq(
      ("org.tribuo"       % "tribuo-onnx"      % "4.3.2").exclude("com.google.protobuf", "protobuf-java"),
      ("com.example"     %% "cross-lib"        % "1.0.0")
        .excludeAll(ExclusionRule("com.example", "other").withCrossVersion(CrossVersion.binary)),
      ("com.example"      % "intransitive-lib" % "1.0.0").intransitive(),
      ("com.example"      % "wildcard-lib"     % "1.0.0").excludeAll(ExclusionRule("com.example")),
      "com.google.guava"  % "guava"            % "33.4.0-jre"
    )
  )
  .settings(assertPom := {
    val _       = makePom.value
    val listing = bomDependenciesListing.value

    val entries = (listing \ "dependencies" \ "dependency").map { dependency =>
      val exclusions = (dependency \ "exclusions" \ "exclusion")
        .map(exclusion => ((exclusion \ "groupId").text, (exclusion \ "artifactId").text))
        .toSet

      (dependency \ "artifactId").text -> exclusions
    }.toMap

    val expected = Map(
      "tribuo-onnx"      -> Set(("com.google.protobuf", "protobuf-java")),
      "cross-lib_2.13"   -> Set(("com.example", "other_2.13")),
      "cross-lib_3"      -> Set(("com.example", "other_3")),
      "intransitive-lib" -> Set(("*", "*")),
      "wildcard-lib"     -> Set(("com.example", "*")),
      "guava"            -> Set.empty[(String, String)]
    )

    assert(entries == expected, s"Unexpected BOM exclusions: $entries")

    assert(!listing.toString.contains("<exclusions/>"), s"Empty exclusions block in the listing: $listing")
  })
