# Finance Hub (Core Backend)

![Java](https://img.shields.io/badge/Java-25%2B-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green)
![Architecture](https://img.shields.io/badge/Architecture-Clean%20%2F%20Multi--Tenant-blueviolet)
![Maven](https://img.shields.io/badge/Build-Maven-blue)
![Docker](https://img.shields.io/badge/Docker-Container-2496ED)

The **Finance Hub** is the central nervous system ("The Brain") for your personal and family accounting, investment tracking, and lifelong financial simulation platform.

It serves as the primary backend API for **Hub UI**, enforces Japanese statutory tax/pension/mortgage rules inside a pure-function simulation engine, orchestrates multi-format document ingestion, and provides secure multi-tenant isolation with family sharing and privacy masking.

## 🏗️ Architecture & Role

The backend follows a 4-module Clean Architecture (`hub-common`, `hub-domain`, `hub-app`, `hub`):

*   **Source of Truth (Fact vs. Settings Separation)**: Factual brokerage/bank snapshots are stored separately from user-defined simulation settings (such as DC/iDeCo target allocations, contribution overrides, and profile assumptions) so re-imports never wipe out user preferences.
*   **Multi-Tenant & Family Privacy**: Built on `MultiUserBaseEntity`, Hibernate tenant filtering (`ownerFilter`), OAuth2 JWT authentication, `FamilyGroup` sharing, and 3-tier `PrivacyLevel` (`PRIVATE`, `AGGREGATE_ONLY`, `SHARED`) with automatic DTO masking (`PrivacyMaskingUtil`) controlled via `X-View-Scope`.
*   **Pure-Function Simulation Core**: Repository access is isolated in context builders (`SimulationContextFactory`); yearly pipelines, tax engines, and calculators operate strictly on immutable/isolated context DTOs for deterministic and high-throughput optimization runs.
*   **Ingestion & Reconciliation**: Supports Chrome Extension webhooks, manual file uploads (HTML, PDF, Excel), and transaction-to-receipt reconciliation.

## 🚀 Key Features

### Accounting, Ingestion & Reconciliation
*   **Multi-Platform Ingestion**: Supports Bank statements, Credit Card transactions, Amazon, Rakuten, Sofmap, E-Trade (ESPP / RSU / lots), SBI NISA, and Sompo/SBI DC & iDeCo via Chrome Extensions and file parsers.
*   **Document Parsing**: Extracts Japanese Nenkin (pension) statements using **Apache PDFBox** and exchange rate sheets using **Apache POI**.
*   **Smart Reconciliation & Categories**: Hierarchical transaction categories (with essential/non-essential flags) and reconciliation linking credit card charges to itemized shopping orders.

### Assets, Liabilities & Portfolio Management
*   **NISA Tracking**: Enforces separate statutory sub-limits for Growth (¥2.4M/yr) and Tsumitate (¥1.2M/yr) quotas alongside the ¥18M lifetime cap, plus active regular contribution plan tracking.
*   **DC / iDeCo Management**: Account-level batch allocation updates with strict 100% sum validation, `BigDecimal` floor rounding, and designated remainder-fund absorption (`remainderFundCode`), paired with independent user target allocations for simulation scenarios.
*   **Mortgages, Pensions, Insurance & Company Stock**: Tracks existing and future mortgages (including bonus payments and scheduled early repayments), pension accrual, insurance policies, and E-Trade stock holdings.

### Japanese Statutory Tax, Social Insurance & Education Modeling
*   **Year-Aware Tax Strategy Engine**: Automatically switches statutory rules across simulation years (Legacy ≤2024, Reiwa 7 / 2025, Reiwa 8–9 / 2026–2027, and Reiwa 10+ / 2028+) for both **Basic Deduction** (`BasicDeductionStrategyFactory`, including high-income inhabitant tax phase-out between ¥24M–¥25M) and **Employment Income Deduction** (`EmploymentIncomeDeductionStrategyFactory`).
*   **Split Social Insurance Statutory Caps**: Applies separate statutory caps for monthly base salary and bonus payments (Health Insurance: ¥1.39M/mo & ¥5.73M/yr bonus; Kousei Pension: ¥650k/mo & ¥1.5M/payment bonus).
*   **Mortgage Tax Deduction (住宅ローン控除)**: Year- and residence-type-aware deduction strategies (including 2026+ energy-saving housing ¥140k cap, combined per-residence caps across co-existing loans, and `InhabitantTaxLimitStrategy` enforcing 7%/¥136.5k pre-2022 vs. 5%/¥97.5k from 2022).
*   **Retirement Income Deduction (退職所得控除)**: Ceiling-rounded service year calculation (`TaxDateUtils`) in accordance with NTA rules for DC/iDeCo lump-sum withdrawals.
*   **Configurable Rules & Monthly Education Model**: Database-backed Pension Rule configurations (e.g., Reiwa 7 full basic pension ¥831,700) and monthly-granularity child education cost projections across school track transitions.

### Lifelong Financial Simulation & Global Optimizer
*   **Lifelong Cashflow & Net Worth Projection**: Combines dynamic salary modeling, career milestones, life events, retirement-year segmented tax & year-end adjustments, mortgages, child education, pensions, and tax-advantaged accounts.
*   **Global Optimizer**: Hybrid **Grid Search + Genetic Algorithm (GA)** engine to optimize retirement timing, investment contributions, and mortgage early repayment strategies under bankruptcy-avoidance constraints.

### AI-Assisted Categorization & Reporting
*   **AI Agent Categorization Workflow**: Currently exports uncategorized transactions as CSV for external AI Agent classification and re-imports categorized results; planned to evolve into direct AI Agent API / MCP integration.
*   **Financial Reports**: Generates downloadable Markdown summary reports of your financial state.

## 🗺️ Project Roadmap

- **Phase 1: Basic Accounting, Reconciliation & Extensions** `[✅ Completed]`
  - Core accounting models, shopping/bank webhooks, hierarchical categories, and CSV-based AI categorization workflow.
- **Phase 2: Assets, Investments & Pensions** `[✅ Completed]`
  - E-Trade stock tracking, NISA, Corporate DC / iDeCo, mortgages, insurance policies, and pension records.
- **Phase 3: Long-Term Asset Projection & Global Optimizer** `[✅ Completed]`
  - Lifelong cashflow simulation, career/life events, dynamic baseline expense estimation, and Hybrid Grid Search / GA optimization.
- **Phase 4: Multi-Tenant Architecture, Family Sharing & Privacy Masking** `[✅ Completed]`
  - `MultiUserBaseEntity`, Hibernate tenant filtering, JWT OAuth2 integration, family groups, and 3-tier privacy masking.
- **Phase 5: Japanese Statutory Compliance & DC Allocation Modeling** `[✅ Completed]`
  - Multi-year tax reform strategies (Reiwa 7–10+), split social insurance caps, 2026+ mortgage tax deduction & inhabitant tax limit strategies, configurable pension rules, monthly education cost modeling, and DC/iDeCo floor+remainder allocation & target allocation settings.
- **Phase 6: Direct AI Integration & Next-Gen Capabilities** `[🚧 Planned]`
  - **Direct AI Agent Integration (API / MCP)**: Replace manual CSV export/import with direct AI Agent API / Spring AI MCP integration for automated categorization and interactive financial queries.
  - **Family Financial Simulator**: Spouse income, pension, and asset cashflow integration (Sandbox & Link modes).
  - **DC/iDeCo Sandbox Target Allocation Execution**: Apply user target allocations inside what-if sandbox overrides and auto-invalidate stale settings on lineup changes.
  - **Item-Level Privacy Write Support & Chrome Extractor V3**: Fine-grained per-item privacy controls and unified Manifest V3 browser extraction.
  - **Inhabitant Tax June-Switch Lightweight Engine**: Prior-year June tax switching without full monthly simulation overhead.

## 🛠️ Tech Stack

*   **Language**: Java 25+ (Virtual Threads enabled)
*   **Framework**: Spring Boot 3.5 (WebMVC, Data JPA, Security OAuth2 Resource Server, Validation)
*   **Database**: MySQL (Core Data)
*   **Mapping & Boilerplate**: MapStruct, Lombok
*   **API Documentation**: Springdoc OpenAPI (Swagger UI)
*   **Parsing**: Jsoup (HTML), Apache PDFBox (PDF), Apache POI (Excel)
*   **AI Integration**: Spring AI MCP Server Starter (WebMVC)
*   **Build & Deployment**: Maven, Docker

## 📦 Prerequisites & Dependencies

Since this project was split from a mono-repo, it depends on the shared **Platform** library.

1.  **Platform Library**: Build and install the `Platform` project locally first.
    ```bash
    cd ../Platform
    mvn clean install
    ```
2.  **Database**: A MySQL instance.

## ⚙️ Configuration

Create a `.env` file in the root directory (or override properties in `hub/src/main/resources/application.properties`):

```properties
# Server Configuration (default local dev port: 8085)
SERVER_PORT=8085

# Database
DB_URL=jdbc:mysql://localhost:3308/financehub
DB_USERNAME=root
DB_PASSWORD=secret
```

## 🏃‍♂️ Build & Run

### Local Development

```bash
# 1. Build the project
cd finance-hub-backend
mvn clean package

# 2. Run the packaged JAR (output directory configured to ../bin)
java -jar ../bin/finance-hub.jar
```

## 🔌 API Documentation (Swagger / OpenAPI)

Because REST endpoints evolve continuously across domains, the authoritative, always-up-to-date API reference is served directly via **Springdoc OpenAPI / Swagger UI** when the service is running:

*   **Swagger UI**: `http://localhost:8085/swagger-ui.html`
*   **OpenAPI JSON**: `http://localhost:8085/v3/api-docs`

*(In local development, `app.security.swagger-public=true` exposes Swagger UI without requiring a JWT.)*

## 📜 License

Proprietary - All Rights Reserved.
