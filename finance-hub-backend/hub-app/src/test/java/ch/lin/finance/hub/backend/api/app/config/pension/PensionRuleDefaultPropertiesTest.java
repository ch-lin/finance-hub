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
package ch.lin.finance.hub.backend.api.app.config.pension;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = { PensionRuleDefaultPropertiesTest.TestConfig.class })
@TestPropertySource(properties = {
        "finance-hub.pension-rule.max-basic-pension-amount=831700",
        "finance-hub.pension-rule.max-basic-pension-months=480",
        "finance-hub.pension-rule.kousei-multiplier-per-mille=5.481",
        "finance-hub.pension-rule.kousei-salary-cap-monthly=650000",
        "finance-hub.pension-rule.kousei-bonus-cap-per-payment=1500000"
})
public class PensionRuleDefaultPropertiesTest {

    @EnableConfigurationProperties(PensionRuleDefaultProperties.class)
    public static class TestConfig {
    }

    @Autowired
    private PensionRuleDefaultProperties properties;

    @Test
    void shouldLoadDefaultProperties() {
        assertThat(properties.getName()).isEqualTo("default");
        assertThat(properties.getEnabled()).isTrue();
        assertThat(properties.getMaxBasicPensionAmount()).isEqualTo(831700L);
        assertThat(properties.getMaxBasicPensionMonths()).isEqualTo(480L);
        assertThat(properties.getKouseiMultiplierPerMille()).isEqualTo(new java.math.BigDecimal("5.481"));
        assertThat(properties.getKouseiSalaryCapMonthly()).isEqualTo(650000L);
        assertThat(properties.getKouseiBonusCapPerPayment()).isEqualTo(1500000L);
    }
}
