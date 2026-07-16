# User and operator documentation

Status: done

## Problem Statement

DNSCheck had no install, configure, or usage documentation for infrastructure operators, DNS operators, or automation users. Docker deployment and first-admin bootstrap were undocumented; production bootstrap required manual env vars.

## Solution

Add README and `docs/` guides, Docker assets (single image, service + CLI entrypoints, PostgreSQL Compose), example zone/key/systemd files, and auto-generated admin bootstrap when env vars are omitted.

## User Stories

1. As an infrastructure operator, I want Docker quick start and bare-metal install docs, so I can deploy the service with PostgreSQL and bind-mounted zones/keys.
2. As a DNS operator, I want a UI workflow guide covering rights and record actions, so I can manage zones without reading code.
3. As an automation user, I want CLI and Docker CLI entrypoint docs with exit codes and cron examples, so I can run reconciliation outside the service.
4. As a deployer on first boot, I want an auto-generated admin password in logs when env vars are unset, so I can log in without pre-seeding secrets.

## Implementation Decisions

- README + `docs/install.md`, `configure.md`, `operator-guide.md`, `cli.md`
- `docs/examples/` for zone JSON, TSIG key, systemd unit
- `docker-compose.yml`: service + PostgreSQL, `DNSCHECK_DRY_RUN=true` by default
- `Dockerfile`: one image; `docker-entrypoint.sh` with `service` (default) and `cli` commands
- Pre-built release as primary bare-metal path; systemd unit template
- Bootstrap: `admin` + random password logged once when both bootstrap env vars empty; env override unchanged
- BIND: bridge networking default; host network documented as alternative
- Service scheduler + CLI documented as complementary

## Out of Scope

- Contributor / build-from-source guide
- BIND server setup tutorial
- Release CI pipeline producing tarballs
