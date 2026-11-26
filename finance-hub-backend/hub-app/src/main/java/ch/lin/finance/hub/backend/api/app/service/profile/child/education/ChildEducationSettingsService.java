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
package ch.lin.finance.hub.backend.api.app.service.profile.child.education;

import ch.lin.finance.hub.backend.api.app.service.profile.child.education.command.UpdateChildEducationSettingsCommand;
import ch.lin.finance.hub.backend.api.app.service.profile.child.education.result.ChildEducationSettingsResult;

public interface ChildEducationSettingsService {

    /**
     * Retrieves the education settings for the given child profile ID.
     *
     * @param childProfileId the ID of the child profile
     * @return the child education settings result
     */
    ChildEducationSettingsResult getSettings(Long childProfileId);

    /**
     * Updates (or creates) the education settings for the given child profile
     * ID.
     *
     * @param childProfileId the ID of the child profile
     * @param command the update command
     * @return the updated child education settings result
     */
    ChildEducationSettingsResult updateSettings(Long childProfileId, UpdateChildEducationSettingsCommand command);

    /**
     * Deletes the education settings for the given child profile ID.
     *
     * @param childProfileId the ID of the child profile
     */
    void deleteSettings(Long childProfileId);
}
