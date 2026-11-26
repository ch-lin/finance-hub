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
package ch.lin.finance.hub.backend.api.domain.model.config.education;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrackConfigTest {

    @Test
    @DisplayName("Should create TrackConfig with standardEntranceAge via builder")
    void shouldCreateTrackConfigWithStandardEntranceAge() {
        TrackConfig config = TrackConfig.builder()
                .tuition(new BigDecimal("1000000"))
                .duration(36)
                .startMonth(4)
                .termsPerYear(2)
                .entranceFee(new BigDecimal("200000"))
                .standardEntranceAge(3)
                .build();

        assertThat(config.getTuition()).isEqualByComparingTo("1000000");
        assertThat(config.getDuration()).isEqualTo(36);
        assertThat(config.getStartMonth()).isEqualTo(4);
        assertThat(config.getTermsPerYear()).isEqualTo(2);
        assertThat(config.getEntranceFee()).isEqualByComparingTo("200000");
        assertThat(config.getStandardEntranceAge()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should allow setting standardEntranceAge via setter")
    void shouldAllowSettingStandardEntranceAge() {
        TrackConfig config = new TrackConfig();
        config.setStandardEntranceAge(6);

        assertThat(config.getStandardEntranceAge()).isEqualTo(6);
    }
}
