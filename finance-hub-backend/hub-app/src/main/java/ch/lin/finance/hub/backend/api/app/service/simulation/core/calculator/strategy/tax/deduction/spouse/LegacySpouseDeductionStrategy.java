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
 * Strategy implementation for tax years up to 2025 (令和6年分以前/令和7年分過渡期舊制).
 * Based on employment income deduction minimum of 550,000 JPY (103萬/150萬/201.6萬之壁).
 * Implements 2D matrix matching NTA No.1191 and No.1195.
 */
public class LegacySpouseDeductionStrategy implements SpouseDeductionStrategy {

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

        // 1. 配偶者控除 (給与年収 103 萬以下)
        if (spouseGrossSalary <= 1_030_000L) {
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

        // 2. 配偶者特別控除 滿額區間 (103萬超 ～ 150萬以下，依法無老人加算)
        if (spouseGrossSalary <= 1_500_000L) {
            return switch (tier) {
                case 1 -> 380_000L;
                case 2 -> 260_000L;
                case 3 -> 130_000L;
                default -> 0L;
            };
        }

        // 3. 配偶者特別控除 遞減區間 (150萬超 ～ 201.6萬以下)
        if (spouseGrossSalary <= 1_600_000L) {
            return switch (tier) {
                case 1 -> 360_000L;
                case 2 -> 240_000L;
                case 3 -> 120_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_668_000L) {
            return switch (tier) {
                case 1 -> 310_000L;
                case 2 -> 210_000L;
                case 3 -> 110_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_752_000L) {
            return switch (tier) {
                case 1 -> 260_000L;
                case 2 -> 180_000L;
                case 3 -> 90_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_836_000L) {
            return switch (tier) {
                case 1 -> 210_000L;
                case 2 -> 140_000L;
                case 3 -> 70_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_904_000L) {
            return switch (tier) {
                case 1 -> 160_000L;
                case 2 -> 110_000L;
                case 3 -> 60_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_972_000L) {
            return switch (tier) {
                case 1 -> 110_000L;
                case 2 -> 80_000L;
                case 3 -> 40_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 2_014_000L) {
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

        // 1. 配偶者控除 (給与年収 103 萬以下)
        if (spouseGrossSalary <= 1_030_000L) {
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

        // 2. 配偶者特別控除 滿額區間 (103萬超 ～ 150萬以下，依法無老人加算)
        if (spouseGrossSalary <= 1_500_000L) {
            return switch (tier) {
                case 1 -> 330_000L;
                case 2 -> 220_000L;
                case 3 -> 110_000L;
                default -> 0L;
            };
        }

        // 3. 配偶者特別控除 遞減區間 (150萬超 ～ 201.6萬以下)
        if (spouseGrossSalary <= 1_600_000L) {
            return switch (tier) {
                case 1 -> 330_000L;
                case 2 -> 220_000L;
                case 3 -> 110_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_668_000L) {
            return switch (tier) {
                case 1 -> 310_000L;
                case 2 -> 210_000L;
                case 3 -> 110_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_752_000L) {
            return switch (tier) {
                case 1 -> 260_000L;
                case 2 -> 180_000L;
                case 3 -> 90_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_836_000L) {
            return switch (tier) {
                case 1 -> 210_000L;
                case 2 -> 140_000L;
                case 3 -> 70_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_904_000L) {
            return switch (tier) {
                case 1 -> 160_000L;
                case 2 -> 110_000L;
                case 3 -> 60_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 1_972_000L) {
            return switch (tier) {
                case 1 -> 110_000L;
                case 2 -> 80_000L;
                case 3 -> 40_000L;
                default -> 0L;
            };
        } else if (spouseGrossSalary <= 2_014_000L) {
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
