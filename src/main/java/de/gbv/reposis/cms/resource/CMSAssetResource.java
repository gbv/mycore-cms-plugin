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

import de.gbv.reposis.cms.service.CMSPermissionService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.gbv.reposis.cms.dto.CMSAssetDTO;
import de.gbv.reposis.cms.service.CMSAssetService;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

/**
 * REST resource for CMS assets (files and directories).
 */
@Path("assets")
public class CMSAssetResource {

    private final CMSAssetService assetService = new CMSAssetService();
    private final CMSPermissionService permissionService = new CMSPermissionService();

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * GET /assets - List assets in root directory
     * GET /assets?path={path} - List assets in specified directory
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @SuppressWarnings("PMD.AvoidDuplicateLiterals")
    public Response listAssets(@QueryParam("path") String path) {

        if (!permissionService.canReadAsset(path)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        try {
            List<CMSAssetDTO> assets = assetService.listAssets(path);
            return Response.ok(assets).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\": \"" + escapeJson(e.getMessage()) + "\"}")
                .type(MediaType.APPLICATION_JSON)
                .build();
        } catch (IOException e) {
            LOGGER.error("Error listing assets", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"Failed to list assets\"}")
                .type(MediaType.APPLICATION_JSON)
                .build();
        }
    }


    /**
     * GET /assets/{path:.*} - Get asset info or download file
     */
    @GET
    @Path("{path:.*}")
    @SuppressWarnings("PMD.AvoidDuplicateLiterals")
    public Response getAsset(@PathParam("path") String path,
        @QueryParam("info") boolean infoOnly) {
        if (!permissionService.canReadAsset(path)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        try {
            if (infoOnly) {
                // Return asset metadata as JSON
                Optional<CMSAssetDTO> asset = assetService.getAssetInfo(path);
                if (asset.isEmpty()) {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                return Response.ok(asset.get())
                    .type(MediaType.APPLICATION_JSON)
                    .build();
            }

            // Check if it's a directory - return listing
            Optional<CMSAssetDTO> assetInfo = assetService.getAssetInfo(path);
            if (assetInfo.isPresent() && assetInfo.get().isDirectory()) {
                List<CMSAssetDTO> assets = assetService.listAssets(path);
                return Response.ok(assets)
                    .type(MediaType.APPLICATION_JSON)
                    .build();
            }

            // Return file content
            Optional<java.nio.file.Path> filePath = assetService.getAssetPath(path);
            if (filePath.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            java.nio.file.Path file = filePath.get();
            String contentType = Files.probeContentType(file);
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM;
            }

            StreamingOutput stream = output -> Files.copy(file, output);
            return Response.ok(stream)
                .type(contentType)
                .header(HttpHeaders.CONTENT_LENGTH, Files.size(file))
                .header("Content-Disposition", "inline; filename=\"" + file.getFileName() + "\"")
                .build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\": \"" + escapeJson(e.getMessage()) + "\"}")
                .type(MediaType.APPLICATION_JSON)
                .build();
        } catch (IOException e) {
            LOGGER.error("Error getting asset: {}", path, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"Failed to get asset\"}")
                .type(MediaType.APPLICATION_JSON)
                .build();
        }
    }

    /**
     * POST /assets/{path:.*} - Upload a file
     * Use Content-Type header to specify file type.
     * Use query parameter ?directory=true to create a directory instead.
     */
    @POST
    @Path("{path:.*}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadAsset(@PathParam("path") String path,
        @QueryParam("directory") boolean createDirectory,
        @HeaderParam(HttpHeaders.CONTENT_LENGTH) long contentLength,
        InputStream inputStream) {
        if (!permissionService.canWriteAsset(path)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        try {
            CMSAssetDTO asset;
            if (createDirectory) {
                asset = assetService.createDirectory(path);
            } else {
                // Validate content length
                long maxSize = assetService.getMaxUploadSize();
                if (contentLength > maxSize) {
                    return Response.status(Response.Status.REQUEST_ENTITY_TOO_LARGE)
                        .entity("{\"error\": \"File size exceeds maximum allowed size of " + maxSize + " bytes\"}")
                        .build();
                }
                asset = assetService.uploadAsset(path, inputStream, contentLength);
            }
            return Response.status(Response.Status.CREATED)
                .entity(asset)
                .build();

        } catch (FileAlreadyExistsException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity("{\"error\": \"Path already exists\"}")
                .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\": \"" + escapeJson(e.getMessage()) + "\"}")
                .build();
        } catch (IOException e) {
            LOGGER.error("Error uploading asset: {}", path, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"Failed to upload asset\"}")
                .build();
        }
    }

    /**
     * PUT /assets/{path:.*} - Move/rename an asset
     * Request body should contain: {"target": "new/path"}
     */
    @PUT
    @Path("{path:.*}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response moveAsset(@PathParam("path") String sourcePath,
        MoveRequest request) {
        if (!permissionService.canWriteAsset(sourcePath)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        if (request == null || request.target == null || request.target.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\": \"target is required\"}")
                .build();
        }

        try {
            CMSAssetDTO asset = assetService.moveAsset(sourcePath, request.target);
            return Response.ok(asset).build();

        } catch (FileAlreadyExistsException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity("{\"error\": \"Target already exists\"}")
                .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\": \"" + escapeJson(e.getMessage()) + "\"}")
                .build();
        } catch (IOException e) {
            LOGGER.error("Error moving asset from {} to {}", sourcePath, request.target, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"Failed to move asset\"}")
                .build();
        }
    }

    /**
     * DELETE /assets/{path:.*} - Delete an asset
     * Use query parameter ?recursive=true to delete non-empty directories.
     */
    @DELETE
    @Path("{path:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteAsset(@PathParam("path") String path,
        @QueryParam("recursive") boolean recursive) {
        if (!permissionService.canDeleteAsset(path)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        try {
            boolean deleted = assetService.deleteAsset(path, recursive);
            if (!deleted) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.noContent().build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\": \"" + escapeJson(e.getMessage()) + "\"}")
                .build();
        } catch (IOException e) {
            LOGGER.error("Error deleting asset: {}", path, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"" + escapeJson(e.getMessage()) + "\"}")
                .build();
        }
    }

    /**
     * GET /assets - Get upload limits and configuration
     */
    @GET
    @Path("_config")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConfig() {
        return Response.ok("{\"max_upload_size\": " + assetService.getMaxUploadSize() + "}").build();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    /**
     * Request body for move operations.
     */
    public static class MoveRequest {
        public String target;
    }
}
