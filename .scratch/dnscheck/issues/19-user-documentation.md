# 19 — User and operator documentation

**What to build:** Install/configure/use documentation for infrastructure operators, DNS operators, and automation users; Docker Compose with PostgreSQL; single Docker image with service and CLI entrypoints; example zone/key files; systemd template; auto-generated admin bootstrap on first start.

**Status:** done

**Spec:** [17-user-documentation.md](../specs/17-user-documentation.md)

- [x] README with Docker quick start
- [x] docs/install.md, configure.md, operator-guide.md, cli.md
- [x] docs/examples (zone JSON, TSIG key, systemd unit)
- [x] Dockerfile + docker-compose.yml + docker-entrypoint.sh
- [x] Auto-generated admin bootstrap when bootstrap env vars omitted
- [x] OperatorBootstrap unit tests
- [x] mvn verify passes
