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
package ch.lin.finance.hub.backend.api.app.repository.family;

import ch.lin.finance.hub.backend.api.domain.model.family.FamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {

    /**
     * Find a user's family membership. Since we enforce one family per user,
     * this will return at most one result.
     *
     * @param userId the user ID
     * @return the membership if it exists
     */
    Optional<FamilyMember> findByUserId(String userId);

    /**
     * Find all members of a specific family group.
     *
     * @param familyGroupId the family group ID
     * @return the list of memberships
     */
    List<FamilyMember> findByFamilyGroupId(Long familyGroupId);

    /**
     * Delete a user's membership.
     *
     * @param userId the user ID to delete
     */
    void deleteByUserId(String userId);
}
