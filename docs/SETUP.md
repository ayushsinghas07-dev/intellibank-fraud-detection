# IntelliBank - Complete Setup & Execution Guide

This guide provides step-by-step instructions for setting up, building, and running **IntelliBank - Intelligent Banking Transaction & Fraud Detection System** on Windows, macOS, and Linux.

> **Note**: IntelliBank uses **MySQL 8/9** as its sole database engine. No external services (such as Firebase, AWS, or cloud SDKs) are required. All assets (Chart.js, Lucide icons, fonts) are self-hosted locally for 100% offline execution.

---

## 1. System Requirements & Prerequisites

| Requirement | Supported Version | Notes |
|---|---|---|
| **Operating System** | Windows 10/11, macOS, Linux | Cross-platform scripts provided |
| **Java Development Kit** | JDK 21 (LTS) | Ensure `JAVA_HOME` environment variable is set |
| **Database** | MySQL Server 8.0, 8.4, or 9.x | Running locally on port 3306 |
| **Build Tool** | Maven 3.8+ (or included `mvnw`) | Included in repo wrapper |

---

## 2. MySQL Database Setup

### Option A: Command Line Setup (Recommended)
1. Open Command Prompt or Terminal.
2. Log into MySQL shell:
   ```bash
   mysql -u root -p
   ```
3. Enter your MySQL root password when prompted.
4. Execute the schema and seed scripts:
   ```sql
   SOURCE C:/path/to/intellibank/database/schema.sql;
   ```

### Option B: MySQL Workbench Setup (Click-by-Click)
1. Open **MySQL Workbench** and connect to your local MySQL instance.
2. Click **File -> Open SQL Script...** and select `database/schema.sql`.
3. Click the **Lightning Bolt (Execute)** button to execute the schema.
4. Verify that `intellibank_db` appears under the **Schemas** list with 10 tables.

---

## 3. Configuration Setup (`config.properties`)

Create or edit `config.properties` in the root project directory (`./intellibank/config.properties`). This file is git-ignored and should contain your local environment credentials:

```properties
server.port=8085
db.url=jdbc:mysql://localhost:3306/intellibank_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
db.user=root
db.password=12345
db.pool.maxSize=15
db.pool.minIdle=5
jwt.secret=c3VwZXJfc2VjdXJlX2p3dF9zZWNyZXRfa2V5X2Zvcl9pbnRlbGxpYmFua19iMnJfYXBwbGljYXRpb25fMjAyNl9zZWN1cmU=
jwt.expiry.minutes=120
login.max.attempts=5
login.lockout.minutes=15
```

### HikariCP Connection Pool Architecture
Connection pooling is managed by `com.intellibank.config.DatabaseConfig`. On startup, HikariCP initializes an optimized connection pool configured with:
- `maximumPoolSize=15`
- `minimumIdle=5`
- `prepStmtCacheSize=250`
- `useServerPrepStmts=true`

---

## 4. Database Seeding

To initialize the 120+ customers, 186 accounts, and 1,520 transactions over 90 days:

### Command Line Seeder Flag:
```bash
java -jar target/intellibank.jar --seed
```
Or via Maven wrapper:
```bash
./mvnw compile exec:java
```

---

## 5. Building & Running the Application

### Option A: One-Command Scripts
- **Windows**: Double-click `run.bat` or run:
  ```cmd
  run.bat
  ```
- **macOS / Linux**:
  ```bash
  chmod +x run.sh mvnw
  ./run.sh
  ```

### Option B: Manual Terminal Build & Run
```bash
# 1. Clean and package executable fat JAR
./mvnw clean package

# 2. Start the embedded Jetty 11 server
java -jar target/intellibank.jar
```

Once started, open your web browser to:
**`http://localhost:8085/`**

---

## 6. Demo Login Credentials

The seed script creates 3 ready-to-use demo accounts for testing Role-Based Access Control (RBAC):

| Role | Username | Password | Privileges & Permissions |
|---|---|---|---|
| **System Administrator** | `admin` | `Admin@12345` | Full access (User management, rule configuration, audit log) |
| **Lead Fraud Analyst** | `analyst` | `Analyst@12345` | Transactions, customer details, alert investigation & resolution |
| **Compliance Auditor** | `viewer` | `Viewer@12345` | Read-only audit access (Dashboard, Analytics, Reports) |

---

## 7. Troubleshooting Guide

### 1. `ERROR 1045 (28000): Access denied for user 'root'@'localhost'`
- **Cause**: Incorrect MySQL password in `config.properties`.
- **Fix**: Update `db.password` in `config.properties` to match your local MySQL root password.

### 2. `Communications link failure / Connection refused`
- **Cause**: MySQL service is not running.
- **Fix**: Open Windows Services (`services.msc`) and start `MySQL80` or `MySQL90`. On Linux: `sudo systemctl start mysql`.

### 3. `java.io.IOException: Failed to bind to 0.0.0.0:8085`
- **Cause**: Port 8085 is already in use by another application.
- **Fix**: Change `server.port` in `config.properties` to an open port (e.g. `8088` or `9090`).

### 4. `JAVA_HOME is not set`
- **Cause**: Environment variable `JAVA_HOME` is not pointing to JDK 21.
- **Fix**: Set `JAVA_HOME=C:\Program Files\Java\jdk-21.0.11` in Environment Variables.

### 5. Blank Dashboard / Zero Row Counts
- **Cause**: Database seeder was not executed after schema creation.
- **Fix**: Run `java -jar target/intellibank.jar --seed`.
