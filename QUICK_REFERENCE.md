# ALM Service - Quick Reference Guide

## 🚀 Quick Start

### 1. Build the Project
```bash
cd /path/to/alm_zain_bh_financials
mvn clean install
```

### 2. Run Locally
```bash
mvn spring-boot:run
```

### 3. Access Application
```
Base URL: http://localhost:8080
```

### 4. Deploy to Tomcat
```bash
cp target/alm_zain_bh_financials.war $CATALINA_HOME/webapps/
```

---

## 📚 Module Quick Links

| Module | Controllers | Main Services | Purpose |
|--------|-------------|---------------|---------|
| **Financial Reports** | `FinancialReportController` | `FinancialReportService`, `FinanceReportFetchService` | Asset financial data management |
| **Approvals** | `FinanceApprovalController`, `FinanceApprovals` | `ApprovalWorkflowService` | Multi-level workflow approvals |
| **Inventory** | `InventoryMappingController`, `AssetSyncController` | `InventorySyncService`, `UnmappedInventoryService` | Inventory sync and tracking |
| **Depreciation** | `DepreciationManualController` | `DepreciationService` | Asset depreciation calculations |
| **Write-offs** | - | `WriteOffReportService` | Asset decommissioning |

---

## 🔌 API Endpoint Quick Reference

### Financial Reports
```
POST   /api/financial/upload
GET    /api/financial/reports
POST   /api/financial/fetch-financereport
PUT    /api/financial/reports/modify/{identifier}
DELETE /api/financial/reports/{id}
```

### Approvals
```
POST /finance-approval/approvals                    (Pending approvals by team)
POST /finance-approval/fetch-approvals              (Advanced search)
POST /finance-approval/fetch-approvals-history      (Completed approvals)
POST /finance-approval/approve                      (Approve items)
POST /finance-approval/reject                       (Reject items)
POST /finance-approval/cancel                       (Cancel items)
```

### Inventory
```
POST /api/inventory/sync
GET  /api/inventory/unmapped
POST /api/financial/fetch-unmappedactive
POST /api/financial/fetch-unmappedpassive
POST /api/financial/fetch-unmappedit
```

### Depreciation
```
POST /api/depreciation/calculate
GET  /api/depreciation/report
```

---

## 💾 Database Quick Reference

### Most Important Tables

| Table | Purpose | Key Fields |
|-------|---------|-----------|
| `tb_FinancialReport` | Asset financial records | assetName, initialCost, statusFlag, financialApprovalStatus |
| `tb_ApprovalWorkflow` | Approval workflow tracking | ASSET_ID, UPDATED_STATUS, INSERTEDBY, CHANGEDBY |
| `tb_Asset_Depreciation` | Monthly depreciation | assetId, depreciationAmount, month |
| `tb_AuditLog` | Audit trail | objectId, previousStatus, newStatus, notes |
| `UnmappedActiveInventory` | Unmapped active assets | serialNumber, elementId, type |
| `UnmappedPassiveInventory` | Unmapped passive assets | serial, objectId, elementType |
| `UnmappedITInventory` | Unmapped IT equipment | hostSerialNumber, elementId, hostname |

---

## 🔄 Common Request Payloads

### 1. Fetch Financial Reports with Filters
```json
{
  "page": 0,
  "size": 100,
  "startDate": "2026-04-01",
  "endDate": "2026-04-30",
  "filters": [
    {
      "column": "ASSET_SERIAL_NUMBER",
      "operator": "EQUALS",
      "value": "SN123456"
    }
  ],
  "format": "xlsx",
  "exportAll": false
}
```

### 2. Fetch Pending Approvals by Team
```json
{
  "page": 0,
  "size": 100,
  "team": "Financial L1"
}
```

### 3. Fetch Approval History
```json
{
  "page": 0,
  "size": 100,
  "startDate": "2026-01-01",
  "endDate": "2026-04-30",
  "filters": [
    {
      "column": "UPDATED_STATUS",
      "operator": "EQUALS",
      "value": "Approved"
    }
  ]
}
```

### 4. Modify Financial Report
```json
{
  "id": 515,
  "initialCost": 50000.00,
  "assetSerialNumber": "NEW-SERIAL-123",
  "siteId": "SITE001"
}
```

### 5. Approve Workflow Items
```json
{
  "items": [1, 2, 3],
  "comment": "Approved - all documents verified",
  "username": "approver_l3"
}
```

---

## 🔍 Common Search Queries

### Find Asset by Serial Number
```json
{
  "filters": [
    {
      "column": "ASSET_SERIAL_NUMBER",
      "operator": "EQUALS",
      "value": "LP-2026-001"
    }
  ]
}
```

### Find All Rejected Approvals in Last 30 Days
```json
{
  "updatedStatus": "REJECTED",
  "startDate": "2026-03-28",
  "endDate": "2026-04-28"
}
```

### Find Approvals by Specific Approver
```json
{
  "filters": [
    {
      "column": "UPDATER",
      "operator": "EQUALS",
      "value": "approver_l3"
    }
  ]
}
```

### Find Assets Containing "LAPTOP"
```json
{
  "filters": [
    {
      "column": "ASSET_ID",
      "operator": "CONTAINS",
      "value": "LAPTOP"
    }
  ]
}
```

### Export All Modifications from Q1 2026
```json
{
  "format": "xlsx",
  "exportAll": true,
  "startDate": "2026-01-01",
  "endDate": "2026-03-31",
  "originalStatus": "pending modification"
}
```

---

## 🛠️ Common Development Tasks

### Add New Approval Status Filter
1. Edit `ApprovalWorkflowService.buildApprovalFilters()`
2. Add new case in filter builder
3. Test with test payloads
4. Update documentation

### Add New Financial Report Column
1. Add field to `tb_FinancialReport` entity
2. Run database migration
3. Update export headers in service
4. Add to filter columns
5. Test export functionality

### Add New Scheduled Task
1. Create new scheduler class with `@Scheduled` annotation
2. Add to `configs/Constants.java`
3. Update `application.properties` with interval
4. Add to startup logging in `AlmOpticsApplication`

### Add New REST Endpoint
1. Add method to appropriate Controller
2. Add corresponding service method
3. Add logging (info level for entry/exit)
4. Handle exceptions properly
5. Add to this quick reference
6. Update main README

---

## 📝 Logging & Debugging

### Log Locations
```
Application Log: /home/app/logs/ALM/Assets/AssetManagement.log
Audit Log:       /home/app/logs/ALM/Assets/alm-audit.log
```

### Enable Debug Logging
Edit `application.properties`:
```properties
logging.level.com.telkom.co.ke.almoptics=DEBUG
logging.level.org.hibernate.SQL=DEBUG
```

### View Recent Logs
```bash
tail -f /home/app/logs/ALM/Assets/AssetManagement.log
tail -100 /home/app/logs/ALM/Assets/alm-audit.log
```

### Common Log Patterns
```
[INFO]  Workflow ID {} approved to status: {}
[WARN]  Asset is in approval workflow pending approvals
[ERROR] Failed to find Financial Report for ASSET_ID: {}
[DEBUG] Generated candidate PROCESS_ID: {}
```

---

## ✅ Common Testing Scenarios

### Test 1: Full Approval Workflow
1. Create financial report
2. Submit for modification (creates workflow)
3. L1 approves → status: Pending L2
4. L2 approves → status: Pending L3
5. L3 approves → status: Approved, report updated

### Test 2: Rejection at L2
1. Create financial report
2. Submit for modification
3. L1 approves
4. L2 rejects → status: REJECTED
5. Report reverted to original state

### Test 3: Export with Filters
1. Fetch reports with date range filter
2. Select export format (CSV/Excel)
3. Set exportAll: true for large datasets
4. Verify file downloaded
5. Check data accuracy

### Test 4: Inventory Sync
1. Place Huawei dumps file in configured location
2. Trigger sync endpoint
3. Monitor logs for progress
4. Verify unmapped inventory creation
5. Check asset linking

### Test 5: Multi-Column Filter
1. Apply multiple filter conditions
2. Use different operators (EQUALS, CONTAINS, etc.)
3. Combine with date range
4. Verify result accuracy
5. Test pagination

---

## 🚨 Troubleshooting Checklist

| Issue | Check | Solution |
|-------|-------|----------|
| Connection timeout | DB running? | Verify DB host/port in application.properties |
| Workflow stuck | Status in DB | Manually update UPDATED_STATUS in table |
| Export too slow | Row count | Reduce date range or add filters |
| Unmapped inventory empty | File location | Check dumps.file.location in properties |
| Permission denied | Logs | Verify Tomcat user has write access to log directory |
| Out of memory | Heap size | Increase Xmx parameter in JAVA_OPTS |
| Missing workflow | Asset ID | Verify asset exists with correct ID format |

---

## 🔐 Important Configuration Keys

| Key | Default | Purpose |
|-----|---------|---------|
| `spring.datasource.url` | localhost | Database connection URL |
| `spring.jpa.hibernate.ddl-auto` | none | Schema auto-update (never use create/create-drop in prod) |
| `dumps.file.location` | /home/telkom/ALM/Dumps | Huawei dumps file location |
| `alm.fetch_scheduler` | 300000 | Sync interval in milliseconds |
| `audit.persist.db` | false | Write audit to DB (disabled by default) |
| `logging.file.name` | /home/app/logs/ALM | Application log file path |

---

## 📊 Performance Tuning Tips

### Database Optimization
```sql
-- Create indexes on frequently queried columns
CREATE INDEX idx_financial_report_asset ON tb_FinancialReport(assetName);
CREATE INDEX idx_workflow_status ON tb_ApprovalWorkflow(UPDATED_STATUS);
CREATE INDEX idx_workflow_date ON tb_ApprovalWorkflow(CHANGEDATE);
```

### Connection Pool Settings
```properties
spring.datasource.hikari.maximum-pool-size=30
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.idle-timeout=30000
spring.datasource.hikari.max-lifetime=1800000
```

### JVM Tuning
```bash
# In Tomcat catalina.sh
JAVA_OPTS="$JAVA_OPTS -Xms512m -Xmx2048m -XX:+UseG1GC"
```

---

## 🎯 Key Concepts

### Approval Workflow Status Flow
```
Pending L1 Approval ──(L1 Approves)──> Pending L2 Approval
       ↓ (L1 Rejects)                        ↓ (L2 Rejects)
    REJECTED                            REJECTED
                                            ↓ (L2 Approves)
                                      Pending L3 Approval
                                            ↓ (L3 Approves)
                                          Approved
                                            ↓ (L3 Rejects)
                                          REJECTED
```

### Asset Status Flags
- **NEW**: Recently added asset (< 30 days)
- **EXISTING**: Established asset (>= 30 days)
- **DECOMMISSIONED**: Asset marked for deletion/write-off

### Approval Types
- **pending addition**: New asset awaiting approval
- **pending modification**: Asset changes awaiting approval
- **pending deletion**: Asset decommissioning awaiting approval
- **pending movement**: Asset write-off awaiting approval

### Filter Operators
- **EQUALS**: Exact match (case-insensitive)
- **CONTAINS**: Substring match (case-insensitive)
- **STARTS_WITH**: Prefix match (case-insensitive)
- **ENDS_WITH**: Suffix match (case-insensitive)
- **IS_EMPTY**: Null or empty string
- **IS_NOT_EMPTY**: Not null and not empty

---

## 📞 Getting Help

### Resources
- README.md - Comprehensive system documentation
- FETCH_APPROVALS_HISTORY_ENDPOINT.md - Approval history API docs
- FETCH_APPROVALS_HISTORY_QUICK_TEST.md - Test examples
- POSTMAN_COLLECTION_APPROVAL_HISTORY.json - Postman requests
- Application logs - Detailed error information

### Common Commands

```bash
# Build project
mvn clean install

# Run tests
mvn test

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"

# Check for dependencies
mvn dependency:tree

# Format code
mvn spotless:apply

# Analyze logs
grep "ERROR" /home/app/logs/ALM/Assets/AssetManagement.log
grep "Workflow ID" /home/app/logs/ALM/Assets/alm-audit.log
```

---

## 🎓 Learning Path

### For New Developers
1. Read README.md (overview)
2. Review core entities (tb_FinancialReport, tb_ApprovalWorkflow)
3. Study main controllers (FinancialReportController, FinanceApprovalController)
4. Trace one API call end-to-end
5. Run local tests

### For DevOps/Deployment
1. Review application.properties
2. Check database prerequisites
3. Understand logging configuration
4. Review deployment checklist
5. Set up monitoring

### For QA/Testing
1. Review QUICK_TEST_GUIDE.md
2. Study test payloads
3. Run manual test cases
4. Verify expected behaviors
5. Document findings

---

**Last Updated**: May 2, 2026  
**For Questions**: Contact ALM Development Team
