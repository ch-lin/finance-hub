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
import java.util.Map;
import java.util.Optional;

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
import org.springframework.web.server.ResponseStatusException;

import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionAccountSnapshotRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionUserSettingsRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.FundAllocationCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.SaveDcUserSettingsCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAccountAllocationsCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DcUserSettingsResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DcTargetAllocationSetting;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionAccountSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionFundSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionUserSettings;

@ExtendWith(MockitoExtension.class)
class DefinedContributionUserSettingsServiceImplTest {

    @Mock
    private DefinedContributionUserSettingsRepository repository;

    @Mock
    private DefinedContributionAccountSnapshotRepository dcAccountRepository;

    @InjectMocks
    private DefinedContributionUserSettingsServiceImpl service;

    @Test
    @SuppressWarnings("null")
    @DisplayName("updateTargetAllocations: stores CORPORATE_DC and IDECO independently even with overlapping fundCodes")
    void testUpdateTargetAllocations_IsolatesAccountTypes() {
        DefinedContributionAccountSnapshot dcSnapshot = createSnapshot(DefinedContributionType.CORPORATE_DC,
                "SHARED_CODE", "DC_ONLY");
        DefinedContributionAccountSnapshot idecoSnapshot = createSnapshot(DefinedContributionType.IDECO,
                "SHARED_CODE", "IDECO_ONLY");

        DefinedContributionUserSettings existing = new DefinedContributionUserSettings();
        existing.updateTargetAllocation(DefinedContributionType.CORPORATE_DC,
                new DcTargetAllocationSetting("DC_ONLY", Map.of("SHARED_CODE", 70.0, "DC_ONLY", 30.0)));

        when(dcAccountRepository.findFirstByTypeOrderByRecordDateDesc(DefinedContributionType.IDECO))
                .thenReturn(Optional.of(idecoSnapshot));
        when(repository.findFirstBy()).thenReturn(Optional.of(existing));
        when(repository.save(any(DefinedContributionUserSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateAccountAllocationsCommand idecoCommand = createCommand("IDECO_ONLY",
                item("SHARED_CODE", 40.0),
                item("IDECO_ONLY", 60.0));

        DcUserSettingsResult result = service.updateTargetAllocations(DefinedContributionType.IDECO, idecoCommand);

        assertThat(result.getIdecoTargetAllocation()).isNotNull();
        assertThat(result.getIdecoTargetAllocation().getRemainderFundCode()).isEqualTo("IDECO_ONLY");
        assertThat(result.getIdecoTargetAllocation().getAllocations())
                .containsEntry("SHARED_CODE", 40.0)
                .containsEntry("IDECO_ONLY", 60.0);

        // CORPORATE_DC target allocation must remain untouched
        assertThat(result.getDcTargetAllocation()).isNotNull();
        assertThat(result.getDcTargetAllocation().getRemainderFundCode()).isEqualTo("DC_ONLY");
        assertThat(result.getDcTargetAllocation().getAllocations())
                .containsEntry("SHARED_CODE", 70.0)
                .containsEntry("DC_ONLY", 30.0);

        // Verify dcSnapshot funds were never mutated
        assertThat(dcSnapshot.getFunds()).allSatisfy(f -> assertThat(f.getAllocationPercentage()).isEqualTo(0.0));
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("updateTargetAllocations: creates new settings row when none exists yet")
    void testUpdateTargetAllocations_CreatesSettingsWhenMissing() {
        DefinedContributionAccountSnapshot snapshot = createSnapshot(DefinedContributionType.CORPORATE_DC, "F001");
        when(dcAccountRepository.findFirstByTypeOrderByRecordDateDesc(DefinedContributionType.CORPORATE_DC))
                .thenReturn(Optional.of(snapshot));
        when(repository.findFirstBy()).thenReturn(Optional.empty());
        when(repository.save(any(DefinedContributionUserSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        DcUserSettingsResult result = service.updateTargetAllocations(
                DefinedContributionType.CORPORATE_DC,
                createCommand("F001", item("F001", 100.0)));

        assertThat(result.getDcTargetAllocation()).isNotNull();
        assertThat(result.getDcTargetAllocation().getRemainderFundCode()).isEqualTo("F001");
        assertThat(result.getDcTargetAllocation().getAllocations()).containsEntry("F001", 100.0);
        assertThat(result.getIdecoTargetAllocation()).isNull();
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("updateTargetAllocations: rejects with 400 when no snapshot exists for account type")
    void testUpdateTargetAllocations_NoSnapshotFound() {
        when(dcAccountRepository.findFirstByTypeOrderByRecordDateDesc(DefinedContributionType.IDECO))
                .thenReturn(Optional.empty());

        UpdateAccountAllocationsCommand command = createCommand("F001", item("F001", 100.0));

        assertThatThrownBy(() -> service.updateTargetAllocations(DefinedContributionType.IDECO, command))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(repository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("updateTargetAllocations: rejects partial fund list or sum != 100% with 400")
    void testUpdateTargetAllocations_ValidationErrors() {
        DefinedContributionAccountSnapshot snapshot = createSnapshot(DefinedContributionType.IDECO, "F001", "F002");
        when(dcAccountRepository.findFirstByTypeOrderByRecordDateDesc(DefinedContributionType.IDECO))
                .thenReturn(Optional.of(snapshot));

        UpdateAccountAllocationsCommand missingFund = createCommand("F001", item("F001", 100.0));
        assertThatThrownBy(() -> service.updateTargetAllocations(DefinedContributionType.IDECO, missingFund))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        UpdateAccountAllocationsCommand wrongSum = createCommand("F002", item("F001", 60.0), item("F002", 30.0));
        assertThatThrownBy(() -> service.updateTargetAllocations(DefinedContributionType.IDECO, wrongSum))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(repository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("clearTargetAllocations: clears only specified account type and preserves the other")
    void testClearTargetAllocations_ClearsOnlyTargetType() {
        DefinedContributionUserSettings existing = new DefinedContributionUserSettings();
        existing.updateTargetAllocation(DefinedContributionType.CORPORATE_DC,
                new DcTargetAllocationSetting("F001", Map.of("F001", 100.0)));
        existing.updateTargetAllocation(DefinedContributionType.IDECO,
                new DcTargetAllocationSetting("F002", Map.of("F002", 100.0)));

        when(repository.findFirstBy()).thenReturn(Optional.of(existing));
        when(repository.save(any(DefinedContributionUserSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<DcUserSettingsResult> result = service.clearTargetAllocations(DefinedContributionType.IDECO);

        assertThat(result).isPresent();
        assertThat(result.get().getIdecoTargetAllocation()).isNull();
        assertThat(result.get().getDcTargetAllocation()).isNotNull();
        assertThat(result.get().getDcTargetAllocation().getRemainderFundCode()).isEqualTo("F001");
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("clearTargetAllocations: returns empty when no settings exist")
    void testClearTargetAllocations_EmptyWhenNoSettings() {
        when(repository.findFirstBy()).thenReturn(Optional.empty());

        Optional<DcUserSettingsResult> result = service.clearTargetAllocations(DefinedContributionType.IDECO);

        assertThat(result).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("saveSettings: preserves existing target allocations when updating scalar settings")
    void testSaveSettings_PreservesTargetAllocations() {
        DefinedContributionUserSettings existing = new DefinedContributionUserSettings();
        DcTargetAllocationSetting idecoTarget = new DcTargetAllocationSetting("F001", Map.of("F001", 100.0));
        existing.updateTargetAllocation(DefinedContributionType.IDECO, idecoTarget);

        when(repository.findFirstBy()).thenReturn(Optional.of(existing));
        when(repository.save(any(DefinedContributionUserSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        SaveDcUserSettingsCommand scalarCmd = new SaveDcUserSettingsCommand(
                23_000L, 0.05, 10_000L, false, null, false, null);

        DcUserSettingsResult result = service.saveSettings(scalarCmd);

        assertThat(result.getMonthlyIdecoContribution()).isEqualTo(23_000L);
        assertThat(result.getIdecoTargetAllocation()).isEqualTo(idecoTarget);
    }

    private DefinedContributionAccountSnapshot createSnapshot(DefinedContributionType type, String... fundCodes) {
        DefinedContributionAccountSnapshot snapshot = new DefinedContributionAccountSnapshot(
                LocalDate.of(2026, 1, 1), type, 0L, 0L, 0L, 20_000L, 20_000L);
        for (String code : fundCodes) {
            snapshot.addFund(new DefinedContributionFundSnapshot(
                    "海外株式", "Fund " + code, code, 0.0, 0.0, 0.0, 0L, 0L, 0L, 0L, 0.0));
        }
        return snapshot;
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
}
