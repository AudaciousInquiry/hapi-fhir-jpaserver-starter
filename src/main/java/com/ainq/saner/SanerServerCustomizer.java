package com.ainq.saner;

import ca.uhn.fhir.jpa.dao.DaoRegistry;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import ca.uhn.fhir.spring.boot.autoconfigure.FhirRestfulServerCustomizer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Library;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ValueSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import com.ainq.utils.FhirUtils;
import com.ainq.utils.JpaUtils;

@Component
public class SanerServerCustomizer implements FhirRestfulServerCustomizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SanerServerCustomizer.class);
    private IParser jsonParser = null;
    private IParser xmlParser = null;
    /**
     * Initialize the server from a set of resources.
     *
     * GIVEN A Set of Resources in a Folder WHEN The Server Is Initialized THEN for
     * each Resource R in the set if the R has not already been previously loaded
     * then R is created on the server
     *
     * GIVEN The Server has been upgraded AND there is a new set of resources AND
     * Some resources have been updated from what previously had been part of the
     * preload set WHEN The Server is Initialized THEN for each Resource R in the
     * set if R had been previously loaded, and R is updated, then R is updated on
     * the Server else if R had been previously loaded, but R is not updated then R
     * is NOT updated on the Server else if R had not been previously loaded then R
     * is created on the Server end if
     *
     * GIVEN The server has not been upgraded AND there is a set of resources in a
     * folder AND An administrator has updated or deleted some previously loaded
     * resource AND that previously loaded resource has a stored history in the
     * database WHEN The server is initialized THEN The administrators adjustments
     * to the resources are not changed
     *
     * TODO: How to determine R has been previously loaded, and how to determine R
     * has been updated from a previous version of what has been loaded, and there
     * have been changes.
     *
     * FOR each named resource in the preload set, there is a record in the database
     * indexed by the name of the resource in the set. That record provides the
     * resource identifier for the resource, it's hash, as well as the version
     * number of the preloaded resource.
     *
     * R has a name (the name of the resource that is being loaded). R has a hash
     * value (the hash computed loaded resource). A previously loaded resource R has
     * a resource identifier in the database.
     */
    private void initFromFiles(RestfulServer server) {

        // TODO: Check to see if resources have already been loaded.
        // We could look to see if the first one already exists, but that assumes
        // that someone wouldn't want to remove it for some reason (e.g., maintenance,
        // updates, et cetera)
        // We should just check for a value in the database somewhere.

        // Enumerate the resources is src/main/resource/preload folder
        PathMatchingResourcePatternResolver r = new PathMatchingResourcePatternResolver();

        ApplicationContext appCtx = (ApplicationContext) server.getServletContext()
            .getAttribute("org.springframework.web.context.WebApplicationContext.ROOT");
        DaoRegistry dao = appCtx.getBean(DaoRegistry.class);
        jsonParser = server.getFhirContext().newJsonParser();
        xmlParser = server.getFhirContext().newXmlParser();
        String resources = "";
        try {
            for (org.springframework.core.io.Resource res : r.getResources(resources = "/preload/*.json")) {
                String name = res.getFilename();
                String resourceType = StringUtils.substringBefore(name, "-");
                // Skip Open API specifications
                if (name.endsWith("openapi.json")) {
                    continue;
                }
                // Skip json files that aren't resources
                if (!FhirUtils.isResource(resourceType)) {
                    continue;
                }
                loadFile(jsonParser, dao, res);
            }

            for (org.springframework.core.io.Resource res : r.getResources(resources = "/preload/*.xml")) {
                String name = res.getFilename();
                // Skip non-bundle xml files.
                if (!name.contains("-bundle-")) {
                    continue;
                }
                loadFile(xmlParser, dao, res);
            }
            for (org.springframework.core.io.Resource res : r.getResources(resources = "/preload/*.zip")) {
                // Zip files should be solely comprised of resources.
                loadZipFile(dao, res);
            }
        } catch (IOException e) {
            LOGGER.error("Unexpected Exception preloading resources {}", resources, e);
        }
    }

    /**
     * Load a resource into the FHIR Server
     * @param p The parser to use.
     * @param dao   The data access object to use for storing
     * @param res   The resource to store in the server
     */
    private void loadFile(IParser p, DaoRegistry dao, org.springframework.core.io.Resource res) {
        try {
            InputStream s = res.getInputStream();
            loadStream(res.getFilename(), p, dao, s);
        } catch (DataFormatException e) {
            LOGGER.error("File is not a FHIR Resource: {}", res.getFilename(), e);
        } catch (Exception e) {
            LOGGER.error("Unexpected Exception while preloading resource {}", res.getFilename(), e);
        }
    }

    /**
     * Load a resource from a stream into the server
     * @param p The parser to use.
     * @param dao   Thee data access object to use for storing.
     * @param s The input stream containing the resource to store.
     */
    private void loadStream(String name, IParser p, DaoRegistry dao, InputStream s) {
        LOGGER.info("Loading resource from {}", name);
        Resource base = (Resource) p.parseResource(s);
        if (base instanceof Bundle) {
            for (Bundle.BundleEntryComponent e : ((Bundle) base).getEntry()) {
                createResource(dao, e.getResource());
            }
        } else {
            createResource(dao, base);
        }
    }

    /**
     * Load the contents of a zip file into thee server
     * @param dao   The data access object to use for storing.
     * @param res   The resource referencing the zip file.
     */
    private void loadZipFile(DaoRegistry dao, org.springframework.core.io.Resource res) {
        try (BufferedInputStream bis = new BufferedInputStream(res.getInputStream());
            ZipInputStream zis = new ZipInputStream(bis)) {
            for (ZipEntry ze = zis.getNextEntry(); ze != null; ze = zis.getNextEntry()) {
                if (ze.isDirectory()) {
                    continue;
                }
                String name = ze.getName();
                // Skip non-XML and non-JSON files
                if (!name.endsWith(".xml") && !name.endsWith(".json")) {
                    continue;
                }
                loadStream(name, ze.getName().endsWith("xml") ? xmlParser : jsonParser, dao, zis);
            }
        } catch (IOException e) {
            LOGGER.error("Unexpected Exception while preloading resource {}", res.getFilename(), e);
        }
    }

    /**
     * Create a resource in the given data access object
     * @param dao   The data access object to store the resource in
     * @param base  The resource to store
     */
    private void createResource(DaoRegistry dao, Resource base) {
        if (base instanceof Measure ||
            base instanceof ValueSet ||
            base instanceof CodeSystem ||
            base instanceof Library) {
            // These resources are defined by their url, not their id
            String url = FhirUtils.getPrimitiveValue(base, "url");
            List<? extends Resource> l = JpaUtils.lookupAllByUrl(dao, url, base.getClass());
            for (Resource r: l) {
                try {
                    LOGGER.info("Deleting pre-existing resource {} with same url {}", r.getId(), url);
                    JpaUtils.delete(dao, r);
                } catch (Exception e) {
                    LOGGER.warn("Error deleting existing resource with url = {}", url);
                }
            }
        }
        try {
            JpaUtils.create(dao, base);
        } catch (UnprocessableEntityException upe) {
            String msg = upe.getMessage();
            if (StringUtils.contains(msg, "already have one with resource ID: ")) {
                String id = StringUtils.substringAfter(StringUtils.substringAfter(msg, "resource ID: "), "/");
                LOGGER.info("Updating existing resource {}", id);
                base.setId(id);
                JpaUtils.update(dao, base);
            }
        }
    }

    @Override
    public void customize(RestfulServer server) {
        initFromFiles(server);
    }

}
