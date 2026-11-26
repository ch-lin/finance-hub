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
package ch.lin.finance.hub.backend.api.app.service.family;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import ch.lin.finance.hub.backend.api.app.repository.family.FamilyGroupRepository;
import ch.lin.finance.hub.backend.api.app.repository.family.FamilyMemberRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.UserProfileRepository;
import ch.lin.finance.hub.backend.api.app.service.family.result.FamilyGroupResult;
import ch.lin.finance.hub.backend.api.app.service.family.result.FamilyMemberResult;
import ch.lin.finance.hub.backend.api.domain.model.family.FamilyGroup;
import ch.lin.finance.hub.backend.api.domain.model.family.FamilyMember;
import ch.lin.finance.hub.backend.api.domain.model.family.FamilyRole;
import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;

@ExtendWith(MockitoExtension.class)
class FamilyServiceImplTest {

    @Mock
    private FamilyGroupRepository familyGroupRepository;

    @Mock
    private FamilyMemberRepository familyMembershipRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private FamilyServiceImpl familyService;

    private FamilyGroup group;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        group = new FamilyGroup("Test Family");
        ReflectionTestUtils.setField(group, "id", 100L);
    }

    @Test
    @SuppressWarnings("null")
    void createFamilyGroup_success() {
        when(familyMembershipRepository.findByUserId("user1")).thenReturn(Optional.empty());
        when(familyGroupRepository.save(any(FamilyGroup.class))).thenAnswer(i -> {
            FamilyGroup g = i.getArgument(0);
            ReflectionTestUtils.setField(g, "id", 100L);
            return g;
        });

        FamilyGroupResult result = familyService.createFamilyGroup("user1", "My Family");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("My Family");
        assertThat(result.getInviteCode()).isNotNull().hasSize(6);
        assertThat(result.getInviteCodeExpiresAt()).isAfter(LocalDateTime.now());

        verify(familyGroupRepository, times(2)).save(any(FamilyGroup.class));
        verify(familyMembershipRepository)
                .save(argThat(m -> m.getUserId().equals("user1") && m.getRole() == FamilyRole.ADMIN));
    }

    @Test
    @SuppressWarnings("null")
    void createFamilyGroup_fail_alreadyInFamily() {
        FamilyMember existing = new FamilyMember("user1", group, FamilyRole.MEMBER);
        when(familyMembershipRepository.findByUserId("user1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> familyService.createFamilyGroup("user1", "My Family"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User already belongs to a family group.");

        verify(familyGroupRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    void joinFamily_success() {
        group.setInviteCode("ABCDEF");
        group.setInviteCodeExpiresAt(LocalDateTime.now().plusMinutes(10));

        when(familyMembershipRepository.findByUserId("user2")).thenReturn(Optional.empty());
        when(familyGroupRepository.findByInviteCode("ABCDEF")).thenReturn(Optional.of(group));

        familyService.joinFamily("user2", "ABCDEF");

        verify(familyMembershipRepository)
                .save(argThat(m -> m.getUserId().equals("user2") && m.getRole() == FamilyRole.MEMBER));
    }

    @Test
    void joinFamily_fail_codeExpired() {
        group.setInviteCode("ABCDEF");
        group.setInviteCodeExpiresAt(LocalDateTime.now().minusMinutes(1)); // Expired

        when(familyMembershipRepository.findByUserId("user2")).thenReturn(Optional.empty());
        when(familyGroupRepository.findByInviteCode("ABCDEF")).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> familyService.joinFamily("user2", "ABCDEF"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invite code has expired.");
    }

    @Test
    void joinFamily_fail_alreadyInFamily() {
        FamilyMember existing = new FamilyMember("user2", group, FamilyRole.MEMBER);
        when(familyMembershipRepository.findByUserId("user2")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> familyService.joinFamily("user2", "ABCDEF"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User already belongs to a family group.");
    }

    @Test
    void removeMember_memberLeaves_success() {
        FamilyMember existing = new FamilyMember("user2", group, FamilyRole.MEMBER);
        when(familyMembershipRepository.findByUserId("user2")).thenReturn(Optional.of(existing));

        familyService.removeMember("user2", "user2");

        verify(familyMembershipRepository).deleteByUserId("user2");
    }

    @Test
    void removeMember_adminLeaves_fail() {
        FamilyMember existing = new FamilyMember("admin1", group, FamilyRole.ADMIN);
        when(familyMembershipRepository.findByUserId("admin1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> familyService.removeMember("admin1", "admin1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN cannot leave the group.");
    }

    @Test
    void removeMember_adminKicksMember_success() {
        FamilyMember adminMembership = new FamilyMember("admin1", group, FamilyRole.ADMIN);
        FamilyMember targetMembership = new FamilyMember("user2", group, FamilyRole.MEMBER);

        when(familyMembershipRepository.findByUserId("admin1")).thenReturn(Optional.of(adminMembership));
        when(familyMembershipRepository.findByUserId("user2")).thenReturn(Optional.of(targetMembership));

        familyService.removeMember("admin1", "user2");

        verify(familyMembershipRepository).deleteByUserId("user2");
    }

    @Test
    void removeMember_memberKicksMember_fail() {
        FamilyMember memberMembership = new FamilyMember("user2", group, FamilyRole.MEMBER);
        when(familyMembershipRepository.findByUserId("user2")).thenReturn(Optional.of(memberMembership));

        assertThatThrownBy(() -> familyService.removeMember("user2", "user3"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only ADMIN can remove other members.");
    }

    @Test
    @SuppressWarnings("null")
    void getFamilyGroup_success() {
        FamilyMember member = new FamilyMember("user1", group, FamilyRole.MEMBER);
        when(familyMembershipRepository.findByUserId("user1")).thenReturn(Optional.of(member));

        Optional<FamilyGroupResult> result = familyService.getFamilyGroup("user1");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(100L);
        assertThat(result.get().getName()).isEqualTo("Test Family");
    }

    @Test
    @SuppressWarnings("null")
    void getFamilyGroup_notFound_returnsEmpty() {
        when(familyMembershipRepository.findByUserId("user1")).thenReturn(Optional.empty());

        Optional<FamilyGroupResult> result = familyService.getFamilyGroup("user1");

        assertThat(result).isEmpty();
    }

    @Test
    void disbandFamily_success() {
        FamilyMember adminMembership = new FamilyMember("admin1", group, FamilyRole.ADMIN);
        FamilyMember otherMember = new FamilyMember("user2", group, FamilyRole.MEMBER);
        List<FamilyMember> members = List.of(adminMembership, otherMember);

        when(familyMembershipRepository.findByUserId("admin1")).thenReturn(Optional.of(adminMembership));
        when(familyMembershipRepository.findByFamilyGroupId(group.getId())).thenReturn(members);

        familyService.disbandFamily("admin1");

        verify(familyMembershipRepository).deleteAll(Objects.requireNonNull(members));
        verify(familyGroupRepository).delete(Objects.requireNonNull(group));
    }

    @Test
    void disbandFamily_fail_notInFamily() {
        when(familyMembershipRepository.findByUserId("user1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> familyService.disbandFamily("user1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Current user does not belong to a family group.");
    }

    @Test
    void disbandFamily_fail_notAdmin() {
        FamilyMember member = new FamilyMember("user2", group, FamilyRole.MEMBER);
        when(familyMembershipRepository.findByUserId("user2")).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> familyService.disbandFamily("user2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only an ADMIN can disband the family group.");
    }

    @Test
    @SuppressWarnings("null")
    void getFamilyMembers_resolvesDisplayNameAndFallback() {
        FamilyMember member1 = new FamilyMember("user-alice-1234", group, FamilyRole.ADMIN);
        FamilyMember member2 = new FamilyMember("user-bob-5678", group, FamilyRole.MEMBER);
        FamilyMember member3 = new FamilyMember("user-charlie-9999", group, FamilyRole.MEMBER);

        when(familyMembershipRepository.findByUserId("user-alice-1234")).thenReturn(Optional.of(member1));
        when(familyMembershipRepository.findByFamilyGroupId(group.getId()))
                .thenReturn(List.of(member1, member2, member3));

        UserProfile profile1 = new UserProfile("user-alice-1234", null, null, null);
        profile1.setDisplayName("Alice in Wonderland");
        when(userProfileRepository.findById("user-alice-1234")).thenReturn(Optional.of(profile1));

        UserProfile profile2 = new UserProfile("user-bob-5678", null, null, null);
        profile2.setDisplayName(""); // blank display name
        when(userProfileRepository.findById("user-bob-5678")).thenReturn(Optional.of(profile2));

        when(userProfileRepository.findById("user-charlie-9999")).thenReturn(Optional.empty()); // no profile

        List<FamilyMemberResult> results = familyService.getFamilyMembers("user-alice-1234");

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getDisplayName()).isEqualTo("Alice in Wonderland");
        assertThat(results.get(1).getDisplayName()).isEqualTo("User-user"); // fallback to first 4 chars of userId
        assertThat(results.get(2).getDisplayName()).isEqualTo("User-user"); // fallback
    }
}
