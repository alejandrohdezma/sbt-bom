ThisBuild / scalaVersion                  := _root_.scalafix.sbt.BuildInfo.scala212
ThisBuild / crossScalaVersions            := Seq(scalaVersion.value, "3.8.4")
ThisBuild / organization                  := "com.alejandrohdezma"
ThisBuild / pluginCrossBuild / sbtVersion := scalaVersion.value.on(2)("1.12.15").getOrElse("2.0.0")
ThisBuild / versionPolicyIntention        := Compatibility.BinaryAndSourceCompatible

addCommandAlias("ci-test", "fix --check; mdoc; +versionPolicyCheck; +test; +publishLocal; +scripted")
addCommandAlias("ci-docs", "github; mdoc; headerCreateAll")
addCommandAlias("ci-publish", "versionCheck; github; ci-release")

lazy val documentation = project
  .enablePlugins(MdocPlugin)

lazy val `sbt-bom` = module
  .enablePlugins(SbtPlugin)
  .settings(scriptedLaunchOpts += s"-Dplugin.version=${version.value}")
  .settings(scriptedBufferLog := true)
  .settings(scriptedBatchExecution := true)
  .settings(scriptedParallelInstances := 5)
