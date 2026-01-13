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

package de.gbv.reposis.cms.model;

/**
 * Status enumeration for CMS page versions.
 */
public enum CMSPageStatus {
    /**
     * Draft version, not yet published.
     */
    DRAFT("draft"),

    /**
     * Published version, visible to users with read permission.
     */
    PUBLISHED("published"),

    /**
     * Archived version, page has been taken offline.
     */
    ARCHIVED("archived");

    private final String value;

    CMSPageStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static CMSPageStatus fromValue(String value) {
        for (CMSPageStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status: " + value);
    }
}
