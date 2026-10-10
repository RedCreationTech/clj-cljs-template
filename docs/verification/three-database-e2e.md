# Three-database baseline acceptance

All acceptance jobs run from the same PR head. The workflow expands to 13 jobs:
static checks; three backend suites; and three database variants each of the web,
generated-module and mobile UI suites. SQLite, MySQL 8.4 and PostgreSQL 17 use the
same browser tests and the same six-field `ci-demo` input.

## Evidence required before calling this complete

| Gate | SQLite | MySQL | PostgreSQL |
| --- | --- | --- | --- |
| Full backend tests, migration up/down/up | required | required | required |
| Fresh isolated database, schema/seed assertions | required | required | required |
| Actual JDBC product/version and HTTP datasource identity | required | required | required |
| Production browser login/logout and positive/negative permissions | required | required | required |
| User/role CRUD and bidirectional assignment | required | required | required |
| Department/menu/post/config/notice CRUD | required | required | required |
| Dictionary type and child data CRUD | required | required | required |
| Existing search/pagination/import/export/upload/download suites | required | required | required |
| Six-field generated backend tests, migration, browser CRUD/filter/page/validation | required | required | required |
| New backend process reads saved business record through browser | required | required | required |
| New backend process reads all six generated fields through browser | required | required | required |
| Existing two Flutter Web mobile UI tests | required | required | required |

`EXPECTED_DB`, `DB_TYPE`, `DB_ENABLED` and JDBC URL must agree. The service's own
JDBC metadata is checked through its authenticated HTTP datasource endpoint;
a separate JDBC check verifies all migration IDs are present and six key seed
tables plus the unique administrator exist. A different database product fails.
Reports include the actual database version, named tests, failures and skips.

`bb e2e:matrix` creates a unique SQLite file or a uniquely named database in an
explicit CI-local disposable MySQL/PostgreSQL service. It starts its own backend,
runs browser acceptance, writes a post through the UI, stops only its own process,
starts a fresh process using the same database, then reads and deletes that post
through the browser. Scaffold jobs also retain and read back all six generated
field values. No reset, down/up, or reseed is used between these two processes.
Finally it stops its process and removes only its own temporary database/files.

The existing migration roundtrip remains a separate, deliberately destructive
check against a different disposable test database. It proves reversibility;
it does not claim data preservation. Synthetic UI fixtures use unique names and
clean up exact owned records, never the seeded administrator/default roles.

The production suite intentionally excludes the development snapshot scenario;
the scaffold development build checks it separately. Normal web jobs may skip
mobile-specific tests because the dedicated mobile matrix requires and runs them.
Generated restart tests apply only to jobs where the module is generated. Every
skip must remain visible in the final PR evidence, rather than being counted as a pass.

## Scope limits

This is baseline template business-loop acceptance, not a claim that every
possible business boundary or production deployment is verified. It does not
cover production concurrency/capacity, fault injection, real third-party services,
or upgrades from every historical populated schema. Department moves, every menu
type, complex rich-text permutations and every bulk-operation combination remain
outside this particular browser matrix. No merge or deployment is performed.
