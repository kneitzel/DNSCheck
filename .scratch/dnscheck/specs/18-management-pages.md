# Management pages (operators, rights, groups)

Status: done

## Problem Statement

The admin UI used raw operator/group IDs for grant assignment, had no password self-service, and did not show whether rights were direct or inherited from groups.

## Solution

Add rights-summary and change-password API endpoints; rebuild admin UI with URL routes, account page, and operator/group detail pages with rights matrices and membership editing.

## User Stories

1. As any operator, I can change my password on an Account page (current password required).
2. As an admin, I can reset another operator's password on their detail page.
3. As an admin, I see effective rights with direct vs group sources on operator detail; group-derived rows are read-only.
4. As an admin, I manage group membership from operator detail and group detail (chips + add/remove).
5. As an admin, I manage direct grants on operator and group detail pages; zone grants use catalog dropdown.
6. As any operator, I can view my effective rights read-only on Account.

## Implementation Decisions

- `POST /api/v1/auth/change-password`, `GET /api/v1/auth/me/rights`
- `GET /api/v1/admin/operators/{id}`, `.../rights`, `GET /api/v1/admin/groups/{id}`, `.../rights`
- `RightsSummaryBuilder` merges direct and group sources; one row per (right, zone)
- React Router: `/account`, `/admin/operators`, `/admin/operators/:id`, `/admin/groups`, `/admin/groups/:id`
- Remove legacy flat AdminPanel

## Out of Scope

- Password complexity rules, audit log, operator/group delete UI
