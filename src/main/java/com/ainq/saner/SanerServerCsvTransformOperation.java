package com.ainq.saner;

import java.text.SimpleDateFormat;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.dao.DaoRegistry;
import ca.uhn.fhir.jpa.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.UriParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Measure;
import org.springframework.beans.factory.annotation.Autowired;

import com.ainq.utils.JpaUtils;

/**
 * This class supports the $convert and $report operations on measures.
 * $convert converts from CSV format to a MeasureReport
 * $report performs the conversion and creates the report on the server as if a Put operation was performed.
 * If there is an existing report for the same time period from the same location, it is replaced.
 *
 * @author Keith W. Boone, Brian Harris
 *
 */
public class SanerServerCsvTransformOperation implements IResourceProvider {
    /** The servers fhirContext */
    @Autowired
    private FhirContext fhirContext;

    /** A registry of data access objects used to talk to the back end data store */
    @Autowired
    private DaoRegistry dao;

    /** The dao for Measures */
    private IFhirResourceDao<Measure> resDao = null;

    SimpleDateFormat fmt;
    /** Perform post construction initialization steps, mostly
     * involving initialization operations depending on autowired
     * components.
     */
    @PostConstruct
    private void init() {
        System.setProperty("javax.xml.transform.TransformerFactory", "net.sf.saxon.TransformerFactoryImpl");
        resDao = (IFhirResourceDao<Measure>) dao.getResourceDao(Measure.class);
    }

    /**
     * Force a refresh of the stylesheet resources
     * @param resp  The ServletResponse to send a message back to.
     */
    @Operation(name = "$init", manualResponse = true, manualRequest = true)
    public void init(HttpServletResponse resp) {
        init();
        resp.setStatus(HttpServletResponse.SC_OK);
    }
    /**
     * Specify the resource type for this provider, these operations
     * work on existing measures, so it's Measure.class.
     */
    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return Measure.class;
    }

    /**
     * Supports reporting by measure name or url.
     * Input parameters are either the name of the measure (which may not be unique, in which
     * case, this method should report an error on a match of more than one), OR
     * the canonical url of the measure.  Regardless of lack of uniqueness, all Measure
     * definitions sharing the same canonical url SHOULD be identical.
     *
     * @param theServletRequest     The original request.
     * @param theServletResponse    The servlet response in which to write the results.
     *
     */
    @Operation(name = "$convert", manualResponse = true, manualRequest = true)
    public void convert(HttpServletRequest theServletRequest, HttpServletResponse theServletResponse) {
        getMeasureAndConvert(theServletRequest, theServletResponse, false);
    }

    @Operation(name = "$report", manualResponse = true, manualRequest = true)
    public void report(HttpServletRequest theServletRequest, HttpServletResponse theServletResponse) {
        getMeasureAndConvert(theServletRequest, theServletResponse, true);
    }

    private void getMeasureAndConvert(HttpServletRequest theServletRequest, HttpServletResponse theServletResponse, boolean create) {
        String measureName = theServletRequest.getParameter("name");
        String measureUrl = theServletRequest.getParameter("url");
        String query = "";
        SearchParameterMap theParams = new SearchParameterMap();
        if (!StringUtils.isEmpty(measureName)) {
            // find by name (we will search by name or title).
            theParams.add("title", new StringParam(measureName));
            query="title=" + measureName;
        }

        if (!StringUtils.isEmpty(measureUrl)) {
            // Find by url
            theParams.add("url", new UriParam(measureUrl));
            if (query.length() != 0) {
                query += "&";
            }
            query += "url=" + measureUrl;
        }

        if (theParams.size() == 0) {
            throw new InvalidRequestException("No query parameters provided to identify measure");
        }

        List<IBaseResource> l = resDao.search(theParams).getResources(0, 2);
        if (l.size() == 0) {
            throw new ResourceNotFoundException("Measure?" + query);
        }
        if (l.size() > 2) {
            throw new InvalidRequestException("More than one measure matches Measure?" + query);
        }
        ConversionRequest.convert(dao, fhirContext, theServletRequest, theServletResponse, (Measure)l.get(0), create);
    }

    @Operation(name = "$convert", manualResponse = true, manualRequest = true)
    public void convert(HttpServletRequest theServletRequest, HttpServletResponse theServletResponse,
        @IdParam IdType measureId) {
        ConversionRequest.convert(dao, fhirContext, theServletRequest, theServletResponse, measureId, false);
    }

    @Operation(name = "$report", manualResponse = true, manualRequest = true)
    public void report(HttpServletRequest theServletRequest, HttpServletResponse theServletResponse,
        @IdParam IdType measureId) {
        ConversionRequest.convert(dao, fhirContext, theServletRequest, theServletResponse, measureId, true);
    }
}
