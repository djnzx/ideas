Global / onChangedBuildSource := ReloadOnSourceChanges

name := "ideas"
organization := "io.jnz"

// Releases are cut from a branch named X.Y.Z; the release workflow passes that
// through as RELEASE_VERSION. Everything else is a snapshot.
//
// `.filter(_.nonEmpty)` matters: the workflow validates non-release merges too, and
// there RELEASE_VERSION is set to the empty string rather than left unset. A plain
// getOrElse would hand back "" and the build would carry an empty version.
version := sys.env.get("RELEASE_VERSION").filter(_.nonEmpty).getOrElse("0.1.0-SNAPSHOT")

// GitHub Packages. Credentials come from the workflow's GITHUB_TOKEN.
publishTo := Some(MavenRepository("GitHub Packages", "https://maven.pkg.github.com/djnzx/ideas"))

credentials += Credentials(
  "GitHub Package Registry",
  "maven.pkg.github.com",
  sys.env.getOrElse("GITHUB_ACTOR", ""),
  sys.env.getOrElse("GITHUB_TOKEN", "")
)

scalaVersion := "2.13.18"

// only for Java code in the repo, must match scala release
javacOptions := Seq("-source", "17", "-target", "17")

// Full list of flags: https://docs.scala-lang.org/overviews/compiler-options/index.html
scalacOptions ++= Seq(
  "-release:17",                           // target JVM 17 bytecode + JDK 17 API
  "-encoding",                             // source file encoding, value on next line
  "UTF-8",
  "-feature",                              // warn where an explicit import is needed
  "-deprecation",                          // warn on deprecated API usage
  "-unchecked",                            // warn on type tests erasure cannot check
  "-language:postfixOps",                  // allow `xs tail` without an import
  "-language:higherKinds",                 // allow F[_] type parameters
  "-language:existentials",                // allow T forSome { ... } types
  "-Wconf:cat=other-match-analysis:error", // non-exhaustive match is an error
  "-Wunused",                              // warn on unused imports, locals, params
  //  "-Xfatal-warnings", // promote every warning to an error
  "-Ymacro-annotations",                   // enable macro annotation expansion
  "-Ywarn-numeric-widen",                  // warn on implicit Int -> Long widening
  "-Ywarn-value-discard",                  // warn when a non-Unit value is dropped
  "-Ywarn-dead-code"                       // warn on unreachable code
)

libraryDependencies ++= Seq(
  /** some useful plugin things */
  compilerPlugin(("org.typelevel" %% "kind-projector" % "0.13.4").cross(CrossVersion.full)),
  compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  /** testing */
  "org.scalatest"     %% "scalatest"       % "3.2.20"   % Test,
  "org.scalacheck"    %% "scalacheck"      % "1.20.0"   % Test,
  "org.scalatestplus" %% "scalacheck-1-19" % "3.2.20.0" % Test,
  /** colored & informative output */
  "com.lihaoyi"       %% "pprint"          % "0.9.6"
)

lazy val precommit = taskKey[Unit]("Run before committing: formats everything, then runs all tests.")

// Sequential, so formatting lands before anything compiles.
// Uncached because sbt 2 hashes task results and testFull's TestResult has no HashWriter --
// without it the build will not even load.
precommit := Def.uncached(
  Def
    .sequential(
      scalafmtAll,
      Compile / scalafmtSbt,
      Test / testFull
    )
    .value
)

lazy val validate = taskKey[Unit]("For CI: fails on unformatted code, then runs all tests. Never rewrites a file.")

// Sequential, cheapest check first: no point compiling and running the suite for a
// tree that will be rejected over whitespace. Uncached also.
validate := Def.uncached(
  Def
    .sequential(
      scalafmtCheckAll,
      Compile / scalafmtSbtCheck,
      Test / testFull
    )
    .value
)

lazy val release = taskKey[Unit]("For CI: publishes to GitHub Packages. Refuses to publish a snapshot.")

// Guards publishing rather than `version`, so local compile/test still work on the
// snapshot default. Uncached for the same reason as the tasks above.
release := Def.uncached(
  Def
    .sequential(
      Def.task {
        if (isSnapshot.value)
          sys.error(s"refusing to publish ${version.value} as a release -- RELEASE_VERSION is not set")
      },
      publish
    )
    .value
)
