# sbt 2

Notes on what changed moving this project from sbt 1.x to sbt 2.0.8.
Everything here was checked against `sbt 2.0.8` rather than recalled from docs.

## Testing

`test` no longer runs all tests. It is now an alias for `testQuick`, and the old
behaviour moved to a new task name.

| Task                 | sbt 1              | sbt 2                                                   |
|----------------------|--------------------|---------------------------------------------------------|
| `test`               | runs everything    | **only failed, never-run, or changed-dependency tests** |
| `testQuick`          | incremental subset | alias for `test` — the same task                        |
| `testFull`           | *(did not exist)*  | **runs everything**                                     |
| `testOnly` (no args) | all tests          | all tests                                               |
| `testOnly <Suite>`   | that suite         | that suite                                              |

sbt states it itself:

```
> inspect test
Description:
  Executes the tests that either failed before, were not run or whose
  transitive dependencies changed, among those provided as arguments.
Dependencies:
  Test / testQuick
```

### Why it bites

On an unchanged tree `sbt test` prints and exits green:

```
[info] Passed: Total 0, Failed 0, Errors 0, Passed 0
[info] No tests to run for Test / testQuick
[success]
```

That is a pass with zero tests executed. In CI it is easy to miss, because a
cold cache hides it — but `actions/setup-java` with `cache: sbt` restores
`~/.sbt` and `~/.cache/coursier` between runs, so `test` really can no-op.

- Use **`sbt testFull`** in CI and whenever you want a real run.
- Use `sbt clean test` if the compile should be invalidated too.
- `sbt test` is still the right thing for a fast inner loop.

## Dependency tree

`dependencyTree` is built into sbt 2 — `addDependencyTreePlugin` is no longer
needed in `project/plugins.sbt`, and the sbt 1 task names are gone entirely:
`dependencyBrowseGraph`, `dependencyBrowseTree`, `dependencyDot`, and
`dependencyGraphMl` now report *"No matches for regular expression"*, exactly
like a task name that never existed. They are subcommands of `dependencyTree`.

### Viewing

| Command                               | Output                                 |
|---------------------------------------|----------------------------------------|
| `sbt "dependencyTree"`                | ascii tree in shell                    |
| `sbt "dependencyTree graph --browse"` | rendered DOT graph in browser          |
| `sbt "dependencyTree html --browse"`  | jsTree page in browser (no search box) |

Browser output lands under sbt 2's nested target layout, not `target/` root —
this is why it looks like nothing was generated:

| Subcommand | File                                                                |
|------------|---------------------------------------------------------------------|
| `html`     | `target/out/jvm/scala-<ver>/<project>/compile/html/tree.html`       |
| `graph`    | `target/out/jvm/scala-<ver>/<project>/compile/htmlgraph/graph.html` |

### Subcommands

| Subcommand | Output |
|---|---|
| `tree` | ascii tree (default) |
| `list` | flat list of all dependencies |
| `graph` / `dot` | GraphViz DOT |
| `html` | HTML page |
| `html-graph` | HTML page wrapping the DOT |
| `json` | JSON |
| `xml` | GraphML |
| `stats` | statistics |

### Options

| Option | Effect |
|---|---|
| `--browse` | opens the browser; only meaningful with `graph` or `html` |
| `--out <file>` | writes to a file; extension picks the default subcommand |
| `--quiet` | returns the output as the task value instead of printing |

Also: `sbt "whatDependsOn <org> <module> <version>"` for reverse lookup.

### Gotchas

- **`--out` is silently ignored for `html` and `html-graph`.** It reports
  `[success]` and writes nothing. Works fine for `dot`, `json`, `xml`. Use
  `--browse` and copy the file out of `target/` instead.
- **Both built-in HTML pages load JS from CDNs** (cdnjs, unpkg, d3js.org), so
  they do not render offline. There is no built-in offline view.
- **`graph --browse` needs no local Graphviz** — rendering happens in-browser
  via WASM. `dependencyTree dot` output is only useful if `dot` is installed.
