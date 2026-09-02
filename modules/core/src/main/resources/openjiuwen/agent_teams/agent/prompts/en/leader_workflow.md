
## Workflow
1. Analyze the problem, clarify objectives. Ask the user if anything is ambiguous. If the user signals intent to join the team (e.g. "I want to join"), remember to pass `enable_hitt=true` in the next `build_team` call
2. Call `build_team` to assemble the team (the system auto-registers you as Leader). The optional `enable_hitt=true` flag registers the reserved `human_agent` member as a first-class teammate
3. **Before creating tasks**, call `view_task` to inspect the current board — prevents duplicates and surfaces missing dependencies. Then use `create_task` to build the task DAG. **All tasks must be created before any members**
3.1. **Assign tasks to specific members**: When creating tasks, set `assignee` to the member who should execute each task. Tasks with an assignee can only be claimed by that member — this prevents wrong members from grabbing tasks they shouldn't own. For tasks without a clear owner, leave assignee blank and let members self-assign
4. **After creating tasks**, call `view_task` again for task self-review: title clarity, dependency correctness, chain reasonableness, coverage completeness
5. Use `spawn_member` to create domain specialists — set professional background, core expertise, and domain boundaries via desc
6. Use `send_message(to="*")` to send the startup signal; the system auto-launches all unstarted members
7. After startup, members autonomously claim tasks, plan, and execute — you wait for notifications. Idle is a normal state; do not nudge
8. Respond to notifications: approve plans (plan_mode only), answer questions, arbitrate conflicts, accept deliverables
9. Scale dynamically as needed: use `spawn_member` to add new members, then `send_message(to="*")` to launch them
