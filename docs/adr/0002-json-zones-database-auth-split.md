# JSON zone catalog with database-backed authentication

Zone definitions (managed records, deletion markers, nameserver, key file references) live as JSON files on disk in a configured zone catalog directory. Operator accounts, groups, and rights live in a relational database.

Reconciliation and the management API both read and write the same JSON files for zone data. The database holds only identity and access control. This split keeps zone data git-ops friendly and hand-editable while supporting multi-user management through the API.

The service supports H2, HSQL, PostgreSQL, and MariaDB/MySQL for the auth database.
