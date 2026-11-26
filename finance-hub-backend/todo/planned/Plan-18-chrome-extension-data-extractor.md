# Plan 18: Finance Extractor Chrome Extension 跨站點金融與薪資資料抽取擴充計畫 (High-Level)

## 🎯 背景與核心動機 (Background & Motivation)

目前系統的部分資料來源（如部分證券戶、SBI NISA、薪資單、退休金等）仍依賴手動另存 HTML/CSV 檔案並放置到伺服器或 Docker 容器掛載目錄中進行批次解析。

隨著系統向 **Plan 02（多使用者架構與家族共享）** 及 **Plan 03（家族財務服務與配偶現金流整合）** 推進，系統的目標是模擬整個家庭的真實財務生命週期。在實際家庭使用場景中：
1. **家庭成員使用門檻**：我們無法要求非技術背景的家人（配偶、長輩等）每次更新資料時，手動將網頁另存成檔案並上傳到 Docker 容器。這種操作模式極不友善，實務上難以長期維持。
2. **資料完整性保證**：透過 Chrome Extension 在使用者瀏覽金融或薪資入口時直接讀取已完成渲染的即時 DOM，能自然且根本地杜絕手動另存靜態 HTML 時因延遲載入（Lazy Loading）造成的殘缺快照問題。

因此，**打造輕量、一鍵式的 Chrome Extension 是落實多使用者與家族財務自動化的核心關鍵設施**。

---

## 🏛️ 高層架構設計 (High-Level Architecture)

本計畫以模組化、高擴充性為核心原則，採用 Chrome Extension Manifest V3 規範：

```
[家庭成員瀏覽各金融/薪資網站]
           │
           ▼ (即時渲染完成之 DOM / Client Response)
┌──────────────────────────────────────────────┐
│       Finance Extractor Chrome Extension      │
│  - 站點偵測 (URL Pattern Matching)            │
│  - 渲染完成檢查 (DOM Ready Guard)              │
│  - 模組化 Extractor 介面 (Data Extraction)    │
│  - 本地標準化轉換 (JSON Payload Normalization)│
└──────────────────────────────────────────────┘
           │
           ▼ (HTTPS / JWT Authenticated REST API)
┌──────────────────────────────────────────────┐
│           finance-hub-backend                │
│  - 租戶與家族成員身分校驗 (Family Multi-Tenant)│
│  - 直接寫入資料庫 / 更新帳戶歷史快照          │
└──────────────────────────────────────────────┘
```

### 1. 模組化 Extractor 介面設計
Extension 內部建立統一的抽取層抽象介面：
- `canHandle(url: string): boolean`：自動識別當前頁面所屬金融或薪資站點。
- `isReady(document: Document): boolean`：確認頁面動態資料或表格已完全渲染完畢。
- `extract(document: Document): Promise<NormalizedFinancePayload>`：將頁面資料解析並轉換為後端通用的 DTO 結構。

### 2. 安全同步與家族權限機制
- Extension 支援儲存後端 API Endpoint 與家庭成員個人的登入 Token（或 API Key）。
- 抽取後直接呼叫後端專屬的 Ingestion API，並自動綁定至該家庭成員之 `userId`，無縫銜接多使用者與家族隱私隔離規則。

---

## 🌐 支援站點策略 (Site Scope Strategy)

為保持規劃彈性，**本 Plan 不在此階段寫死所有目標站點的實作細節**：
- **涵蓋類型**：
  - 各類投資與證券帳戶（如 SBI NISA、一般特定帳戶等）
  - 薪資與所得明細系統（公司 Payroll / 給與明細等）
  - 退休與公的年金入口（如 ねんきんネット、企業 DC / iDeCo 入口等）
  - 其他家庭日常往來之銀行或金融機構
- **動態定案**：
  - 具體優先支援哪些網站、各網站的抽取欄位細節，留待實際啟動 Plan 18 時，根據家庭成員實際使用的金融機構清單進行盤點後，再依序排定先後順序實作。
