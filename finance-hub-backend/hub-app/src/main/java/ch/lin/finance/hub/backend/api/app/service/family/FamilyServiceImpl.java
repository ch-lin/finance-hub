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
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.family.FamilyGroupRepository;
import ch.lin.finance.hub.backend.api.app.repository.family.FamilyMemberRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.UserProfileRepository;
import ch.lin.finance.hub.backend.api.app.service.family.result.FamilyGroupResult;
import ch.lin.finance.hub.backend.api.app.service.family.result.FamilyMemberResult;
import ch.lin.finance.hub.backend.api.domain.model.family.FamilyGroup;
import ch.lin.finance.hub.backend.api.domain.model.family.FamilyMember;
import ch.lin.finance.hub.backend.api.domain.model.family.FamilyRole;
import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyServiceImpl implements FamilyService {

    private final FamilyGroupRepository familyGroupRepository;
    private final FamilyMemberRepository familyMembershipRepository;
    private final UserProfileRepository userProfileRepository;

    @Override
    @Transactional
    public FamilyGroupResult createFamilyGroup(String currentUserId, String name) {
        if (familyMembershipRepository.findByUserId(currentUserId).isPresent()) {
            throw new IllegalStateException("User already belongs to a family group.");
        }

        FamilyGroup group = new FamilyGroup(name);
        group = familyGroupRepository.save(group);

        generateInviteCode(group);
        familyGroupRepository.save(group);

        FamilyMember membership = new FamilyMember(currentUserId, group, FamilyRole.ADMIN);
        familyMembershipRepository.save(membership);

        log.info("Created new family group '{}' (id: {}) by user {}", name, group.getId(), currentUserId);
        return FamilyGroupResult.fromEntity(group);
    }

    @Override
    @Transactional
    public FamilyGroupResult refreshInviteCode(String currentUserId) {
        FamilyMember membership = familyMembershipRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new IllegalStateException("User does not belong to any family group."));

        if (membership.getRole() != FamilyRole.ADMIN) {
            throw new IllegalStateException("Only an ADMIN can refresh the invite code.");
        }

        FamilyGroup group = membership.getFamilyGroup();
        generateInviteCode(group);
        familyGroupRepository.save(Objects.requireNonNull(group));

        log.info("Refreshed invite code for family group '{}'", group.getId());
        return FamilyGroupResult.fromEntity(group);
    }

    @Override
    @Transactional
    public void joinFamily(String currentUserId, String inviteCode) {
        if (familyMembershipRepository.findByUserId(currentUserId).isPresent()) {
            throw new IllegalStateException("User already belongs to a family group.");
        }

        FamilyGroup group = familyGroupRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite code."));

        if (group.getInviteCodeExpiresAt() == null || group.getInviteCodeExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Invite code has expired.");
        }

        FamilyMember membership = new FamilyMember(currentUserId, group, FamilyRole.MEMBER);
        familyMembershipRepository.save(membership);

        log.info("User {} joined family group '{}'", currentUserId, group.getId());
    }

    @Override
    @Transactional
    public void removeMember(String currentUserId, String targetUserId) {
        FamilyMember currentUserMembership = familyMembershipRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new IllegalStateException("Current user does not belong to a family group."));

        // Case 1: Member leaving themselves
        if (currentUserId.equals(targetUserId)) {
            if (currentUserMembership.getRole() == FamilyRole.ADMIN) {
                throw new IllegalStateException("ADMIN cannot leave the group. Transfer ownership or disband instead.");
            }
            familyMembershipRepository.deleteByUserId(currentUserId);
            log.info("User {} left family group '{}'", currentUserId, currentUserMembership.getFamilyGroup().getId());
            return;
        }

        // Case 2: Admin kicking someone else
        if (currentUserMembership.getRole() != FamilyRole.ADMIN) {
            throw new IllegalStateException("Only ADMIN can remove other members.");
        }

        FamilyMember targetUserMembership = familyMembershipRepository.findByUserId(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Target user does not exist in a family group."));

        if (!targetUserMembership.getFamilyGroup().getId().equals(currentUserMembership.getFamilyGroup().getId())) {
            throw new IllegalStateException("Target user is not in your family group.");
        }

        familyMembershipRepository.deleteByUserId(targetUserId);
        log.info("User {} removed user {} from family group '{}'", currentUserId, targetUserId,
                currentUserMembership.getFamilyGroup().getId());
    }

    @Override
    @Transactional
    public void disbandFamily(String currentUserId) {
        FamilyMember currentUserMembership = familyMembershipRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new IllegalStateException("Current user does not belong to a family group."));

        if (currentUserMembership.getRole() != FamilyRole.ADMIN) {
            throw new IllegalStateException("Only an ADMIN can disband the family group.");
        }

        FamilyGroup group = currentUserMembership.getFamilyGroup();
        List<FamilyMember> members = familyMembershipRepository.findByFamilyGroupId(group.getId());
        familyMembershipRepository.deleteAll(Objects.requireNonNull(members));
        familyGroupRepository.delete(group);

        log.info("Disbanded family group '{}' (id: {}) by admin {}", group.getName(), group.getId(), currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FamilyMemberResult> getFamilyMembers(String currentUserId) {
        FamilyMember currentUserMembership = familyMembershipRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new IllegalStateException("User does not belong to any family group."));

        List<FamilyMember> memberships = familyMembershipRepository
                .findByFamilyGroupId(currentUserMembership.getFamilyGroup().getId());

        return memberships.stream().map(membership -> {
            String userId = membership.getUserId();
            Optional<UserProfile> profileOpt = userProfileRepository.findById(Objects.requireNonNull(userId));
            String displayName = profileOpt
                    .map(p -> p.getDisplayName())
                    .filter(name -> name != null && !name.trim().isEmpty())
                    .orElse("User-" + userId.substring(0, Math.min(4, userId.length())));

            FamilyMemberResult result = FamilyMemberResult.builder()
                    .userId(userId)
                    .displayName(displayName)
                    .role(membership.getRole())
                    .joinedAt(membership.getJoinedAt())
                    .build();
            result.setOwnerUserId(currentUserId);
            return result;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getFamilyMemberIds(String currentUserId) {
        FamilyMember currentUserMembership = familyMembershipRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new IllegalStateException("User does not belong to any family group."));

        return familyMembershipRepository.findByFamilyGroupId(currentUserMembership.getFamilyGroup().getId())
                .stream().map(m -> m.getUserId()).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FamilyGroupResult> getFamilyGroup(String currentUserId) {
        return familyMembershipRepository.findByUserId(currentUserId)
                .map(membership -> FamilyGroupResult.fromEntity(membership.getFamilyGroup()));
    }

    private void generateInviteCode(FamilyGroup group) {
        String code = java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        group.setInviteCode(code);
        group.setInviteCodeExpiresAt(LocalDateTime.now().plusMinutes(15));
    }
}
