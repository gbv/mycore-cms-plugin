package de.gbv.reposis.cms;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.servlet.ServletContainer;
import org.mycore.common.events.MCRStartupHandler.AutoExecutable;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;

/**
 * Deploys the CMS Jersey REST API programmatically at runtime.
 * This is necessary because the plugin JAR is added to the classpath at runtime,
 * after the servlet container has already started.
 */
public class JerseyAPIDeployer implements AutoExecutable {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String SERVLET_NAME = "CMSRestAPI";
    private static final String URL_PATTERN = "/api/cms/v1/*";

    @Override
    public String getName() {
        return "MCRJerseyAPIDeployer";
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public void startUp(ServletContext servletContext) {
        if (servletContext == null) {
            LOGGER.warn("ServletContext is null, cannot deploy CMS REST API");
            return;
        }

        // Check if already registered
        if (servletContext.getServletRegistration(SERVLET_NAME) != null) {
            LOGGER.info("CMS REST API servlet already registered");
            return;
        }

        try {
            // Create and register the Jersey servlet with our application
            ServletRegistration.Dynamic servlet = servletContext.addServlet(
                SERVLET_NAME,
                new ServletContainer(new CMSApp()));

            if (servlet == null) {
                LOGGER.error("Failed to register CMS REST API servlet - addServlet returned null");
                return;
            }

            servlet.addMapping(URL_PATTERN);
            servlet.setLoadOnStartup(1);
            servlet.setAsyncSupported(true);

            LOGGER.info("Successfully deployed CMS REST API at {}", URL_PATTERN);
        } catch (Exception e) {
            LOGGER.error("Failed to deploy CMS REST API", e);
        }
    }
}
