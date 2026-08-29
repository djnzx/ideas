Global / onChangedBuildSource := ReloadOnSourceChanges

name := "ideas"
organization := "io.jnz"

// Releases are cut from a branch named X.Y.Z; the release workflow passes that
// through as RELEASE_VERSION. Everything else is a snapshot.
ThisBuild / version :=
  sys.env
    .get("RELEASE_VERSION")
    .filter(_.nonEmpty)
    .getOrElse(sbtdynver.DynVer.version(new java.util.Date))

ThisBuild / versionScheme := Some("early-semver")

enablePlugins(BuildInfoPlugin)

buildInfoPackage := "io.jnz.example.build"

// `version` is the resolved one -- RELEASE_VERSION when the workflow set it, otherwise dynver's
// git-derived string. gitCommit is not redundant with it: a release build's version is a plain
// "1.2.3" with the sha stripped out, and that is exactly the jar you most want to trace back.
buildInfoKeys := Seq[BuildInfoKey](
  name,
  organization,
  version,
  scalaVersion,
  sbtVersion,
  // BuildInfoKey.action re-runs on every compile, unlike a plain key which is read once at load.
  // That matters here: commit and dirtiness change under sbt without a reload.
  BuildInfoKey.action("gitCommit")(git("rev-parse", "HEAD").getOrElse("unknown")),
  BuildInfoKey.action("gitDirty")(git("status", "--porcelain").exists(_.nonEmpty))
)

buildInfoOptions ++= Seq(
  BuildInfoOption.BuildTime, // adds builtAtMillis: Long and builtAtString: String (builder's local zone)
  BuildInfoOption.ToMap,     // adds toMap: Map[String, Any]
  BuildInfoOption.ToJson     // adds toJson: String, hand-rolled -- pulls in no json library
)

/** Shells out to git, returning None if git is missing, this is not a repo, or the call fails.
  * Every caller has a fallback, so a source tarball with no .git still builds.
  */
def git(args: String*): Option[String] =
  scala.util.Try(scala.sys.process.Process("git" +: args).!!.trim).toOption

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
        // Guard on the env var, not isSnapshot. Since `version` now falls back to a
        // git-derived one, a clean checkout sitting exactly on a tag produces a
        // NON-snapshot version (v9.9.9 -> 9.9.9), which an isSnapshot check would wave
        // straight through to publish. Only an explicit RELEASE_VERSION should publish.
        if (!sys.env.get("RELEASE_VERSION").exists(_.nonEmpty))
          sys.error(s"refusing to publish ${version.value} as a release -- RELEASE_VERSION is not set")
      },
      publish
    )
    .value
)
