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
package ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.mapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.app.service.simulation.command.SimulationOverrides;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.Chromosome;
import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.Chromosome.MortgageRepaymentGene;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.EarlyRepaymentType;

class ChromosomeMapperTest {

    private ChromosomeMapper mapper;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        mapper = new ChromosomeMapper();
    }

    @Test
    void testToOverrides_MapsRepaymentTypesCorrectly() {
        Chromosome chromosome = new Chromosome();
        chromosome.setMortgageRepayments(Map.of(1L, List.of(
                new MortgageRepaymentGene(2028, 1000000L, EarlyRepaymentType.REDUCE_PAYMENT),
                new MortgageRepaymentGene(2030, 2000000L, EarlyRepaymentType.REDUCE_TERM)
        )));

        SimulationOverrides overrides = mapper.toOverrides(chromosome);

        assertNotNull(overrides.mortgageEarlyRepayments());
        List<SimulationOverrides.MortgageEarlyRepaymentOverride> list = overrides.mortgageEarlyRepayments().get(1L);
        assertNotNull(list);
        assertEquals(2, list.size());

        assertEquals(2028, list.get(0).year());
        assertEquals(1000000L, list.get(0).amount());
        assertEquals(EarlyRepaymentType.REDUCE_PAYMENT, list.get(0).repaymentType());

        assertEquals(2030, list.get(1).year());
        assertEquals(2000000L, list.get(1).amount());
        assertEquals(EarlyRepaymentType.REDUCE_TERM, list.get(1).repaymentType());
    }

    @Test
    void testToOverrides_GridSearchBridge_DefaultsToReduceTerm() {
        Chromosome chromosome = new Chromosome();
        chromosome.setGridSingleMortgageRepaymentYear(2035);
        // Pre-populate empty list for mortgage 2
        chromosome.getMortgageRepayments().put(2L, List.of());

        SimulationOverrides overrides = mapper.toOverrides(chromosome);

        assertNotNull(overrides.mortgageEarlyRepayments());
        List<SimulationOverrides.MortgageEarlyRepaymentOverride> list = overrides.mortgageEarlyRepayments().get(2L);
        assertNotNull(list);
        assertEquals(1, list.size());

        assertEquals(2035, list.get(0).year());
        assertEquals(Long.MAX_VALUE, list.get(0).amount());
        assertEquals(EarlyRepaymentType.REDUCE_TERM, list.get(0).repaymentType());
    }
}
