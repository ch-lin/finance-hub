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
 * Strategy implementation for Reiwa 8 tax year and onwards (>= 2026).
 * Based on employment income deduction minimum of 740,000 JPY (136萬/169萬/207萬之壁).
 * Implements 2D matrix matching NTA No.1191, No.1195 and Local Tax Law (令和8年4月1日現在法令等).
 */
public class Reiwa8SpouseDeductionStrategy implements SpouseDeductionStrategy {

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

        // 1. 配偶者控除 (給与年収 136 萬以下 / 合計所得 62 萬以下)
        if (spouseGrossSalary <= 1_360_000L) {
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

        // 2. 配偶者特別控除 滿額區間 (136萬超 ～ 169萬以下 / 合計所得 62萬超 ～ 95萬以下)
        // 依法特別控除無老人加算，扣除額與一般配偶控除同為 38 萬 / 26 萬 / 13 萬
        if (spouseGrossSalary <= 1_690_000L) {
            return switch (tier) {
                case 1 -> 380_000L;
                case 2 -> 260_000L;
                case 3 -> 130_000L;
                default -> 0L;
            };
        }

        // 3. 配偶者特別控除 遞減區間 (169萬超 ～ 207萬以下 / 合計所得 95萬超 ～ 133萬以下)
        if (spouseGrossSalary <= 1_740_000L) {
            return switch (tier) {
                case 1 -> 360_000L;
                case 2 -> 240_000L;
                case 3 -> 120_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_800_000L) {
            return switch (tier) {
                case 1 -> 310_000L;
                case 2 -> 210_000L;
                case 3 -> 110_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_850_000L) {
            return switch (tier) {
                case 1 -> 260_000L;
                case 2 -> 180_000L;
                case 3 -> 90_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_910_000L) {
            return switch (tier) {
                case 1 -> 210_000L;
                case 2 -> 140_000L;
                case 3 -> 70_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_960_000L) {
            return switch (tier) {
                case 1 -> 160_000L;
                case 2 -> 110_000L;
                case 3 -> 60_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 2_020_000L) {
            return switch (tier) {
                case 1 -> 110_000L;
                case 2 -> 80_000L;
                case 3 -> 40_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 2_050_000L) {
            return switch (tier) {
                case 1 -> 60_000L;
                case 2 -> 40_000L;
                case 3 -> 20_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 2_070_000L) {
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

        // 1. 住民稅配偶者控除 (地方稅法第314條之2規定合計所得 62 萬以下，給与年収 136 萬以下)
        if (spouseGrossSalary <= 1_360_000L) {
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

        // 2. 住民稅配偶者特別控除 滿額區間 (136萬超 ～ 169萬以下 / 合計所得 62萬超 ～ 95萬以下)
        // 依法特別控除無老人加算，扣除額上限同為 33 萬 / 22 萬 / 11 萬
        if (spouseGrossSalary <= 1_690_000L) {
            return switch (tier) {
                case 1 -> 330_000L;
                case 2 -> 220_000L;
                case 3 -> 110_000L;
                default -> 0L;
            };
        }

        // 3. 住民稅配偶者特別控除 遞減區間 (169萬超 ～ 207萬以下 / 合計所得 95萬超 ～ 133萬以下)
        if (spouseGrossSalary <= 1_740_000L) {
            return switch (tier) {
                case 1 -> 330_000L;
                case 2 -> 220_000L;
                case 3 -> 110_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_800_000L) {
            return switch (tier) {
                case 1 -> 310_000L;
                case 2 -> 210_000L;
                case 3 -> 110_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_850_000L) {
            return switch (tier) {
                case 1 -> 260_000L;
                case 2 -> 180_000L;
                case 3 -> 90_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_910_000L) {
            return switch (tier) {
                case 1 -> 210_000L;
                case 2 -> 140_000L;
                case 3 -> 70_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_960_000L) {
            return switch (tier) {
                case 1 -> 160_000L;
                case 2 -> 110_000L;
                case 3 -> 60_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 2_020_000L) {
            return switch (tier) {
                case 1 -> 110_000L;
                case 2 -> 80_000L;
                case 3 -> 40_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 2_050_000L) {
            return switch (tier) {
                case 1 -> 60_000L;
                case 2 -> 40_000L;
                case 3 -> 20_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 2_070_000L) {
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
