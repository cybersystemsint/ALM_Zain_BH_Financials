# Asset Lifecycle Management (ALM) System - Financial Reports & Inventory Management

## 📋 Project Overview

The **Asset Lifecycle Management (ALM) System** is a comprehensive Spring Boot microservice designed to manage asset financial records, inventory synchronization, depreciation calculations, and approval workflows for the Telkom organization across multiple regions (Bahrain, KSA, and other regions).

This service provides a centralized platform for:
- **Financial Report Management**: Track asset financial details including cost, depreciation, and write-offs
- **Inventory Synchronization**: Sync equipment data from multiple sources (Huawei dumps, active/passive inventory)
- **Approval Workflows**: Multi-level approval process (L1, L2, L3) for asset modifications and deletions
- **Depreciation Calculation**: Automated monthly depreciation computation based on asset lifecycle
- **Asset Reconciliation**: Identify missing, unmapped, or duplicate assets across inventory sources
- **Export & Reporting**: Generate comprehensive reports in CSV/Excel formats with filtering and search capabilities

**Organization**: Telkom (Kenya/Bahrain)  
**Version**: 0.0.1-SNAPSHOT  
**Status**: Active Development  

---

## 🏗️ Architecture Overview

### Technology Stack

| Component | Technology |
|-----------|-----------|
| **Framework** | Spring Boot 2.4.6 |
| **Language** | Java 15 |
| **Database** | MySQL 5.7+ |
| **ORM** | Hibernate/JPA |
| **Build Tool** | Maven 3.6+ |
| **Deployment** | Apache Tomcat (WAR) |
| **Cache** | Redis (Jedis 3.6.3) |
| **Email** | Spring Mail |
| **Export Formats** | CSV (OpenCSV), Excel (Apache POI) |

### Project Structure

```
alm_zain_bh_financials/
├── src/main/java/com/telkom/co/ke/almoptics/
│   ├── controllers/              # REST API Controllers
│   ├── services/                 # Business Logic Services
│   ├── serviceImplementor/       # Service Implementations
│   ├── entities/                 # JPA Entity Models
│   ├── models/                   # DTOs and Models
│   ├── dto/                      # Data Transfer Objects
│   ├── repository/               # Data Access Layer (Repositories)
│   ├── schedulers/               # Scheduled Tasks
│   ├── configs/                  # Configuration Classes
│   └── utilities/                # Utility Classes
├── src/main/resources/
│   ├── application.properties    # Application Configuration
│   └── logback-spring.xml        # Logging Configuration
└── pom.xml                       # Maven Dependencies
```

---

## 📦 Core Modules & Responsibilities

### 1. **Financial Report Management**
**Controllers**: `FinancialReportController`, `FinancialReportUploadController`  
**Services**: `FinancialReportService`, `FinanceReportFetchService`, `FinancialReportExport`

**Capabilities**:
- Create, read, update, delete financial reports
- Bulk upload financial data via Excel/CSV
- Apply date range and column-based filtering
- Export to CSV/Excel with advanced options
- Track asset financial status (approval, depreciation, write-off)

**Key Endpoints**:
```
POST    /api/financial/upload              - Upload financial reports
GET     /api/financial/reports             - Fetch reports with filters
POST    /api/financial/fetch-financereport - Advanced search with export
PUT     /api/financial/reports/modify/{id} - Modify financial report
```

---

### 2. **Approval Workflow Management**
**Controllers**: `FinanceApprovalController`, `FinanceApprovals`  
**Services**: `ApprovalWorkflowService`, `ApprovalReportFetchService`

**Capabilities**:
- Three-level approval workflow (L1, L2, L3)
- Support for multiple approval types: pending addition, modification, deletion, movement
- Workflow action: approve, reject, cancel
- Audit trail with original state JSON backup
- Complete approval history tracking

**Approval Types**:
- **Pending Addition**: New asset approval
- **Pending Modification**: Asset detail changes
- **Pending Deletion**: Asset decommissioning
- **Pending Movement**: Asset write-off process

**Key Endpoints**:
```
POST    /finance-approval/fetch-approvals           - Fetch pending approvals by team
POST    /finance-approval/fetch-approvals-history   - Fetch completed approvals (NEW)
POST    /finance-approval/approve                   - Approve workflow items
POST    /finance-approval/reject                    - Reject workflow items
POST    /finance-approval/cancel                    - Cancel workflow items
```

**Workflow Status**:
- `Pending L1 Approval` → `Pending L2 Approval` → `Pending L3 Approval` → `Approved`
- `Pending L1 Approval` → `REJECTED` (at any level)
- `Pending L*` → `CANCELLED` (by requester or admin)

---

### 3. **Inventory Management & Synchronization**
**Controllers**: `InventoryMappingController`, `AssetSyncController`  
**Services**: `InventorySyncService`, `UnmappedInventoryService`, `AssetSyncService`, `ReconciliationSyncService`

**Capabilities**:
- Sync assets from multiple sources (Huawei dumps, active/passive inventory, IT assets)
- Track unmapped inventory items
- Identify and report missing/duplicate assets
- Reconcile inventory across systems
- Support three inventory types: Active, Passive, IT

**Inventory Categories**:
- **Active Inventory**: Running network/IT equipment
- **Passive Inventory**: Non-active assets (cables, passive components)
- **IT Inventory**: Computers, servers, networking equipment

**Key Endpoints**:
```
POST    /api/inventory/sync                - Synchronize inventory from dumps
GET     /api/inventory/unmapped            - Fetch unmapped inventory items
POST    /api/financial/fetch-unmappedactive   - Search unmapped active inventory
POST    /api/financial/fetch-unmappedpassive  - Search unmapped passive inventory
POST    /api/financial/fetch-unmappedit        - Search unmapped IT inventory
```

---

### 4. **Depreciation Management**
**Controllers**: `DepreciationManualController`  
**Services**: `DepreciationService`, `tb_Asset_DepreciationService`

**Capabilities**:
- Automated monthly depreciation calculation
- Support multiple depreciation methods
- Track accumulated depreciation
- Manual depreciation adjustments
- Scheduled depreciation runs

**Key Endpoints**:
```
POST    /api/depreciation/calculate       - Calculate monthly depreciation
GET     /api/depreciation/report          - Fetch depreciation reports
POST    /api/depreciation/manual-adjust   - Manual depreciation adjustment
```

---

### 5. **Write-Off Report Management**
**Services**: `WriteOffReportService`, `WriteOffReportFetchService`

**Capabilities**:
- Generate write-off reports for decommissioned assets
- Track asset decommissioning status
- Support FAR (Fixed Asset Register) reporting

**Key Endpoints**:
```
GET     /api/writeoff/reports             - Fetch write-off reports
POST    /api/writeoff/export              - Export write-off data
```

---

### 6. **Audit & Compliance**
**Services**: `AuditLogService`

**Capabilities**:
- Log all approval workflow status changes
- Track user actions with timestamps
- Original state JSON backup for modifications
- Comprehensive audit trail for compliance

**Log Locations**:
- File-based: `/home/app/logs/ALM/Assets/alm-audit.log`
- Database: `tb_AuditLog` (optional, disabled by default)

---

### 7. **Scheduled Tasks & Background Processing**
**Schedulers**: `DepreciationScheduler`, `FinancialSyncScheduler`  
**Services**: `MyAsyncService`, `SyncOrchestratorService`

**Scheduled Operations**:
- Monthly depreciation calculation
- Periodic asset synchronization (300 seconds interval)
- Missing asset detection
- Inventory reconciliation

**Configuration**:
```properties
alm.fetch_scheduler=300000              # Fetch scheduler interval (ms)
```

---

## 🗄️ Database Schema Overview

### Core Entities

| Entity | Purpose |
|--------|---------|
| `tb_FinancialReport` | Financial details of assets (cost, depreciation, write-off) |
| `tb_ApprovalWorkflow` | Workflow records for pending/completed approvals |
| `tb_Asset_Depreciation` | Monthly depreciation calculations |
| `tb_AuditLog` | Audit trail of all actions |
| `tb_Item` | Asset items and SKUs |
| `tb_FarReport` | Fixed Asset Register reports |
| `WriteOffReport` | Write-off asset records |
| `ActiveInventory` | Running equipment inventory |
| `PassiveInventory` | Non-active assets inventory |
| `ItInventory` | IT equipment inventory |
| `UnmappedActiveInventory` | Unmapped active assets |
| `UnmappedPassiveInventory` | Unmapped passive assets |
| `UnmappedITInventory` | Unmapped IT assets |
| `NELicense` | Network Element licenses |
| `NotificationSetting` | User notification preferences |

### Key Fields in tb_FinancialReport

```
- id, assetName, assetSerialNumber
- initialCost, monthlyDepreciationAmount
- accumulatedDepreciation, netCost, salvageValue
- poNumber, poDate, faCategory
- statusFlag (NEW/EXISTING/DECOMMISSIONED)
- financialApprovalStatus (Pending/Approved/Rejected)
- siteId, zone, nodeType
- originalState (JSON backup of previous state)
- writeOffDate, insertDate, changeDate
```

### Key Fields in tb_ApprovalWorkflow

```
- ID, PROCESS_ID
- ASSET_ID, OBJECT_TYPE
- ORIGINAL_STATUS (pending addition/modification/deletion/movement)
- UPDATED_STATUS (Pending L1/L2/L3 Approval, Approved, Rejected, Cancelled)
- COMMENTS, INSERTEDBY, CHANGEDBY
- INSERTDATE, CHANGEDATE
```

---

## 🔌 API Endpoints Overview

### Financial Reports

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/financial/upload` | Upload financial reports (Excel/CSV) |
| GET | `/api/financial/reports` | Fetch reports with pagination |
| POST | `/api/financial/fetch-financereport` | Advanced search with export (CSV/Excel) |
| PUT | `/api/financial/reports/modify/{identifier}` | Modify report and create approval workflow |
| DELETE | `/api/financial/reports/{id}` | Delete report |

### Approvals

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/finance-approval/approvals` | List pending approvals by team |
| POST | `/finance-approval/fetch-approvals` | Advanced approval search with export |
| POST | `/finance-approval/fetch-approvals-history` | Fetch completed approvals (Approved/Rejected) |
| POST | `/finance-approval/approve` | Approve workflow items |
| POST | `/finance-approval/reject` | Reject workflow items |
| POST | `/finance-approval/cancel` | Cancel workflow items |

### Inventory

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/inventory/sync` | Synchronize inventory from Huawei dumps |
| GET | `/api/inventory/unmapped` | List unmapped inventory items |
| POST | `/api/financial/fetch-unmappedactive` | Search unmapped active inventory |
| POST | `/api/financial/fetch-unmappedpassive` | Search unmapped passive inventory |
| POST | `/api/financial/fetch-unmappedit` | Search unmapped IT inventory |

### Depreciation

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/depreciation/calculate` | Calculate monthly depreciation |
| GET | `/api/depreciation/report` | Fetch depreciation reports |

---

## 🔐 Data Flow & Processing

### Asset Modification Workflow

```
1. User submits modification request
   ↓
2. Endpoint: PUT /api/financial/reports/modify/{identifier}
   ↓
3. Create Approval Workflow (status: Pending L1 Approval)
   ↓
4. Original state saved as JSON in originalState field
   ↓
5. L1 Approver reviews → Approve/Reject/Cancel
   ↓
6. If Approved → L2 Approver reviews
   ↓
7. If Approved → L3 Approver reviews
   ↓
8. If Approved → 
   - Set financialApprovalStatus = "Approved"
   - Clear originalState (no longer needed)
   - Update financial report with new values
   - Clean up unmapped inventory if "pending addition"
   ↓
9. Audit log created, notifications sent
```

### Approval History Query

```
Request: POST /finance-approval/fetch-approvals-history
   ↓
Query: SELECT * FROM tb_ApprovalWorkflow
       WHERE UPDATED_STATUS IN ('Approved', 'Rejected')
       AND CHANGEDATE BETWEEN startDate AND endDate
   ↓
Result: Complete audit trail of all completed approvals
```

### Inventory Synchronization

```
1. Source: Huawei dumps (RAN equipment data)
          OR Active/Passive/IT Inventory files
   ↓
2. Parse CSV/XML data
   ↓
3. Try to match with tb_FinancialReport by:
   - Serial Number
   - Asset Name
   - IMEI
   ↓
4. If Match Found → Update mapping
   ↓
5. If No Match → Create UnmappedInventory record
   ↓
6. Report discrepancies
```

---

## ⚙️ Configuration & Setup

### Prerequisites

- **Java**: JDK 15+
- **Maven**: 3.6+
- **MySQL**: 5.7+
- **Tomcat**: 9.0+ (for WAR deployment)
- **Redis**: Optional (for caching)

### Database Setup

```sql
-- Create database
CREATE DATABASE ALM_ZAIN_BH CHARACTER SET utf8mb4;

-- Create user and grant privileges
CREATE USER 'almuser'@'localhost' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON ALM_ZAIN_BH.* TO 'almuser'@'localhost';
FLUSH PRIVILEGES;
```

### Configuration Files

**application.properties** - Key configurations:

```properties
# Database
spring.datasource.url=jdbc:mysql://77.68.67.55:3306/ALM_ZAIN_BH
spring.datasource.username=root
spring.datasource.password=ALMDev@2025!
spring.jpa.hibernate.ddl-auto=none

# Connection Pooling (HikariCP)
spring.datasource.hikari.maximum-pool-size=30
spring.datasource.hikari.idle-timeout=30000

# Logging
logging.file.name=/home/app/logs/ALM/Assets/AssetManagement.log

# Audit
audit.persist.db=false

# Scheduler
alm.fetch_scheduler=300000

# Dumps Location
dumps.file.location=/home/telkom/ALM/Dumps/RAN/Huawei/Inventory_dumps/
```

### Build & Deployment

```bash
# Build
mvn clean install

# Run locally
mvn spring-boot:run

# Deploy WAR to Tomcat
cp target/alm_zain_bh_financials.war $CATALINA_HOME/webapps/

# Restart Tomcat
$CATALINA_HOME/bin/shutdown.sh
$CATALINA_HOME/bin/startup.sh
```

---

## 📊 Key Features & Capabilities

### ✅ Financial Report Management
- Create, update, delete financial records
- Bulk import via Excel/CSV upload
- Track asset lifecycle (NEW → EXISTING → DECOMMISSIONED)
- Automatic depreciation calculation
- Support for multiple asset categories

### ✅ Multi-Level Approval Workflow
- Three-level approval (L1, L2, L3)
- Support for additions, modifications, deletions, movements
- Approval history tracking
- Original state JSON backup
- Comprehensive audit trail

### ✅ Inventory Synchronization
- Sync from multiple sources (Huawei dumps, active/passive/IT inventory)
- Unmapped inventory tracking
- Missing asset detection
- Automatic reconciliation
- Bulk import/export

### ✅ Advanced Filtering & Search
- Multi-column filtering
- Date range queries
- Operator support (EQUALS, CONTAINS, STARTS_WITH, ENDS_WITH, IS_EMPTY)
- Flexible query builder
- Pagination support

### ✅ Export Capabilities
- CSV export with streaming
- Excel export with formatting
- Cursor-based pagination for large datasets
- Batch processing
- Compression support

### ✅ Audit & Compliance
- Complete audit logging
- JSON state snapshots
- User action tracking
- Timestamp recording
- Approval history

### ✅ Scheduled Operations
- Monthly depreciation runs
- Periodic synchronization
- Background asset reconciliation
- Configurable intervals
- Async processing

---

## 🔧 Development Guidelines

### Code Organization

**Controllers**: Handle HTTP requests/responses, routing
**Services**: Business logic, calculations, orchestration
**Repositories**: Database access, queries
**Entities**: JPA models, database mapping
**DTOs**: Data transfer objects for requests/responses
**Models**: Domain models and helper classes

### Transaction Management

All approval workflow modifications use `@Transactional`:
- Ensures atomicity (all-or-nothing)
- Automatic rollback on error
- Proper exception handling

### Exception Handling

```java
try {
    // Business logic
} catch (IllegalStateException e) {
    logger.error("Workflow validation failed", e);
    return ResponseEntity.status(HttpStatus.CONFLICT).body(...);
} catch (Exception e) {
    logger.error("Unexpected error", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(...);
}
```

### Logging Standards

```java
logger.info("Operation started. Asset: {}, User: {}", assetId, username);
logger.debug("Processing batch: {}", batchSize);
logger.warn("Potential issue detected: {}", warningMessage);
logger.error("Critical error occurred", exception);
```

### Performance Considerations

- **Pagination**: Use for large result sets (default: 100 per page)
- **Indexing**: Ensure indexes on frequently queried columns
- **Batch Processing**: Use batch operations for bulk updates
- **Caching**: Redis for frequently accessed data
- **Streaming**: Use streaming for large exports

---

## 🐛 Troubleshooting

### Common Issues

**Issue**: Database connection timeout
- **Check**: Database is running and accessible
- **Solution**: Verify connection string and credentials in application.properties

**Issue**: Approval workflow stuck in "Pending L1"
- **Check**: L1 approver permissions and workflow status
- **Solution**: Check tb_ApprovalWorkflow status and manually intervene if needed

**Issue**: Export file is very large
- **Check**: Date range and applied filters
- **Solution**: Narrow date range or apply more specific filters

**Issue**: Unmapped inventory not syncing
- **Check**: Dumps file location and format
- **Solution**: Verify file exists and format matches expected CSV/XML structure

---

## 📈 Performance Metrics

### Expected Performance

| Operation | Time (ms) | Scale |
|-----------|-----------|-------|
| Fetch 100 reports | < 500 | Small |
| Filtered search | < 1000 | Medium |
| Approval workflow create | < 100 | Real-time |
| CSV export (10K records) | < 3000 | Large |
| Excel export (10K records) | < 5000 | Large |
| Monthly depreciation run | < 30000 | System-wide |

### Resource Limits

```properties
# Connection Pool
maximum-pool-size=30
minimum-idle=5
max-lifetime=1800000 (30 minutes)

# Batch Processing
asset.sync.batch-size=100
export.batch-size=100000

# Memory
Xms=512m (min heap)
Xmx=2048m (max heap)
```

---

## 📝 Documentation & References

### Additional Documentation Files

- **APPROVAL_WORKFLOW_COMPLETION_ANALYSIS.md** - Detailed approval process
- **FETCH_APPROVALS_HISTORY_ENDPOINT.md** - History endpoint documentation
- **FETCH_APPROVALS_HISTORY_QUICK_TEST.md** - Test cases and examples
- **IMPLEMENTATION_SUMMARY_FETCH_APPROVALS_HISTORY.md** - Implementation details
- **POSTMAN_COLLECTION_APPROVAL_HISTORY.json** - Postman test collection

### Related Files

- Column filtering fixes documentation
- PUT endpoint test documentation
- Financial report export analysis
- Unmapped inventory configuration

---

## 🎯 Future Enhancements

### Planned Features
- [ ] Dashboard analytics and visualizations
- [ ] Real-time approval notifications
- [ ] Advanced depreciation methods
- [ ] Mobile application support
- [ ] API rate limiting
- [ ] OAuth 2.0 authentication
- [ ] Advanced reporting and BI integration
- [ ] Automated invoice matching
- [ ] Asset linking and relationships
- [ ] Multi-currency support

### Known Limitations
- Single-region database (region-specific instances)
- Email notifications disabled (can be enabled)
- No real-time synchronization (batch-based)
- Manual approval process (can be automated)

---

## 📞 Support & Contact

**Development Team**: ALM Development Team  
**Organization**: ALM(Kenya/Bahrain)  
**Issue Tracking**: GitHub Issues  
**Documentation**: See additional markdown files in project root

### Reporting Issues

When reporting issues, include:
1. Environment (Dev/Staging/Production)
2. Steps to reproduce
3. Expected vs actual behavior
4. Error logs and stack traces
5. Request/response payloads

---

## 📄 License & Copyright

**Copyright**: Telkom (Kenya)  
**Version**: 0.0.1-SNAPSHOT  
**Status**: Active Development  

---

## 🙏 Acknowledgments

**Developed by**: ALM Development Team  
**Supported by**: ALM Support Team  
**Database**: ALM Development Team 
**Deployment**: ALM Development Team 
---

## 📋 Changelog

### Version 0.0.1-SNAPSHOT (Current)
- ✅ Core financial report management
- ✅ Three-level approval workflow
- ✅ Inventory synchronization
- ✅ Depreciation calculations
- ✅ Advanced filtering and export
- ✅ Approval history endpoint
- ✅ Unmapped inventory tracking
- ✅ Audit logging
- ✅ Write-off reports

### Scheduled for Next Release
- [ ] Dashboard improvements
- [ ] Additional reporting features
- [ ] Performance optimizations
- [ ] Enhanced security

---

**Last Updated**: May 2, 2026  
**Maintained By**: ALM Development Team  
**Status**: Active
