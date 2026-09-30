# KRAPulse — Enterprise KRA & Performance Platform

Daily work tracking, KRA/KPI management and performance scoring for a multi-department
organisation.

- **Blueprint (source of truth):** [`docs/blueprint.html`](docs/blueprint.html) — architecture,
  schema, API surface, scoring formulas and the seven-phase delivery plan.
- **Delivered:** Phase 1 — Foundation (identity, organisation, people) · Phase 2 — Work (projects,
  tasks, daily work logs, approval) · Phase 3 — Goals (KRA/KPI templates, assignment, targets,
  achievement) · Phase 4 — Engine (configurable formula, score snapshots, scheduled jobs, freeze) ·
  Phase 5 — Appraisal (cycles, self assessment, manager evaluation, configurable review workflow) ·
  Phase 6 — Insight (role dashboards, fifteen reports with exports, notifications) ·
  Phase 7 — Hardening (rate limiting, caching, audit integrity, indexes, Docker, deployment).
- **All seven phases are shipped.** 203 backend tests pass; `docker compose up` brings up the
  database, the API and the SPA.

## Stack

| Layer    | Choice |
| -------- | ------ |
| Backend  | Java 21 · Spring Boot 3.3 · Spring Security · Spring Data JPA · Flyway |
| Database | MySQL 8 |
| Frontend | React 18 · TypeScript · Vite · MUI 6 · TanStack Query · Recharts |
| Auth     | JWT access token (15 min) + rotating refresh token (7 days) |
| Docs     | OpenAPI 3 at `/swagger-ui.html` |

The UI follows the **Precision Enterprise Performance** design system: deep indigo primary,
slate neutral canvas, border-first elevation, compact 38px data-grid rows. Tokens live in
`frontend/src/theme/tokens.ts` and are applied through the MUI theme in `theme.ts`.

## Prerequisites

- **JDK 21** — Spring Boot 3.3 supports Java 17 and 21. Newer JDKs are not supported at runtime.
- **Maven 3.9+**
- **Node 20+**
- **MySQL 8** with an empty schema:

```sql
CREATE DATABASE kra_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```
> The `pom.xml` pins newer Lombok, Mockito and ByteBuddy than the Spring Boot 3.3 BOM.
> Those overrides only matter when the build itself runs on a JDK newer than 21 — the
> older versions cannot instrument classes on recent JDKs. The application still targets
> Java 21.

## Running the backend

```bash
cd backend
export DB_URL="jdbc:mysql://localhost:3306/kra_system?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata"
export DB_USERNAME=root
export DB_PASSWORD=yourpassword
export JWT_SECRET="a-long-random-string-at-least-32-characters"
export ADMIN_DEFAULT_PASSWORD="Admin@12345"
mvn spring-boot:run
```

Flyway creates the schema and seeds reference data on first start:

- 32 permissions and 5 roles (Super Admin, HR Admin, Department Head, Reporting Manager, Employee)
- 13 departments and 16 designations — all editable, none hard-coded
- 14 activity categories and 34 department task templates (Software Development, QA, Sales, HR,
  Customer Support, Insurance, Compliance, IT) — the vocabulary each department logs work in
- 5 published scorecard templates (Software Engineer, QA Engineer, Sales Executive, HR Executive,
  Engineering Manager) with 21 KRAs and 42 KPIs, every level weighted to exactly 100%
- 4 scoring formulas (daily, monthly, quarterly, annual) and 6 rating bands, all editable
- One administrator account: **`admin` / the value of `ADMIN_DEFAULT_PASSWORD`**
- The default four-stage review workflow (self assessment → manager → department head, optional →
  HR sign-off), editable on the appraisal cycles screen

The seed migration ships **no password hash**. `BootstrapAdminInitializer` encodes
`ADMIN_DEFAULT_PASSWORD` on first start and flags the account to force a change at first sign-in.

API docs: <http://localhost:8080/swagger-ui.html>

## Running the frontend

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

Opens <http://localhost:5173> and proxies `/api` to `http://localhost:8080`.

## What Phase 1 delivers

**Backend**

- JWT authentication with refresh-token rotation and reuse detection (a replayed token revokes
  the whole family), BCrypt strength 12, progressive account lockout.
- Permission-based authorisation: `@PreAuthorize("hasAuthority('…')")` on services and
  controllers — code asks what a user may do, never who they are.
- `AccessScopeResolver` answers *to whom*: SELF / REPORTS / DEPARTMENT / ORGANISATION. Every
  employee-bound query takes the resolved id set rather than trusting a request parameter.
- Departments, teams, designations, holidays and working-hour policy as configuration.
- Employees with effective-dated reporting hierarchy, cycle detection, and account creation in
  the same transaction.
- Insert-only audit trail with before/after JSON on every regulated action.

**Frontend**

- Sign-in, forgot/reset password, forced password change.
- App shell with permission-filtered sidebar, command search, dark mode, profile menu.
- Dashboard with real organisation figures (headcount by department, access scope).
- Employee directory, profile with reporting history, manager reassignment.
- Department / team / designation management, company settings with holiday calendar.
- Roles & permissions editor, audit log viewer with before/after diffs.

Later-phase items (work logs, KRA, scoring) appear in the sidebar as disabled entries, so the
shape of the product is legible without pretending the data exists.

## What Phase 2 delivers

**Work tracking**

- Projects, activity categories and per-department task templates, all configuration rather than
  code. A category decides whether time counts as productive, which is what the Phase 4 score
  will read.
- Tasks, self-created or manager-assigned. Assignment is scope-checked, and a task's actual hours
  are recomputed from the timesheet rather than typed in.
- The daily work log: time-slot entries, a proportional day ribbon with the unallocated gaps drawn
  explicitly, and cached day totals (office, logged, productive, meeting, break, unallocated,
  overtime) recomputed from the entries after every write so they cannot drift.
- Submission and review per **day**, not per entry: DRAFT → SUBMITTED → APPROVED, with
  reject / return-for-changes / reopen, an append-only history of every transition, and a mandatory
  comment on anything other than an approval.

**Rules the server enforces**

- No overlapping entries — a half-open interval test, so 11:00–13:00 and 13:00–14:00 do not clash.
- End after start; a minimum five-minute slot; duration always computed server-side.
- Inside the shift unless overtime is enabled, and never past the shift plus the overtime allowance.
- No future dates, and nothing older than the back-dating window.
- A submitted day is read-only until a manager returns it. Nobody reviews their own day.

## What Phase 3 delivers

**Goals**

- KRA and KPI templates per designation. The 100% rule is enforced at both levels — KRAs within a
  template, and KPIs within each KRA — on save and again at publish, because an unbalanced
  scorecard cannot produce a defensible score.
- Assignment **copies** the template onto the employee for a period rather than pointing at it. A
  template revised next month cannot rewrite what somebody was measured on last month.
- Targets per period, and actuals recorded by the manager — never by the person being measured.
- Weightages adjusted as a complete set, validated to 100%, with the reason audited.

**Achievement, computed both ways**

```
higher-is-better   achievement = min(actual / target × 100, cap)
lower-is-better    achievement = min(target / actual × 100, cap)
boolean            achievement = met ? 100 : 0
```

Caps default to 120%, so over-achievement is rewarded but bounded. A KPI with no target or no
actual is **excluded and its weight redistributed**, never scored zero — an administrative gap is
not a performance failure. The same rule applies one level up: a KRA that cannot be scored drops
out of the overall rather than counting against the employee.

**KPIs that measure themselves**

A KPI can declare where its actual comes from. Three sources read Phase 2 data directly:

| Source | Reads |
| ------ | ----- |
| `WORK_LOG_PRODUCTIVE_HOURS` | Productive minutes ÷ logged minutes over the period |
| `TASK_COMPLETION_RATE` | Tasks completed ÷ tasks assigned in the period |
| `TASK_ON_TIME_RATE` | Of tasks completed, the share closed by their due date |

These are read fresh rather than stored, are never measured into the future, and refuse manual
entry. Cross-module reads go through ports the KRA module declares (`TimesheetMetrics`,
`TaskMetrics`), implemented in the modules that own the data.

Work-log entries and tasks can also be attributed to a KRA, which is how a day's hours reach a
result area in Phase 4.

## What Phase 4 delivers

**The formula is data**

Four configurations, one per grain, each a versioned set of weighted parameters:

| Grain | Default weights |
| ----- | --------------- |
| Daily | Task completion 30 · KRA 25 · Productivity 20 · Quality 15 · Manager 10 |
| Monthly | Daily average 40 · KRA 25 · KPI 20 · Manager 15 |
| Quarterly | Monthly average 60 · KPI 25 · Manager 15 |
| Annual | Quarterly average 50 · KRA 20 · KPI 15 · Manager 10 · Dept head 5 |

Weights must total exactly 100%, and a parameter the engine cannot measure at that grain is
refused outright. **Saving never edits the running version**: it creates a new one and retires the
old, so a score computed last quarter keeps naming the formula it was computed under.

**Scores are snapshots, and they explain themselves**

Every snapshot stores its `formula_version` and a JSON breakdown holding each parameter's raw
value, weight, effective weight and contribution. That breakdown is what the employee sees, so
"why 84 and not 86" has an answer that adds up on screen.

A parameter with no input is **excluded and its weight redistributed** across the rest — the same
rule as an untargeted KPI in Phase 3. A missing manager rating is an unfilled form, not a bad
month. Non-working days with nothing logged are not scored at all, so weekends do not drag a
monthly average; a day somebody actually worked and filed is scored whatever the calendar says.

An optional consistency penalty docks a fixed number of points when the work-log submission rate
falls below a threshold, because the other figures rest on that evidence.

**Score integrity**

- No endpoint accepts a computed score as input.
- An adjustment demands a reason, records the previous value and the actor, and shows both to the
  employee.
- `FINALIZED` is a real freeze: recalculation skips it (and says so), adjustment is refused, and
  only `PERFORMANCE_REOPEN` can unfreeze it — with a reason, logged at WARN and audited.
- Rating bands are validated to be contiguous from 0 with no gap or overlap, so no score falls
  between two bands or into both.

**Scheduled jobs**

Daily at 01:15, monthly on the 1st, quarterly and annual at period end. Each run claims a row in
`scheduled_job_runs` keyed by job and period; the unique constraint is the lock, so two instances
cannot both score the same night. One employee failing is counted, not fatal.

## What Phase 5 delivers

**A cycle is a period with windows**

A performance cycle names a period (monthly, quarterly or annual) and, optionally, when each
window is open: self assessments from one date to another, reviews from another. Opening a cycle
creates one appraisal per eligible employee on the configured workflow, and reports how many it
made; opening it twice creates nothing twice. Closing it stops further submissions without
disturbing appraisals already in flight.

**The review flow is configuration, not code**

`review_workflow_steps` is an ordered list of active steps. Removing the department head review
takes it out of every appraisal in flight rather than leaving a hole; an optional step can be
skipped, a required one cannot. Three shapes are refused, because they cannot work:

- nothing active — there would be no flow at all;
- self assessment alone — that would let people rate themselves into a final rating;
- an optional last step — the last step is the sign-off.

An appraisal sitting on a step that has since been deactivated is not stranded: it moves to the
first configured step after it in the canonical order.

**Ratings finally reach the score**

The manager evaluation is where `QUALITY_RATING` and `MANAGER_RATING` come from — the two formula
parameters Phase 4 had to redistribute for want of an input. A 1–5 rating becomes 0–100 (×20).
Only **submitted** reviews count; a draft is nobody's judgement yet. If no overall rating is
given, it is derived from the competencies that were rated — KRA and KPI ratings are excluded from
that mean, because the engine measures those directly and a reviewer's view of them should not be
counted twice. A competency left unrated is left out, never scored zero.

**What the workflow will not let through**

- An employee writes only their own assessment, and nobody reviews their own appraisal.
- Submitting freezes the document: a reviewer never reads one that shifts underneath them.
- A step cannot be advanced until its own work is done — the self assessment submitted, the review
  submitted — unless the step is optional and is explicitly skipped.
- A submitted review demands a quality rating, an overall rating (given or derivable) and
  something written: numbers alone are not feedback.
- Returning a step reopens the previous submission as a draft and records the reason, so the trail
  shows both the send-back and what was asked for.

Every move — submitted, approved, returned, skipped, completed — is appended to
`review_step_actions` with its actor and comment, which is what makes a disputed rating auditable.

## What Phase 6 delivers

**Fifteen reports, one screen, no new endpoints**

A report is a declaration — key, title, permission, the filters it understands and the module that
answers it — registered as a bean. The registry finds it, one controller runs it, the shared filter
panel drives it and all three renderers can already export it. A sixteenth report is a bean and
nothing else: no controller, no route, no frontend change.

| Group | Reports |
| ----- | ------- |
| Work tracking | daily work log · employee timesheet · department timesheet · productivity · unfiled work logs · task completion |
| Goals | KRA performance · KPI achievement |
| Performance | monthly · quarterly · annual · department performance · top performers · employees needing support |
| Appraisal | appraisal progress |

The report module owns no SQL. Each module exposes a reporting port — `WorkReportData`,
`TaskReportData`, `KraReportData`, `PerformanceReportData`, `AppraisalReportData` — and the query
lives next to the data it reads, **with the caller's access scope applied inside the query** rather
than filtered afterwards. The KRA reports go further and read the same scorecard the employee sees,
so a report can never quietly disagree with the screen it summarises.

Two refusals rather than quiet damage: a result over `app.reports.max-rows` is refused with advice
to narrow the filters instead of being truncated into something that looks complete, and a report
the caller lacks the permission for is refused rather than returned empty.

**Exports that describe themselves**

XLSX through POI's streaming workbook (rows flush to disk, so size is bounded by disk not heap),
CSV streamed with a byte-order mark so Excel reads UTF-8 correctly, and PDF with the title, the
filter set and the row count printed on it. Numbers are written as numbers and dates as dates, so
a recipient can sort and total a sheet without cleaning it up first.

Every export writes a `report_requests` row — who ran what, with which filters, how many rows left
the system — and is audited. Small exports render inline; anything over
`app.reports.async-threshold` is queued, written on a background thread and **notified when it is
ready**. Only the person who ran an export can download it, because the file holds rows from the
scope they had at the time.

**Notifications**

Services publish a `NotificationRaised` event; an async listener running `AFTER_COMMIT` renders the
configured template and fans out to channels. Nobody is told about an approval that then rolled
back, and a notification failure never undoes the work that caused it.

Wording lives in `notification_templates`, not in Java: an administrator can reword "your day was
returned" without a release. A placeholder the event did not supply is left as written rather than
printed as "null".

Fourteen events are wired — work log submitted and reviewed, task assigned, due and overdue,
unfiled day, score published, finalised and reopened, cycle opened, appraisal step ready, returned
and completed, review past its SLA, export ready. Daily scores are deliberately silent: a
notification for every person every night would train people to ignore the feed.

Delivery is recorded per recipient per channel, so "nobody told me" is answerable. Email has no
mail sender wired in this deployment, so its deliveries record **SKIPPED with the reason** rather
than pretending to send — the switch and one class are all that change when SMTP arrives.

The nightly reminders check before they speak: nothing is sent twice in a day, nothing is sent
about a non-working day, and nothing is sent about a step with no configured SLA.

**Dashboards**

The employee and manager dashboards from Phase 2 are joined by a department-head view and an
organisation view, each one backend call returning a typed payload — the browser does no
aggregation. Both new dashboards are composed from the same ports the reports read, so a tile and
the report behind it cannot disagree.

The organisation view carries two things domain dashboards usually leave out: whether the scheduled
scoring jobs actually ran, and the last few audited actions. A scheduler that stopped and a
permission that was quietly changed are both invisible until somebody is shown them.

## What Phase 7 delivers

**Throttling the door, not the building**

Phase 1 locked an account after five failed attempts. That does nothing about one guess sprayed
across two hundred accounts, which no per-account counter ever sees. A per-IP fixed window now sits
in front of the endpoints reachable without a token — ten sign-in or reset attempts a minute, forty
token refreshes — and returns `429 RATE_LIMITED` in the same error envelope as everything else.
Authenticated traffic is deliberately untouched: it is already attributable and scope-limited, and
throttling it would mean throttling a manager approving forty timesheets on a Monday morning.

The window is in-process, which is honest about what this deployment is. Behind more than one
instance the limit becomes per-instance; the fix then is a shared store, not a bigger map.

**Caching the one read that happens on every request**

Every authenticated request rebuilt the signed-in principal from four tables. It is now cached for
two minutes and evicted explicitly wherever what it holds can change — a role's permissions, a
password, a profile, a manager, a deactivation. Two minutes is a safety net for a path somebody
forgets to evict, and it sits well inside the fifteen-minute life of the access token that carried
the request in, so it widens no window that was not already open. Measured on the running system:
one miss and six hits across seven requests.

The working calendar and company settings are cached the same way, evicted when a holiday or the
working week changes. Nothing employee-scoped is cached at all: a cache keyed by the wrong thing is
how one person ends up reading another's data, and none of those reads is expensive enough to be
worth the risk.

**The audit trail as evidence**

`AuditLogRepository` now extends the bare `Repository` rather than `JpaRepository`, so `delete` and
`deleteAll` do not exist on it. The trail is insert-and-read by construction rather than by
convention. Every row carries the request's trace id, which is the same id in the log line and in
the `X-Trace-Id` response header — "it failed at about half past three" becomes one grep.

Retention clears **read** notifications past their window nightly. Unread ones stay: age does not
settle a message somebody never saw. Audit rows are never touched by the application; archiving
them is an operational task, and [`docs/ops/partition-audit-logs.sql`](docs/ops/partition-audit-logs.sql)
carries the partitioning script with the reason it is not a migration — MySQL refuses foreign keys
on a partitioned table and demands the partition column in every unique key, so partitioning costs
either referential integrity or a changed primary key. `audit_logs` is the one growth table that
can pay it, and only once it is large enough to be worth it.

**Refusing to start unsafely**

Under the prod profile the application checks its own configuration at startup and refuses to serve
traffic with a development signing key, a short one, an empty database password, or CORS still
allowing localhost. Every problem is named, not just the first. A forgotten default is the kind of
thing noticed only after it is exploited.

Response headers now carry a Content-Security-Policy of `default-src 'none'` — the API serves JSON
and nothing else — plus Permissions-Policy alongside the HSTS, frame and referrer headers from
Phase 1. Actuator exposes health and info publicly for the orchestrator; metrics, env, configprops
and beans all require authentication.

**Running it**

```bash
cp .env.example .env     # nothing has a working default: compose refuses to start without these
docker compose up -d --build
```

MySQL with a healthcheck the backend waits on, the API as a multi-stage build running non-root with
a container-aware heap, and the SPA behind Nginx with SPA history fallback, gzip and `/api` proxied
through. Exports live on a named volume, so a deploy does not lose a file somebody queued.

## Project layout

```
backend/
  src/main/java/com/company/kra/
    common/       Base entities, shared DTOs, enums
    config/       Security, OpenAPI, JPA auditing, async, bootstrap
    exception/    Error codes, business exceptions, global handler
    security/     JWT, principal, user details, AccessScopeResolver
    audit/        Audit entity, service, controller
    modules/
      auth/ identity/ organisation/ employee/ dashboard/
      task/         projects, categories, templates, tasks
      worklog/      day header, entries, TimesheetValidator, review
      kra/          templates, assignment, targets, actuals, AchievementCalculator
      performance/  formula config, ScoreComposer, snapshots, freeze
      appraisal/    cycles, workflow config, self assessment, reviews, step trail
      notification/ templates, feed, delivery records, channels (in-app, email)
      report/       registry, 15 definitions, renderers (XLSX, CSV, PDF), export requests
  scheduler/      PerformanceJobs (daily, monthly, quarterly, annual)
                  ReminderJobs (unfiled days, task due/overdue, review SLA)
                  RetentionJobs (read notifications past their window)
  observability/  TraceIdFilter, ProductionReadinessCheck
  common/metrics/ TimesheetMetrics, TaskMetrics, KraMetrics, ReviewMetrics — the ports
                  the engine reads through, implemented by the owning modules
  src/main/resources/db/migration/   V1 baseline, V2 seed, V3 work tracking, V4 catalogue seed,
                                    V5 KRA/KPI, V6 template seed, V7 engine, V8 formula seed,
                                    V9 appraisal, V10 workflow seed, V11 insight,
                                    V12 notification catalogue, V13 hardening indexes,
                                    V14 sign-in invite, V15 pending-activation backfill,
                                    V16 back-dating unlock, V17 unlock link fix
frontend/
  src/
    api/ app/ components/ config/ contexts/ hooks/ layouts/ routes/ theme/ types/ utils/
    features/auth  dashboard  employees  organisation  admin  profile
             worklogs (daily log, timeline ribbon, review queue)  tasks
             calendar (month grid, pure day-classification model + tests)
             kra (scorecard, template builder, team assignment)
             performance (score breakdown, trend, console, formula editor)
             appraisal (self assessment, review console, cycles, workflow editor)
             reports (catalogue, shared filter panel, grid, exports)
             notifications (bell, feed)
             dashboard (employee, manager, department, organisation)
docs/
  blueprint.html
  ops/partition-audit-logs.sql

Dockerfile (backend) · Dockerfile + nginx.conf (frontend) · docker-compose.yml · .env.example
```

## Conventions

- Controllers bind and delegate; business logic lives in services; repositories are never called
  from a controller.
- Entities are never exposed over HTTP — every endpoint speaks DTOs.
- Modules talk through service interfaces (`EmployeeDirectory`), never each other's repositories.
- Deactivation, not deletion: work logs and performance history must keep resolving their
  department, designation and manager.
- Errors return one envelope with a stable `code`; the frontend reacts to the code, the user
  reads the message.

## Calendar

A month grid over the work log: each working day is coloured by where it has got to — approved,
awaiting review, draft, or nothing recorded at all — with holidays named in the cell and week-offs
left blank. Picking a day opens that day's log.

The rule for "is this a working day" is the one thing worth care here, because the same question
decides both what the calendar paints red and what the score engine expected. It lives in
[`calendarModel.ts`](frontend/src/features/calendar/calendarModel.ts) as pure functions with tests
(`npm test`), deliberately mirroring the backend's `WorkingCalendar`: the company's working-day
tokens, minus holidays, minus anything before the person joined. A draft counts as filed — a day
somebody has started is not a day they missed — and only a working day that has passed with no row
at all is reported as not filed.

Managers with `WORKLOG_READ_TEAM` can switch to a colleague's month; that view is read-only and the
access scope is still enforced by the API, not by hiding the picker.

## Filing a day the window has closed

Work-log entries are refused beyond `backdateWindowDays`, and the refusal has always told people to
ask their manager. This is that thing: a manager with `WORKLOG_APPROVE` reopens **one date for one
person** from Team work logs, with a reason, and it closes again on its own after
`app.worklog.unlock-valid-hours` (72 by default).

The alternative an administrator has is to widen the window, which reopens every past day for
everybody at once. A grant here reopens one day for one person, records who allowed it and why, and
expires whether it is used or not — so a forgotten exception is not a permanent hole in the rule.

Four things it refuses outright, because a grant that changes nothing is worse than no grant:
a date the window already allows, a date that has not happened, somebody outside the granting
manager's access scope, and the manager's own day. That last one is the point of the control: an
exception the same person both needs and approves is not an exception. Their own manager can grant
it, and widening the window is still available to an administrator and equally on the record.

Re-granting the same day extends the existing exception rather than stacking another row, so "what
is currently open" stays answerable. Revoking closes it early; entries already filed are untouched
either way.

## Getting a new joiner signed in

No temporary password is ever created, transmitted or stored. Creating an employee gives the account
a random secret nobody holds plus a single-use token valid for seven days; the person chooses their
own password on the reset page, so at no point does a password exist in an inbox, a chat message or
an administrator's memory.

With `NOTIFICATIONS_EMAIL_ENABLED=true` and `MAIL_HOST` set, the invite is emailed on creation. With
email off — the default — nothing is sent, and the **Re-send invite** action on the employee row
(`EMPLOYEE_MANAGE`) issues a fresh token and shows the administrator a link to pass on.

That the link is shown at all is deliberate rather than careless. Email can be off, misconfigured or
simply slow, and without a fallback the only remaining route is reading the token out of the
database by hand. It is restricted to `EMPLOYEE_MANAGE`, every issue is audited with who asked, and
re-issuing invalidates whatever was outstanding — so a link left in an old inbox stops working.
Anyone holding that permission could reset the account by other means already; the capability is not
new, only visible.

Accounts that predate this are brought into line by `V15`, under a deliberately narrow test —
never signed in, still owes a password, still holding an unspent invite. All three together describe
an account nobody can sign into today, so the backfill costs no one their access; the row simply
stops claiming otherwise.

Until the invite is used the account is `PENDING_ACTIVATION`, not `ACTIVE`, and the directory shows
that as an **Invite pending** chip beside the employment status — they are two different questions,
and showing only the second is what let a new joiner read as open while being unable to sign in at
all. Setting a password flips the account to `ACTIVE`; it will not, however, reinstate one somebody
deactivated, and an invite cannot be issued for a deactivated employee.

`ACCOUNT_INVITE` is the one template in the catalogue that is email-only, and necessarily so:
somebody who cannot sign in yet will never see an in-app notification. The other fifteen stay in-app
by default, so turning on a mail server does not silently start emailing everybody about everything.

## Where it goes next

The seven phases in the blueprint are done. What a first production deployment would want on top of
this, in the order it would want it:

- **A shared store for the rate limiter and the caches** once there is more than one instance —
  Redis is already in the deployment sketch for exactly this.
- **Testcontainers** for repository and migration tests. The 203 tests here are unit tests over
  service logic; the queries are exercised by hand against MySQL rather than in CI.
- **Load testing the report paths** with a year of real volume, which is when the row cap, the
  export threshold and the partitioning script stop being theory.
