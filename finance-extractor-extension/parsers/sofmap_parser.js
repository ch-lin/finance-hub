window.financeHubParser = {
    platform: 'sofmap',
    parse: function() {
        console.log("Starting Sofmap parser...");
        
        try {
            // Helper function to extract money amounts
            const parseMoney = (text) => {
                if (!text) return 0;
                const clean = text.replace(/[^0-9.-]/g, '');
                if (!clean) return 0;
                return parseInt(clean, 10);
            };

            // 1. Order ID
            let orderId = null;
            const orderIdDt = Array.from(document.querySelectorAll('dt')).find(dt => dt.textContent.includes('ご注文番号'));
            if (orderIdDt && orderIdDt.nextElementSibling) {
                orderId = orderIdDt.nextElementSibling.textContent.trim();
            }
            if (!orderId) {
                throw new Error("Missing mandatory field: Order ID");
            }

            // 2. Order Date
            let orderDate = null;
            const orderDateDt = Array.from(document.querySelectorAll('dt')).find(dt => dt.textContent.includes('ご注文日'));
            if (orderDateDt && orderDateDt.nextElementSibling) {
                const text = orderDateDt.nextElementSibling.textContent.trim();
                const match = text.match(/(\d{4})年(\d{1,2})月(\d{1,2})日/);
                if (match) {
                    const month = match[2].padStart(2, '0');
                    const day = match[3].padStart(2, '0');
                    orderDate = `${match[1]}-${month}-${day}`;
                }
            }
            if (!orderDate) {
                throw new Error("Missing or unparseable mandatory field: Order Date");
            }

            // 3. Financial Summary
            let totalAmount = null;
            let billedAmount = null;
            let shippingFee = 0;
            let pointsUsed = 0;
            let earnedPoints = 0;

            const headers = document.querySelectorAll('th');
            headers.forEach(th => {
                const text = th.textContent.trim();
                const td = th.nextElementSibling;
                if (!td || td.tagName.toLowerCase() !== 'td') return;

                const amount = parseMoney(td.textContent);

                if (text.includes('ご注文合計') || text.includes('ご注文金額')) {
                    if (totalAmount === null || text.includes('ご注文合計')) {
                        totalAmount = amount;
                    }
                } else if (text.includes('お支払い総額') || text.includes('お支払い金額')) {
                    billedAmount = amount;
                } else if (text.includes('送料合計')) {
                    shippingFee = amount;
                } else if (text.includes('ソフマップポイント値引') || text.includes('利用ポイント')) {
                    pointsUsed = Math.abs(amount);
                } else if (text.includes('加算ポイント')) {
                    earnedPoints = Math.abs(amount);
                }
            });

            if (totalAmount === null) {
                throw new Error("Missing mandatory field: Total Amount");
            }

            // Fallback for billed amount if explicit one not found
            if (billedAmount === null) {
                billedAmount = totalAmount - pointsUsed;
            }

            // 4. Payment Methods & Shipments
            const payments = [];
            const shipments = [];
            
            const paymentDt = Array.from(document.querySelectorAll('dt')).find(dt => dt.textContent.includes('お支払い方法'));
            if (paymentDt && paymentDt.nextElementSibling) {
                const method = paymentDt.nextElementSibling.textContent.trim();
                if (billedAmount > 0) {
                    payments.push({
                        method: method,
                        amount: billedAmount
                    });
                }
            }

            let shippingDate = null;
            const shipmentDt = Array.from(document.querySelectorAll('dt')).find(dt => dt.textContent.includes('出荷状況'));
            if (shipmentDt && shipmentDt.nextElementSibling) {
                const status = shipmentDt.nextElementSibling.textContent.trim();
                shippingDate = orderDate;
                shipments.push({
                    carrier: 'Sofmap Default',
                    trackingNumber: null,
                    shippingDate: shippingDate,
                    status: status
                });
            }

            // 5. Items
            const items = [];
            const productBoxes = document.querySelectorAll('.product_box');
            productBoxes.forEach(box => {
                const nameEl = box.querySelector('.product_name');
                if (!nameEl || !nameEl.textContent.trim()) return;

                const item = {
                    name: nameEl.textContent.trim(),
                    unitPrice: 0,
                    quantity: 1,
                    itemId: null,
                    seller: null
                };

                const itemDts = box.querySelectorAll('dt');
                const priceDt = Array.from(itemDts).find(dt => dt.textContent.includes('販売価格'));
                if (priceDt && priceDt.nextElementSibling) {
                    item.unitPrice = parseMoney(priceDt.nextElementSibling.textContent);
                }

                const qtyDt = Array.from(itemDts).find(dt => dt.textContent.includes('数量'));
                if (qtyDt && qtyDt.nextElementSibling) {
                    const qtyClean = qtyDt.nextElementSibling.textContent.replace(/[^0-9]/g, '');
                    if (qtyClean) {
                        item.quantity = parseInt(qtyClean, 10);
                    }
                }

                items.push(item);
            });

            return {
                orderId,
                orderDate,
                shippingDate,
                totalAmount,
                billedAmount,
                pointsUsed,
                giftCardAmount: 0,
                amazonPointsUsed: 0,
                rakutenPointsUsed: 0,
                dPointsUsed: 0,
                discounts: 0,
                shippingFee,
                refundAmount: 0,
                earnedPoints,
                payments,
                items,
                shipments
            };
        } catch (e) {
            console.error("Sofmap parser error:", e);
            throw e;
        }
    }
};
