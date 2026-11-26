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
package ch.lin.finance.hub.backend.api.app.service.accounting.mortgage;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.finance.hub.backend.api.app.repository.accounting.mortgage.MortgageRecordRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.command.SaveMortgageRecordCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.command.SaveMortgageRecordCommand.MortgageEarlyRepaymentCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.TaxStrategyFactory;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.EarlyRepaymentType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.InterestRateType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.RepaymentMethod;

@ExtendWith(MockitoExtension.class)
class MortgageRecordServiceImplTest {

    @Mock
    private MortgageRecordRepository repository;

    @Mock
    private TaxStrategyFactory taxStrategyFactory;

    @InjectMocks
    private MortgageRecordServiceImpl mortgageRecordService;

    private SaveMortgageRecordCommand createCommand(LocalDate startDate, List<MortgageEarlyRepaymentCommand> earlyRepayments) {
        return new SaveMortgageRecordCommand(
                "ソニー銀行",
                "1",
                "Home Loan",
                30_000_000L,
                25_000_000L,
                LocalDate.now(),
                startDate,
                startDate.plusYears(35),
                420,
                0.5,
                InterestRateType.VARIABLE,
                RepaymentMethod.EQUAL_PAYMENT,
                80_000L,
                List.of(),
                null,
                null,
                null,
                false,
                null,
                null,
                true,
                earlyRepayments
        );
    }

    @Test
    @DisplayName("createMortgage should succeed when early repayment is in the future")
    @SuppressWarnings("null")
    void createMortgage_FutureRepayment_Succeeds() {
        LocalDate startDate = LocalDate.now().minusYears(1);
        LocalDate repaymentDate = LocalDate.now().plusMonths(6);
        MortgageEarlyRepaymentCommand er = new MortgageEarlyRepaymentCommand(
                null, repaymentDate, 1_000_000L, EarlyRepaymentType.REDUCE_PAYMENT, 0L, null
        );
        SaveMortgageRecordCommand command = createCommand(startDate, List.of(er));

        when(repository.save(any(MortgageRecord.class))).thenAnswer(invocation -> invocation.<MortgageRecord>getArgument(0));

        var result = mortgageRecordService.createMortgage(command);
        assertThat(result).isNotNull();
        assertThat(result.getEarlyRepayments()).hasSize(1);
        assertThat(result.getEarlyRepayments().get(0).getRepaymentDate()).isEqualTo(repaymentDate);
        verify(repository).save(any(MortgageRecord.class));
    }

    @Test
    @DisplayName("createMortgage should throw IllegalArgumentException when early repayment is in the past")
    void createMortgage_PastRepayment_ThrowsException() {
        LocalDate startDate = LocalDate.now().minusYears(1);
        LocalDate repaymentDate = LocalDate.now().minusDays(1);
        MortgageEarlyRepaymentCommand er = new MortgageEarlyRepaymentCommand(
                null, repaymentDate, 1_000_000L, EarlyRepaymentType.REDUCE_PAYMENT, 0L, null
        );
        SaveMortgageRecordCommand command = createCommand(startDate, List.of(er));

        assertThatThrownBy(() -> mortgageRecordService.createMortgage(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Early repayment date")
                .hasMessageContaining("cannot be earlier than");
    }

    @Test
    @DisplayName("createMortgage for future mortgage should throw IllegalArgumentException when repayment is before startDate")
    void createMortgage_FutureMortgage_RepaymentBeforeStart_ThrowsException() {
        LocalDate startDate = LocalDate.now().plusMonths(6);
        LocalDate repaymentDate = LocalDate.now().plusMonths(1);
        MortgageEarlyRepaymentCommand er = new MortgageEarlyRepaymentCommand(
                null, repaymentDate, 1_000_000L, EarlyRepaymentType.REDUCE_PAYMENT, 0L, null
        );
        SaveMortgageRecordCommand command = createCommand(startDate, List.of(er));

        assertThatThrownBy(() -> mortgageRecordService.createMortgage(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Early repayment date")
                .hasMessageContaining("cannot be earlier than " + startDate);
    }
}
