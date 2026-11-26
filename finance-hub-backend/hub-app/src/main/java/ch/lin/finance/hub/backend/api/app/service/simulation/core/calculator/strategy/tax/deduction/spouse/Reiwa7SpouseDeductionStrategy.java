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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.spouse;

/**
 * Strategy implementation for Reiwa 7 tax year (2025 income year / 2026 inhabitant tax year).
 * Based on employment income deduction minimum of 650,000 JPY (123萬/160萬/201.6萬之壁).
 * Implements 2D matrix matching NTA 令和7年度改正 and Local Tax Law (地方税法第314条の2).
 */
public class Reiwa7SpouseDeductionStrategy implements SpouseDeductionStrategy {

    private static final long TIER1_MAX_SALARY = 10_950_000L; // 本人合計所得 900萬以下
    private static final long TIER2_MAX_SALARY = 11_450_000L; // 本人合計所得 900萬超 950萬以下
    private static final long TIER3_MAX_SALARY = 11_950_000L; // 本人合計所得 950萬超 1,000萬以下

    private int getTaxpayerTier(long userGrossSalary) {
        if (userGrossSalary <= TIER1_MAX_SALARY) {
            return 1;
        } else if (userGrossSalary <= TIER2_MAX_SALARY) {
            return 2;
        } else if (userGrossSalary <= TIER3_MAX_SALARY) {
            return 3;
        } else {
            return 4; // 排富 (> 1,000萬所得)
        }
    }

    @Override
    public long calculateIncomeTaxDeduction(long userGrossSalary, long spouseGrossSalary, boolean isElderlySpouse) {
        int tier = getTaxpayerTier(userGrossSalary);
        if (tier == 4) {
            return 0L;
        }

        // 1. 配偶者控除 (給与年収 123 萬以下 / 合計所得 58 萬以下)
        if (spouseGrossSalary <= 1_230_000L) {
            if (isElderlySpouse) {
                return switch (tier) {
                    case 1 -> 480_000L;
                    case 2 -> 320_000L;
                    case 3 -> 160_000L;
                    default -> 0L;
                };
            } else {
                return switch (tier) {
                    case 1 -> 380_000L;
                    case 2 -> 260_000L;
                    case 3 -> 130_000L;
                    default -> 0L;
                };
            }
        }

        // 2. 配偶者特別控除 滿額區間 (123萬超 ～ 160萬以下 / 合計所得 58萬超 ～ 95萬以下)
        // 依法特別控除無老人加算，扣除額上限同為 38 萬 / 26 萬 / 13 萬
        if (spouseGrossSalary <= 1_600_000L) {
            return switch (tier) {
                case 1 -> 380_000L;
                case 2 -> 260_000L;
                case 3 -> 130_000L;
                default -> 0L;
            };
        }

        // 3. 配偶者特別控除 遞減區間 (160萬超 ～ 201.6萬以下 / 合計所得 95萬超 ～ 133萬以下)
        if (spouseGrossSalary <= 1_650_000L) {
            return switch (tier) {
                case 1 -> 360_000L;
                case 2 -> 240_000L;
                case 3 -> 120_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_700_000L) {
            return switch (tier) {
                case 1 -> 310_000L;
                case 2 -> 210_000L;
                case 3 -> 110_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_750_000L) {
            return switch (tier) {
                case 1 -> 260_000L;
                case 2 -> 180_000L;
                case 3 -> 90_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_800_000L) {
            return switch (tier) {
                case 1 -> 210_000L;
                case 2 -> 140_000L;
                case 3 -> 70_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_850_000L) {
            return switch (tier) {
                case 1 -> 160_000L;
                case 2 -> 110_000L;
                case 3 -> 60_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_900_000L) {
            return switch (tier) {
                case 1 -> 110_000L;
                case 2 -> 80_000L;
                case 3 -> 40_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_970_000L) {
            return switch (tier) {
                case 1 -> 60_000L;
                case 2 -> 40_000L;
                case 3 -> 20_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 2_016_000L) {
            return switch (tier) {
                case 1 -> 30_000L;
                case 2 -> 20_000L;
                case 3 -> 10_000L;
                default -> 0L;
            };
        }

        return 0L;
    }

    @Override
    public long calculateInhabitantTaxDeduction(long userGrossSalary, long spouseGrossSalary, boolean isElderlySpouse) {
        int tier = getTaxpayerTier(userGrossSalary);
        if (tier == 4) {
            return 0L;
        }

        // 1. 住民稅配偶者控除 (地方稅法規定合計所得 58 萬以下，給与年収 123 萬以下)
        if (spouseGrossSalary <= 1_230_000L) {
            if (isElderlySpouse) {
                return switch (tier) {
                    case 1 -> 380_000L;
                    case 2 -> 260_000L;
                    case 3 -> 130_000L;
                    default -> 0L;
                };
            } else {
                return switch (tier) {
                    case 1 -> 330_000L;
                    case 2 -> 220_000L;
                    case 3 -> 110_000L;
                    default -> 0L;
                };
            }
        }

        // 2. 住民稅配偶者特別控除 滿額區間 (123萬超 ～ 165萬以下 / 合計所得 58萬超 ～ 100萬以下)
        // 依地方稅法，所得在 100 萬以下（年收 165 萬以下）住民稅維持滿額 33 萬 / 22 萬 / 11 萬
        if (spouseGrossSalary <= 1_650_000L) {
            return switch (tier) {
                case 1 -> 330_000L;
                case 2 -> 220_000L;
                case 3 -> 110_000L;
                default -> 0L;
            };
        }

        // 3. 住民稅配偶者特別控除 遞減區間 (165萬超 ～ 201.6萬以下 / 合計所得 100萬超 ～ 133萬以下)
        if (spouseGrossSalary <= 1_700_000L) {
            return switch (tier) {
                case 1 -> 310_000L;
                case 2 -> 210_000L;
                case 3 -> 110_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_750_000L) {
            return switch (tier) {
                case 1 -> 260_000L;
                case 2 -> 180_000L;
                case 3 -> 90_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_800_000L) {
            return switch (tier) {
                case 1 -> 210_000L;
                case 2 -> 140_000L;
                case 3 -> 70_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_850_000L) {
            return switch (tier) {
                case 1 -> 160_000L;
                case 2 -> 110_000L;
                case 3 -> 60_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_900_000L) {
            return switch (tier) {
                case 1 -> 110_000L;
                case 2 -> 80_000L;
                case 3 -> 40_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_970_000L) {
            return switch (tier) {
                case 1 -> 60_000L;
                case 2 -> 40_000L;
                case 3 -> 20_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 2_016_000L) {
            return switch (tier) {
                case 1 -> 30_000L;
                case 2 -> 20_000L;
                case 3 -> 10_000L;
                default -> 0L;
            };
        }

        return 0L;
    }
}
