# IntelliBank Interaction Matrix Verification Log

Every interactive component across all 12 modules in IntelliBank has been executed, tested against the backend REST API & MySQL database, and verified PASS.

| Module | Component / Element | Action / Event | REST API Endpoint | Expected Behavior | Status |
|---|---|---|---|---|---|
| **Login** | Username / Password Input | Form Submit | `POST /api/auth/login` | Validates BCrypt hash, sets JWT, updates login audit log | **PASS** |
| **Login** | Demo User Chips | Click | Local JS | Pre-fills admin, analyst, or viewer credentials | **PASS** |
| **Shell** | Nav Items (Dashboard, Customers, etc.) | Click | Hash Router (`#/...`) | Renders SPA module view, updates active highlight | **PASS** |
| **Shell** | Theme Toggle | Click | Local JS | Toggles `.dark-mode` CSS variables & persists in localStorage | **PASS** |
| **Shell** | Global Search Trigger | Ctrl+K / Click | `GET /api/search?q=...` | Opens search modal, queries across 4 database tables | **PASS** |
| **Shell** | Notification Bell | Click | `GET /api/notifications` | Opens notification center, shows unread badge counter | **PASS** |
| **Shell** | Logout Button | Click | `POST /api/auth/logout` | Clears JWT & user session, redirects to login | **PASS** |
| **Dashboard** | 8 KPI Cards | Render / Click | `GET /api/analytics` | Displays real-time aggregated metrics from MySQL | **PASS** |
| **Dashboard** | Priority Review Queue | Click Item | `GET /api/fraud/alerts` | Backed by `RiskPriorityQueue` (max-heap) by risk score | **PASS** |
| **Dashboard** | Recent Suspicious Table | Click Row | `GET /api/transactions` | Opens transaction detail drawer | **PASS** |
| **Customers** | Add Customer Modal | Form Submit | `POST /api/customers` | Validates input, creates customer record | **PASS** |
| **Customers** | Search & Filters | Input / Click | `GET /api/customers` | Performs server-side pagination & filter search | **PASS** |
| **Customers** | Deactivate Customer | Click Action | `POST /api/customers/{id}/deactivate` | Safety check: blocks if non-zero balance or pending alert | **PASS** |
| **Accounts** | Deposit Button | Form Submit | `POST /api/accounts/deposit` | Executes deposit pipeline, updates balance atomically | **PASS** |
| **Accounts** | Withdraw Button | Form Submit | `POST /api/accounts/withdraw` | Executes withdrawal pipeline, enforces daily limit & balance | **PASS** |
| **Accounts** | Transfer Button | Form Submit | `POST /api/accounts/transfer` | `SELECT FOR UPDATE` ascending lock, debit & credit in 1 DB txn | **PASS** |
| **Accounts** | Freeze / Unfreeze | Click Action | `POST /api/accounts/{id}/freeze` | Updates account status in DB | **PASS** |
| **Transactions**| Search & Date Filters | Filter Submit | `GET /api/transactions` | Server-side filtered pagination query | **PASS** |
| **Transactions**| Export CSV Button | Click Link | `GET /api/reports/transactions/export-csv` | Streams formatted CSV file with proper escaping | **PASS** |
| **Transactions**| Row Click | Click Row | `GET /api/risk/analyze/{id}` | Opens drawer showing "WHY WAS THIS FLAGGED?" evidence cards | **PASS** |
| **Fraud Alerts**| Status Tabs (Open, Review, etc.) | Click Tab | `GET /api/fraud/alerts` | Filters alerts by status and displays status counts | **PASS** |
| **Fraud Alerts**| False Positive Resolution | Click Button | `POST /api/fraud/alerts/{id}/status` | Releases held funds in DB transaction, re-validating balance | **PASS** |
| **Fraud Alerts**| Confirm Fraud Resolution | Click Button | `POST /api/fraud/alerts/{id}/status` | Keeps transaction BLOCKED, optional customer account freeze | **PASS** |
| **Fraud Alerts**| Add Analyst Note | Form Submit | `POST /api/fraud/alerts/{id}/notes` | Appends note to investigation log | **PASS** |
| **Risk Analysis**| What-If Simulator | Form Submit | `POST /api/risk/simulate` | Evaluates 10 rules transiently with ZERO DB mutations | **PASS** |
| **Risk Analysis**| Rules Config Panel | Form Submit | `PUT /api/fraud/rules/{code}` | Admin updates rule points, enable flag, and thresholds | **PASS** |
| **Analytics** | Date Range Controls | Select Change | `GET /api/analytics` | Aggregates volume, value, channel & risk distribution | **PASS** |
| **Notifications**| Mark All as Read | Click | `POST /api/notifications/read-all` | Updates read_state in notifications table | **PASS** |
| **Reports** | Download CSV Buttons | Click Link | `GET /api/reports/*/export-csv` | Downloads Customers, Accounts, Transactions, Alerts CSVs | **PASS** |
| **Audit Logs** | Filter & Pagination | Change | `GET /api/audit-log` | Displays audit log trail with user, action, timestamp | **PASS** |
| **User Mgmt** | Create System User | Form Submit | `POST /api/users` | Admin creates user with BCrypt password hash | **PASS** |
| **User Mgmt** | User Status Toggle | Click Action | `POST /api/users/{id}/status` | Self-deactivation and self-demotion safety checks enforced | **PASS** |
