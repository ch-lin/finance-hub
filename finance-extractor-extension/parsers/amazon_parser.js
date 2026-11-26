/*=============================================================================
 * Copyright (c) 2025-2026 Che-Hung Lin. All rights reserved.
 *
 * This software and associated documentation files (the "Software") are the
 * proprietary and confidential property of Che-Hung Lin.
 *
 * No permission is granted to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, in whole or in part, without
 * the prior express written permission of the copyright holder.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *===========================================================================*/
window.financeHubParser = {
    platform: 'amazon',
    
    parse: function() {
        if (window.location.href.includes('/your-orders/orders')) {
            return this.parseAmazonOrderList();
        }
        return this.parseAmazonOrder();
    },

    parseAmazonOrderList: function() {
        const orders = [];
        const orderCards = document.querySelectorAll('.order-card');
        
        orderCards.forEach((card, index) => {
            const isCancelled = card.textContent.includes('キャンセル済み');
            if (!isCancelled) {
                // Find orderID from any link in the card
                const detailLink = card.querySelector('a[href*="orderID="], a[href*="orderId="]');
                if (detailLink) {
                    const href = detailLink.getAttribute('href');
                    const orderIdMatch = href.match(/orderID=([A-Z0-9-]+)/i) || href.match(/orderId=([A-Z0-9-]+)/i);
                    if (orderIdMatch) {
                        const orderId = orderIdMatch[1];
                        const cardId = 'finance-hub-order-card-' + index;
                        card.id = cardId;
                        orders.push({
                            orderId: orderId,
                            link: `https://www.amazon.co.jp/gp/css/summary/print.html?orderID=${orderId}`,
                            cardId: cardId
                        });
                    }
                }
            }
        });
        
        // Ensure uniqueness by orderId
        const uniqueOrders = [];
        const seenIds = new Set();
        for (const order of orders) {
            if (!seenIds.has(order.orderId)) {
                seenIds.add(order.orderId);
                uniqueOrders.push(order);
            }
        }
        
        return { type: 'amazon_list_page', orders: uniqueOrders };
    },

    parseAmazonOrder: function() {
        // 1. Parse Order ID
        const orderIdEl = document.querySelector("div[data-component='orderId'] span");
        const orderId = orderIdEl ? orderIdEl.textContent.trim() : 'Unknown Order ID';

        // 2. Parse Order Date
        const orderDateEl = document.querySelector("div[data-component='orderDate'] span");
        let orderDate = 'Unknown Date';
        if (orderDateEl) {
            const text = orderDateEl.textContent.trim();
            const dateMatch = text.match(/(\d{4}年\d{1,2}月\d{1,2}日)/);
            if (dateMatch) orderDate = dateMatch[1];
        }

        // 2.5 Parse Shipments
        const shipments = [];
        const statusElements = document.querySelectorAll("div[data-component='shipmentStatus']");
        
        statusElements.forEach(element => {
            const text = element.textContent.trim();
            let parsedDate = null;
            
            const fullDateMatch = text.match(/(\d{4}年\d{1,2}月\d{1,2}日)/);
            if (fullDateMatch) {
                parsedDate = fullDateMatch[1];
            } else {
                const shortDateMatch = text.match(/(\d{1,2}月\d{1,2}日)/);
                if (shortDateMatch) {
                    const yearMatch = orderDate.match(/^(\d{4})年/);
                    let year = yearMatch ? parseInt(yearMatch[1], 10) : new Date().getFullYear();
                    // cross year handled simply by assuming order year as base
                    parsedDate = `${year}年${shortDateMatch[1]}`;
                }
            }
            
            if (parsedDate) {
                let carrier = null;
                let trackingNumber = null;
                
                const container = element.closest(".a-box");
                if (container) {
                    const containerText = container.textContent;
                    const carrierMatch = containerText.match(/配送業者[\s:：]*([^\s]+)/);
                    if (carrierMatch) carrier = carrierMatch[1];
                    
                    const trackingMatch = containerText.match(/トラッキングID[\s:：]*([A-Za-z0-9-]+)/);
                    if (trackingMatch) trackingNumber = trackingMatch[1];
                }
                
                shipments.push({ shippingDate: parsedDate, carrier, trackingNumber });
            }
        });

        // 3. Parse Purchased Items
        const items = [];
        const itemContainers = document.querySelectorAll("div[data-component='purchasedItems'] .a-fixed-left-grid");
        
        itemContainers.forEach(container => {
            const titleLink = container.querySelector("div[data-component='itemTitle'] a");
            if (!titleLink || !titleLink.textContent.trim()) return;

            const itemName = titleLink.textContent.trim();
            const href = titleLink.getAttribute("href") || "";
            
            // ASIN Regex (Supports /dp/, /gp/product/, /product/)
            const asinMatch = href.match(/\/(?:dp|gp\/product|product)\/([A-Z0-9]{10})/);
            const asin = asinMatch ? asinMatch[1] : null;

            // Seller
            const sellerEl = container.querySelector("div[data-component='orderedMerchant']");
            const seller = sellerEl ? sellerEl.textContent.replace("販売:", "").trim() : "Amazon";

            // Unit Price
            let unitPrice = 0;
            let priceEl = container.querySelector("div[data-component='unitPrice'] span.a-offscreen") 
                          || container.querySelector("div[data-component='unitPrice'] span[aria-hidden='true']");
            if (priceEl) {
                const cleanPrice = priceEl.textContent.replace(/[^0-9.-]/g, "");
                if (cleanPrice) unitPrice = parseInt(cleanPrice, 10);
            }

            // Quantity
            let quantity = 1;
            const qtyEl = container.querySelector("div[data-component='quantity'] span, .od-item-view-qty span, .od-item-view-qty");
            if (qtyEl) {
                const qtyText = qtyEl.textContent.replace(/[^0-9]/g, "");
                if (qtyText) quantity = parseInt(qtyText, 10);
            }

            items.push({ itemName, asin, seller, unitPrice, quantity });
        });

        // 4. Parse Financial Summary
        let billedAmount = 0;
        let totalAmount = 0;
        let discounts = 0;
        let amazonPointsUsed = 0;
        let dPointsUsed = 0;
        let giftCardAmount = 0;
        let shippingFee = 0;
        let refundAmount = 0;
        let payments = [];
        let hasBilledAmount = false;

        const rows = document.querySelectorAll("div[data-component='orderSummary'] .od-line-item-row");
        
        rows.forEach(row => {
            const labelEl = row.querySelector(".od-line-item-row-label");
            const contentEl = row.querySelector(".od-line-item-row-content span");
            if (!labelEl || !contentEl) return;

            const labelText = labelEl.textContent.trim();
            const cleanAmount = contentEl.textContent.replace(/[^0-9.-]/g, "");
            const amount = cleanAmount ? parseInt(cleanAmount, 10) : 0;
            const absAmount = Math.abs(amount);

            if (labelText.includes("ご請求額") || labelText.includes("この注文の合計")) {
                billedAmount = amount;
                hasBilledAmount = true;

                if (labelText.includes("この注文の合計") && totalAmount === 0) {
                    totalAmount = amount;
                }
                if (amount > 0) {
                    payments.push({ method: this.parseMainPaymentMethod(document), amount: amount });
                }
            } else if (labelText.includes("注文合計") || labelText.includes("注文の合計")) {
                totalAmount = amount;
            } else if (labelText.includes("商品の小計")) {
                if (totalAmount === 0) totalAmount = amount;
            } else if (labelText.includes("配送料・手数料")) {
                shippingFee = amount;
            } else if (labelText.includes("割引") || labelText.includes("プロモーション")) {
                discounts += absAmount;
            } else if (labelText.includes("Amazonギフトカード") || labelText.includes("Amazon商品券") || labelText.includes("Amazonギフト券") || labelText.includes("ギフトカード")) {
                giftCardAmount += absAmount;
                payments.push({ method: "Amazon Gift Card", amount: absAmount });
            } else if ((labelText.includes("Amazonポイント") && !labelText.includes("手数料"))
                    || labelText === "ポイント:" || labelText === "ポイント") {
                amazonPointsUsed += absAmount;
                payments.push({ method: "Amazon Points", amount: absAmount });
            } else if (labelText.includes("パートナーポイント") || labelText.includes("dポイント") || labelText.includes("PayPayポイント")) {
                dPointsUsed += absAmount;
                payments.push({ method: "Partner Points", amount: absAmount });
            } else if (labelText.includes("育児支援券")) {
                payments.push({ method: "Amazon育児支援券", amount: absAmount });
            } else if (labelText.includes("返金額の合計") || labelText.includes("返金合計")) {
                refundAmount += absAmount;
            }
        });

        // Fallback for missing Billed Amount (Common in digital/0 yen orders)
        if (!hasBilledAmount && totalAmount > 0) {
            billedAmount = totalAmount - amazonPointsUsed - giftCardAmount - dPointsUsed - discounts;
            if (billedAmount > 0) {
                payments.push({ method: this.parseMainPaymentMethod(document), amount: billedAmount });
            }
        }

        return { 
            orderId, orderDate, totalAmount, billedAmount, 
            discounts, amazonPointsUsed, rakutenPointsUsed: 0, dPointsUsed, giftCardAmount, shippingFee, refundAmount, payments,
            items, shipments
        };
    },

    // Payment Method Parsing (100% aligned with Java)
    parseMainPaymentMethod: function(doc) {
        const summary = doc.querySelector("div[data-component='orderSummary']");
        if (summary && summary.textContent.includes("代引手数料")) {
            return "代金引換";
        }

        const instruments = doc.querySelectorAll("[data-testid='payment-instrument']");
        for (const instrument of instruments) {
            const detailEl = instrument.querySelector("[data-testid='payment-instrument-feature-detail'], [data-testid='paragraph']");
            if (detailEl) {
                const detailText = detailEl.textContent;
                if (detailText.includes("現在料金は発生していません") || detailText.includes("未払いの請求はありません")) {
                    return "なし (Free)";
                }
            }

            const nameEl = instrument.querySelector("[data-testid='payment-instrument-name']");
            if (nameEl) {
                const name = nameEl.textContent.trim();
                if (!name.includes("ポイント") && !name.includes("ギフト") && !name.includes("Gift") && !name.includes("券")) {
                    const numberEl = instrument.querySelector("[data-testid='payment-instrument-number']");
                    if (numberEl) {
                        return name + " " + numberEl.textContent.trim();
                    } else if (name === "カード") {
                        return "Credit Card";
                    }
                    return name;
                }
            }
        }

        // Aligned with Java's CREDIT_CARD_PATTERN: (?i)(Visa|MasterCard|JCB|Amex|American Express|Diners)[^0-9]*(\d{4})
        const cardRegex = /(Visa|MasterCard|JCB|Amex|American Express|Diners)[\s\S]*?(\d{4})/i;

        const headers = Array.from(doc.querySelectorAll("div.od-payment-header, span, h5")).filter(el => {
            if (el.matches("div.od-payment-header")) return true;
            return Array.from(el.childNodes).some(node => node.nodeType === Node.TEXT_NODE && node.textContent.trim() === "支払い方法");
        });
        if (headers.length > 0) {
            let container = headers[0].closest(".a-box");
            if (!container) container = headers[0].parentElement;
            
            if (container) {
                const text = container.textContent;
                const cardMatch = text.match(cardRegex);
                if (cardMatch) return cardMatch[1] + " " + cardMatch[2];
                if (text.includes("代金引換")) return "代金引換";
                if (text.includes("コンビニ")) return "コンビニ・ATM・ネットバンキング・電子マネー払い";
                if (text.includes("現在料金は発生していません") || text.includes("未払いの請求はありません")) return "なし (Free)";
                // Note the regex here: exclude "ギフト" (gift) but include "カード" (card)
                if (text.match(/.*(?<!ギフト)カード.*/)) return "Credit Card";
            }
        }

        const templates = doc.querySelectorAll("template");
        for (const template of templates) {
            const templateText = template.content ? template.content.textContent : template.innerHTML;
            const tm = templateText.match(cardRegex);
            if (tm) return tm[1] + " " + tm[2];
        }

        return "Credit Card";
    }
};
