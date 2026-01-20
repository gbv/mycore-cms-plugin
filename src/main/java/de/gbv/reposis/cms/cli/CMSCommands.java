package de.gbv.reposis.cms.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mycore.frontend.cli.annotation.MCRCommand;
import org.mycore.frontend.cli.annotation.MCRCommandGroup;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.gbv.reposis.cms.dto.CMSPageExportDTO;
import de.gbv.reposis.cms.service.CMSPageService;

@MCRCommandGroup(name = "cms")
public class CMSCommands {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @MCRCommand(syntax = "cms export pages with slug begins with {0} to file {1}",
        help = "Export CMS pages with slugs beginning with the specified prefix to a JSON file")
    public static void exportPagesWithSlug(String slugPrefix, String filePath) {
        LOGGER.info("Exporting CMS pages with slug prefix '{}' to file '{}'", () -> slugPrefix, () -> filePath);

        CMSPageService pageService = new CMSPageService();
        List<CMSPageExportDTO> pages = pageService.getPagesBySlugPrefix(slugPrefix);

        if (pages.isEmpty()) {
            LOGGER.warn("No pages found with slug prefix '{}'", () -> slugPrefix);
            return;
        }

        Path path = Paths.get(filePath).toAbsolutePath();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String json = OBJECT_MAPPER.writeValueAsString(pages);
            Files.writeString(path, json);
            LOGGER.info("Successfully exported {} pages to '{}'", pages::size, () -> filePath);
        } catch (IOException e) {
            if(LOGGER.isErrorEnabled()){
                LOGGER.error("Failed to write export file: {}", e.getMessage(), e);
            }
        }
    }

    @MCRCommand(syntax = "cms import pages from file {0}",
        help = "Import CMS pages from the specified JSON file. "
            + "If a page with the same slug already exists, it will be completely replaced.")
    public static void importPagesFromFile(String filePath) {
        LOGGER.info("Importing CMS pages from file '{}'", () -> filePath);

        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            LOGGER.error("Import file does not exist: {}", () -> filePath);
            return;
        }

        List<CMSPageExportDTO> pages;
        try {
            pages = OBJECT_MAPPER.readValue(path.toFile(), new TypeReference<>() {
            });
        } catch (IOException e) {
            if(LOGGER.isErrorEnabled()){
                LOGGER.error("Failed to read import file: {}", e.getMessage(), e);
            }
            return;
        }

        if (pages.isEmpty()) {
            LOGGER.warn("No pages found in import file");
            return;
        }

        LOGGER.info("Found {} pages to import", pages::size);

        CMSPageService pageService = new CMSPageService();

        // Import the pages
        int importedCount = 0;
        int replacedCount = 0;
        for (CMSPageExportDTO pageDTO : pages) {
            try {
                boolean replaced = pageService.importPageWithReplace(pageDTO);
                if (replaced) {
                    LOGGER.info("Replaced existing page with slug '{}'", pageDTO::getSlug);
                    replacedCount++;
                } else {
                    LOGGER.info("Imported new page with slug '{}'", pageDTO::getSlug);
                }
                importedCount++;
            } catch (Exception e) {
                if(LOGGER.isErrorEnabled()){
                    LOGGER.error("Failed to import page with slug '{}': {}",  pageDTO.getSlug(), e.getMessage(), e);
                }
            }
        }
        final int finalImportedCount = importedCount;
        final int finalReplacedCount = replacedCount;

        LOGGER.info("Successfully imported {} pages ({} new, {} replaced)", () -> finalImportedCount,
            () -> finalImportedCount - finalReplacedCount, () -> finalReplacedCount);
    }

    @MCRCommand(syntax = "cms delete pages with slug begins with {0}",
        help = "Permanently delete all CMS pages with slugs beginning with the specified prefix")
    public static void deletePagesWithSlugPrefix(String slugPrefix) {
        LOGGER.info("Deleting CMS pages with slug prefix '{}'", slugPrefix);

        CMSPageService pageService = new CMSPageService();
        int deletedCount = pageService.deletePagesBySlugPrefix(slugPrefix);

        if (deletedCount > 0) {
            LOGGER.info("Permanently deleted {} pages with prefix '{}'", () -> deletedCount, () -> slugPrefix);
        } else {
            LOGGER.warn("No pages found with slug prefix '{}'", () -> slugPrefix);
        }
    }
}
