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

import java.util.Optional;

import org.springframework.lang.NonNull;

import ch.lin.finance.hub.backend.api.app.service.profile.user.command.SaveUserProfileCommand;
import ch.lin.finance.hub.backend.api.app.service.profile.user.result.UserProfileResult;

public interface UserProfileService {

    /**
     * Retrieves the user profile by ID.
     *
     * @param id the user ID
     * @return the user profile result if found
     */
    Optional<UserProfileResult> getUserProfile(@NonNull String id);

    /**
     * Saves or updates the user profile.
     *
     * @param id      the user ID
     * @param command the command containing user profile data to save
     * @return the saved user profile result
     */
    UserProfileResult saveUserProfile(@NonNull String id, SaveUserProfileCommand command);
}
