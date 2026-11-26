const fs = require('fs');
const path = require('path');

describe('rakuten_parser.js', () => {
  beforeAll(() => {
    // JSDOM doesn't support innerText which rakuten_parser uses heavily, 
    // so we map it to textContent for testing purposes.
    if (!Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'innerText')) {
        Object.defineProperty(HTMLElement.prototype, 'innerText', {
        get() {
            return this.textContent;
        }
        });
    }
  });

  beforeEach(() => {
    // Reset DOM
    document.body.innerHTML = '';
    window.financeHubParser = undefined;

    // Load parser script using require for coverage
    jest.resetModules();
    require('../../parsers/rakuten_parser.js');
  });

  it('parses a standard Rakuten order correctly', () => {
    document.body.innerHTML = `
      <div>注文番号 123456-20240101-000001</div>
      <div>注文日時 2024/01/01</div>
      
      <div>小計 5,000円</div>
      <div>ポイント利用 -500ポイント</div>
      <div>支払い金額 4,500円</div>
      
      <div>お支払い方法 クレジットカード決済</div>
      
      <!-- Items -->
      <div>
        <a href="https://item.rakuten.co.jp/test-shop/item123/">Rakuten Test Item</a>
        <span>数量 2</span>
        <span>2,500 円</span>
      </div>

      <!-- INITIAL_STATE Scripts for shipping info -->
      <script>
        window.__INITIAL_STATE__ = {"orderData":{"shippingList":[{"trackingNumber":"987654321","deliveryInfo":{"deliveryCarrierName":"Japan Post","deliveryDateTime":"01/03"}}]}};
      </script>
    `;

    const result = window.financeHubParser.parse();
    
    expect(result.orderId).toBe('123456-20240101-000001');
    expect(result.orderDate).toBe('2024/01/01');
    
    expect(result.totalAmount).toBe(5000);
    expect(result.rakutenPointsUsed).toBe(500);
    expect(result.billedAmount).toBe(4500);
    
    expect(result.payments.length).toBe(1);
    expect(result.payments[0]).toEqual({ method: 'Credit Card', amount: 4500 });
    
    expect(result.items.length).toBe(1);
    expect(result.items[0].itemName).toBe('Rakuten Test Item');
    expect(result.items[0].unitPrice).toBe(2500);
    expect(result.items[0].quantity).toBe(2);
    expect(result.items[0].seller).toBe('test-shop');
    expect(result.items[0].asin).toBe('item123');

    expect(result.shipments.length).toBe(1);
    expect(result.shipments[0].trackingNumber).toBe('987654321');
    expect(result.shipments[0].carrier).toBe('Japan Post');
    expect(result.shipments[0].shippingDate).toBe('2024/01/03');
  });
});
