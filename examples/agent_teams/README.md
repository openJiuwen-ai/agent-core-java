# Agent Teams E2E Example

This example demonstrates the agent_team functionality aligned with Python's `agent_team_e2e.py`.

## Features Demonstrated

- Creating a team with leader using `TeamAgentSpec` and `TeamFactory`
- Running interactive CLI loop with streaming output
- Dispatching tasks and broadcasting messages
- Snapshot and recovery functionality
- Session management

## Files

- `AgentTeamE2eExample.java` - Main entry point
- `AgentTeamE2eExampleSupport.java` - Support implementation with interactive CLI
- `AgentTeamE2eExampleTest.java` - Test runner for verification

## Running the Example

```bash
# Compile and package
mvn package -DskipTests

# Run the test
mvn test -Dtest=AgentTeamE2eTest

# Run interactive example (requires apiconfig.json)
java -cp target/classes:target/agent-core-java-0.1.12.jar:... \
  examples.agent_teams.AgentTeamE2eExample
```

## Configuration

The example requires `examples/apiconfig.json` with:

```json
{
  "API_BASE": "https://your-api-base/v1",
  "API_KEY": "your-api-key",
  "MODEL_PROVIDER": "OpenAI",
  "MODEL_NAME": "your-model-name"
}
```

## Core Classes Used

- `TeamAgentSpec` - Team specification builder
- `TeamMemberSpec` - Member definition (leader/teammate)
- `ModelPoolEntry` - Model configuration
- `TeamFactory` - Factory for creating/recovering teams
- `TeamAgent` - Main agent team class with stream/dispatch/interact methods

## Related Tests

See `src/test/java/com/openjiuwen/agentteams/AgentTeamE2eTest.java` for the JUnit test suite.
