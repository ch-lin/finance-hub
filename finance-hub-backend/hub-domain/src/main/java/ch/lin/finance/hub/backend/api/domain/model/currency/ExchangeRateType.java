/*
 * Copyright (c) 2026 Che-Hung Lin. All rights reserved.
 *
 * This software and associated documentation files (the "Software") are the
 * proprietary and confidential property of Che-Hung Lin. Unauthorized copying,
 * modification, distribution, reverse engineering, or use of the Software,
 * via any medium, is strictly prohibited without prior written permission.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package ch.lin.finance.hub.backend.api.domain.model.currency;

/**
 * Represents the type of foreign exchange rate quotation.
 */
public enum ExchangeRateType {
    /**
     * Telegraphic Transfer Middle rate (公表仲値) - official benchmark rate.
     */
    TTM,

    /**
     * Telegraphic Transfer Selling rate (対顧客電信売相場).
     */
    TTS,

    /**
     * Telegraphic Transfer Buying rate (対顧客電信買相場).
     */
    TTB,

    /**
     * Spot exchange rate (即時スポット為替相場).
     */
    SPOT,

    /**
     * Official market closing rate (公表終値為替相場).
     */
    CLOSING
}
