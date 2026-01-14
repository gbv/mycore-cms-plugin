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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.mycore.backend.jpa.MCREntityManagerProvider;
import org.mycore.common.MCRSessionMgr;

import de.gbv.reposis.cms.dto.CMSCreateVersionDTO;
import de.gbv.reposis.cms.dto.CMSPageDetailDTO;
import de.gbv.reposis.cms.dto.CMSPageListDTO;
import de.gbv.reposis.cms.dto.CMSTranslationDTO;
import de.gbv.reposis.cms.dto.CMSTranslationDetailDTO;
import de.gbv.reposis.cms.dto.CMSVersionDetailDTO;
import de.gbv.reposis.cms.dto.CMSVersionInfoDTO;
import de.gbv.reposis.cms.dto.CMSVersionSummaryDTO;
import de.gbv.reposis.cms.model.CMSLanguage;
import de.gbv.reposis.cms.model.CMSPage;
import de.gbv.reposis.cms.model.CMSPageStatus;
import de.gbv.reposis.cms.model.CMSPageVersion;
import de.gbv.reposis.cms.model.CMSPageVersionTranslation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;

/**
 * Service for CMS page operations.
 */
public class CMSPageService {

    private final CMSPermissionService permissionService = new CMSPermissionService();

    /**
     * Get all pages that the current user has read permission for.
     */
    public List<CMSPageListDTO> getAllPages() {
        EntityManager em = MCREntityManagerProvider.getCurrentEntityManager();
        TypedQuery<CMSPage> query = em.createQuery("SELECT p FROM CMSPage p", CMSPage.class);
        return query.getResultList().stream()
            .filter(page -> {
                List<CMSPageVersion> versions = getVersionEntities(em, page.getId());
                return permissionService.canReadPage(page, versions);
            })
            .map(this::toPageListDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get a page by slug.
     */
    public Optional<CMSPageListDTO> getPageBySlug(String slug) {
        EntityManager em = MCREntityManagerProvider.getCurrentEntityManager();
        TypedQuery<CMSPage> query = em.createQuery(
            "SELECT p FROM CMSPage p WHERE p.slug = :slug", CMSPage.class);
        query.setParameter("slug", slug);
        try {
            CMSPage page = query.getSingleResult();
            List<CMSPageVersion> versions = getVersionEntities(em, page.getId());
            if (permissionService.canReadPage(page, versions)) {
                return Optional.of(toPageListDTO(page));
            }
            return Optional.empty();
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Check if a page with the given slug exists.
     */
    public boolean slugExists(String slug) {
        EntityManager em = MCREntityManagerProvider.getCurrentEntityManager();
        TypedQuery<Long> query = em.createQuery(
            "SELECT COUNT(p) FROM CMSPage p WHERE p.slug = :slug", Long.class);
        query.setParameter("slug", slug);
        return query.getSingleResult() > 0;
    }

    /**
     * Get a page by ID with all versions.
     */
    public Optional<CMSPageDetailDTO> getPageById(Long pageId) {
        EntityManager em = MCREntityManagerProvider.getCurrentEntityManager();
        CMSPage page = em.find(CMSPage.class, pageId);
        if (page == null) {
            return Optional.empty();
        }
        List<CMSPageVersion> versions = getVersionEntities(em, pageId);
        if (!permissionService.canReadPage(page, versions)) {
            return Optional.empty();
        }
        return Optional.of(toPageDetailDTO(page));
    }

    /**
     * Get the internal page entity by ID.
     */
    public Optional<CMSPage> getPageEntityById(Long pageId) {
        EntityManager em = MCREntityManagerProvider.getCurrentEntityManager();
        CMSPage page = em.find(CMSPage.class, pageId);
        return Optional.ofNullable(page);
    }

    /**
     * Create a new page.
     */
    public CMSPage createPage(String slug) {
        EntityManager em = MCREntityManagerProvider.getCurrentEntityManager();
        CMSPage page = new CMSPage(slug);
        em.persist(page);
        em.flush(); // Ensure ID is generated
        return page;
    }

    /**
     * Delete a page (set to archived status).
     */
    public boolean deletePage(Long pageId, String userId) {
        EntityManager em = MCREntityManagerProvider.getCurrentEntityManager();
        CMSPage page = em.find(CMSPage.class, pageId);
        if (page == null) {
            return false;
        }
        if (!permissionService.canDelete(page)) {
            return false;
        }

        // Create a new archived version
        int nextVersionNumber = getNextVersionNumber(page);
        CMSPageVersion archivedVersion = new CMSPageVersion(page, nextVersionNumber, userId, CMSPageStatus.ARCHIVED);
        page.addVersion(archivedVersion);
        em.persist(archivedVersion);
        return true;
    }

    /**
     * Get all versions of a page.
     */
    @SuppressWarnings("PMD.AvoidDuplicateLiterals")
    public List<CMSVersionInfoDTO> getVersions(Long pageId) {
        EntityManager em = MCREntityManagerProvider.getCurrentEntityManager();
        CMSPage page = em.find(CMSPage.class, pageId);
        if (page == null) {
            return List.of();
        }
        List<CMSPageVersion> versions = getVersionEntities(em, pageId);
        if (!permissionService.canReadPage(page, versions) || !permissionService.canReadVersions(page)) {
            return List.of();
        }
        return versions.stream()
            .filter(v -> permissionService.canReadVersion(page, v))
            .map(this::toVersionInfoDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get the current (highest) version of a page.
     */
    public Optional<CMSVersionDetailDTO> getCurrentVersion(Long pageId) {
        EntityManager em = MCREntityManagerProvider.getCurrentEntityManager();
        CMSPage page = em.find(CMSPage.class, pageId);
        if (page == null) {
            return Optional.empty();
        }
        List<CMSPageVersion> versions = getVersionEntities(em, pageId);
        if (!permissionService.canReadPage(page, versions)) {
            return Optional.empty();
        }
        return versions.stream()
            .filter(v -> permissionService.canReadVersion(page, v))
            .findFirst()
            .map(this::toVersionDetailDTO);
    }

    /**
     * Get the highest published version of a page.
     */
    public Optional<CMSVersionDetailDTO> getPublishedVersion(Long pageId) {
        EntityManager em = MCREntityManagerProvider.getCurrentEntityManager();
        CMSPage page = em.find(CMSPage.class, pageId);
        if (page == null) {
            return Optional.empty();
        }
        List<CMSPageVersion> versions = getVersionEntities(em, pageId);
        if (!permissionService.canReadPage(page, versions)) {
            return Optional.empty();
        }
        return versions.stream()
            .filter(v -> v.getStatus() == CMSPageStatus.PUBLISHED)
            .findFirst()
            .map(this::toVersionDetailDTO);
    }

    /**
     * Get a specific version of a page.
     */
    public Optional<CMSVersionDetailDTO> getVersion(Long pageId, Integer versionNumber) {
        EntityManager em = MCREntityManagerProvider.getCurrentEntityManager();
        CMSPage page = em.find(CMSPage.class, pageId);
        if (page == null) {
            return Optional.empty();
        }
        List<CMSPageVersion> versions = getVersionEntities(em, pageId);
        if (!permissionService.canReadPage(page, versions)) {
            return Optional.empty();
        }
        return versions.stream()
            .filter(v -> v.getVersionNumber().equals(versionNumber))
            .filter(v -> permissionService.canReadVersion(page, v))
            .findFirst()
            .map(this::toVersionDetailDTO);
    }

    /**
     * Get a specific translation of a version.
     */
    public Optional<CMSTranslationDetailDTO> getTranslation(Long pageId, Integer versionNumber, String languageCode) {
        EntityManager em = MCREntityManagerProvider.getCurrentEntityManager();
        CMSPage page = em.find(CMSPage.class, pageId);
        if (page == null) {
            return Optional.empty();
        }
        List<CMSPageVersion> versions = getVersionEntities(em, pageId);
        if (!permissionService.canReadPage(page, versions)) {
            return Optional.empty();
        }
        return versions.stream()
            .filter(v -> v.getVersionNumber().equals(versionNumber))
            .filter(v -> permissionService.canReadVersion(page, v))
            .findFirst()
            .flatMap(version -> version.getTranslations().stream()
                .filter(t -> t.getLanguage().getCode().equals(languageCode))
                .findFirst()
                .map(t -> toTranslationDetailDTO(version, t)));
    }

    /**
     * Create a new version for a page.
     */
    public Optional<CMSVersionDetailDTO> createVersion(Long pageId, CMSCreateVersionDTO dto) {
        EntityManager em = MCREntityManagerProvider.getCurrentEntityManager();
        CMSPage page = em.find(CMSPage.class, pageId);
        if (page == null || !permissionService.canWrite(page)) {
            return Optional.empty();
        }

        String userId = MCRSessionMgr.getCurrentSession().getUserInformation().getUserID();
        int nextVersionNumber = getNextVersionNumber(page);
        CMSPageStatus status = CMSPageStatus.fromValue(dto.getStatus());

        CMSPageVersion version = new CMSPageVersion(page, nextVersionNumber, userId, status);
        version.setComment(dto.getComment());

        if (dto.getTranslations() != null) {
            for (CMSTranslationDTO translationDTO : dto.getTranslations()) {
                CMSLanguage language = getOrCreateLanguage(em, translationDTO.getLanguage());
                CMSPageVersionTranslation translation = new CMSPageVersionTranslation(
                    version, language, translationDTO.getTitle(), translationDTO.getContent());
                version.addTranslation(translation);
            }
        }

        page.addVersion(version);
        em.persist(version);
        return Optional.of(toVersionDetailDTO(version));
    }

    /**
     * Get all version entities for a page, sorted by version number descending.
     */
    private List<CMSPageVersion> getVersionEntities(EntityManager em, Long pageId) {
        TypedQuery<CMSPageVersion> query = em.createQuery(
            "SELECT v FROM CMSPageVersion v WHERE v.page.id = :pageId ORDER BY v.versionNumber DESC",
            CMSPageVersion.class);
        query.setParameter("pageId", pageId);
        return query.getResultList();
    }

    private int getNextVersionNumber(CMSPage page) {
        // Query versions directly to avoid lazy loading issues with cached entities
        EntityManager em = MCREntityManagerProvider.getCurrentEntityManager();
        TypedQuery<Integer> query = em.createQuery(
            "SELECT MAX(v.versionNumber) FROM CMSPageVersion v WHERE v.page.id = :pageId",
            Integer.class);
        query.setParameter("pageId", page.getId());
        Integer maxVersion = query.getSingleResult();
        return (maxVersion != null ? maxVersion : 0) + 1;
    }

    private CMSLanguage getOrCreateLanguage(EntityManager em, String code) {
        TypedQuery<CMSLanguage> query = em.createQuery(
            "SELECT l FROM CMSLanguage l WHERE l.code = :code", CMSLanguage.class);
        query.setParameter("code", code);
        try {
            return query.getSingleResult();
        } catch (NoResultException e) {
            CMSLanguage language = new CMSLanguage(code, code);
            em.persist(language);
            return language;
        }
    }

    private CMSPageListDTO toPageListDTO(CMSPage page) {
        EntityManager em = MCREntityManagerProvider.getCurrentEntityManager();
        CMSPageListDTO dto = new CMSPageListDTO();
        dto.setId(page.getId());
        dto.setSlug(page.getSlug());
        dto.setCreatedAt(page.getCreatedAt());
        dto.setUpdatedAt(page.getUpdatedAt());

        List<CMSPageVersion> versions = getVersionEntities(em, page.getId());
        versions.stream()
            .filter(v -> permissionService.canReadVersion(page, v))
            .findFirst()
            .ifPresent(currentVersion -> {
                CMSVersionSummaryDTO versionSummary = new CMSVersionSummaryDTO(
                    currentVersion.getVersionNumber(),
                    currentVersion.getStatus().getValue(),
                    currentVersion.getCreatedAt());
                dto.setCurrentVersion(versionSummary);
            });

        return dto;
    }

    private CMSPageDetailDTO toPageDetailDTO(CMSPage page) {
        EntityManager em = MCREntityManagerProvider.getCurrentEntityManager();
        CMSPageDetailDTO dto = new CMSPageDetailDTO();
        dto.setId(page.getId());
        dto.setSlug(page.getSlug());
        dto.setCreatedAt(page.getCreatedAt());
        dto.setUpdatedAt(page.getUpdatedAt());

        List<CMSPageVersion> versions = getVersionEntities(em, page.getId());
        dto.setVersions(versions.stream()
            .filter(v -> permissionService.canReadVersion(page, v))
            .map(this::toVersionInfoDTO)
            .collect(Collectors.toList()));
        return dto;
    }

    private CMSVersionInfoDTO toVersionInfoDTO(CMSPageVersion version) {
        CMSVersionInfoDTO dto = new CMSVersionInfoDTO();
        dto.setVersionNumber(version.getVersionNumber());
        dto.setStatus(version.getStatus().getValue());
        dto.setComment(version.getComment());
        dto.setCreatedAt(version.getCreatedAt());
        dto.setCreatedBy(version.getCreatedBy());
        return dto;
    }

    private CMSVersionDetailDTO toVersionDetailDTO(CMSPageVersion version) {
        CMSVersionDetailDTO dto = new CMSVersionDetailDTO();
        dto.setVersionNumber(version.getVersionNumber());
        dto.setStatus(version.getStatus().getValue());
        dto.setComment(version.getComment());
        dto.setCreatedAt(version.getCreatedAt());
        dto.setCreatedBy(version.getCreatedBy());
        dto.setTranslations(version.getTranslations().stream()
            .map(t -> new CMSTranslationDTO(
                t.getLanguage().getCode(),
                t.getTitle(),
                t.getContent()))
            .collect(Collectors.toList()));
        return dto;
    }

    private CMSTranslationDetailDTO toTranslationDetailDTO(CMSPageVersion version,
        CMSPageVersionTranslation translation) {
        CMSTranslationDetailDTO dto = new CMSTranslationDetailDTO();
        dto.setVersionNumber(version.getVersionNumber());
        dto.setStatus(version.getStatus().getValue());
        dto.setLanguage(translation.getLanguage().getCode());
        dto.setTitle(translation.getTitle());
        dto.setContent(translation.getContent());
        return dto;
    }
}
