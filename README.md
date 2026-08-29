# ideas

A small Scala 2.13 library about one question: **what should the signature of `a + b` be?**

JVM gives you `(Int, Int) => Int`, and it lies: `Int.MaxValue + 1` is `-2147483648`.
`io.jnz.example.Adder` collects eight honest answers instead. The arithmetic is identical
in every one of them — what changes is the return type, because that is where the overflow
has to surface. All eight rest on a single branch-free predicate, `overflows`.

```scala
import io.jnz.example.Adder._

add(Int.MaxValue, 1)           // Int                -2147483648, wraps silently
addSaturating(Int.MaxValue, 1) // Int                2147483647, clamps to the boundary
addExact(Int.MaxValue, 1)      // Int                throws ArithmeticException
addOption(Int.MaxValue, 1)     // Option[Int]        None, no detail
addEither(Int.MaxValue, 1)     // Either[Long, Int]  Left(2147483648), exact sum kept
addWidened(Int.MaxValue, 1)    // Long               2147483648, cannot overflow
addWithCarry(Int.MaxValue, 1)  // (Int, Boolean)     (-2147483648, true)
```

# Reading this in browser

```shell
python3 tools/render-readme.py 
open target/readme.html
```

## What is published

|              |                                                                           |
|--------------|---------------------------------------------------------------------------|
| Group ID     | `io.jnz`                                                                  |
| Artifact ID  | `ideas_2.13` — sbt's `%%` appends the `_2.13`                             |
| Versions     | `0.1.2`, `0.1.3`, `...` — see [tags](https://github.com/djnzx/ideas/tags) |
| Packaging    | `jar`                                                                     |
| Registry URL | [GitHub Packages](https://github.com/djnzx/ideas/packages)                |
| Scala        | `2.13.18`                                                                 |

Everything below is about getting that dependency to resolve.


# Usage

This project publishes to **GitHub Packages**, not Maven Central. That single fact
drives everything below: GitHub's Maven registry requires an access token for
*every* request, including reads of a public package. There is no anonymous access
and no way to turn it on.

Everything in this document was executed against the live registry, not recalled
from docs.

## The error you get without a token

This is the failure everyone hits first. Nothing about it says "you need a token":

```
[error] (update) sbt.librarymanagement.ResolveException: Error downloading io.jnz:ideas_2.13:0.1.1
[error]   Not found
[error]   not found: /Users/you/.ivy2/local/io.jnz/ideas_2.13/0.1.1/ivys/ivy.xml
[error]   not found: https://repo1.maven.org/maven2/io/jnz/ideas_2.13/0.1.1/ideas_2.13-0.1.1.pom
[error]   unauthorized: https://maven.pkg.github.com/djnzx/ideas/io/jnz/ideas_2.13/0.1.1/ideas_2.13-0.1.1.pom
```

The line that matters is the last one: **`unauthorized:`**, not `not found:`. 
The repository resolved fine; the credentials were missing or empty.

You can confirm the same thing without a build tool at all:

```console
$ curl -s -o /dev/null -w '%{http_code}\n' \
    https://maven.pkg.github.com/djnzx/ideas/io/jnz/ideas_2.13/0.1.1/ideas_2.13-0.1.1.pom
401
```

## Creating a token

### Which kind of token you need

| You want to                             | Token needs                                             |
|-----------------------------------------|---------------------------------------------------------|
| Depend on the artifact (read)           | `read:packages`                                         |
| Publish from your own machine           | `write:packages` (implies read)                         |
| Delete a published version              | `delete:packages`                                       |
| Publish from this repo's GitHub Actions | **nothing** — see [Releasing](#releasing-a-new-version) |

Do not reach for `repo` or `admin:*`. Reading a package needs `read:packages` and
nothing else. (For a package in a **private** repo you additionally need `repo`,
so the token can see the repo the package is attached to. `djnzx/ideas` is public,
so that does not apply here.)

### Option A — reuse the token the `gh` CLI already has (fastest)

If you have used `gh` on this machine, you already have a working token:

```console
$ gh auth token
github_pat_11ABCDEFG0abcdefghijklmn_...
```

That token authenticates against the Maven registry as-is — verified against this
package. If `gh auth token` prints nothing, log in first:

```console
$ gh auth login
```

If the token turns out to lack package access, how you widen it depends on which
kind `gh` gave you. For an OAuth token (`gho_`/`ghp_`), add the scope in place:

```console
$ gh auth refresh -h github.com -s read:packages
```

For a fine-grained token (`github_pat_`), `gh auth refresh` does not apply — edit
the token's permissions at
https://github.com/settings/personal-access-tokens, or create one as in Option C.

This is the least ceremony, but the lifetime is managed by `gh` and the scopes are
whatever you granted the CLI — which makes it a poor fit for anything shared. For
a build server, create a dedicated token instead (Option B).

### Option B — a classic personal access token (the canonical route)

Classic PATs are the best-supported credential for GitHub Packages. Every registry
feature works with them.

1. Go to **https://github.com/settings/tokens** — or navigate:
   avatar (top right) → **Settings** → **Developer settings** (bottom of the left
   sidebar) → **Personal access tokens** → **Tokens (classic)**.
2. Click **Generate new token** → **Generate new token (classic)**.
3. Fill in the form:
   - **Note**: something you will recognise in six months, e.g.
     `maven-read-ideas` or `laptop / github packages read`.
   - **Expiration**: 90 days is a sane default. Choosing *No expiration* means
     you are creating a credential you will never rotate — avoid it for anything
     that touches a shared machine.
   - **Scopes**: tick **`read:packages`** only. It sits under the top-level
     `write:packages` group — expand it and tick the *child*, not the parent,
     unless you also intend to publish.
4. Click **Generate token** at the bottom.
5. **Copy the token now.** It is displayed exactly once. It begins with `ghp_`.
   If you lose it, come back and regenerate — there is no way to read it again.

Store it somewhere real — see [Storing the token safely](#storing-the-token-safely) for
the per-OS commands. Do not paste it into `build.sbt`, `pom.xml`, or anything git tracks.

### Option C — a fine-grained personal access token

Fine-grained tokens are scoped to specific repositories and expire by policy.
They work for reading this package — verified — and are the better choice if you
want a credential that can only ever see `djnzx/ideas`.

1. **https://github.com/settings/personal-access-tokens/new**
   (Settings → Developer settings → Personal access tokens → Fine-grained tokens
   → **Generate new token**).
2. **Token name**, **Expiration**, and **Resource owner**. For a package owned by
   the user `djnzx`, the resource owner is what determines whether the token can
   see it — an org-owned token cannot read a user-owned package.
3. **Repository access**: *Only select repositories* → `djnzx/ideas`.
4. **Permissions** → *Repository permissions* → **Packages: Read-only**
   (or *Read and write* to publish).
5. **Generate token**, then copy it. It begins with `github_pat_`.

Caveat worth knowing before you commit to this route: fine-grained tokens have
historically lagged classic PATs on package registry support, particularly for
organisation-owned packages and for delete operations. If something behaves
strangely, retry with a classic PAT before debugging further.

### What the username is for

Basic auth over this registry wants a username and a password. **Only the password
(the token) is actually checked** — a request with the username `anything`, or with
an empty username, succeeds as long as the token is valid.

Your build tool still requires *some* string there. Use your GitHub login
(`djnzx`) so the config reads sensibly to the next person. Never put the token in
the username field.

## Wiring the token into your build

### sbt (this project's own convention)

Two resolvers-and-credentials shapes work. Both are verified against a cold cache.

#### Environment variables

Matches how this repo's own `build.sbt` is written, and how CI passes credentials:

```scala
resolvers += MavenRepository("GitHub Packages", "https://maven.pkg.github.com/djnzx/ideas")

credentials += Credentials(
  "GitHub Package Registry",         // realm - must match exactly
  "maven.pkg.github.com",            // host
  sys.env.getOrElse("GITHUB_ACTOR", ""),
  sys.env.getOrElse("GITHUB_TOKEN", "")
)

libraryDependencies += "io.jnz" %% "ideas" % "0.1.1"
```

```console
$ export GITHUB_ACTOR=your-github-login
$ export GITHUB_TOKEN=ghp_xxxxxxxxxxxx
$ sbt run
```

The realm string `GitHub Package Registry` is not arbitrary — it is what the
server sends back in its challenge (`www-authenticate: Basic realm="GitHub Package
Registry"`). Get it wrong and sbt will hold the credentials back and you will see
`unauthorized:` as though you had configured nothing.

> **sbt 2 gotcha.** sbt 2 runs a background server that snapshots the environment
> when it starts; later `sbt` invocations are thin clients reusing it. Exporting
> `GITHUB_TOKEN` *after* a server is already running has no effect. Run
> `sbt shutdown` after changing the variables. This is the same trap the release
> workflow comments about.

#### A credentials file (better for a workstation)

Keeps the token out of your shell history and out of every process's environment. Better
still, have sbt read it from the OS secret store — see
[Storing the token safely](#storing-the-token-safely).

```console
$ cat > ~/.sbt/gh-credentials <<'EOF'
realm=GitHub Package Registry
host=maven.pkg.github.com
user=your-github-login
password=ghp_xxxxxxxxxxxx
EOF
$ chmod 600 ~/.sbt/gh-credentials
```

```scala
credentials += Credentials(Path.userHome / ".sbt" / "gh-credentials")
```

To make it apply to *every* sbt project on the machine without touching any
`build.sbt`, put that line in `~/.sbt/1.0/global.sbt` (sbt 1) or
`~/.sbt/2/global.sbt` (sbt 2 — note `2`, not `2.0`) instead. You still need the `resolvers +=` line
per project — a resolver is a build fact, credentials are a machine fact.

### Maven

`~/.m2/settings.xml` — the `<id>` is the join between the two files and must match
on both sides:

```xml
<settings>
  <servers>
    <server>
      <id>github-djnzx-ideas</id>
      <username>your-github-login</username>
      <password>ghp_xxxxxxxxxxxx</password>
    </server>
  </servers>
</settings>
```

`pom.xml`:

```xml
<repositories>
  <repository>
    <id>github-djnzx-ideas</id>
    <url>https://maven.pkg.github.com/djnzx/ideas</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>io.jnz</groupId>
    <artifactId>ideas_2.13</artifactId>
    <version>0.1.1</version>
  </dependency>
</dependencies>
```

Note the explicit `_2.13` — Maven has no `%%` operator, so you spell the Scala
suffix yourself.

That is the credentials half. If you are calling this library from **Java**, the
[Java with Maven](#java-with-maven) chapter covers the rest: the static forwarders
that make `Adder` callable as plain static methods, unwrapping the Scala types at
the boundary, and what the Scala runtime costs you on the classpath.

### Gradle

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/djnzx/ideas")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("io.jnz:ideas_2.13:0.1.1")
}
```

### scala-cli

```scala
//> using repository "https://maven.pkg.github.com/djnzx/ideas"
//> using dep "io.jnz::ideas:0.1.1"
```

scala-cli reads credentials from coursier, so either set them once —

```console
$ export COURSIER_REPOSITORIES="ivy2Local|central|https://maven.pkg.github.com/djnzx/ideas"
$ export COURSIER_CREDENTIALS="maven.pkg.github.com your-login:ghp_xxxxxxxxxxxx"
```

— or write `~/.config/coursier/credentials.properties`:

```properties
gh.host=maven.pkg.github.com
gh.username=your-github-login
gh.password=ghp_xxxxxxxxxxxx
gh.auto=true
```

### CI that is not GitHub Actions

Store the PAT as a secret in your CI provider and export it as `GITHUB_TOKEN` (or
whatever your build reads). The token belongs to a *person*, so prefer a dedicated
machine account over your own login for anything a team shares — otherwise the
build breaks the day you rotate your credentials or leave.

### CI that *is* GitHub Actions

No PAT. Every workflow run gets an automatic `secrets.GITHUB_TOKEN`; grant it the
package scope in the job and pass it through:

```yaml
permissions:
  packages: read

steps:
  - env:
      GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}   # GITHUB_ACTOR is set for you
    run: sbt -batch compile
```

Reading a package from a *different* repository than the one running the workflow
is the exception: the automatic token is scoped to its own repo, so a cross-repo
read needs a PAT in a secret, or the package's *Manage Actions access* settings
adjusted to admit the consuming repo.

## Verifying it works

Smallest possible check — no build tool, no project:

```console
$ curl -s -o /dev/null -w '%{http_code}\n' -u "your-login:$GITHUB_TOKEN" \
    https://maven.pkg.github.com/djnzx/ideas/io/jnz/ideas_2.13/0.1.1/ideas_2.13-0.1.1.pom
302
```

`302` is success — the registry redirects to a signed S3 URL for the actual bytes.
`401` means the token is missing, empty, malformed, or lacks package read.

List what versions exist:

```console
$ curl -sL -u "your-login:$GITHUB_TOKEN" \
    https://maven.pkg.github.com/djnzx/ideas/io/jnz/ideas_2.13/maven-metadata.xml
```

Then the real thing. A four-line project that resolves and runs the published code:

```scala
// build.sbt
scalaVersion := "2.13.18"
resolvers    += MavenRepository("GitHub Packages", "https://maven.pkg.github.com/djnzx/ideas")
credentials  += Credentials("GitHub Package Registry", "maven.pkg.github.com",
                            sys.env("GITHUB_ACTOR"), sys.env("GITHUB_TOKEN"))
libraryDependencies += "io.jnz" %% "ideas" % "0.1.1"
```

```scala
// src/main/scala/Main.scala
import io.jnz.example.Adder

object Main extends App {
  println(Adder.addEither(Int.MaxValue, 1))     // Left(2147483648)
  println(Adder.addSaturating(Int.MaxValue, 1)) // 2147483647
}
```

```console
$ sbt run
[info] running (fork) Main
Left(2147483648)
2147483647
[success]
```

Those two output lines are the actual observed output, and they are the point of
the library: `addEither` keeps the exact sum as a `Long` in the `Left`, while
`addSaturating` clamps.

## Troubleshooting

**`unauthorized: https://maven.pkg.github.com/...`**
The token never reached the request. In order of likelihood: the env var is empty
in the sbt server's environment (`sbt shutdown`, re-export, retry); the realm
string does not match `GitHub Package Registry` exactly; the credentials file has
a typo in `host=`; the token has expired.

**`not found:` for the GitHub URL, but Maven Central was also tried**
Then it is a coordinate problem, not an auth problem. Check the Scala suffix —
`ideas` alone does not exist, only `ideas_2.13`. With sbt use `%%` and write
`"io.jnz" %% "ideas"`; with Maven/Gradle spell out `ideas_2.13`.

**Worked yesterday, 401 today**
Token expiry. Classic PATs email you seven days out; fine-grained ones expire by
org policy. Regenerate and update wherever you stored it.

**Changed the token but nothing changed**
Two caches to consider. sbt 2's server holds the old environment — `sbt shutdown`.
And coursier caches successful downloads, so a fixed token shows no visible
difference until something needs a fresh fetch. To force a real network round trip:

```console
$ rm -rf ~/Library/Caches/Coursier/v1/https/maven.pkg.github.com   # macOS
$ rm -rf ~/.cache/coursier/v1/https/maven.pkg.github.com           # Linux
```

**`gh api /users/djnzx/packages?package_type=maven` returns 403**
The REST packages API needs `read:packages` explicitly; the Maven registry is more
permissive than the API. A 403 here does not mean your token cannot resolve the
dependency — test with the `curl` above instead.

**The `maven-metadata.xml` groupId looks wrong**
It does: GitHub reports `<groupId>io.jnz.ideas_2</groupId>` with
`<artifactId>13</artifactId>`, having split the coordinate on the last dot of
`ideas_2.13`. It is cosmetic. Pinned versions resolve correctly, and
`latest.integration` was verified to resolve too, because coursier reads the
`<versions>` list rather than trusting those two fields.

## Java with Maven

`ideas` is a Scala library, but it is perfectly usable from plain Java. Everything
in this chapter was compiled and run from a Java 17 Maven project against the
published `0.1.1` jar.

Two things make it easier than you might expect, and one thing makes it harder.

### `Adder` is callable as ordinary static methods

`Adder` is a Scala `object`. That normally means a class named `Adder$` with a
`MODULE$` singleton field, which is miserable to call from Java. But because this
`object` has no companion class, scalac also emits **static forwarders** on a plain
`io.jnz.example.Adder`:

```console
$ javap -cp ideas_2.13-0.1.1.jar io.jnz.example.Adder
public final class io.jnz.example.Adder {
  public static <A> A addFold(int, int, scala.Function2<java.lang.Object, java.lang.Object, A>, scala.Function1<java.lang.Object, A>);
  public static scala.Tuple2<java.lang.Object, java.lang.Object> addWithCarry(int, int);
  public static long addWidened(int, int);
  public static int addExact(int, int) throws java.lang.ArithmeticException;
  public static scala.util.Either<java.lang.Object, java.lang.Object> addEither(int, int);
  public static scala.Option<java.lang.Object> addOption(int, int);
  public static int addSaturating(int, int);
  public static int add(int, int);
  public static boolean overflows(int, int);
}
```

So Java calls it the obvious way:

```java
import io.jnz.example.Adder;

int sum = Adder.add(2, 3);
```

### A complete working `pom.xml`

Credentials go in `~/.m2/settings.xml` exactly as in
[Wiring the token into your build](#maven) — the `<id>` must match on both sides:

```xml
<settings>
  <servers>
    <server>
      <id>github-djnzx-ideas</id>
      <username>your-github-login</username>
      <password>ghp_xxxxxxxxxxxx</password>
    </server>
  </servers>
</settings>
```

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>jdemo</artifactId>
  <version>1.0-SNAPSHOT</version>

  <properties>
    <maven.compiler.release>17</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <repositories>
    <repository>
      <id>github-djnzx-ideas</id>
      <url>https://maven.pkg.github.com/djnzx/ideas</url>
    </repository>
  </repositories>

  <dependencies>
    <dependency>
      <groupId>io.jnz</groupId>
      <artifactId>ideas_2.13</artifactId>
      <version>0.1.1</version>
    </dependency>
  </dependencies>
</project>
```

Remember the `_2.13` on the artifact ID. Maven has no `%%` operator, so plain
`ideas` resolves to nothing.

## Releasing a new version

For maintainers. The release is driven entirely by the *branch name*; there is no
manual publish step and **no PAT anywhere in the process** — GitHub Actions' own
`GITHUB_TOKEN` has `packages: write` granted in the workflow.

1. Branch off `master` with a name that is exactly `X.Y.Z` — `0.1.2`, not
   `v0.1.2` and not `release/0.1.2`. The name *is* the version.
2. Push it. `non-master-ci` checks **before anything else** that tag `vX.Y.Z` does
   not already exist, and fails the build if it does. A version you cannot publish
   is caught here, on the first push, before a PR exists — and before any JDK or
   test run is spent on it. Then it runs `sbt validate`.
3. Open a PR into `master` and merge it.
4. On merge, `.github/workflows/master.yml` runs `sbt validate` (scalafmt check +
   full test suite) on the merge commit — **every** merge, whatever the branch was
   called.
5. It then checks the branch name against `^[0-9]+\.[0-9]+\.[0-9]+$`. Any other name
   and it stops there, validated but unreleased. A matching name re-checks the tag,
   publishes with `RELEASE_VERSION` set, tags the merge commit `vX.Y.Z`, and deletes
   the release branch.

The tag is checked twice on purpose. Step 2 gives you the answer early, when fixing
it costs a branch rename; step 5 is the backstop for the window in between, where
someone else could have tagged `vX.Y.Z` after your branch went green.

### Which workflow does what

Two workflows, split by branch:

| File                               | Workflow        | Fires on                           | Does                                                        |
|------------------------------------|-----------------|------------------------------------|-------------------------------------------------------------|
| `.github/workflows/non-master.yml` | `non-master-ci` | push to any branch except `master` | refuses a version branch whose tag is taken, then validates |
| `.github/workflows/master.yml`     | `master-ci`     | anything landing on `master`       | validates, and releases when the branch was `X.Y.Z`         |

`non-master-ci` is one job of five steps, and the tag check is step 2 — before the
JDK and sbt installs, so a doomed branch never costs a build. Its checkout uses
`fetch-depth: 0` deliberately: the default shallow clone carries only the tags
pointing at the tip commit, so an older `vX.Y.Z` would read as absent and the check
would wave a colliding branch through.

To turn that red check into a blocked merge button, mark the `validate` job a
required status check on the `master` branch protection rule.

`master-ci` covers three cases, and validates in all of them:

| What happened                         | Event          | Result                                   |
|---------------------------------------|----------------|------------------------------------------|
| Merged PR from a branch named `X.Y.Z` | `pull_request` | validate + publish + tag + delete branch |
| Merged PR from any other branch       | `pull_request` | validate only                            |
| Direct push to `master`               | `push`         | validate only                            |

The branch-name check is anchored, so a near miss fails safe — `v0.1.3`, `0.1`,
`0.1.3.4`, `0.1.3-rc1` and `release/0.1.3` all validate and publish nothing, rather
than publishing under a version you did not mean.

### Why `master-ci` listens to two events

`pull_request` carries the source branch as `head.ref`, which is what the version is
read from. `push` carries no branch information at all, so it can only ever mean
"validate". Both are needed because a direct push fires only the second.

Deriving the branch from the commit instead — and dropping to a single `push`
trigger — does not work here: this repository squash-merges, so a merge commit has
one parent and a PR-title subject, indistinguishable from a direct push. Both
after-the-fact lookups were tried against real history and both failed on a genuine
merge commit, because master was rewritten and GitHub still associates the PR with
the original sha. A heuristic that silently skips a release is not worth the saved
minutes.

**The cost:** a merge fires *both* events, so it builds twice. Once branch protection
makes direct pushes impossible, delete the `push` trigger from `master.yml` and the
duplicate goes with it — every path into `master` stays covered, because the only
one that needed `push` can no longer happen.
