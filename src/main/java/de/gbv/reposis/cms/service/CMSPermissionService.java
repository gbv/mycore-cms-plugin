/*
 * This file is part of ***  M y C o R e  ***
 * See https://www.mycore.de/ for details.
 *
 * MyCoRe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyCoRe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MyCoRe.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.gbv.reposis.cms.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.mycore.access.MCRAccessManager;

import de.gbv.reposis.cms.model.CMSPage;
import de.gbv.reposis.cms.model.CMSPageStatus;
import de.gbv.reposis.cms.model.CMSPageVersion;

/**
 * Service for checking CMS permissions.
 */
public class CMSPermissionService {

    public static final String PERMISSION_PAGE_READ = "read";
    public static final String PERMISSION_PAGE_WRITE = "write";
    public static final String PERMISSION_PAGE_DELETE = "delete";
    public static final String PERMISSION_PAGE_READ_DRAFT = "read-draft";
    public static final String PERMISSION_PAGE_READ_ARCHIVED = "read-archived";
    public static final String PERMISSION_PAGE_READ_VERSIONS = "read-versions";

    private static final String PERMISSION_ASSETS_READ = "read";
    private static final String PERMISSION_ASSETS_WRITE = "write";
    private static final String PERMISSION_ASSETS_DELETE = "delete";
    private static final String PERMISSION_ASSETS_ID_PREFIX = "cms:asset:";

    /**
     * Check if the current user has a specific permission on a page.
     *
     * @param page       the page to check
     * @param permission the permission to check
     * @return true if the user has the permission
     */
    public boolean checkPermission(CMSPage page, String permission) {
        return MCRAccessManager.checkPermission(page.getPermissionId(), permission);
    }

    /**
     * Check if the current user can read a page at all.
     * The visibility depends on the last non-draft version:
     * - If last non-draft version is published: user needs "read" permission
     * - If last non-draft version is archived: user needs "read-archived" permission
     * - If no non-draft version exists: user needs "read-draft" permission to see the drafts
     *
     * @param page     the page to check
     * @param versions the list of all versions of the page (sorted by version number desc)
     * @return true if the user can read the page
     */
    public boolean canReadPage(CMSPage page, List<CMSPageVersion> versions) {
        if (!MCRAccessManager.checkPermission(page.getPermissionId(), PERMISSION_PAGE_READ)) {
            return false;
        }

        // Find the last non-draft version
        Optional<CMSPageVersion> lastNonDraftVersion = versions.stream()
            .sorted(Comparator.comparingInt(CMSPageVersion::getVersionNumber).reversed())
            .filter(v -> v.getStatus() != CMSPageStatus.DRAFT)
            .findFirst();

        if (lastNonDraftVersion.isEmpty()) {
            // No non-draft version exists, only users with read-draft can see the page
            return MCRAccessManager.checkPermission(page.getPermissionId(), PERMISSION_PAGE_READ_DRAFT);
        }

        CMSPageVersion version = lastNonDraftVersion.get();
        if (version.getStatus() == CMSPageStatus.ARCHIVED) {
            // Last non-draft version is archived, need read-archived permission
            return MCRAccessManager.checkPermission(page.getPermissionId(), PERMISSION_PAGE_READ_ARCHIVED);
        }

        // Last non-draft version is published, read permission is sufficient
        return true;
    }

    /**
     * Check if the current user can read a specific version.
     * Assumes canReadPage has already been checked.
     *
     * @param page    the page
     * @param version the version to check
     * @return true if the user can read the version
     */
    public boolean canReadVersion(CMSPage page, CMSPageVersion version) {
        if (!MCRAccessManager.checkPermission(page.getPermissionId(), PERMISSION_PAGE_READ)) {
            return false;
        }
        if (version.getStatus() == CMSPageStatus.DRAFT) {
            return MCRAccessManager.checkPermission(page.getPermissionId(),
                PERMISSION_PAGE_READ_DRAFT);
        }
        if (version.getStatus() == CMSPageStatus.ARCHIVED) {
            return MCRAccessManager.checkPermission(page.getPermissionId(),
                PERMISSION_PAGE_READ_ARCHIVED);
        }
        return true;
    }

    /**
     * Check if the current user can read all versions of a page.
     *
     * @param page the page
     * @return true if the user can read all versions
     */
    public boolean canReadVersions(CMSPage page) {
        return checkPermission(page, PERMISSION_PAGE_READ) && checkPermission(page,
            PERMISSION_PAGE_READ_VERSIONS);
    }

    /**
     * Check if the current user can write to a page.
     *
     * @param page the page
     * @return true if the user can write
     */
    public boolean canWrite(CMSPage page) {
        return checkPermission(page, PERMISSION_PAGE_WRITE);
    }

    /**
     * Check if the current user can delete a page.
     *
     * @param page the page
     * @return true if the user can delete
     */
    public boolean canDelete(CMSPage page) {
        return checkPermission(page, PERMISSION_PAGE_DELETE);
    }

    /**
     * Check if the current user can read an asset.
     *
     * @param assetId the asset ID
     * @return true if the user can read the asset
     */
    public boolean canReadAsset(String assetId) {
        String permissionId = PERMISSION_ASSETS_ID_PREFIX + assetId;
        return MCRAccessManager.checkPermission(permissionId, PERMISSION_ASSETS_READ);
    }

    /**
     * Check if the current user can write to an asset.
     *
     * @param assetId the asset ID
     * @return true if the user can write to the asset
     */
    public boolean canWriteAsset(String assetId) {
        String permissionId = PERMISSION_ASSETS_ID_PREFIX + assetId;
        return MCRAccessManager.checkPermission(permissionId, PERMISSION_ASSETS_WRITE);
    }

    /**
     * Check if the current user can delete an asset.
     *
     * @param assetId the asset ID
     * @return true if the user can delete the asset
     */
    public boolean canDeleteAsset(String assetId) {
        String permissionId = PERMISSION_ASSETS_ID_PREFIX + assetId;
        return MCRAccessManager.checkPermission(permissionId, PERMISSION_ASSETS_DELETE);
    }

}
