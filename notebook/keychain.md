# macOS Keychain

How to keep the GitHub Packages token out of `~/.zshrc` and out of the repo.
Every command here was run on macOS 26.5.2 rather than recalled from docs.

Terms: an item is identified by **`-a` account** + **`-s` service**. Both are
arbitrary strings — the pair is just a lookup key, so pick something you will
recognise. Below: account = your GitHub login, service = the registry host.

## Store

Put `-w` **last, with no value**, and `security` prompts instead of taking it from
the command line — so the token never lands in shell history:

```console
$ security add-generic-password -a djnzx -s maven.pkg.github.com -U -w
password data for new item:
```

`security` says so itself: *"Use of the -p or -w options is insecure. Specify -w as
the last option to be prompted."*

`-U` updates the item if it already exists. Without it a second `add` on the same
account+service fails rather than overwriting:

```
security: SecKeychainItemCreateFromContent (<default>): The specified item already exists in the keychain.
```

So `-U` is the flag you want when rotating a token and are not sure whether one is
already stored.

## Read

```console
$ security find-generic-password -a djnzx -s maven.pkg.github.com -w
ghp_xxxxxxxxxxxx
```

Without `-w` it prints the item's metadata — keychain path, class, attributes — but
not the secret.

## Delete

```console
$ security delete-generic-password -a djnzx -s maven.pkg.github.com
```

## Wire it into sbt

The good version: sbt shells out at build load, so the token exists in no file and
no environment variable.

```scala
credentials += Credentials(
  "GitHub Package Registry",
  "maven.pkg.github.com",
  "djnzx",
  scala.sys.process
    .Process(Seq("security", "find-generic-password",
                 "-a", "djnzx", "-s", "maven.pkg.github.com", "-w"))
    .!!
    .trim
)
```

Verified: with this, no `GITHUB_TOKEN` set, and a cold coursier cache, the
dependency resolves. Put it in `~/.sbt/2/global.sbt` to get it machine-wide —
note the dir is `2`, not `2.0`.

## Wire it into a shell

If a build insists on environment variables, scope them to one command rather than
exporting globally:

```bash
ghtoken() {
  GITHUB_ACTOR=djnzx \
  GITHUB_TOKEN=$(security find-generic-password -a djnzx -s maven.pkg.github.com -w) \
  "$@"
}
# ghtoken sbt compile
```

Verified in zsh and bash: the command sees `GITHUB_TOKEN`, the calling shell does
not — it is gone again the moment `ghtoken` returns.

## Gotchas

- **Do not use `-A`.** Its own help calls it *"insecure, not recommended"* — it lets
  any application read the item without prompting. By default only the tool that
  created the item is trusted, which is the behaviour you want.
- **First read from a new process may show a GUI prompt.** Click *Always Allow* once
  and sbt's `security` child process stops asking.
- **The Keychain is not a substitute for expiry.** Rotate the token on schedule and
  revoke the old one at github.com/settings/tokens; a stored secret is still a live
  credential.
- **sbt 2 snapshots the environment at server start.** If you use the `ghtoken`
  route rather than the `build.sbt` route, run `sbt shutdown` after changing the
  variables or the running server keeps the old ones.

See the README's *Storing the token safely* chapter for the Linux and Windows
equivalents, and for which token scope to ask for in the first place.
