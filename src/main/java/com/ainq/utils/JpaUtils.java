package com.ainq.utils;

import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.OperationOutcome;
import ca.uhn.fhir.jpa.dao.DaoRegistry;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;

/**
 * The DomUtils class provides some utility functions for working with XML
 * documents.
 *
 * @author Keith W. Boone
 *
 */
public class JpaUtils {

    private JpaUtils() {

    }

    /**
     * Handles the base create operation.
     * @param <T>   The type of resource to create.
     * @param resource  The class to store
     * @return  The OperationOutcome
     */
    public static <T extends org.hl7.fhir.r4.model.Resource> OperationOutcome create(DaoRegistry dao, T resource) {
        @SuppressWarnings("unchecked")
        OperationOutcome oc = (OperationOutcome)dao.getResourceDao((Class<T>) resource.getClass()).create(resource).getOperationOutcome();
        resource.setId(oc.getId());
        return oc;
    }


    /**
     * Handles the base update operation.
     * @param <T>   The type of resource to create or update.
     * @param resource  The resource to create or update.
     * @return  The OperationOutcome
     */
    public static <T extends org.hl7.fhir.r4.model.Resource> OperationOutcome createOrUpdate(DaoRegistry dao, T resource) {
        if (resource.hasId()) {
            return create(dao, resource);
        }
        return update(dao, resource);
    }

    /**
     * Handles the base update operation.
     * @param <T>   The type of resource to update.
     * @param resource  The resource to update.
     * @return  The OperationOutcome
     */
    public static <T extends org.hl7.fhir.r4.model.Resource> OperationOutcome update(DaoRegistry dao, T resource) {
        @SuppressWarnings("unchecked")
        OperationOutcome oc = (OperationOutcome)dao.getResourceDao((Class<T>) resource.getClass()).update(resource).getOperationOutcome();
        return oc;
    }

    /**
     * Handles the base retrieval operation for lookups.
     * @param <T>   The type of resource to find.
     * @param type  The class for the type of resource
     * @param theParams The search paramaters for finding the resource.
     * @return  The located resource
     */
    public static <T extends org.hl7.fhir.r4.model.Resource> T lookup(DaoRegistry dao, Class<T> type, SearchParameterMap theParams) {
        IBundleProvider p = dao.getResourceDao(type).search(theParams);
        if (p.isEmpty()) {
            throw new ResourceNotFoundException("No resources match");
        }

        List<IBaseResource> l = p.getResources(0, 2);
        if (l.size() > 1) {
            throw new InvalidRequestException("More than one resource matches.");
        }
        return type.cast(l.get(0));
    }

    /**
     * Given the identifier for a location or organization, go find it.  If there's more
     * than one with the same name, report an exception.  If it's not found, also
     * report an exception.
     *
     * @param <T>   The type of resource to find.
     * @param identifier The identifier of the resource.
     * @param type  The class for the type of resource
     * @return  The located resource
     */
    public static <T extends org.hl7.fhir.r4.model.Resource> T lookupByIdentifier(DaoRegistry dao, String identifier, Class<T> type) {
        SearchParameterMap theParams = new SearchParameterMap();
        theParams.add("identifier", new TokenParam(identifier));
        return lookup(dao, type, theParams);
    }

    /**
     * Given the name for a location or organization, go find it.  If there's more
     * than one with the same name, report an exception.  If it's not found, also
     * report an exception.
     *
     * @param <T>   The type of resource to find.
     * @param name  The name of the resource.
     * @param type  The class for the type of resource
     * @return  The located resource
     */
    public static <T extends org.hl7.fhir.r4.model.Resource> T lookupByName(DaoRegistry dao, String name, Class<T> type) {
        SearchParameterMap theParams = new SearchParameterMap();
        theParams.add("name", new StringParam(name));
        return lookup(dao, type, theParams);
    }

    /**
     * Given an identifier, go get the resource it references.
     *
     * @param <T>        The type of resource
     * @param resourceId The resource identifier
     * @param type       The type of resource expected
     * @return The referenced resource
     */
    public static <T extends org.hl7.fhir.r4.model.Resource> T validateResource(DaoRegistry dao, IdType resourceId, Class<T> type) {
        return dao.getResourceDao(type).read(resourceId);
    }

    /**
     * Given an identifier as a string, go get the resource it references
     *
     * @param <T>       The type of resource
     * @param reference The resource identifier as a string
     * @param type      The type of resource expected
     * @return The referenced resource
     */
    public static <T extends org.hl7.fhir.r4.model.Resource> T validateResource(DaoRegistry dao, String reference, Class<T> type) {
        return validateResource(dao, new IdType(reference), type);
    }

}
