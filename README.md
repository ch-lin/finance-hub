# Finance Hub (Core Backend)

![Java](https://img.shields.io/badge/Java-25%2B-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green)
![Architecture](https://img.shields.io/badge/Architecture-Microservice-blueviolet)
![Maven](https://img.shields.io/badge/Build-Maven-blue)
![Docker](https://img.shields.io/badge/Docker-Container-2496ED)

The **Finance Hub** is the central nervous system ("The Brain") for your personal accounting and finance management platform.

It serves as the primary backend API for the **Finance-Hub-UI**, manages the core business logic (accounting records, shopping reconciliations, retirement projections), orchestrates data parsing tasks, and provides a unified interface for data injected from browser extensions (e.g., Bank statements, Amazon orders, Rakuten, Sofmap).

## 🏗️ Architecture & Role

*   **The "Brain"**: Centralizes state management. It knows your bank balances, credit card transactions, NISA/DC/Pension snapshots, and categorizes your expenses.
*   **Extension Webhooks**: Provides secure endpoints for the Chrome Extension to automatically push order history and statement data directly into the database.
*   **Reconciliation Engine**: Automatically links credit card transactions to shopping orders (e.g., matching a Visa charge to an Amazon purchase).
*   **AI Categorization**: Leverages AI tools to automatically categorize unknown or uncategorized expenses.

### Design Philosophy
*   **Source of Truth**: The Hub's database is the single source of truth for all your financial states.
*   **Extensibility**: Designed to easily add new parsers for different banks or shopping platforms.

## 🚀 Key Features

*   **Shopping & Bank Integrations**: Automated data injection from Amazon, Rakuten, Sofmap, and major banks via Chrome Extensions.
*   **Retirement & Investment Tracking**: Tracks NISA, iDeCo/Corporate DC, and Pension records with future projection capabilities.
*   **Smart Reconciliation & Categorization**: Hierarchical category management (with essential/non-essential flags) and an interactive UI to manually or semi-automatically link credit card charges to itemized shopping receipts.
*   **AI-Powered Categorization**: Export and import uncategorized transactions for AI-assisted classification.
*   **Advanced Financial Planning**:
    *   **Dynamic Salary Modeling**: Project future income using an automated baseline and custom career milestones (e.g., job changes, promotions).
    *   **Life Events Planning**: Incorporate large, one-time future expenses (e.g., mortgage payoff, education funds) into your lifelong financial timeline.
    *   **Dynamic Expense Calculation**: Automatically estimate your essential living expenses from historical, categorized transaction data.
    *   **Investment Goal Seeking**: Dynamically adjust monthly investment contributions (e.g., NISA) and expected return rates to reverse-calculate the requirements for maintaining a secure lifelong asset balance.
    *   **Interactive Dashboard**: Visualize the unified lifelong asset water-level chart combining salaries, pensions, investments, and life events.
*   **Reporting**: Generate Markdown summary reports of your financial health.

## 🗺️ Project Roadmap

- **Phase 1: Basic Accounting & Extensions** [✅ Completed]
  - Core data models, Chrome Extension webhooks, and AI categorization.
- **Phase 2: Assets & Pensions** [✅ Completed]
  - E-Trade stock tracking, NISA, iDeCo/DC, and basic pension tracking.
- **Phase 3: Long-Term Asset Projection & Goal Seeking** [✅ Completed]
  - Dynamic salary modeling with career milestones.
  - Life events planning for major future expenses.
  - Dynamic baseline expenses calculation via transaction reconciliation.
  - Reverse goal-seeking for investments (e.g., NISA contribution limits).
  - Interactive dashboard for lifetime asset water-level visualization.
- **Phase 4: Multi-Tenant Architecture** [🚧 Next Up]
  - Multi-user support, family accounts, and shared resources.

## 🛠️ Tech Stack

*   **Language**: Java (25+)
*   **Framework**: Spring Boot 3.5
*   **Database**: MySQL (Core Data)
*   **Build Tool**: Maven

## 📦 Prerequisites & Dependencies

Since this project was split from a mono-repo, it depends on the shared **Platform** library.

1.  **Platform Library**: You must build and install the `Platform` project locally first.
    ```bash
    cd ../Platform
    mvn clean install
    ```
2.  **Database**: A MySQL instance.

## ⚙️ Configuration

Create a `.env` file in the root directory. You can copy `.env.example` if available.

```properties
# Server Configuration
SERVER_PORT=8080

# Database
DB_URL=jdbc:mysql://localhost:3306/finance_hub
DB_USERNAME=root
DB_PASSWORD=secret
```

## 🏃‍♂️ Build & Run

### Local Development

```bash
# 1. Build the project
cd finance-hub-backend
mvn clean package

# 2. Run the JAR
java -jar hub/target/finance-hub-0.0.1-SNAPSHOT.jar
```

## 🔌 API Endpoints (Overview)

Here is the list of currently exposed REST endpoints in the system, organized by feature domains:

## Banking & Credit Cards
| Method | Path | Controller | Handler | Description |
|---|---|---|---|---|
| POST | `/bank/extension` | BankExtensionController.java | `receiveStatementFromExtension` | Receives and processes a bank statement payload from the Chrome Extension. |
| GET | `/bank/extension/{bankName}/{statementMonth}` | BankExtensionController.java | `getStatement` | Retrieves an existing bank statement for the Chrome Extension. |
| GET | `/banks/balances` | BankTransactionController.java | `getBalances` | Retrieves the latest balances for all banks. |
| GET | `/banks/transactions` | BankTransactionController.java | `getTransactions` | Retrieves a paginated list of bank transactions filtered by given criteria. |
| GET | `/credit-cards` | CreditCardTransactionController.java | `getAvailableCardNames` | Retrieves a list of all available credit card names. |
| GET | `/credit-cards/total-amounts` | CreditCardTransactionController.java | `getTotalAmounts` | Retrieves the total amounts grouped by card name for a given period. |
| GET | `/credit-cards/transactions` | CreditCardTransactionController.java | `getTransactions` | Retrieves a paginated list of credit card transactions matching the given criteria. |

## Income & Expenses
| Method | Path | Controller | Handler | Description |
|---|---|---|---|---|
| GET | `/expenses` | ExpenseController.java | `getAllExpenses` | Retrieves a paginated list of all aggregated expenses. |
| GET | `/expenses/available-sources` | ExpenseController.java | `getAvailableSources` | TODO: Add description |
| GET | `/expenses/{id}/details` | ExpenseController.java | `getExpenseDetails` | Retrieves detailed order information for a specific expense item. |
| GET | `/salaries` | SalaryRecordController.java | `getAllRecords` | Retrieves all salary records sorted by payment date in descending order. |

## Assets & Portfolios
| Method | Path | Controller | Handler | Description |
|---|---|---|---|---|
| GET | `/assets/dc` | DefinedContributionController.java | `getAllRecords` | Retrieves all DC snapshot records sorted by record date in descending order. |
| POST | `/assets/dc/extension` | DefinedContributionController.java | `receiveDcFromExtension` | TODO: Add description |
| PATCH | `/assets/dc/funds/{fundId}/return-rate` | DefinedContributionController.java | `updateReturnRate` | Updates the expected return rate for a specific DC fund snapshot. |
| POST | `/assets/dc/upload` | DefinedContributionController.java | `uploadFile` | Uploads and parses a new DC HTML file. |
| GET | `/assets/nisa` | NisaSnapshotRecordController.java | `getAllRecords` | Retrieves all NISA snapshot records sorted by record date in descending order. |
| GET | `/assets/nisa/projection` | NisaSnapshotRecordController.java | `getProjection` | TODO: Add description |
| GET | `/assets/pensions` | PensionRecordController.java | `getAllRecords` | Retrieves all pension records. |
| POST | `/assets/pensions` | PensionRecordController.java | `createOrUpdateRecord` | Creates a new pension record or updates an existing one. |
| GET | `/assets/pensions/projection` | PensionRecordController.java | `getProjection` | Calculates future pension projections. |
| DELETE | `/assets/pensions/{id}` | PensionRecordController.java | `deleteRecord` | Deletes a pension record by its ID. |
| GET | `/assets/company-stock/etrade` | CompanyStockController.java | `getAllSnapshots` | Retrieves all company stock snapshots. |
| GET | `/assets/company-stock/etrade/cash-transactions` | CompanyStockController.java | `getCashTransactions` | TODO: Add description |
| GET | `/assets/company-stock/etrade/espp` | CompanyStockController.java | `getEspp` | TODO: Add description |
| POST | `/assets/company-stock/etrade/extension` | CompanyStockController.java | `receiveETradeFromExtension` | Upload a parsed E-Trade JSON payload to update the snapshot. |
| GET | `/assets/company-stock/etrade/lots` | CompanyStockController.java | `getLots` | TODO: Add description |
| GET | `/assets/company-stock/etrade/rsu-events` | CompanyStockController.java | `getRsuEvents` | TODO: Add description |
| GET | `/assets/company-stock/etrade/rsu-grants` | CompanyStockController.java | `getRsuGrants` | TODO: Add description |
| GET | `/assets/company-stock/etrade/rsu-vest-schedules` | CompanyStockController.java | `getRsuVestSchedules` | TODO: Add description |
| POST | `/assets/company-stock/etrade/upload` | CompanyStockController.java | `uploadETradeFile` | Upload a holdings.html file to update the current company stock asset snapshot. |

## Categorization & AI
| Method | Path | Controller | Handler | Description |
|---|---|---|---|---|
| GET | `/ai-categorization/export` | AiCategorizationController.java | `exportUncategorizedItems` | Exports uncategorized items as a CSV file. |
| POST | `/ai-categorization/import` | AiCategorizationController.java | `importCategorizedItems` | Imports categorized items from an uploaded CSV file. |
| GET | `/transaction-categories` | CategoryController.java | `getAllCategories` | Retrieves all available transaction categories. |
| POST | `/transaction-categories` | CategoryController.java | `createCategory` | Creates a new transaction category. |
| DELETE | `/transaction-categories/{id}` | CategoryController.java | `deleteCategory` | Deletes a transaction category by its ID. |
| GET | `/transaction-categories/{id}` | CategoryController.java | `getCategoryById` | Retrieves a specific transaction category by its ID. |
| PUT | `/transaction-categories/{id}` | CategoryController.java | `updateCategory` | Updates an existing transaction category. |

## Reconciliation & Shopping Extensions
| Method | Path | Controller | Handler | Description |
|---|---|---|---|---|
| GET | `/reconciliation/amazon-orders/search` | ReconciliationController.java | `searchUnmatchedAmazonOrders` | TODO: Add description |
| POST | `/reconciliation/link` | ReconciliationController.java | `linkTransaction` | TODO: Add description |
| GET | `/reconciliation/unmatched` | ReconciliationController.java | `getUnmatchedTransactions` | TODO: Add description |
| GET | `/reconciliation/{transactionId}/suggestions` | ReconciliationController.java | `getSuggestions` | TODO: Add description |
| POST | `/amazon/extension` | AmazonExtensionController.java | `receiveOrderFromExtension` | Receives and processes an Amazon order payload from the Chrome Extension. |
| POST | `/rakuten/extension` | RakutenExtensionController.java | `receiveOrderFromExtension` | Receives and processes a Rakuten order payload from the Chrome Extension. |
| POST | `/sofmap/extension` | SofmapExtensionController.java | `receiveOrderFromExtension` | Receives and processes a Sofmap order payload from the Chrome Extension. |
| GET | `/sofmap/extension/{orderId}` | SofmapExtensionController.java | `getOrderForExtension` | Retrieves an existing Sofmap order with all details for the Chrome Extension. |

## Asset Projection & Life Event Simulation
| Method | Path | Controller | Handler | Description |
|---|---|---|---|---|
| GET | `/simulation/career-events` | CareerEventController.java | `getEvents` | TODO: Add description |
| POST | `/simulation/career-events` | CareerEventController.java | `createEvent` | TODO: Add description |
| DELETE | `/simulation/career-events/{id}` | CareerEventController.java | `deleteEvent` | TODO: Add description |
| GET | `/simulation/asset-simulation/{targetAge}` | AssetSimulationController.java | `runSimulation` | Runs the lifelong asset projection simulation up to the target age. |
| GET | `/simulation/life-events` | LifeEventController.java | `getEvents` | TODO: Add description |
| POST | `/simulation/life-events` | LifeEventController.java | `createEvent` | TODO: Add description |
| DELETE | `/simulation/life-events/{id}` | LifeEventController.java | `deleteEvent` | TODO: Add description |

## Background Tasks & Scheduler
| Method | Path | Controller | Handler | Description |
|---|---|---|---|---|
| DELETE | `/tasks/deletion` | DataCleanupController.java | `cleanUp` | Deletes all transactional data processed by the Finance Hub. |
| POST | `/tasks/scheduler/start` | SchedulerController.java | `startScheduler` | Starts the scheduled background jobs for auto-processing financial data. |
| GET | `/tasks/scheduler/status` | SchedulerController.java | `getSchedulerStatus` | Retrieves the current status of the global task scheduler. |
| POST | `/tasks/scheduler/stop` | SchedulerController.java | `stopScheduler` | Stops the scheduled background jobs. |
| GET | `/tasks/{transactionSource}/history` | JobHistoryController.java | `getJobHistory` | Retrieves the paginated job history for a given transaction source. |
| POST | `/tasks/{transactionSource}/process` | TransactionSourceTaskController.java | `processData` | Initiates asynchronous data processing for a specific transaction source. |
| GET | `/tasks/{transactionSource}/status` | TransactionSourceTaskController.java | `getProcessStatus` | Checks if a processing job is currently running for the specified transaction source. |
| POST | `/tasks/{transactionSource}/upload` | TransactionSourceTaskController.java | `uploadFile` | Uploads and processes a financial data file for a specific transaction source. |

## System & Configurations
| Method | Path | Controller | Handler | Description |
|---|---|---|---|---|
| GET | `/health` | HealthCheckController.java | `healthCheck` | Provides a health check endpoint for the main backend service. |
| DELETE | `/configs/system` | FinanceHubConfigController.java | `deleteAllConfigs` | Deletes all global configurations. |
| GET | `/configs/system` | FinanceHubConfigController.java | `getAllConfigs` | Retrieves the names of all available global configurations and identifies which one is currently enabled. |
| POST | `/configs/system` | FinanceHubConfigController.java | `createConfig` | Creates a new global configuration. |
| DELETE | `/configs/system/{name}` | FinanceHubConfigController.java | `deleteConfig` | Deletes a specific global configuration by its name. |
| GET | `/configs/system/{name}` | FinanceHubConfigController.java | `getConfig` | Retrieves a specific global configuration by its name. |
| PATCH | `/configs/system/{name}` | FinanceHubConfigController.java | `saveConfig` | Creates or partially updates a specific global configuration. |
| DELETE | `/configs/transaction-sources` | SchedulingConfigController.java | `deleteAllConfigs` | Deletes all hub configurations. |
| GET | `/configs/transaction-sources/supported` | SchedulingConfigController.java | `getSupported` | Retrieves a list of all transaction sources that currently have an active executor strategy registered in the system. |
| GET | `/configs/transaction-sources/timezones` | SchedulingConfigController.java | `getTimeZones` | Retrieves a list of clean and sorted time zones. |
| GET | `/configs/transaction-sources/types` | SchedulingConfigController.java | `getTypes` | Retrieves all available transaction sources. |
| GET | `/configs/transaction-sources/{transactionSource}` | SchedulingConfigController.java | `getAllConfigs` | Retrieves the names of all available hub configurations and identifies which one is currently enabled. |
| POST | `/configs/transaction-sources/{transactionSource}` | SchedulingConfigController.java | `createConfig` | Creates a new hub configuration. |
| DELETE | `/configs/transaction-sources/{transactionSource}/{name}` | SchedulingConfigController.java | `deleteConfig` | Deletes a specific hub configuration by its name. |
| GET | `/configs/transaction-sources/{transactionSource}/{name}` | SchedulingConfigController.java | `getConfig` | Retrieves a specific hub configuration by its name. |
| PATCH | `/configs/transaction-sources/{transactionSource}/{name}` | SchedulingConfigController.java | `saveConfig` | Creates or partially updates a specific hub configuration. |
| GET | `/configs/transaction-sources/{transactionSource}/{name}/resolved` | SchedulingConfigController.java | `getResolvedConfig` | Resolves and returns the appropriate hub configuration. |

## Reports
| Method | Path | Controller | Handler | Description |
|---|---|---|---|---|
| GET | `/reports/summary` | ReportController.java | `downloadSummaryReport` | Generates and downloads a summary report in Markdown format. |

## 📜 License

Proprietary - All Rights Reserved.
