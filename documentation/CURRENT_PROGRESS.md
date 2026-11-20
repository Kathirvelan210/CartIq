# Current progress

Milestones 1–3: COMPLETED and verified on live MySQL 8 and H2. Database `cartiq_db` and user `cartiq_user` provisioned on MySQL 8.0.44; Flyway migrations V1–V7 applied and verified with baseline 24 products, 27 variants, 7 categories, and 3 platforms. All 6 MySQL tests passed (`MysqlMilestone2Test`, `MysqlMilestone3Test`, `MysqlMilestone4Test`). Full H2 test run (93 tests) passed. Automatic product import V8/V9 code and safety tests verified on H2; V8/V9 held pending approval before MySQL application. Frontend dependencies installed; TypeScript checks passed (0 errors); frontend test suite passed (35/35). AI service Python environment initialized and passed 6/6 tests. 

Frontend-to-Backend Connectivity & CORS:
- Frontend (`http://localhost:8092`) is fully connected to the backend (`http://127.0.0.1:8080/api`).
- Enabled Spring Security CORS origin patterns allowing any local development port (`http://localhost:*`, `http://127.0.0.1:*`) with credential sharing.
- Added RootController at `GET /` which automatically redirects web browser visitors (HTTP 302) to the frontend at `http://localhost:8092`, and provides JSON API metadata for programmatic requests.
- Resolved `UNAUTHORIZED` (401) on root and unauthenticated routes, and `INTERNAL` (500) on unsupported HTTP methods.

All three application services are actively running concurrently:
- Spring Boot Backend: http://127.0.0.1:8080 (Health: UP, Root: redirects to frontend)
- FastAPI AI Service: http://127.0.0.1:8001 (Health: status ok)
- Expo Web Frontend: http://localhost:8092 (Metro bundler: HTTP 200 OK)

Milestones 4 (import against MySQL) and 5–10 remain pending.
