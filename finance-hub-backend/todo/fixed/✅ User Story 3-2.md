# User Story 3-2: UI REST API 遮罩 - 全域隱私防護 (Global Data Masking)

**目標**：確保所有繼承 `MultiUserBaseEntity` 或 `PrivacyOverrideBaseEntity` 的領域模型在對外提供 API 查詢時，不會洩漏 `AGGREGATE_ONLY` / `PRIVATE` 的敏感文字，並統一套用 MapStruct 進行轉換以保護資料。

## 共通實作原則 (Common Implementation Guidelines)

幾乎所有 Mapper 都會共用以下標準的遮罩實作邏輯（請依各模組實際情況微調欄位與 MaskingType）：

1. 加入 `PrivacyMaskingUtil` 與 `SecurityUtil` 的 `import`。
2. 建立 `@AfterMapping` 攔截方法：例如 `default void applyPrivacyMasking(Entity entity, @MappingTarget Response response)`。
3. 呼叫 `SecurityUtil.getCurrentUserId()` 取得當前發送 Request 的使用者。
4. 使用 `PrivacyMaskingUtil.shouldMask(currentUserId, entity.getOwnerUserId(), entity.getPrivacyLevel())` 判斷是否需要進行隱私遮罩。（若為繼承父層的子明細，請改用 4 個參數的覆蓋判斷方法 `shouldMask(currentUserId, ownerUserId, itemPrivacyLevel, parentPrivacyLevel)`）。
5. 如果為 `true`，進行資料遮罩：
   - 將敏感的名稱欄位替換為：`PrivacyMaskingUtil.getMaskedName(PrivacyMaskingUtil.MaskingType.XXXX)`
   - 將敏感的描述、備註、或詳細資訊欄位設為 `null`。

## 實作拆解 (Tasks)

### ✅ Task 3-2-0: 擴充 PrivacyMaskingUtil 支援類型
- **目標檔案**: `PrivacyMaskingUtil.java`
- **實作內容**:
  1. 檢視現有的 `MaskingType` enum，擴充不足的領域類型（例如 `SALARY`, `MORTGAGE`, `PENSION`, `NISA`, `DC`, `COMPANY_STOCK`, `LIFE_EVENT`, `CAREER_EVENT`, `CATEGORY` 等）。
  2. 為每個新增的 Type 定義標準的預設遮罩名稱（例如 "Masked Salary", "Masked Mortgage" 等）。

### Task 3-2-1: Bank API 資料遮罩
- **目標檔案**: `BankTransactionMapper.java` (以及其他 Bank 相關 Mapper)
- **對應 Endpoint**: `GET /banks/transactions`, `GET /banks/balances` 等
- **實作內容**: 套用共通原則。MaskingType 設為 `BANK`，清空 Memo 欄位。

### Task 3-2-2: Credit Card API 資料遮罩
- **目標檔案**: `CreditCardTransactionMapper.java`
- **對應 Endpoint**: `GET /credit-cards/transactions`, `GET /credit-cards/total-amounts` 等
- **實作內容**: 套用共通原則。MaskingType 設為 `CREDIT_CARD`，清空 Memo 欄位。

### Task 3-2-3: Shopping & Reconciliation API 資料遮罩
- **目標檔案**: `ReconciliationMapper.java` (及其他購物關聯 Mapper)
- **對應 Endpoint**: `GET /reconciliation/amazon-orders/search`, `GET /reconciliation/unmatched`, `GET /reconciliation/{transactionId}/suggestions`
- **實作內容**: 針對 AmazonOrder 與 SuggestedMatch 等套用共通原則（注意子明細需用 4 參數的 shouldMask 進行覆蓋判斷）。MaskingType 設為對應商城 (如 `AMAZON`)。

### Task 3-2-4: Salary API 資料遮罩
- **目標檔案**: `SalaryRecordMapper.java`
- **對應 Endpoint**: `GET /salaries`
- **實作內容**: 套用共通原則。MaskingType 設為 `SALARY`，清空備註與詳細來源資訊。

### Task 3-2-5: Mortgage API 資料遮罩
- **目標檔案**: `MortgageRecordMapper.java`
- **對應 Endpoint**: `GET /mortgages`
- **實作內容**: 套用共通原則。MaskingType 設為 `MORTGAGE`，清空銀行名稱等敏感資訊。

### Task 3-2-6: NISA API 資料遮罩
- **目標檔案**: `NisaSnapshotMapper.java`, `NisaContributionSettingMapper.java`
- **對應 Endpoint**: `GET /assets/nisa`, `GET /assets/nisa/settings`, `GET /assets/nisa/projection`
- **實作內容**: 套用共通原則。MaskingType 設為 `NISA`，清空券商或自訂標籤。

### Task 3-2-7: Defined Contribution (DC) API 資料遮罩
- **目標檔案**: `DefinedContributionMapper.java`
- **對應 Endpoint**: `GET /assets/dc`
- **實作內容**: 套用共通原則。MaskingType 設為 `DC`，清空基金或帳戶自訂名稱。

### Task 3-2-8: Pension API 資料遮罩
- **目標檔案**: `PensionRecordMapper.java`
- **對應 Endpoint**: `GET /assets/pensions`, `GET /assets/pensions/projection`
- **實作內容**: 套用共通原則。MaskingType 設為 `PENSION`，清空年金詳細名稱。

### Task 3-2-9: Company Stock API 資料遮罩
- **目標檔案**: `CompanyStockMapper.java`
- **對應 Endpoint**: `GET /assets/company-stock/etrade/*`
- **實作內容**: 套用共通原則。MaskingType 設為 `COMPANY_STOCK`，清空公司名稱或股票代號。

### ~~Task 3-2-10: Life & Career Events API 資料遮罩~~ (Cancelled)
~~- **目標檔案**: `LifeEventMapper.java`, `CareerEventMapper.java`~~
~~- **對應 Endpoint**: `GET /simulation/life-events`, `GET /simulation/career-events`~~
~~- **實作內容**: 套用共通原則。MaskingType 設為 `LIFE_EVENT` / `CAREER_EVENT`，清空事件名稱與詳細描述。~~
> **Note**: 已取消實作。因為在「未來財務模擬」中，`AGGREGATE_ONLY` 隱藏事件名稱會導致圖表出現無法解釋的暴漲暴跌，失去模擬意義。且若設為 `PRIVATE`，底層 DB Filter 早已直接剃除資料，因此 Java 層不需要（也不應該）再做額外的 DTO 遮罩。

### Task 3-2-11: Category API 資料遮罩
- **目標檔案**: `CategoryMapper.java`
- **對應 Endpoint**: `GET /transaction-categories/*`
- **實作內容**: 套用共通原則。MaskingType 設為 `CATEGORY`，若為使用者自訂的分類且設定為 Private/Aggregate Only，則遮罩分類名稱。

### Task 3-2-12: 驗證與例外排除
- **驗證項目**: 
  - 確認 `mvn clean test` 順利通過。
  - 確認所有 Extension Controllers (如 `BankExtensionController`, `AmazonExtensionController`, `SofmapExtensionController`, `RakutenExtensionController` 等) 因為 `ExtensionSecurityInterceptor` 限定為個人存取（濾掉 `X-View-Scope: FAMILY`），無需實作遮罩邏輯。`BankExtensionController` 等 Extension API 是專供使用者的 Chrome 擴充功能同步自己的資料。基於資安考量，`ExtensionSecurityInterceptor` 會主動丟棄這些路徑的 `X-View-Scope: FAMILY` Header，強制套用個人資料過濾。這塊 API 只會拿到自己的資料，無需實作遮罩邏輯。
