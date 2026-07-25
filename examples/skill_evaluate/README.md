# Skill Evaluator Example

This example runs the Java `SkillEvaluator` module against a local target skill.

## Default target

If you do not pass a path, the example evaluates:

`examples/skill_use/skills/image_resizer`

## Run

Set the same model-related environment variables used by the other examples:

- `API_BASE`
- `API_KEY`
- `MODEL_PROVIDER`
- `MODEL_NAME`
- `LLM_SSL_VERIFY` (optional)

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
  /absolute/path/to/skill
```

The evaluator writes reports under `examples/skill_evaluate/output/` by default.
