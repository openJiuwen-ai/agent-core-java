# migration

`com.openjiuwen.core.memory.migration` provides migration plans and entry points for KV, SQL, and vector memory stores.

## Modules

| Module | Description |
| --- | --- |
| [`migrator`](./migration/migrator.README.md) | contains backend-specific migration runners for KV, SQL, vector, and metadata stores. |
| [`operation`](./migration/operation.README.md) | defines migration operation metadata, registries, and concrete schema-change operations. |

## Types

| Type | Kind | Description |
| --- | --- | --- |
| [`MigrationPlan`](./migration/MigrationPlan.md) | class | Global migration registries for SQL, vector, and KV operations. |
| [`RunMigrations`](./migration/RunMigrations.md) | class | Entry point for running all memory migrations (SQL, Vector, KV). |

## Notes

- This package page exposes the documented child packages for the current memory subtree.
- The current page also links the 2 direct public type page(s) defined in this package.
