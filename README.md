Generate Bill of Materials (BOM) poms from your sbt build

A [Bill of Materials (BOM)](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#bill-of-materials-bom-poms) is a special pom whose `<dependencyManagement>` section pins the versions of a set of artifacts. Consumers import the BOM and get a consistent, curated set of versions without tracking each one individually.

This plugin generates such a BOM from your build:

- Pin the published coordinates of **projects in your build** (`bomIncludeProjects`) and of **external artifacts** (`bomIncludeModules`).
- Only settings are read from the included projects — generating or publishing the BOM **never compiles them**.
- Two publishing modes: one BOM **per Scala version** (suffixed artifact) or a **single BOM** listing every Scala variant.
- Every sbt cross-version is honoured when computing artifact names: `binary`, `full`, `for3Use2_13`, `constant` and plain Java modules.
- Projects that don't cross-build for the running Scala version are skipped automatically (a comment is left in their place).
- Published for both **sbt 1.x and sbt 2.x**. A successor to Lightbend's [`sbt-bill-of-materials`](https://github.com/lightbend/sbt-bill-of-materials) (same keys), which isn't available for sbt 2.

## Installation

Add the following line to your `project/plugins.sbt` file:

```sbt
addSbtPlugin("com.alejandrohdezma" % "sbt-bom" % "0.1.1")
```

## Usage

Create a project for the BOM, enable the plugin on it and list what it should pin:

```scala
lazy val `my-library-core` = project

lazy val `my-library-cats` = project

lazy val `my-library-bom` = project
  .enablePlugins(BomPlugin)
  .settings(bomIncludeProjects := Seq(`my-library-core`, `my-library-cats`))
  .settings(bomIncludeModules += "org.typelevel" %% "cats-core" % "2.13.0")
  .settings(bomIncludeModules += "com.google.guava" % "guava" % "33.4.0-jre")
```

`my-library-bom/publish` then publishes a pom-only artifact whose `<dependencyManagement>` section pins every listed project and module. Nothing gets compiled in the process: only settings (like each project's `projectID`) are read.

---

- [How it works](#how-it-works)
  + [One BOM per Scala version (default)](#user-content-one-bom-per-scala-version-default)
  + [A single BOM for all Scala versions](#user-content-a-single-bom-for-all-scala-versions)
  + [Cross-version handling](#user-content-cross-version-handling)
- [Consuming the BOM](#consuming-the-bom)
- [Migrating from sbt-bill-of-materials](#migrating-from-sbt-bill-of-materials)

## How it works

<details><summary><b id="one-bom-per-scala-version-default">One BOM per Scala version (default)</b></summary><br/>

By default the BOM project behaves like any cross-built project: under each `++<scala-version>` pass the BOM artifact gets that Scala suffix (`my-library-bom_2.13`, `my-library-bom_3`...) and its `<dependencyManagement>` lists entries suffixed for that Scala version only.

Included projects that don't cross-build for the running Scala version are skipped, leaving an XML comment in their place:

```xml
<!-- my-library-legacy is not available for Scala 3 -->
```

So `+my-library-bom/publish` publishes one consistent BOM per Scala version, each listing exactly the artifacts that exist for it.

---

</details>

<details><summary><b id="a-single-bom-for-all-scala-versions">A single BOM for all Scala versions</b></summary><br/>

Set `crossVersion := CrossVersion.disabled` on the BOM project to publish a single unsuffixed BOM listing every Scala variant of each entry:

```scala
lazy val `my-library-bom` = project
  .enablePlugins(BomPlugin)
  .settings(crossVersion := CrossVersion.disabled)
  .settings(bomIncludeProjects := Seq(`my-library-core`))
  .settings(bomIncludeModules += "org.typelevel" %% "cats-core" % "2.13.0")
```

Each included project is expanded according to its own `crossScalaVersions`; external modules are expanded according to the BOM project's `crossScalaVersions`:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.typelevel</groupId>
      <artifactId>cats-core_2.13</artifactId>
      <version>2.13.0</version>
    </dependency>
    <dependency>
      <groupId>org.typelevel</groupId>
      <artifactId>cats-core_3</artifactId>
      <version>2.13.0</version>
    </dependency>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>my-library-core_2.13</artifactId>
      <version>1.0.0</version>
    </dependency>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>my-library-core_3</artifactId>
      <version>1.0.0</version>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Publish this mode with a plain `my-library-bom/publish` (there is a single artifact, so no `+` needed).

---

</details>

<details><summary><b id="cross-version-handling">Cross-version handling</b></summary><br/>

Artifact names are computed with sbt's own cross-version logic, so every flavor works:

| Declaration | Entry for Scala 3.3.7 |
|---|---|
| `"com.example" %% "lib" % "1.0.0"` | `lib_3` |
| `("com.example" % "lib" % "1.0.0").cross(CrossVersion.full)` | `lib_3.3.7` |
| `("com.example" %% "lib" % "1.0.0").cross(CrossVersion.for3Use2_13)` | `lib_2.13` |
| `("com.example" % "lib" % "1.0.0").cross(CrossVersion.constant("special"))` | `lib_special` |
| `"com.example" % "lib" % "1.0.0"` | `lib` |

The generated `<dependencyManagement>` section is also exposed as the `bomDependenciesListing` setting, in case you want to inspect it or post-process it before it's appended to `pomExtra`.

---

</details>

## Consuming the BOM

From Maven, import it in your `<dependencyManagement>` section:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>my-library-bom_3</artifactId>
      <version>1.0.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

sbt 2.x [supports BOMs natively](https://www.scala-sbt.org/2.x/docs/en/changes/sbt-2.0-change-summary.html): add the BOM with `.pomOnly()` and use `"*"` as the version of the dependencies it pins:

```scala
libraryDependencies += ("com.example" %% "my-library-bom" % "1.0.0").pomOnly()

libraryDependencies += "com.example" %% "my-library-core" % "*"
```

Alternatively, [sbt-dependencies](https://github.com/alejandrohdezma/sbt-dependencies) offers the same experience on both sbt 1.x and 2.x: declare the BOM with its `bom` configuration and use `*` as the version of the dependencies it pins:

```hocon
my-project = [
  "com.example::my-library-bom:1.0.0:bom"
  "com.example::my-library-core:*"
]
```

The BOM's pins are also applied to `dependencyOverrides`, so *transitive* dependencies align with the BOM too — matching Maven's `dependencyManagement` behavior. BOMs declared in its `common-settings` group are inherited by every project, and they also flow through `dependsOn`, so declaring the BOM once is enough for the whole build.

## Migrating from sbt-bill-of-materials

This plugin uses the same keys as Lightbend's [`sbt-bill-of-materials`](https://github.com/lightbend/sbt-bill-of-materials) (`bomIncludeProjects`, `bomIncludeModules`, `bomDependenciesListing`) and the same `crossVersion := CrossVersion.disabled` convention for the single-BOM mode, so migrating is a matter of swapping the `addSbtPlugin` line and the plugin name in `enablePlugins` (`BillOfMaterialsPlugin` → `BomPlugin`).

Main differences:

- Published for sbt 2.x as well as sbt 1.x.
- Projects are read through their `projectID`, so `moduleName` overrides are honoured.
- All cross-versions are supported (`full`, `for3Use2_13`, `constant`...), not just `binary` and `disabled`.

## Contributors to this project

| <a href="https://github.com/alejandrohdezma"><img alt="alejandrohdezma" src="https://avatars.githubusercontent.com/u/9027541?v=4&s=120" width="120px" /></a> |
| :--: |
| <a href="https://github.com/alejandrohdezma"><sub><b>alejandrohdezma</b></sub></a> |
