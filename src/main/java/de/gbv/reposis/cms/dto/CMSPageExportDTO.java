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
package de.gbv.reposis.cms.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for exporting/importing CMS pages with all versions.
 */
public class CMSPageExportDTO {

    private String slug;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    private List<CMSVersionExportDTO> versions;

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    public CMSPageExportDTO() {
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<CMSVersionExportDTO> getVersions() {
        return versions;
    }

    public void setVersions(List<CMSVersionExportDTO> versions) {
        this.versions = versions;
    }
}
