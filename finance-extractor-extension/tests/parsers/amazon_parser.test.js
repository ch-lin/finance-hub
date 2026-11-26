const fs = require('fs');
const path = require('path');

describe('amazon_parser.js', () => {
  beforeEach(() => {
    // Reset DOM
    document.body.innerHTML = '';
    window.financeHubParser = undefined;

    // Load parser script using require for coverage
    jest.resetModules();
    require('../../parsers/amazon_parser.js');
  });

  it('parses a standard Amazon order correctly', () => {
    document.body.innerHTML = `
      <div data-component="orderId"><span>250-1234567-8901234</span></div>
      <div data-component="orderDate"><span>2024年5月10日</span></div>
      
      <!-- Shipments -->
      <div class="a-box">
        <div data-component="shipmentStatus">2024年5月12日にお届け</div>
        配送業者: Yamato
        トラッキングID: 123456789012
      </div>

      <!-- Items -->
      <div data-component="purchasedItems">
        <div class="a-fixed-left-grid">
          <div data-component="itemTitle"><a href="/dp/B08N5M7S6K">Test Item 1</a></div>
          <div data-component="orderedMerchant">販売: Amazon</div>
          <div data-component="unitPrice"><span class="a-offscreen">￥1,500</span></div>
          <div data-component="quantity"><span>2</span></div>
        </div>
      </div>

      <!-- Order Summary -->
      <div data-component="orderSummary">
        <div class="od-line-item-row">
          <div class="od-line-item-row-label">商品の小計:</div>
          <div class="od-line-item-row-content"><span>￥3,000</span></div>
        </div>
        <div class="od-line-item-row">
          <div class="od-line-item-row-label">配送料・手数料:</div>
          <div class="od-line-item-row-content"><span>￥500</span></div>
        </div>
        <div class="od-line-item-row">
          <div class="od-line-item-row-label">割引:</div>
          <div class="od-line-item-row-content"><span>-￥200</span></div>
        </div>
        <div class="od-line-item-row">
          <div class="od-line-item-row-label">ご請求額:</div>
          <div class="od-line-item-row-content"><span>￥3,300</span></div>
        </div>
        
        <!-- Payment -->
        <div class="a-box">
            <div class="od-payment-header">支払い方法</div>
            Visa 末尾 1234
        </div>
      </div>
    `;

    const result = window.financeHubParser.parse();
    
    expect(result.orderId).toBe('250-1234567-8901234');
    expect(result.orderDate).toBe('2024年5月10日');
    
    // Financials
    expect(result.totalAmount).toBe(3000);
    expect(result.billedAmount).toBe(3300);
    expect(result.shippingFee).toBe(500);
    expect(result.discounts).toBe(200);

    // Items
    expect(result.items.length).toBe(1);
    expect(result.items[0]).toEqual({
        itemName: 'Test Item 1',
        asin: 'B08N5M7S6K',
        seller: 'Amazon',
        unitPrice: 1500,
        quantity: 2
    });

    // Shipments
    expect(result.shipments.length).toBe(1);
    expect(result.shipments[0]).toEqual({
        shippingDate: '2024年5月12日',
        carrier: 'Yamato',
        trackingNumber: '123456789012'
    });
    
    // Payments
    expect(result.payments.length).toBe(1);
    expect(result.payments[0]).toEqual({ method: 'Visa 1234', amount: 3300 });
  });

  it('handles 0 yen orders paid fully by points', () => {
    document.body.innerHTML = `
      <div data-component="orderId"><span>250-9999999-0000000</span></div>
      <div data-component="orderDate"><span>2024年6月1日</span></div>
      
      <!-- Order Summary -->
      <div data-component="orderSummary">
        <div class="od-line-item-row">
          <div class="od-line-item-row-label">注文の合計:</div>
          <div class="od-line-item-row-content"><span>￥2,000</span></div>
        </div>
        <div class="od-line-item-row">
          <div class="od-line-item-row-label">Amazonポイント:</div>
          <div class="od-line-item-row-content"><span>-￥2,000</span></div>
        </div>
        <div class="od-line-item-row">
          <div class="od-line-item-row-label">ご請求額:</div>
          <div class="od-line-item-row-content"><span>￥0</span></div>
        </div>
      </div>
    `;

    const result = window.financeHubParser.parse();
    
    expect(result.orderId).toBe('250-9999999-0000000');
    expect(result.totalAmount).toBe(2000);
    expect(result.billedAmount).toBe(0);
    expect(result.amazonPointsUsed).toBe(2000);
    
    // Should still record the points payment
    expect(result.payments.length).toBe(1);
    expect(result.payments[0]).toEqual({ method: 'Amazon Points', amount: 2000 });
  });
});
