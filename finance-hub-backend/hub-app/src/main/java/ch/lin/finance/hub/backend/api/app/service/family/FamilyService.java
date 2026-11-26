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

import java.util.List;

import ch.lin.finance.hub.backend.api.app.service.family.result.FamilyGroupResult;
import ch.lin.finance.hub.backend.api.app.service.family.result.FamilyMemberResult;

public interface FamilyService {

    /**
     * Create a new family group.
     *
     * @param currentUserId the ID of the user creating the group
     * @param name the name of the family group
     * @return the created family group
     */
    FamilyGroupResult createFamilyGroup(String currentUserId, String name);

    /**
     * Refresh the invite code for the current user's family group.
     *
     * @param currentUserId the ID of the user requesting the refresh (must be
     * ADMIN)
     * @return the family group with the new invite code
     */
    FamilyGroupResult refreshInviteCode(String currentUserId);

    /**
     * Join an existing family group using an invite code.
     *
     * @param currentUserId the ID of the user joining
     * @param inviteCode the invite code
     */
    void joinFamily(String currentUserId, String inviteCode);

    /**
     * Remove a member from the family group (or leave).
     *
     * @param currentUserId the ID of the user performing the action
     * @param targetUserId the ID of the user to be removed
     */
    void removeMember(String currentUserId, String targetUserId);

    /**
     * Disband the family group. Only the ADMIN can perform this action.
     * Removes all members and deletes the family group entity.
     *
     * @param currentUserId the ID of the user requesting disbandment
     */
    void disbandFamily(String currentUserId);

    /**
     * Get a list of family members for the current user's family group.
     *
     * @param currentUserId the ID of the current user
     * @return list of family members with display names
     */
    List<FamilyMemberResult> getFamilyMembers(String currentUserId);

    /**
     * Internal method to get just the user IDs of the family members for
     * filtering.
     *
     * @param currentUserId the ID of the current user
     * @return list of user IDs in the family
     */
    List<String> getFamilyMemberIds(String currentUserId);

    /**
     * Get the family group that the current user belongs to, if any.
     *
     * @param currentUserId the ID of the current user
     * @return optional containing the family group result, or empty if not in
     * any group
     */
    java.util.Optional<FamilyGroupResult> getFamilyGroup(String currentUserId);
}
