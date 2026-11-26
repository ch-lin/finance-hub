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
    platform: 'rakuten',
    
    parse: function() {
        return this.parseRakutenOrder();
    },

    parseRakutenOrder: function() {
        let orderId = "Unknown";
        let orderDate = "Unknown Date";
        
        const allText = document.body.innerText; // Use innerText to avoid text concatenation without spaces
        
        const orderIdMatch = allText.match(/注文番号[^\d]*([\d-]+)/);
        if (orderIdMatch) orderId = orderIdMatch[1];
        
        const orderDateMatch = allText.match(/注文日時[^\d]*(\d{4}\/\d{2}\/\d{2})/);
        const orderDateMatch2 = allText.match(/(\d{4})年(\d{1,2})月(\d{1,2})日[^\d]*(?:ご注文|更新)/);
        if (orderDateMatch) {
            orderDate = orderDateMatch[1];
        } else if (orderDateMatch2) {
            const y = orderDateMatch2[1];
            const m = orderDateMatch2[2].padStart(2, '0');
            const d = orderDateMatch2[3].padStart(2, '0');
            orderDate = `${y}/${m}/${d}`;
        }
    
        let totalAmount = 0;
        let billedAmount = 0;
        let shippingFee = 0;
        let rakutenPointsUsed = 0;
        let payments = [];
    
        const totalMatch = allText.match(/(?:支払い金額|総合計)[^\d]*([\d,]+)[^\d]*円/);
        if (totalMatch) billedAmount = parseInt(totalMatch[1].replace(/,/g, ''), 10);
    
        const subTotalMatch = allText.match(/(?:小計|商品合計金額)[^\d]*([\d,]+)[^\d]*円/);
        if (subTotalMatch) totalAmount = parseInt(subTotalMatch[1].replace(/,/g, ''), 10);
    
        const pointsMatch = allText.match(/ポイント利用[^\d]*([\d,]+)[^\d]*(?:ポイント|円)/);
        if (pointsMatch) rakutenPointsUsed = parseInt(pointsMatch[1].replace(/,/g, ''), 10);
    
        const paymentMethodMatch = allText.match(/(?:お)?支払い方法\s*クレジットカード決済|(?:お)?支払い方法\s*([^\n]+)/);
        let method = paymentMethodMatch ? (paymentMethodMatch[0].includes("クレジットカード") ? "Credit Card" : paymentMethodMatch[1].trim()) : "Unknown";
        if (billedAmount > 0) {
            payments.push({ method: method, amount: billedAmount });
        }
    
        const items = [];
        const elements = document.querySelectorAll('*');
        const itemLinks = [];
        
        for (const el of elements) {
            if (el.tagName === 'A' && el.href && (el.href.includes('item.rakuten.co.jp') || el.href.includes('books.rakuten.co.jp/rb/'))) {
                const text = el.textContent.trim();
                if (text.length > 10 && !itemLinks.find(l => l.href === el.href)) {
                    let container = el.parentElement;
                    let depth = 0;
                    while (container && depth < 10) {
                        const containerText = container.innerText;
                        if (containerText && containerText.includes('数量') && containerText.includes('円')) {
                            
                            let price = 0;
                            let qty = 1;
                            
                            const matchPrice = containerText.match(/([\d,]+)\s*円/);
                            if (matchPrice) price = parseInt(matchPrice[1].replace(/,/g, ''), 10);
                            
                            // Because of innerText, 数量: \n 1 format might appear.
                            const matchQty = containerText.match(/数量[^\d]*(\d+)/);
                            if (matchQty) qty = parseInt(matchQty[1], 10);
                            
                            const urlObj = new URL(el.href);
                            const paths = urlObj.pathname.split('/').filter(p => p);
                            let seller = paths[0] || 'Unknown';
                            let asin = paths[1] || 'Unknown';
                            
                            if (!items.find(i => i.itemName === text)) {
                                items.push({
                                    itemName: text,
                                    asin: asin,
                                    seller: seller,
                                    unitPrice: price,
                                    quantity: qty
                                });
                            }
                            break;
                        }
                        container = container.parentElement;
                        depth++;
                    }
                    itemLinks.push(el);
                }
            }
        }
    
        let shipments = [];
        const scripts2 = document.querySelectorAll('script');
        for (const script of scripts2) {
            if (script.textContent.includes('window.__INITIAL_STATE__')) {
                const match = script.textContent.match(/window\.__INITIAL_STATE__\s*=\s*(\{.*\});?/);
                if (match) {
                    try {
                        const state = JSON.parse(match[1]);
                        
                        let shippedDate = orderDate;
                        if (state.orderData && state.orderData.orderStatusObject && state.orderData.orderStatusObject.statusHistory) {
                            const deliveredStatus = state.orderData.orderStatusObject.statusHistory.find(s => s.name === 'OPP_ORDER_STATUS_DELIVERED' || s.name === 'OPP_ORDER_STATUS_SHIPPED');
                            if (deliveredStatus && deliveredStatus.date) {
                                shippedDate = deliveredStatus.date.split(' ')[0];
                            }
                        }

                        if (state.orderData && state.orderData.shippingList) {
                            for (const shipping of state.orderData.shippingList) {
                                if (shipping.trackingNumber) {
                                    let shippingDateFinal = shippedDate;
                                    if (shipping.deliveryInfo && shipping.deliveryInfo.deliveryDateTime) {
                                        const dtMatch = shipping.deliveryInfo.deliveryDateTime.match(/(\d{2})\/(\d{2})/);
                                        if (dtMatch && orderDate && orderDate.includes('/')) {
                                            const month = dtMatch[1];
                                            const day = dtMatch[2];
                                            let yearStr = orderDate.split('/')[0];
                                            const orderMonth = parseInt(orderDate.split('/')[1], 10);
                                            const deliveryMonth = parseInt(month, 10);
                                            if (deliveryMonth < orderMonth - 2) {
                                                yearStr = (parseInt(yearStr, 10) + 1).toString();
                                            }
                                            shippingDateFinal = `${yearStr}/${month}/${day}`;
                                        }
                                    }
                                    shipments.push({
                                        carrier: shipping.deliveryInfo ? shipping.deliveryInfo.deliveryCarrierName : "Unknown",
                                        trackingNumber: shipping.trackingNumber,
                                        shippingDate: shippingDateFinal
                                    });
                                }
                            }
                        }
                    } catch (e) {
                        console.error("Failed to parse Rakuten INITIAL_STATE", e);
                    }
                }
                break;
            }
        }
    
        return { 
            orderId, orderDate, totalAmount, billedAmount, 
            discounts: 0, amazonPointsUsed: 0, rakutenPointsUsed, dPointsUsed: 0, giftCardAmount: 0, 
            shippingFee, refundAmount: 0, payments,
            items, shipments
        };
    }
};
