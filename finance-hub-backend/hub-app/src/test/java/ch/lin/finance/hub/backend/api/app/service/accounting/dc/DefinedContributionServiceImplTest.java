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
package ch.lin.finance.hub.backend.api.app.service.accounting.dc;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionAccountSnapshotRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionFundSnapshotRepository;
import ch.lin.finance.hub.backend.api.app.security.OwnershipGuard;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.FundAllocationCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAccountAllocationsCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAllocationCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DefinedContributionAccountSnapshotResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DefinedContributionFundSnapshotResult;
import ch.lin.finance.hub.backend.api.common.exception.ItemNotFoundException;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionAccountSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionFundSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionType;

@ExtendWith(MockitoExtension.class)
class DefinedContributionServiceImplTest {

    @Mock
    private DefinedContributionAccountSnapshotRepository dcRepository;

    @Mock
    private DefinedContributionFundSnapshotRepository dcFundRepository;

    @Mock
    private OwnershipGuard ownershipGuard;

    @InjectMocks
    private DefinedContributionServiceImpl service;

    @Test
    @SuppressWarnings("null")
    @DisplayName("updateAllocation: updates only expectedReturnRate without altering contribution or percentage")
    void testUpdateAllocation_UpdatesOnlyExpectedReturnRate() {
        DefinedContributionFundSnapshot fund = new DefinedContributionFundSnapshot(
                "海外株式", "Fund A", "F001",
                10.0, 1000.0, 1100.0, 10_000L, 11_000L, 1_000L,
                12_000L, 60.0);
        ReflectionTestUtils.setField(fund, "id", 1L);

        when(ownershipGuard.loadOwned(dcFundRepository, 1L)).thenReturn(fund);
        when(dcFundRepository.save(any(DefinedContributionFundSnapshot.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UpdateAllocationCommand command = new UpdateAllocationCommand();
        command.setExpectedReturnRate(0.05);

        DefinedContributionFundSnapshotResult result = service.updateAllocation(1L, command);

        assertThat(result.getExpectedReturnRate()).isEqualTo(0.05);
        assertThat(result.getMonthlyContributionAmount()).isEqualTo(12_000L);
        assertThat(result.getAllocationPercentage()).isEqualTo(60.0);
    }

    @Test
    @DisplayName("updateAllocation: throws 404 when fund does not exist")
    void testUpdateAllocation_NotFound() {
        when(ownershipGuard.loadOwned(dcFundRepository, 99L))
                .thenThrow(new ItemNotFoundException("Resource not found"));

        UpdateAllocationCommand command = new UpdateAllocationCommand();
        command.setExpectedReturnRate(0.04);

        assertThatThrownBy(() -> service.updateAllocation(99L, command))
                .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("updateAccountAllocations: floors non-remainder funds and assigns remainder accurately")
    void testUpdateAccountAllocations_FloorAndRemainder() {
        DefinedContributionAccountSnapshot account = createAccountWithThreeFunds(23_000L);
        when(ownershipGuard.loadOwned(dcRepository, 10L)).thenReturn(account);
        when(dcRepository.save(any(DefinedContributionAccountSnapshot.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UpdateAccountAllocationsCommand command = createCommand("F003",
                item("F001", 33.33),
                item("F002", 33.33),
                item("F003", 33.34));

        DefinedContributionAccountSnapshotResult result = service.updateAccountAllocations(10L, command);

        assertThat(result.getRemainderFundCode()).isEqualTo("F003");
        // floor(23000 * 33.33 / 100) = floor(7665.9) = 7665
        // remainder = 23000 - (7665 + 7665) = 7670
        assertThat(result.getFunds()).hasSize(3);
        assertThat(findResultFund(result, "F001").getAllocationPercentage()).isEqualTo(33.33);
        assertThat(findResultFund(result, "F001").getMonthlyContributionAmount()).isEqualTo(7_665L);
        assertThat(findResultFund(result, "F002").getAllocationPercentage()).isEqualTo(33.33);
        assertThat(findResultFund(result, "F002").getMonthlyContributionAmount()).isEqualTo(7_665L);
        assertThat(findResultFund(result, "F003").getAllocationPercentage()).isEqualTo(33.34);
        assertThat(findResultFund(result, "F003").getMonthlyContributionAmount()).isEqualTo(7_670L);

        @SuppressWarnings("null")
        long totalAssigned = result.getFunds().stream()
                .mapToLong(DefinedContributionFundSnapshotResult::getMonthlyContributionAmount)
                .sum();
        assertThat(totalAssigned).isEqualTo(23_000L);
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("updateAccountAllocations: avoids IEEE-754 floor truncation error on 57% of 10000")
    void testUpdateAccountAllocations_ExactDecimalFloor() {
        DefinedContributionAccountSnapshot account = new DefinedContributionAccountSnapshot(
                LocalDate.of(2026, 1, 1), DefinedContributionType.IDECO,
                100_000L, 100_000L, 0L, 10_000L, 10_000L);
        ReflectionTestUtils.setField(account, "id", 11L);
        account.addFund(new DefinedContributionFundSnapshot(
                "海外株式", "Fund A", "F001", 0, 0, 0, 0, 0, 0, 0L, 0.0));
        account.addFund(new DefinedContributionFundSnapshot(
                "国内株式", "Fund B", "F002", 0, 0, 0, 0, 0, 0, 0L, 0.0));

        when(ownershipGuard.loadOwned(dcRepository, 11L)).thenReturn(account);
        when(dcRepository.save(any(DefinedContributionAccountSnapshot.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UpdateAccountAllocationsCommand command = createCommand("F002",
                item("F001", 57.0),
                item("F002", 43.0));

        DefinedContributionAccountSnapshotResult result = service.updateAccountAllocations(11L, command);

        assertThat(findResultFund(result, "F001").getMonthlyContributionAmount()).isEqualTo(5_700L);
        assertThat(findResultFund(result, "F002").getMonthlyContributionAmount()).isEqualTo(4_300L);
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("updateAccountAllocations: rejects when sum is not 100% with 400 Bad Request")
    void testUpdateAccountAllocations_SumNot100() {
        DefinedContributionAccountSnapshot account = createAccountWithThreeFunds(23_000L);
        when(ownershipGuard.loadOwned(dcRepository, 10L)).thenReturn(account);

        UpdateAccountAllocationsCommand command = createCommand("F003",
                item("F001", 40.0),
                item("F002", 30.0),
                item("F003", 29.0));

        assertThatThrownBy(() -> service.updateAccountAllocations(10L, command))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(dcRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateAccountAllocations: rejects null or blank remainderFundCode with 400 Bad Request")
    void testUpdateAccountAllocations_MissingRemainderFundCode() {
        UpdateAccountAllocationsCommand nullRemainder = createCommand(null,
                item("F001", 50.0),
                item("F002", 50.0));
        UpdateAccountAllocationsCommand blankRemainder = createCommand("   ",
                item("F001", 50.0),
                item("F002", 50.0));

        assertThatThrownBy(() -> service.updateAccountAllocations(10L, nullRemainder))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> service.updateAccountAllocations(10L, blankRemainder))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("updateAccountAllocations: rejects remainderFundCode not in account with 400 Bad Request")
    void testUpdateAccountAllocations_InvalidRemainderFundCode() {
        DefinedContributionAccountSnapshot account = createAccountWithThreeFunds(23_000L);
        when(ownershipGuard.loadOwned(dcRepository, 10L)).thenReturn(account);

        UpdateAccountAllocationsCommand command = createCommand("UNKNOWN",
                item("F001", 50.0),
                item("F002", 30.0),
                item("F003", 20.0));

        assertThatThrownBy(() -> service.updateAccountAllocations(10L, command))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(dcRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("updateAccountAllocations: rejects partial fund list with 400 Bad Request")
    void testUpdateAccountAllocations_MissingFundInRequest() {
        DefinedContributionAccountSnapshot account = createAccountWithThreeFunds(23_000L);
        when(ownershipGuard.loadOwned(dcRepository, 10L)).thenReturn(account);

        UpdateAccountAllocationsCommand command = createCommand("F001",
                item("F001", 60.0),
                item("F002", 40.0));

        assertThatThrownBy(() -> service.updateAccountAllocations(10L, command))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(dcRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("updateAccountAllocations: rejects duplicate fundCode with 400 Bad Request")
    void testUpdateAccountAllocations_DuplicateFundCode() {
        DefinedContributionAccountSnapshot account = createAccountWithThreeFunds(23_000L);
        when(ownershipGuard.loadOwned(dcRepository, 10L)).thenReturn(account);

        UpdateAccountAllocationsCommand command = createCommand("F001",
                item("F001", 50.0),
                item("F001", 50.0));

        assertThatThrownBy(() -> service.updateAccountAllocations(10L, command))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(dcRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateAccountAllocations: throws 404 when account does not exist")
    void testUpdateAccountAllocations_AccountNotFound() {
        when(ownershipGuard.loadOwned(dcRepository, 999L))
                .thenThrow(new ItemNotFoundException("Resource not found"));

        UpdateAccountAllocationsCommand command = createCommand("F001", item("F001", 100.0));

        assertThatThrownBy(() -> service.updateAccountAllocations(999L, command))
                .isInstanceOf(ItemNotFoundException.class);
    }

    private DefinedContributionAccountSnapshot createAccountWithThreeFunds(long monthlyTotalContribution) {
        DefinedContributionAccountSnapshot account = new DefinedContributionAccountSnapshot(
                LocalDate.of(2026, 1, 1),
                DefinedContributionType.IDECO,
                100_000L,
                100_000L,
                0L,
                monthlyTotalContribution,
                monthlyTotalContribution);
        ReflectionTestUtils.setField(account, "id", 10L);
        account.addFund(new DefinedContributionFundSnapshot("海外株式", "Fund A", "F001",
                0, 0, 0, 0, 0, 0, 0L, 0.0));
        account.addFund(new DefinedContributionFundSnapshot("国内株式", "Fund B", "F002",
                0, 0, 0, 0, 0, 0, 0L, 0.0));
        account.addFund(new DefinedContributionFundSnapshot("国内債券", "Fund C", "F003",
                0, 0, 0, 0, 0, 0, 0L, 0.0));
        return account;
    }

    private UpdateAccountAllocationsCommand createCommand(String remainderFundCode, FundAllocationCommand... items) {
        UpdateAccountAllocationsCommand command = new UpdateAccountAllocationsCommand();
        command.setRemainderFundCode(remainderFundCode);
        command.setAllocations(List.of(items));
        return command;
    }

    private FundAllocationCommand item(String fundCode, double pct) {
        FundAllocationCommand cmd = new FundAllocationCommand();
        cmd.setFundCode(fundCode);
        cmd.setAllocationPercentage(pct);
        return cmd;
    }

    private DefinedContributionFundSnapshotResult findResultFund(
            DefinedContributionAccountSnapshotResult result, String fundCode) {
        return result.getFunds().stream()
                .filter(f -> fundCode.equals(f.getFundCode()))
                .findFirst()
                .orElseThrow();
    }
}
