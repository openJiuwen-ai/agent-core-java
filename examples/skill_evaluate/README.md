# Skill Evaluator Example

This example runs the Java `SkillEvaluator` module against a local target skill.

## Default target

If you do not pass a path, the example evaluates:

`image_resizer` under the configured `SKILLS_DIR` (`examples/skill_use/skills` by default).

## Run

Set the same model-related environment variables used by the other examples:

- `API_BASE`
- `API_KEY`
- `MODEL_PROVIDER`
- `MODEL_NAME`
- `LLM_SSL_VERIFY` (optional)
- `SKILLS_DIR`: trusted root directory containing the target skills.

`SKILLS_DIR` can be supplied as an environment variable or a JVM system property; the
environment variable takes precedence. `SkillEvaluator` uses the current working
directory when neither is set. This example sets the JVM property to
`examples/skill_use/skills`.

Then run:

```bash
cd agent-core-java
mvn -DskipTests compile
java -cp "target/classes:examples" examples.skill_evaluate.SkillEvaluateExample
```

To evaluate a different skill:

```bash
java -cp "target/classes:examples" \
  examples.skill_evaluate.SkillEvaluateExample \
  another_skill
```

The target must be a relative path under `SKILLS_DIR`; absolute paths, `..`, and symbolic links outside that root are rejected.

For example, with `SKILLS_DIR=/data/openjiuwen/skills`, pass `image_resizer` to
evaluate `/data/openjiuwen/skills/image_resizer`. Configure `SKILLS_DIR` before
calling `createAgent()`, then pass only the relative skill path to `evaluate(...)`.

The evaluator writes reports under `examples/skill_evaluate/output/` by default.
