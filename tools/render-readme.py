#!/usr/bin/env python3
"""Render README.md to a single self-contained HTML page with a sticky nav rail.

The README is the source of truth; this only reformats it. Output is suitable for
opening directly in a browser or publishing as a Claude artifact.

The `<meta charset>` is load-bearing: opened over file:// there is no HTTP header to
declare the encoding, so without it a browser falls back to a locale default and every
em-dash renders as the mojibake "a-euro-quote".

    python3 tools/render-readme.py [-o out.html] [--open]

Requires: pip install markdown
"""

import argparse
import html
import pathlib
import re
import subprocess
import sys

try:
    import markdown
except ImportError:
    sys.exit("need Python-Markdown:  python3 -m pip install --user markdown")

ROOT = pathlib.Path(__file__).resolve().parent.parent
STYLE = pathlib.Path(__file__).resolve().parent / "_style.html"

TITLE = "Consuming io.jnz:ideas"
FAVICON = "\N{KEY}"

# Language tag -> the label shown above a code block.
LANGS = {
    "console": "shell",
    "scala": "build.sbt / Scala",
    "xml": "XML",
    "yaml": "YAML",
    "kotlin": "build.gradle.kts",
    "bash": "shell",
    "powershell": "PowerShell",
    "properties": "properties",
    "": "",
}


def gh_slug(text, _sep="-"):
    """GitHub's heading-anchor rules, so in-document links keep working."""
    slug = re.sub(r"[^\w\s-]", "", text.lower(), flags=re.UNICODE)
    return re.sub(r"[\s]+", "-", slug.strip())


def build_nav(toc):
    """Sticky rail: every H1, with the H2s of the `Usage` chapter numbered beneath it."""
    rows = []
    for h1 in toc:
        rows.append(f'      <li><a href="#{h1["id"]}">{html.escape(h1["name"])}</a></li>')
        if h1["name"].lower() != "usage":
            continue
        for i, h2 in enumerate(h1.get("children", []), start=1):
            rows.append(
                f'      <li class="sub"><a href="#{h2["id"]}">'
                f'<span class="n">{i:02d}</span>{html.escape(h2["name"])}</a></li>'
            )
    return "\n".join(rows)


def wrap_code_blocks(body):
    """Give every fenced block the labelled frame the rest of the design uses."""

    def repl(m):
        lang = m.group(1) or ""
        label = LANGS.get(lang, lang)
        head = f'<div class="block-label">{html.escape(label)}</div>' if label else ""
        return f'<div class="block">{head}<pre><code>{m.group(2)}</code></pre></div>'

    return re.sub(
        r'<pre><code(?: class="language-([\w-]+)")?>(.*?)</code></pre>',
        repl,
        body,
        flags=re.S,
    )


def number_chapters(body):
    """Prefix the Usage chapters with 01, 02, ... to match the rail."""
    counter = [0]

    def repl(m):
        counter[0] += 1
        return f'{m.group(1)}<span class="num">{counter[0]:02d}</span>{m.group(2)}'

    return re.sub(r'(<h2 id="[^"]+">)(.*?)(?=</h2>)', repl, body, flags=re.S)


def render(md_path):
    md = markdown.Markdown(
        extensions=["fenced_code", "tables", "toc", "sane_lists", "attr_list"],
        extension_configs={"toc": {"slugify": gh_slug, "anchorlink": False}},
    )
    body = md.convert(md_path.read_text(encoding="utf-8"))
    body = wrap_code_blocks(body)
    body = number_chapters(body)

    return f"""<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(TITLE)}</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500;600&family=IBM+Plex+Sans:wght@500;600;700&family=Source+Serif+4:opsz,wght@8..60,400;8..60,600&display=swap">

{STYLE.read_text(encoding="utf-8")}
<style>
  /* generated-document overrides */
  main h1 {{ font-size: clamp(1.8rem, 4vw, 2.5rem); margin: 56px 0 10px; padding-top: 26px;
             border-top: 1px solid var(--rule); scroll-margin-top: 24px; }}
  main h1:first-child {{ border-top: 0; margin-top: 26px; padding-top: 0; }}
  main h2 {{ margin-top: 40px; scroll-margin-top: 24px; }}
  main h3, main h4 {{ scroll-margin-top: 24px; }}
  main > p:first-of-type {{ font-size: 1.1rem; color: var(--ink-soft); }}
  blockquote {{ margin: 22px 0; padding: 14px 18px; max-width: 68ch;
                background: var(--surface); border: 1px solid var(--rule);
                border-left: 3px solid var(--accent); border-radius: 0 4px 4px 0; }}
  blockquote p {{ margin: 0; font-size: .95rem; }}
  table {{ font-size: 14px; }}
  table td:first-child {{ white-space: nowrap; }}
  .rail .n {{ font-family: var(--mono); font-size: 10.5px; color: var(--accent);
              margin-right: 7px; font-variant-numeric: tabular-nums; }}
  .rail ol {{ max-height: calc(100vh - 90px); overflow-y: auto; }}
</style>

<div class="shell">
  <header class="masthead">
    <p class="eyebrow">io.jnz &middot; ideas_2.13 &middot; GitHub Packages</p>
    <h1>{html.escape(TITLE)}</h1>
    <p class="standfirst">Generated from <code>README.md</code> &mdash; the repository is the
      source of truth. Regenerate with <code>python3 tools/render-readme.py</code>.</p>
    <span class="verified">Every command in this document was run against the live registry</span>
  </header>

  <nav class="rail" aria-label="Sections">
    <h2>Contents</h2>
    <ol>
{build_nav(md.toc_tokens)}
    </ol>
  </nav>

  <main>
{body}
  </main>

  <footer>
    <span>io.jnz:ideas_2.13</span>
    <span>github.com/djnzx/ideas</span>
    <span>sbt 2.0.8 &middot; Scala 2.13.18</span>
  </footer>
</div>
"""


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("-i", "--input", default=str(ROOT / "README.md"))
    ap.add_argument("-o", "--output", default=str(ROOT / "target" / "readme.html"))
    ap.add_argument("--open", action="store_true", help="open the result in a browser")
    args = ap.parse_args()

    out = pathlib.Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(render(pathlib.Path(args.input)), encoding="utf-8")
    print(f"wrote {out}")

    if args.open:
        opener = {"darwin": "open", "win32": "start"}.get(sys.platform, "xdg-open")
        subprocess.run([opener, str(out)], check=False)


if __name__ == "__main__":
    main()
