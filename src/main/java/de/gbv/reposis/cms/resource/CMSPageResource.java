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

package de.gbv.reposis.cms.resource;

import java.util.List;
import java.util.Optional;

import org.mycore.common.MCRSessionMgr;
import org.mycore.restapi.annotations.MCRRequireTransaction;

import de.gbv.reposis.cms.dto.CMSCreatePageDTO;
import de.gbv.reposis.cms.dto.CMSCreateVersionDTO;
import de.gbv.reposis.cms.dto.CMSPageDetailDTO;
import de.gbv.reposis.cms.dto.CMSPageListDTO;
import de.gbv.reposis.cms.dto.CMSTranslationDetailDTO;
import de.gbv.reposis.cms.dto.CMSVersionDetailDTO;
import de.gbv.reposis.cms.dto.CMSVersionInfoDTO;
import de.gbv.reposis.cms.model.CMSPage;
import de.gbv.reposis.cms.service.CMSPageService;
import de.gbv.reposis.cms.service.CMSPermissionService;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST resource for CMS pages.
 */
@Path("pages")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CMSPageResource {

    private static final String PATH_PARAM_PAGE_ID = "pageId";

    private final CMSPageService pageService = new CMSPageService();
    private final CMSPermissionService permissionService = new CMSPermissionService();

    /**
     * GET /pages - List all pages
     * GET /pages?slug={slug} - Find page by slug
     */
    @GET
    @MCRRequireTransaction
    public Response getPages(@QueryParam("slug") String slug) {
        if (slug != null && !slug.isEmpty()) {
            Optional<CMSPageListDTO> page = pageService.getPageBySlug(slug);
            return page.map(p -> Response.ok(List.of(p)).build())
                .orElse(Response.ok(List.of()).build());
        }
        List<CMSPageListDTO> pages = pageService.getAllPages();
        return Response.ok(pages).build();
    }

    /**
     * GET /pages/{pageId} - Get page with all versions
     */
    @GET
    @Path("{" + PATH_PARAM_PAGE_ID + "}")
    @MCRRequireTransaction
    public Response getPage(@PathParam(PATH_PARAM_PAGE_ID) Long pageId) {
        Optional<CMSPageDetailDTO> page = pageService.getPageById(pageId);
        return page.map(p -> Response.ok(p).build())
            .orElse(Response.status(Response.Status.FORBIDDEN).build());
    }

    /**
     * POST /pages - Create a new page
     */
    @POST
    @MCRRequireTransaction
    public Response createPage(CMSCreatePageDTO dto) {
        if (dto == null || dto.getSlug() == null || dto.getSlug().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\": \"slug is required\"}")
                .build();
        }

        CMSPage page = pageService.createPage(dto.getSlug());
        return Response.status(Response.Status.CREATED)
            .entity("{\"id\": " + page.getId() + ", \"slug\": \"" + page.getSlug() + "\"}")
            .build();
    }

    /**
     * DELETE /pages/{pageId} - Delete page (set to archived)
     */
    @DELETE
    @Path("{" + PATH_PARAM_PAGE_ID + "}")
    @MCRRequireTransaction
    public Response deletePage(@PathParam(PATH_PARAM_PAGE_ID) Long pageId) {
        Optional<CMSPage> page = pageService.getPageEntityById(pageId);
        if (page.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (!permissionService.canDelete(page.get())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        String userId = MCRSessionMgr.getCurrentSession().getUserInformation().getUserID();
        boolean deleted = pageService.deletePage(pageId, userId);
        if (deleted) {
            return Response.noContent().build();
        }
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * GET /pages/{pageId}/versions - Get all versions of a page
     */
    @GET
    @Path("{" + PATH_PARAM_PAGE_ID + "}/versions")
    @MCRRequireTransaction
    public Response getVersions(@PathParam(PATH_PARAM_PAGE_ID) Long pageId) {
        Optional<CMSPage> page = pageService.getPageEntityById(pageId);
        if (page.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (!permissionService.canReadVersions(page.get())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        List<CMSVersionInfoDTO> versions = pageService.getVersions(pageId);
        return Response.ok(versions).build();
    }

    /**
     * GET /pages/{pageId}/versions/current - Get highest version
     */
    @GET
    @Path("{" + PATH_PARAM_PAGE_ID + "}/versions/current")
    @MCRRequireTransaction
    public Response getCurrentVersion(@PathParam(PATH_PARAM_PAGE_ID) Long pageId) {
        Optional<CMSVersionDetailDTO> version = pageService.getCurrentVersion(pageId);
        return version.map(v -> Response.ok(v).build())
            .orElse(Response.status(Response.Status.FORBIDDEN).build());
    }

    /**
     * GET /pages/{pageId}/versions/published - Get highest published version
     */
    @GET
    @Path("{" + PATH_PARAM_PAGE_ID + "}/versions/published")
    @MCRRequireTransaction
    public Response getPublishedVersion(@PathParam(PATH_PARAM_PAGE_ID) Long pageId) {
        Optional<CMSVersionDetailDTO> version = pageService.getPublishedVersion(pageId);
        return version.map(v -> Response.ok(v).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * GET /pages/{pageId}/versions/{versionNumber} - Get specific version
     */
    @GET
    @Path("{" + PATH_PARAM_PAGE_ID + "}/versions/{versionNumber}")
    @MCRRequireTransaction
    public Response getVersion(@PathParam(PATH_PARAM_PAGE_ID) Long pageId,
        @PathParam("versionNumber") Integer versionNumber) {
        Optional<CMSVersionDetailDTO> version = pageService.getVersion(pageId, versionNumber);
        return version.map(v -> Response.ok(v).build())
            .orElse(Response.status(Response.Status.FORBIDDEN).build());
    }

    /**
     * POST /pages/{pageId}/versions - Create new version
     */
    @POST
    @Path("{" + PATH_PARAM_PAGE_ID + "}/versions")
    @MCRRequireTransaction
    public Response createVersion(@PathParam(PATH_PARAM_PAGE_ID) Long pageId, CMSCreateVersionDTO dto) {
        Optional<CMSPage> page = pageService.getPageEntityById(pageId);
        if (page.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (!permissionService.canWrite(page.get())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Optional<CMSVersionDetailDTO> version = pageService.createVersion(pageId, dto);
        return version.map(v -> Response.status(Response.Status.CREATED).entity(v).build())
            .orElse(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
    }

    /**
     * GET /pages/{pageId}/versions/{versionNumber}/{lang} - Get specific translation
     */
    @GET
    @Path("{" + PATH_PARAM_PAGE_ID + "}/versions/{versionNumber}/{lang}")
    @MCRRequireTransaction
    public Response getTranslation(@PathParam(PATH_PARAM_PAGE_ID) Long pageId,
        @PathParam("versionNumber") Integer versionNumber,
        @PathParam("lang") String lang) {
        Optional<CMSTranslationDetailDTO> translation = pageService.getTranslation(pageId, versionNumber, lang);
        return translation.map(t -> Response.ok(t).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }
}
