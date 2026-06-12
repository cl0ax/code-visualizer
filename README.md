# NeetCode Visualizer

Personal, local replacement for NeetCode Pro's code visualizer. Paste arbitrary Java
(`class Solution`), give inputs, and step through the **real** execution — state cards,
pointer markers, line highlighting — or watch where a failing attempt goes wrong.

- `./visualize` — compile + start the web app at http://localhost:4747
- `./test.sh` — compile + run the full test suite (plain Java mains, no deps)
- Requires a JDK (built on 20). Zero external dependencies by design.


## Input literal syntax
`5` · `true` · `'c'` · `"text"` · `[1,2,3]` · `[[1,2],[3]]` · `{"a":1}` · `null`

## Headless CLI
java -cp out viz.Main trace bench/two-sum/Solution.java '[2,7,11,15]' '9'

## Guardrails
10s wall-clock timeout · 2,000 raw-step cap · unrecognized types degrade to
toString() value cards · every degradation is visible (truncated/notice), never silent.
Single-line loops (e.g. a whole while on one line) cannot be line-stepped by JDI — split the body onto its own line to step it.
