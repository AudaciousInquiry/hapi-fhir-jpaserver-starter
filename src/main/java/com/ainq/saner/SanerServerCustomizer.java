package com.ainq.saner;

import ca.uhn.fhir.jpa.dao.DaoRegistry;
import ca.uhn.fhir.jpa.dao.IFhirResourceDao;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.spring.boot.autoconfigure.FhirRestfulServerCustomizer;
import java.io.IOException;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
public class SanerServerCustomizer implements FhirRestfulServerCustomizer {

  private static final Logger LOGGER = LoggerFactory.getLogger(SanerServerCustomizer.class);

  /**
   * Initialize the server from a set of resources.
   *
   * GIVEN A Set of Resources in a Folder WHEN The Server Is Initialized THEN for each Resource R in
   * the set if the R has not already been previously loaded then R is created on the server
   *
   * GIVEN The Server has been upgraded AND there is a new set of resources AND Some resources have
   * been updated from what previously had been part of the preload set WHEN The Server is
   * Initialized THEN for each Resource R in the set if R had been previously loaded, and R is
   * updated, then R is updated on the Server else if R had been previously loaded, but R is not
   * updated then R is NOT updated on the Server else if R had not been previously loaded then R is
   * created on the Server end if
   *
   * GIVEN The server has not been upgraded AND there is a set of resources in a folder AND An
   * administrator has updated or deleted some previously loaded resource AND that previously loaded
   * resource has a stored history in the database WHEN The server is initialized THEN The
   * administrators adjustments to the resources are not changed
   *
   * TODO: How to determine R has been previously loaded, and how to determine R has been updated
   * from a previous version of what has been loaded, and there have been changes.
   *
   * FOR each named resource in the preload set, there is a record in the database indexed by the
   * name of the resource in the set.  That record provides the resource identifier for the
   * resource, it's hash, as well as the version number of the preloaded resource.
   *
   * R has a name (the name of the resource that is being loaded). R has a hash value (the hash
   * computed loaded resource). A previously loaded resource R has a resource identifier in the
   * database.
   */
  private void initFromFiles(RestfulServer server) {

    // TODO: Check to see if resources have already been loaded.
    // We could look to see if the first one already exists, but that assumes
    // that someone wouldn't want to remove it for some reason (e.g., maintenance, updates, et cetera)
    // We should just check for a value in the database somewhere.

    // Enumerate the resources is src/main/resource/preload folder
    PathMatchingResourcePatternResolver r = new PathMatchingResourcePatternResolver();
    IParser p = server.getFhirContext().newJsonParser();
    try {
      ApplicationContext appCtx = (ApplicationContext) server.getServletContext()
        .getAttribute("org.springframework.web.context.WebApplicationContext.ROOT");
      DaoRegistry dao = appCtx.getBean(DaoRegistry.class);

      for (org.springframework.core.io.Resource res : r.getResources("/preload/*.json")) {
        IAnyResource base = (IAnyResource) p.parseResource(res.getInputStream());
        IFhirResourceDao<IAnyResource> resDao = (IFhirResourceDao<IAnyResource>) dao
          .getResourceDao(base.getClass());
        resDao.create(base);
      }
    } catch (IOException e) {
      LOGGER.error("Unexpected IO Exception while preloading resources", e);
    }
  }

  @Override
  public void customize(RestfulServer server) {
    initFromFiles(server);
  }

}
