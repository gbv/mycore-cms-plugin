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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mycore.common.MCRUtils;
import org.mycore.common.config.MCRConfiguration2;

import de.gbv.reposis.cms.dto.CMSAssetDTO;

/**
 * Service for CMS asset (file) operations.
 */
public class CMSAssetService {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Property key for the CMS file directory.
     */
    public static final String PROPERTY_FILE_DIRECTORY = "MCR.CMS.File.Directory";

    /**
     * Property key for the maximum upload file size in bytes.
     * Default: 10 MB
     */
    public static final String PROPERTY_MAX_UPLOAD_SIZE = "MCR.CMS.File.MaxUploadSize";

    /**
     * Default maximum upload size: 10 MB.
     */
    public static final long DEFAULT_MAX_UPLOAD_SIZE = 10 * 1024 * 1024;

    /**
     * Get the base directory for CMS assets.
     */
    public Path getBaseDirectory() {
        String directory = MCRConfiguration2.getStringOrThrow(PROPERTY_FILE_DIRECTORY);
        return Paths.get(directory);
    }

    /**
     * Get the maximum allowed upload size in bytes.
     */
    public long getMaxUploadSize() {
        return MCRConfiguration2.getLong(PROPERTY_MAX_UPLOAD_SIZE).orElse(DEFAULT_MAX_UPLOAD_SIZE);
    }

    /**
     * List assets in a directory.
     *
     * @param relativePath the relative path within the assets directory (empty string for root)
     * @return list of assets in the directory
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if the path is invalid or outside the base directory
     */
    public List<CMSAssetDTO> listAssets(String relativePath) throws IOException {
        Path baseDir = getBaseDirectory();
        ensureDirectoryExists(baseDir);

        Path targetDir;
        if (relativePath == null || relativePath.isEmpty() || relativePath.equals("/")) {
            targetDir = baseDir;
        } else {
            targetDir = MCRUtils.safeResolve(baseDir, normalizePathSegments(relativePath));
        }

        if (!Files.exists(targetDir)) {
            return List.of();
        }

        if (!Files.isDirectory(targetDir)) {
            throw new IllegalArgumentException("Path is not a directory: " + relativePath);
        }

        List<CMSAssetDTO> assets = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetDir)) {
            for (Path entry : stream) {
                assets.add(toAssetDTO(baseDir, entry));
            }
        }

        // Sort: directories first, then by name
        assets.sort((a, b) -> {
            if (a.isDirectory() != b.isDirectory()) {
                return a.isDirectory() ? -1 : 1;
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });

        return assets;
    }

    /**
     * Get information about a specific asset.
     *
     * @param relativePath the relative path to the asset
     * @return the asset information, or empty if not found
     * @throws IOException if an I/O error occurs
     */
    public Optional<CMSAssetDTO> getAssetInfo(String relativePath) throws IOException {
        Path baseDir = getBaseDirectory();
        Path targetPath = MCRUtils.safeResolve(baseDir, normalizePathSegments(relativePath));

        if (!Files.exists(targetPath)) {
            return Optional.empty();
        }

        return Optional.of(toAssetDTO(baseDir, targetPath));
    }

    /**
     * Get the content of a file asset.
     *
     * @param relativePath the relative path to the file
     * @return the file path if it exists and is a file
     * @throws IOException if an I/O error occurs
     */
    public Optional<Path> getAssetPath(String relativePath) throws IOException {
        Path baseDir = getBaseDirectory();
        Path targetPath = MCRUtils.safeResolve(baseDir, normalizePathSegments(relativePath));

        if (!Files.exists(targetPath) || Files.isDirectory(targetPath)) {
            return Optional.empty();
        }

        return Optional.of(targetPath);
    }

    /**
     * Upload a file to the assets directory.
     *
     * @param relativePath the relative path where to store the file (including filename)
     * @param inputStream the file content
     * @param contentLength the content length (for size validation)
     * @return the created asset DTO
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if the file is too large or path is invalid
     */
    public CMSAssetDTO uploadAsset(String relativePath, InputStream inputStream, long contentLength)
        throws IOException {
        // Validate file size
        long maxSize = getMaxUploadSize();
        if (contentLength > maxSize) {
            throw new IllegalArgumentException(
                "File size " + contentLength + " exceeds maximum allowed size of " + maxSize + " bytes");
        }

        Path baseDir = getBaseDirectory();
        ensureDirectoryExists(baseDir);

        Path targetPath = MCRUtils.safeResolve(baseDir, normalizePathSegments(relativePath));

        // Create parent directories if they don't exist
        Path parentDir = targetPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        // Copy the file
        Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);

        LOGGER.info("Uploaded asset: {}", relativePath);
        return toAssetDTO(baseDir, targetPath);
    }

    /**
     * Create a new directory.
     *
     * @param relativePath the relative path of the directory to create
     * @return the created directory DTO
     * @throws IOException if an I/O error occurs
     */
    public CMSAssetDTO createDirectory(String relativePath) throws IOException {
        Path baseDir = getBaseDirectory();
        ensureDirectoryExists(baseDir);

        Path targetPath = MCRUtils.safeResolve(baseDir, normalizePathSegments(relativePath));

        if (Files.exists(targetPath)) {
            throw new FileAlreadyExistsException("Path already exists: " + relativePath);
        }

        Files.createDirectories(targetPath);
        LOGGER.info("Created directory: {}", relativePath);
        return toAssetDTO(baseDir, targetPath);
    }

    /**
     * Delete an asset (file or empty directory).
     *
     * @param relativePath the relative path to the asset
     * @param recursive if true, delete directories recursively
     * @return true if deleted, false if not found
     * @throws IOException if an I/O error occurs
     */
    public boolean deleteAsset(String relativePath, boolean recursive) throws IOException {
        Path baseDir = getBaseDirectory();
        Path targetPath = MCRUtils.safeResolve(baseDir, normalizePathSegments(relativePath));

        if (!Files.exists(targetPath)) {
            return false;
        }

        if (Files.isDirectory(targetPath)) {
            if (recursive) {
                deleteRecursively(targetPath);
            } else {
                // Check if directory is empty
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetPath)) {
                    if (stream.iterator().hasNext()) {
                        throw new IOException("Directory is not empty: " + relativePath);
                    }
                }
                Files.delete(targetPath);
            }
        } else {
            Files.delete(targetPath);
        }

        LOGGER.info("Deleted asset: {}", relativePath);
        return true;
    }

    /**
     * Move or rename an asset.
     *
     * @param sourcePath the current relative path
     * @param targetRelativePath the new relative path
     * @return the moved asset DTO
     * @throws IOException if an I/O error occurs
     */
    public CMSAssetDTO moveAsset(String sourcePath, String targetRelativePath) throws IOException {
        Path baseDir = getBaseDirectory();
        Path source = MCRUtils.safeResolve(baseDir, normalizePathSegments(sourcePath));
        Path target = MCRUtils.safeResolve(baseDir, normalizePathSegments(targetRelativePath));

        if (!Files.exists(source)) {
            throw new IOException("Source not found: " + sourcePath);
        }

        if (Files.exists(target)) {
            throw new FileAlreadyExistsException("Target already exists: " + targetRelativePath);
        }

        // Create parent directories if needed
        Path parentDir = target.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        Files.move(source, target);
        LOGGER.info("Moved asset from {} to {}", sourcePath, targetRelativePath);
        return toAssetDTO(baseDir, target);
    }

    private void ensureDirectoryExists(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
            LOGGER.info("Created CMS assets directory: {}", directory);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path entry : stream) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.delete(path);
    }

    private String[] normalizePathSegments(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return new String[0];
        }
        // Remove leading/trailing slashes and split
        String normalized = relativePath.replaceAll("^/+|/+$", "");
        if (normalized.isEmpty()) {
            return new String[0];
        }
        return normalized.split("/+");
    }

    private CMSAssetDTO toAssetDTO(Path baseDir, Path path) throws IOException {
        String relativePath = baseDir.relativize(path).toString().replace('\\', '/');
        String name = path.getFileName().toString();
        boolean isDirectory = Files.isDirectory(path);
        Long size = isDirectory ? null : Files.size(path);
        String contentType = isDirectory ? null : Files.probeContentType(path);
        Instant modifiedAt = Files.getLastModifiedTime(path).toInstant();

        return new CMSAssetDTO(name, relativePath, isDirectory, size, contentType, modifiedAt);
    }
}
