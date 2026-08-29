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

The first three share one signature and still disagree, which is half the point: a type
alone does not tell you what happens at the boundary. The eighth, `addFold`, takes the
question back to the caller — every variant above is that fold under a different pair of
continuations.

The test suite is the interesting half. It uses ScalaCheck to show that saturating and
checked addition are **not associative** — so neither forms a lawful `Monoid` — while
wrapping addition is, which is why wrapping is the instance cats actually provides for
`Int`.

|             |                                                                                    |
|-------------|------------------------------------------------------------------------------------|
| Coordinates | `"io.jnz" %% "ideas" % version` [version](https://github.com/djnzx/ideas/releases) |
| Registry    | GitHub Packages — **a token is required**, see [Usage](#usage)                     |
| Scala       | `2.13.18`                                                                          |
| License     | none declared                                                                      |

Everything below is about getting that dependency to resolve.

# Reading this with Claude

`publish README.md as an artifact`

# Usage

This project publishes to **GitHub Packages**, not Maven Central. That single fact
drives everything below: GitHub's Maven registry requires an access token for
*every* request, including reads of a public package. There is no anonymous access
and no way to turn it on.

Everything in this document was executed against the live registry, not recalled
from docs.

## What is published

|             |                                                          |
|-------------|----------------------------------------------------------|
| Registry    | `https://maven.pkg.github.com/djnzx/ideas`               |
| Group ID    | `io.jnz`                                                 |
| Artifact ID | `ideas_2.13` (the `_2.13` suffix is added by sbt's `%%`) |
| Versions    | `0.1.0`, `0.1.1`                                         |
| Scala       | 2.13.18                                                  |
| Packaging   | `jar`                                                    |

Runtime dependencies that come with it: `scala-library` 2.13.18 and
`com.lihaoyi::pprint` 0.9.6. The scalatest/scalacheck dependencies in the POM are
`test`-scoped and will not land on your compile classpath.

The published code is `io.jnz.example.Adder` — eight ways to add two `Int`s,
differing only in how they report overflow (`add`, `addSaturating`, `addOption`,
`addEither`, `addExact`, `addWidened`, `addWithCarry`, `addFold`) plus the
`overflows` predicate they are all built on.

## The error you get without a token

This is the failure everyone hits first. Nothing about it says "you need a token":

```
[error] (update) sbt.librarymanagement.ResolveException: Error downloading io.jnz:ideas_2.13:0.1.1
[error]   Not found
[error]   not found: /Users/you/.ivy2/local/io.jnz/ideas_2.13/0.1.1/ivys/ivy.xml
[error]   not found: https://repo1.maven.org/maven2/io/jnz/ideas_2.13/0.1.1/ideas_2.13-0.1.1.pom
[error]   unauthorized: https://maven.pkg.github.com/djnzx/ideas/io/jnz/ideas_2.13/0.1.1/ideas_2.13-0.1.1.pom
```

The line that matters is the last one: **`unauthorized:`**, not `not found:`. The
repository resolved fine; the credentials were missing or empty.

You can confirm the same thing without a build tool at all:

```console
$ curl -s -o /dev/null -w '%{http_code}\n' \
    https://maven.pkg.github.com/djnzx/ideas/io/jnz/ideas_2.13/0.1.1/ideas_2.13-0.1.1.pom
401
```

## Creating a token

### Which kind of token you need

| You want to | Token needs |
|---|---|
| Depend on the artifact (read) | `read:packages` |
| Publish from your own machine | `write:packages` (implies read) |
| Delete a published version | `delete:packages` |
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

No `MODULE$`, no `Adder$`. (`Adder$.MODULE$.add(2, 3)` also works if you ever need
it — but you don't.)

### JDK requirement: 0.1.1 is Java 8, the next release will be Java 17

The published `0.1.1` jar is **major version 52 — Java 8 bytecode**:

```console
$ javap -v -cp ideas_2.13-0.1.1.jar io.jnz.example.Adder | grep major
  major version: 52
```

So `0.1.1` runs on any JDK from 8 upward, and you may set
`maven.compiler.release` to whatever your own project targets.

That was not intentional. `build.sbt` used to carry

```scala
javacOptions := Seq("-source", "17", "-target", "17")
```

which is inert here: `javacOptions` governs only *Java* sources, and this project has
none. Every class comes from scalac, which targets Java 8 by default in Scala 2.13, so
the "17" never reached the compiler.

**This is now fixed on `master`**, where the setting is the scalac knob instead:

```scala
scalacOptions ++= Seq(
  "-release:17", // target JVM 17 bytecode + JDK 17 API
  ...
)
```

Verified: class files built from `master` are major version 61. So the **next** release
will require **JDK 17 or newer** at runtime. If you are pinned to an older JDK, stay on
`0.1.1` — it will keep working, since published versions are immutable.

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

### What each method costs you in Java

Five of the nine methods take and return primitives — from Java they are
indistinguishable from any Java utility class. The other four hand back Scala
types, and that is where the work is.

| Method | Java signature | Friction |
|---|---|---|
| `overflows` | `boolean (int, int)` | none |
| `add` | `int (int, int)` | none |
| `addSaturating` | `int (int, int)` | none |
| `addExact` | `int (int, int)` | none — throws `ArithmeticException` |
| `addWidened` | `long (int, int)` | none |
| `addOption` | `scala.Option<Object>` | unwrap + cast |
| `addEither` | `scala.util.Either<Object, Object>` | unwrap + cast |
| `addWithCarry` | `scala.Tuple2<Object, Object>` | unwrap + cast |
| `addFold` | `<A> A (int, int, Function2, Function1)` | lambdas, but they work |

The `Object` in those generics is not sloppiness — Scala erases primitives inside
generic containers, so the payload arrives boxed and you cast it to `Integer` or
`Long` yourself.

#### The friction-free half

```java
Adder.overflows(Integer.MAX_VALUE, 1);      // true
Adder.add(Integer.MAX_VALUE, 1);            // -2147483648  (wraps)
Adder.addSaturating(Integer.MAX_VALUE, 1);  // 2147483647   (clamps)
Adder.addWidened(Integer.MAX_VALUE, 1);     // 2147483648L  (widens)
```

`addExact` throws, and `ArithmeticException` is unchecked, so Java will not force
you to catch it:

```java
try {
    Adder.addExact(Integer.MAX_VALUE, 1);
} catch (ArithmeticException ex) {
    System.out.println(ex.getMessage());     // integer overflow
}
```

#### Unwrapping `Option`

```java
import scala.Option;

Option<Object> none = Adder.addOption(Integer.MAX_VALUE, 1);
none.isEmpty();                    // true
none.getOrElse(() -> -1);          // -1   (a Java lambda works as Function0)

Option<Object> some = Adder.addOption(2, 3);
if (some.isDefined()) {
    int value = (Integer) some.get();   // 5
}
```

#### Unwrapping `Either`

`Either.left()` gives you a projection, which is awkward. Casting to the concrete
`Left` / `Right` case class and reading `.value()` is cleaner from Java:

```java
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

Either<Object, Object> e = Adder.addEither(Integer.MAX_VALUE, 1);
if (e.isLeft()) {
    long exact = (Long) ((Left<Object, Object>) e).value();   // 2147483648
}

Either<Object, Object> ok = Adder.addEither(2, 3);
if (ok.isRight()) {
    int value = (Integer) ((Right<Object, Object>) ok).value();  // 5
}
```

Note the asymmetry in the casts: `Left` carries the exact sum as a `Long`, `Right`
carries an `Int`. That is the whole point of `addEither` — the overflow case keeps
the real answer instead of discarding it — but it does mean the two branches box to
different types.

#### Unwrapping `Tuple2`

Scala's `_1` / `_2` are methods in bytecode, so they take parentheses in Java:

```java
import scala.Tuple2;

Tuple2<Object, Object> t = Adder.addWithCarry(Integer.MAX_VALUE, 1);
int wrapped  = (Integer) t._1();   // -2147483648
boolean carry = (Boolean) t._2();  // true
```

#### `addFold` takes Java lambdas

This one usually surprises people. `scala.Function1` and `scala.Function2` compile
to Java interfaces with a single abstract method, so Java lambdas satisfy them
directly — no adapter, no `scala.compat` shim:

```java
String r = Adder.addFold(Integer.MAX_VALUE, 1,
        (a, b) -> "overflow: " + a + " + " + b,
        s      -> "ok: " + s);
// overflow: 2147483647 + 1
```

Scala's two parameter lists — `addFold(a, b)(onOverflow, onSuccess)` — flatten into
one Java argument list. The lambda parameters arrive as `Object`; string
concatenation accepts them as-is, but cast to `Integer` if you need to do
arithmetic.

### The dependency cost

A Java project that adds this 3.6 KB library inherits the Scala runtime. The full
resolved classpath is five jars:

| Jar | Size |
|---|---|
| `ideas_2.13-0.1.1.jar` | 3.6 KB |
| `scala-library-2.13.18.jar` | 5.7 MB |
| `pprint_2.13-0.9.6.jar` | 130 KB |
| `fansi_2.13-0.5.1.jar` | 63 KB |
| `sourcecode_2.13-0.4.3-M5.jar` | 118 KB |

`scala-library` is not optional — `Option`, `Either`, `Tuple2`, and `Function2` all
live there.

The other three are. `pprint` is a compile dependency of the project, but `Adder`
contains **zero references to it** (`javap -c` finds none), so a Java consumer that
only wants the arithmetic can exclude it and take `fansi` and `sourcecode` out with
it:

```xml
<dependency>
  <groupId>io.jnz</groupId>
  <artifactId>ideas_2.13</artifactId>
  <version>0.1.1</version>
  <exclusions>
    <exclusion>
      <groupId>com.lihaoyi</groupId>
      <artifactId>pprint_2.13</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```

That takes the classpath down to two jars — `ideas_2.13` and `scala-library` —
and the verified example below still runs unchanged. Confirm it yourself with:

```console
$ mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout
```

### The whole thing, verified end to end

```java
package demo;

import io.jnz.example.Adder;
import scala.Option;
import scala.Tuple2;
import scala.util.Either;
import scala.util.Left;

public class Main {
    public static void main(String[] args) {
        final int MAX = Integer.MAX_VALUE;

        System.out.println(Adder.addSaturating(MAX, 1));   // 2147483647
        System.out.println(Adder.addWidened(MAX, 1));      // 2147483648

        Option<Object> some = Adder.addOption(2, 3);
        System.out.println((Integer) some.get());          // 5

        Either<Object, Object> e = Adder.addEither(MAX, 1);
        if (e.isLeft()) {
            System.out.println((Long) ((Left<Object, Object>) e).value());  // 2147483648
        }

        Tuple2<Object, Object> t = Adder.addWithCarry(MAX, 1);
        System.out.println(t._1() + " carry=" + t._2());   // -2147483648 carry=true

        System.out.println(Adder.addFold(MAX, 1,
                (a, b) -> "overflow: " + a + " + " + b,
                s      -> "ok: " + s));                    // overflow: 2147483647 + 1
    }
}
```

```console
$ mvn compile exec:java -Dexec.mainClass=demo.Main
2147483647
2147483648
5
2147483648
-2147483648 carry=true
overflow: 2147483647 + 1
```

Those are the observed values, not illustrative ones.

### Java-specific troubleshooting

**`package scala does not exist`**
`scala-library` did not make it onto the compile classpath — usually because
someone excluded it, or set the dependency to `<scope>runtime</scope>`. Java code
that touches `Option`, `Either`, or `Tuple2` needs it at compile time.

**`cannot find symbol: class Adder`**
Check the artifact ID has the `_2.13` suffix. Without it Maven resolves nothing,
and the resulting error points at your import rather than at the dependency.

**`incompatible types: Object cannot be converted to int`**
You skipped a cast. Everything inside `Option`, `Either`, and `Tuple2` is boxed —
`(Integer) some.get()`, not `some.get()`.

**`Left` is ambiguous, or `.value()` does not exist**
Make sure you imported `scala.util.Left`, not something from your own codebase, and
that you cast the `Either` to it first. `Either` itself has no `value()`.

## Releasing a new version

For maintainers. The release is driven entirely by the *branch name*; there is no
manual publish step and **no PAT anywhere in the process** — GitHub Actions' own
`GITHUB_TOKEN` has `packages: write` granted in the workflow.

1. Branch off `master` with a name that is exactly `X.Y.Z` — `0.1.2`, not
   `v0.1.2` and not `release/0.1.2`. The name *is* the version.
2. Open a PR into `master` and merge it.
3. On merge, `.github/workflows/release.yml` checks the branch name against
   `^[0-9]+\.[0-9]+\.[0-9]+$`. Any other name and the job no-ops, so ordinary
   feature branches merge without releasing anything.
4. It then refuses if tag `vX.Y.Z` already exists, runs `sbt validate`
   (scalafmt check + full test suite), publishes with `RELEASE_VERSION` set,
   tags the merge commit `vX.Y.Z`, and deletes the release branch.

Two safety properties worth preserving if you edit that workflow: `version` falls
back to `0.1.0-SNAPSHOT` when `RELEASE_VERSION` is unset, and the `release` task
fails outright rather than publishing a snapshot — so a misconfigured workflow
errors instead of quietly shipping `0.1.0-SNAPSHOT` as a release. And publish
happens *before* tagging, on the reasoning that a missing tag is easier to repair
by hand than a tag pointing at something that was never published.

### Publishing from your machine

Rarely the right move — it produces a version with no tag and no CI evidence — but
if you must, you need a token with **`write:packages`**:

```console
$ export GITHUB_ACTOR=your-github-login
$ export GITHUB_TOKEN=ghp_with_write_packages
$ export RELEASE_VERSION=0.1.2
$ sbt release
```

`sbt publish` would also work, but `release` is the guarded task: without
`RELEASE_VERSION` it fails with *"refusing to publish 0.1.0-SNAPSHOT as a
release"* rather than pushing a snapshot to the registry.

GitHub Packages does not allow overwriting an existing version. Publishing `0.1.1`
a second time fails; bump the version instead.

## Storing the token safely

A token is a password that can read (and possibly publish) your packages. The two habits
worth breaking are pasting it into a build file, and parking it in a shell profile.

`export GITHUB_TOKEN=ghp_...` in `~/.zshrc` or `~/.bashrc` is the common shortcut and the
weakest option: the value sits in plaintext in a file that is often backed up or synced,
it is inherited by *every* process you launch from that shell, and it survives in your
shell history if you ever typed it directly. Keep it in the OS secret store and hand it to
the build only when the build asks.

| Platform | Store | Read it back |
|---|---|---|
| macOS | Keychain | `security find-generic-password` |
| Linux | libsecret / GNOME Keyring, or `pass` | `secret-tool lookup`, `pass show` |
| Windows | PowerShell SecretManagement | `Get-Secret` |

Whatever you choose: never commit it, give it an expiry, and revoke rather than reuse when
you are done with it. Revocation is at
**github.com/settings/tokens** — a revoked token stops working everywhere immediately.

### macOS — Keychain

Store it once. Passing `-w` as the **last** option makes `security` prompt for the value,
so the token never appears in your shell history — the tool says so itself: *"Use of the
-p or -w options is insecure. Specify -w as the last option to be prompted."*

```console
$ security add-generic-password -a your-github-login -s maven.pkg.github.com -U -w
password data for new item:
```

Read it back:

```console
$ security find-generic-password -a your-github-login -s maven.pkg.github.com -w
ghp_xxxxxxxxxxxx
```

The cleanest wiring is to let sbt fetch it at build load, so the token is never in a file
or an environment variable at all:

```scala
credentials += Credentials(
  "GitHub Package Registry",
  "maven.pkg.github.com",
  "your-github-login",
  scala.sys.process
    .Process(Seq("security", "find-generic-password",
                 "-a", "your-github-login", "-s", "maven.pkg.github.com", "-w"))
    .!!
    .trim
)
```

Verified: with that in `build.sbt`, no `GITHUB_TOKEN` set and a cold coursier cache, the
dependency resolves and runs. Put it in `~/.sbt/2/global.sbt` to get it machine-wide.

If you would rather stay with environment variables, at least pull the value from the
Keychain on demand instead of hardcoding it — a shell function keeps it out of every
unrelated process:

```bash
ghtoken() {
  GITHUB_ACTOR=your-github-login \
  GITHUB_TOKEN=$(security find-generic-password -a your-github-login -s maven.pkg.github.com -w) \
  "$@"
}
# then: ghtoken sbt compile
```

Verified in both zsh and bash: the command sees `GITHUB_TOKEN`, and the calling shell does
not — it is unset again the moment `ghtoken` returns.

Do not reach for `security`'s `-A` flag to silence the access prompt. Its own help calls it
*"insecure, not recommended"* — it lets any application read the item. By default only the
creating tool is trusted, which is what you want.

### Linux — libsecret or pass

With GNOME Keyring / KWallet via libsecret (`libsecret-tools` on Debian/Ubuntu,
`libsecret` on Fedora/Arch). `secret-tool store` reads the value from stdin, so it stays
out of your history:

```console
$ secret-tool store --label="GitHub Packages" \
    service maven.pkg.github.com account your-github-login
Password: 

$ secret-tool lookup service maven.pkg.github.com account your-github-login
ghp_xxxxxxxxxxxx
```

Or with [`pass`](https://www.passwordstore.org/), which is GPG-backed and works fine on a
headless box:

```console
$ pass insert github/packages-token
$ pass show github/packages-token
```

Same sbt wiring as macOS, with the command swapped:

```scala
scala.sys.process
  .Process(Seq("secret-tool", "lookup",
               "service", "maven.pkg.github.com", "account", "your-github-login"))
  .!!
  .trim
```

On a headless server with no keyring daemon, a file is a reasonable fallback — but make it
`chmod 600`, keep it outside the repo, and prefer a dedicated machine account's token over
your own.

### Windows — PowerShell SecretManagement

`cmdkey` and Credential Manager can *store* a secret but cannot print it back from the
command line, which makes them awkward for a build. PowerShell's SecretManagement module
is the better fit:

```powershell
Install-Module Microsoft.PowerShell.SecretManagement, Microsoft.PowerShell.SecretStore -Scope CurrentUser
Register-SecretVault -Name LocalStore -ModuleName Microsoft.PowerShell.SecretStore -DefaultVault

Set-Secret -Name GitHubPackages          # prompts, so nothing lands in history
Get-Secret  -Name GitHubPackages -AsPlainText
```

Feed it to the build for one command only:

```powershell
$env:GITHUB_ACTOR = "your-github-login"
$env:GITHUB_TOKEN = Get-Secret -Name GitHubPackages -AsPlainText
sbt compile
```

Or read it from `build.sbt` directly, same shape as the others:

```scala
scala.sys.process
  .Process(Seq("powershell", "-NoProfile", "-Command",
               "Get-Secret -Name GitHubPackages -AsPlainText"))
  .!!
  .trim
```

> The macOS commands above were run on this machine. The Linux and Windows equivalents are
> the standard invocations for those tools but were not executed here — check the output of
> the `lookup` / `Get-Secret` step before wiring it into a build.

### CI

Never a file, never the repo. Use the provider's secret store — GitHub Actions gets an
automatic `secrets.GITHUB_TOKEN` and needs no PAT at all (see
[Releasing a new version](#releasing-a-new-version)); everywhere else, put a PAT in the
provider's encrypted secrets and expose it as an environment variable for the one step
that needs it. Prefer a dedicated machine account over a personal login, so the pipeline
does not break when you rotate your own credentials or leave.
