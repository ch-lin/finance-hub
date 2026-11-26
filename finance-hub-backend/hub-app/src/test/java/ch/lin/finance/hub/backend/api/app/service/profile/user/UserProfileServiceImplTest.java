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
package ch.lin.finance.hub.backend.api.app.service.profile.user;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.finance.hub.backend.api.app.repository.accounting.insurance.InsurancePolicyRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.UserProfileRepository;
import ch.lin.finance.hub.backend.api.app.service.profile.user.command.SaveUserProfileCommand;
import ch.lin.finance.hub.backend.api.app.service.profile.user.result.UserProfileResult;
import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceImplTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private InsurancePolicyRepository insurancePolicyRepository;

    @InjectMocks
    private UserProfileServiceImpl userProfileService;

    private UserProfile profile;

    @BeforeEach
    void setUp() {
        profile = new UserProfile("user-123", LocalDate.of(1990, 1, 1), 200000L, 65);
    }

    @Test
    void saveUserProfile_savesDisplayNameSuccessfully() {
        SaveUserProfileCommand command = new SaveUserProfileCommand(
                "Alex",
                LocalDate.of(1990, 1, 1),
                CurrencyCode.JPY,
                250000L,
                65,
                65,
                Collections.emptyList(),
                Collections.emptyList(),
                85,
                60,
                true);

        when(userProfileRepository.findById("user-123")).thenReturn(Optional.of(profile));
        when(userProfileRepository.save(Objects.requireNonNull(profile)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(insurancePolicyRepository.findAll()).thenReturn(Collections.emptyList());

        UserProfileResult result = userProfileService.saveUserProfile("user-123", command);

        assertThat(result).isNotNull();
        assertThat(result.getDisplayName()).isEqualTo("Alex");
        assertThat(profile.getDisplayName()).isEqualTo("Alex");
        verify(userProfileRepository).save(Objects.requireNonNull(profile));
    }

    @Test
    void getUserProfile_returnsDisplayName() {
        profile.setDisplayName("Alex");
        when(userProfileRepository.findById("user-123")).thenReturn(Optional.of(profile));
        when(insurancePolicyRepository.findAll()).thenReturn(Collections.emptyList());

        Optional<UserProfileResult> resultOpt = userProfileService.getUserProfile("user-123");

        assertThat(resultOpt).isPresent();
        assertThat(resultOpt.get().getDisplayName()).isEqualTo("Alex");
    }
}
