const fs = require('fs');
const path = require('path');

describe('sofmap_parser.js', () => {
  beforeEach(() => {
    // Reset DOM
    document.body.innerHTML = '';
    window.financeHubParser = undefined;

    // Load parser script using require for coverage
    jest.resetModules();
    require('../../parsers/sofmap_parser.js');
  });

  it('parses a standard Sofmap order correctly', () => {
    document.body.innerHTML = `
        <span class="info align-top">
            <dl class="order_dl_grid">
                <dt>ご注文日</dt>
                <dd>2016年2月15日</dd>
                <dt>ご注文番号</dt>
                <dd>046174418</dd>
            </dl>
        </span>
        <span class="info align-top">
            <dl class="order_dl_grid w-sp-100">
                <dt>お支払い方法</dt>
                <dd>クレジットカード　</dd>
                <dt>出荷状況</dt>
                <dd>出荷完了</dd>
            </dl>
        </span>
        
        <h3 class="ttlgray mgb10 mgt50 mgt-6vw">ご注文情報</h3>
        <section class="orderlist_wrp">
            <ul class="orderlist_product">
                <li>
                    <div class="product_box">
                        <span class="product_text">
                            <a href="#" class="product_name">【02/25発売予定】 勇者死す｡ PSV</a>
                            <fieldset class="order_detilinfo">
                                <dl>
                                    <dt>販売価格</dt>
                                    <dd>¥6,458<i>(税込)</i></dd>
                                </dl>
                                <dl>
                                    <dt>数量</dt><dd>1</dd>
                                </dl>
                            </fieldset>
                        </span>
                    </div>
                </li>
            </ul>
        </section>

        <table>
            <tbody>
                <tr>
                    <th class="head_sum">商品合計(税込)</th>
                    <td>¥22,074</td>
                </tr>
                <tr>
                    <th class="head_sum border-bottom">送料合計(税込)</th>
                    <td>¥0</td>
                </tr>
                <tr class="sum_1 ">
                    <th class="head_sum">ご注文合計(税込)</th>
                    <td>¥22,074</td>
                </tr>
                <tr class="usepoint">
                    <th>ソフマップポイント値引</th>
                    <td>¥-74</td>
                </tr>
                <tr>
                    <th class="head_sum">お支払い総額(税込)</th>
                    <td>¥22,000</td>
                </tr>
                <tr>
                    <th>加算ポイント</th>
                    <td class="bold">222ポイント</td>
                </tr>
            </tbody>
        </table>
    `;

    const result = window.financeHubParser.parse();
    
    expect(result.orderId).toBe('046174418');
    expect(result.orderDate).toBe('2016-02-15');
    
    // Financials
    expect(result.totalAmount).toBe(22074);
    expect(result.billedAmount).toBe(22000);
    expect(result.shippingFee).toBe(0);
    expect(result.pointsUsed).toBe(74);
    expect(result.earnedPoints).toBe(222);

    // Items
    expect(result.items.length).toBe(1);
    expect(result.items[0]).toEqual({
        name: '【02/25発売予定】 勇者死す｡ PSV',
        itemId: null,
        seller: null,
        unitPrice: 6458,
        quantity: 1
    });

    // Shipments
    expect(result.shipments.length).toBe(1);
    expect(result.shipments[0]).toEqual({
        shippingDate: '2016-02-15',
        carrier: 'Sofmap Default',
        trackingNumber: null,
        status: '出荷完了'
    });
    
    // Payments
    expect(result.payments.length).toBe(1);
    expect(result.payments[0]).toEqual({ method: 'クレジットカード', amount: 22000 });
  });

  it('handles orders without points', () => {
    document.body.innerHTML = `
        <dl class="order_dl_grid">
            <dt>ご注文日</dt><dd>2014年7月26日</dd>
            <dt>ご注文番号</dt><dd>043396653</dd>
        </dl>
        <table>
            <tbody>
                <tr>
                    <th class="head_sum">ご注文合計(税込)</th>
                    <td>¥9,913</td>
                </tr>
                <tr>
                    <th class="head_sum">お支払い総額(税込)</th>
                    <td>¥9,913</td>
                </tr>
                <tr>
                    <th>加算ポイント</th>
                    <td class="bold">0ポイント</td>
                </tr>
            </tbody>
        </table>
    `;

    const result = window.financeHubParser.parse();
    
    expect(result.orderId).toBe('043396653');
    expect(result.totalAmount).toBe(9913);
    expect(result.billedAmount).toBe(9913);
    expect(result.pointsUsed).toBe(0);
    expect(result.earnedPoints).toBe(0);
  });

  it('handles alternative headers, missing tags, missing siblings and fallback billedAmount', () => {
    document.body.innerHTML = `
        <dl class="order_dl_grid">
            <dt>ご注文番号</dt><dd>046174418</dd>
            <dt>ご注文日</dt><dd>2016年2月15日</dd>
        </dl>
        <dl><dt>お支払い方法</dt></dl> <!-- Missing dd -->
        <dl><dt>出荷状況</dt></dl> <!-- Missing dd -->
        <table><tbody>
            <tr><th>ご注文金額</th><td>1000</td></tr>
            <tr><th>ご注文金額</th><td>2000</td></tr>
            <tr><th>ご注文合計</th><td>1000</td></tr>
            <tr><th>利用ポイント</th><td>100</td></tr>
            <!-- billedAmount is deliberately omitted to test fallback logic: 1000 - 100 = 900 -->
            <tr><th>送料合計</th><th>Not A TD</th></tr>
            <tr><th>Some Field</th></tr>
            <tr><th>ご注文金額</th><td></td></tr> <!-- Empty TD to test !text in parseMoney -->
            <tr><th>ご注文金額</th><td>ABC</td></tr> <!-- Non-numeric TD to test !clean in parseMoney -->
        </tbody></table>
        <div class="product_box">
            <div class="product_name">Item 1</div>
            <dl><dt>販売価格</dt></dl> <!-- Missing dd -->
            <dl><dt>数量</dt></dl> <!-- Missing dd -->
        </div>
        <div class="product_box">
            <div class="product_name">Item 2</div>
            <!-- Missing price and qty completely -->
        </div>
        <div class="product_box">
            <div class="product_name"> </div> <!-- Empty name -->
        </div>
        <div class="product_box">
            <!-- Missing name completely -->
        </div>
    `;

    const result = window.financeHubParser.parse();
    
    expect(result.orderId).toBe('046174418');
    expect(result.totalAmount).toBe(1000);
    expect(result.billedAmount).toBe(900); // 1000 - 100 = 900
    expect(result.pointsUsed).toBe(100);
    
    expect(result.payments.length).toBe(0); // Because sibling missing
    expect(result.shipments.length).toBe(0); // Because sibling missing
    
    expect(result.items.length).toBe(2);
    expect(result.items[0].unitPrice).toBe(0);
    expect(result.items[0].quantity).toBe(1);
    expect(result.items[1].unitPrice).toBe(0);
    expect(result.items[1].quantity).toBe(1);
  });

  it('handles missing order id and date sibling', () => {
    document.body.innerHTML = `
        <dl><dt>ご注文番号</dt></dl>
        <dl><dt>ご注文日</dt></dl>
    `;

    expect(() => window.financeHubParser.parse()).toThrow('Missing mandatory field: Order ID');
  });

  it('handles invalid order date format', () => {
    document.body.innerHTML = `
        <span class="info align-top">
            <dl class="order_dl_grid">
                <dt>ご注文番号</dt><dd>046174418</dd>
                <dt>ご注文日</dt><dd>Invalid Date</dd>
            </dl>
        </span>
    `;

    expect(() => window.financeHubParser.parse()).toThrow('Missing or unparseable mandatory field: Order Date');
  });

  it('throws when total amount is missing', () => {
    document.body.innerHTML = `
        <span class="info align-top">
            <dl class="order_dl_grid">
                <dt>ご注文番号</dt><dd>046174418</dd>
                <dt>ご注文日</dt><dd>2016年2月15日</dd>
            </dl>
        </span>
        <table><tbody>
            <tr><th>送料合計</th><td>1000</td></tr>
        </tbody></table>
    `;

    expect(() => window.financeHubParser.parse()).toThrow('Missing mandatory field: Total Amount');
  });

  it('throws when order id is completely missing', () => {
    document.body.innerHTML = `
        <dl><dt>ご注文日</dt><dd>2016年2月15日</dd></dl>
        <table><tbody><tr><th>ご注文合計</th><td>1000</td></tr></tbody></table>
    `;
    expect(() => window.financeHubParser.parse()).toThrow('Missing mandatory field: Order ID');
  });

  it('throws when order date is completely missing', () => {
    document.body.innerHTML = `
        <dl><dt>ご注文番号</dt><dd>046174418</dd></dl>
        <table><tbody><tr><th>ご注文合計</th><td>1000</td></tr></tbody></table>
    `;
    expect(() => window.financeHubParser.parse()).toThrow('Missing or unparseable mandatory field: Order Date');
  });

  it('handles invalid quantity format', () => {
    document.body.innerHTML = `
        <dl><dt>ご注文番号</dt><dd>046174418</dd></dl>
        <dl><dt>ご注文日</dt><dd>2016年2月15日</dd></dl>
        <table><tbody><tr><th>ご注文合計</th><td>1000</td></tr></tbody></table>
        <div class="product_box">
            <div class="product_name">Item 1</div>
            <dl>
                <dt>数量</dt><dd>ABC</dd>
            </dl>
        </div>
    `;

    const result = window.financeHubParser.parse();
    expect(result.items[0].quantity).toBe(1); // Default to 1
  });

  it('handles orders with 0 billed amount', () => {
    document.body.innerHTML = `
        <dl><dt>ご注文番号</dt><dd>046174418</dd></dl>
        <dl><dt>ご注文日</dt><dd>2016年2月15日</dd></dl>
        <dl><dt>お支払い方法</dt><dd>クレジットカード</dd></dl>
        <table><tbody>
            <tr><th>ご注文合計</th><td>1000</td></tr>
            <tr><th>お支払い総額</th><td>0</td></tr>
        </tbody></table>
    `;

    const result = window.financeHubParser.parse();
    expect(result.billedAmount).toBe(0);
    expect(result.payments.length).toBe(0); // Should not add payment if billedAmount is 0
  });

  it('throws an error if parsing fails catastrophically', () => {
    const originalQuerySelectorAll = document.querySelectorAll;
    document.querySelectorAll = jest.fn(() => { throw new Error('Catastrophic failure'); });
    
    expect(() => window.financeHubParser.parse()).toThrow('Catastrophic failure');
    
    document.querySelectorAll = originalQuerySelectorAll;
  });
});
