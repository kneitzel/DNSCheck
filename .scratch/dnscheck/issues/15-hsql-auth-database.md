# 15 — HSQL auth database support

**What to build:** HSQL is a documented, tested auth database option alongside H2, PostgreSQL, and MariaDB. Deployers can configure an HSQL datasource; CI proves login and persistence on HSQL.

**Blocked by:** None — can start immediately

**Status:** done

**Spec:** [12-hsql-auth-database.md](../specs/12-hsql-auth-database.md)

- [x] HSQL JDBC dependency and custom datasource configuration present
- [x] Example `%hsql` datasource configuration in application properties
- [x] Comment block documents all four supported database kinds
- [x] Dedicated Quarkus test profile runs minimal auth flow against HSQL
- [x] `mvn verify` passes
