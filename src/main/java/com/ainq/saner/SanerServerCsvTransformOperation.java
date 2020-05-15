package com.ainq.saner;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.dao.DaoMethodOutcome;
import ca.uhn.fhir.jpa.dao.DaoRegistry;
import ca.uhn.fhir.jpa.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.HttpHeaders;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.utils.client.ClientUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import net.sf.saxon.jaxp.TransformerImpl;
import net.sf.saxon.serialize.MessageWarner;
import net.sf.saxon.trans.XPathException;

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

    /** Used to load resources from class path */
    @Autowired
    private ResourceLoader resourceLoader;

    /** The servers fhirContext */
    @Autowired
    private FhirContext fhirContext;

    /** A registry of data access objects used to talk to the back end data store */
    @Autowired
    private DaoRegistry dao;

    /** The dao for Measures */
    private IFhirResourceDao<Measure> resDao = null;

    private Resource xsltResource, xmlToCsvResource;

    /** This class handles a conversion request */
    private class ConversionRequest {
        private static final int CSV = 0, XML = 1, JSON = 2;
        private String organization;
        private String subject;
        private String periodStart;
        private String periodEnd;
        private String csvText;
        private String map;
        private Organization reportedOrganization;
        private Location reportedLocation;
        private Measure reportedMeasure;
        private IParser parser = fhirContext.newXmlParser();
        private String inputContentType;
        private String outputContentType;
        private Map<String, String> overrides = new HashMap<>();
        transient private HttpServletRequest theServletRequest;
        private Parameters parameterResource = null;
        private int toCsvFrom = CSV;

        private DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
        {
            dfactory.setNamespaceAware(true);
        }


        ConversionRequest(HttpServletRequest theServletRequest, Measure measure) throws IOException {
            this.theServletRequest = theServletRequest;

            inputContentType = theServletRequest.getContentType();
            outputContentType = theServletRequest.getParameter("_format");
            csvText = IOUtils.toString(theServletRequest.getInputStream(), StandardCharsets.UTF_8);

            handleContentType();

            organization = getParameter("reporter");
            subject = getParameter("subject");
            periodStart = getParameter("period.start");
            periodEnd = getParameter("period.end");
            map = getParameter("map");

            reportedMeasure = measure;
            if (StringUtils.isEmpty(outputContentType)) {
                outputContentType = theServletRequest.getHeader(HttpHeaders.ACCEPT);
            }
            // remove any charset requests
            outputContentType = outputContentType.toLowerCase().split(";")[0].trim();

            if (!StringUtils.isEmpty(map)) {
                for (String mapping : map.split(",")) {
                    String from = StringUtils.substringBeforeLast(mapping, "$");
                    String to = StringUtils.substringAfterLast(mapping, "$");
                    if (StringUtils.isEmpty(to)) {
                        to = from;
                    }
                    overrides.put(to, from);
                }
            }

        }

        private String translateXmlToCSV(String xml) {
            try {
                // Get a new transformer
                Transformer transformer = getTransformer(xmlToCsvResource);
                StringWriter outWriter = new StringWriter();

                // Perform the transformation
                transformer.transform(new StreamSource(new StringReader("<test/>")), new StreamResult(outWriter));
                return outWriter.toString();
            } catch (Exception e) {
                // TODO: Logging
                return null;
            }
        }

        private String translateJsonToCSV(String json) {
            JsonParser parser = new JsonParser();
            JsonElement root = parser.parse(json);
            StringBuilder b = new StringBuilder();
            walk(root, "", b, true);
            b.append("\n");
            walk(root, "", b, false);
            b.append("\n");
            return b.toString();
        }

        private void walk(JsonElement node, String name, StringBuilder b, boolean isHeader) {
            if (node instanceof JsonArray) {
                JsonArray array = (JsonArray)node;
                int count = 0;
                for (JsonElement elem: array) {
                    name += String.format("[%d]", count++);
                    walk(elem, name, b, isHeader);
                }
            } else if (node instanceof JsonObject) {
                JsonObject object = (JsonObject)node;
                for (String key: object.keySet()) {
                    if (name.length() != 0) {
                        name += ".";
                    }
                    walk(object.get(key), name + key, b, isHeader);
                }
            } else if (node instanceof JsonPrimitive) {
                JsonPrimitive prim = (JsonPrimitive) node;
                if (isHeader) {
                    b.append(name);
                } else if (prim.isString()) {
                    b.append("\"").append(StringEscapeUtils.escapeJson(prim.getAsString())).append("\"");
                } else if (prim.isNumber()) {
                    b.append(prim.getAsNumber().toString());
                } else if (prim.isBoolean()) {
                    b.append(prim.getAsBoolean() ? "true" : "false");
                }
                b.append(",");
            } else if (node instanceof JsonNull) {
                if (isHeader) {
                    b.append(name);
                }
                b.append(",");
            }
        }

        private void handleContentType() {
            switch (inputContentType) {
            case "text/csv":
            case "application/csv":
                // normal handling
                return;
            case "application/json":
            case "application/fhir+json":
            case "application/json+fhir":
            case "json":
                // Here we need to look so see if this is a FHIR Resource of type Parameters

                // If it is, we need to read parameters from that resource, overriding parameters
                // in HttpServletRequest.
                parser = fhirContext.newJsonParser();
                toCsvFrom = JSON;
                break;

            case "application/xml":
            case "application/fhir+xml":
            case "application/xml+fhir":
            case "text/xml":
            case "text/fhir+xml":
            case "text/xml+fhir":
            case "xml":
                // Here we need to look so see if this is a FHIR Resource of type Parameters
                parser = fhirContext.newXmlParser();
                toCsvFrom = XML;
                break;
            }
            try {
                parameterResource = parser.parseResource(Parameters.class, csvText);
                // Read the csvText from the input Binary resource.
                Binary b = (Binary) getResourceParameter("input");
                csvText = new String(b.getData(), StandardCharsets.UTF_8);
                String contentType = b.getContentType().split(";")[0].trim().toLowerCase();
                if (contentType.endsWith("xml")) {
                    // For xml content, convert to CSV as follows:
                    // Each text node has a name that is the xpath from root to containing element
                    // in the form /doc/elem1[1]/.../elemN, and the value is the value of the text node
                    // if the parent of the text node is an attribute, the last part is @attName
                    // e.g. /doc/elem1[1]/.../elemN/@attName
                    csvText = translateXmlToCSV(csvText);
                } else if (contentType.endsWith("json")) {
                    // For json content, convert to CSV as follows:
                    // each primitive value has a name that is the path from root to containing element
                    // in the form doc.elem1[1]/.../elemN
                    csvText = translateJsonToCSV(csvText);
                }
            } catch (DataFormatException dfex) {
                // It's not a Parameters, it must be native JSON or XML
                // that needs to be converted in some way.
                // For these, we treat mapCodes as XPath or JsonPath expressions representing
                // the the body content values, and translate the XML or Json to
                // CSV format based on those values

            }

        }

        /**
         * Given a resource parameter name, get the resource.
         * @param string The name of the parameter to get.
         */
        private BaseResource getResourceParameter(String string) {
            for (Parameters.ParametersParameterComponent param: parameterResource.getParameter()) {
                if (param.getName().equals(string)) {
                    return param.getResource();
                }
            }
            return null;
        }

        private String getParameter(String string) {
            if (parameterResource != null) {
                String parts[] = string.split(".");
                Parameters.ParametersParameterComponent found = null;
                for (Parameters.ParametersParameterComponent param: parameterResource.getParameter()) {
                    if (parts[0].equals(param.getName())) {
                        if (parts.length > 1) {
                            // do it one more time
                            for (Parameters.ParametersParameterComponent param2: param.getPart()) {
                                if (parts[1].equals(param2.getName())) {
                                    found = param2;
                                    break;
                                }
                            }
                        } else {
                            found = param;
                        }
                        break;
                    }
                }
                if (found != null) {
                    if (found.hasValue()) {
                        Type t = found.getValue();
                        if (t instanceof PrimitiveType) {
                            return ((PrimitiveType<?>) t).getValueAsString();
                        }
                    } else if (found.hasResource()) {
                        // The only case where this should be true is where the basic type is binary.
                        // So, what we must do here, is convert it to text in some way.
                        BaseResource r = found.getResource();
                        if (r instanceof Binary) {
                            Binary b = (Binary) r;
                            // Treat it as a binary type based on UTF-8 text. This may not
                            // be correct, but is the best we can do.
                            return new String(b.getData(), StandardCharsets.UTF_8);
                        }
                    }
                }
            }
            return theServletRequest.getParameter(string);
        }


        void validateParameters() {

            if (StringUtils.isEmpty(outputContentType) || "*/*".equals(outputContentType)) {
                outputContentType = "application/fhir+json";
            }

            if (!outputContentType.endsWith("xml") && !outputContentType.endsWith("json") && !outputContentType.endsWith("html")) {
                throw new PreconditionFailedException("Unknown content type: " + outputContentType);
            }

            // Validate the period.start parameter
            DateParam dateParamStart = new DateParam(periodStart);
            // If the period start is given as a day, and there is no period end, then set it to
            // the same as the start.
            if (StringUtils.isEmpty(periodEnd)) {
                if (TemporalPrecisionEnum.DAY.equals(dateParamStart.getPrecision())) {
                    periodEnd = periodStart;
                }
            }

            // Validate the period.end parameter
            @SuppressWarnings("unused")
            DateParam dateParamEnd = new DateParam(periodEnd);

            // Validate referenced resources
            reportedLocation = null;
            reportedOrganization = null;

            // We have to have a location to generate the report. If one is not provided or
            // does
            // not exist, an exception will be thrown which will report back to the user.
            reportedLocation = validateResource(subject, Location.class);

            // If no organization was given, we assume that the managing location
            // was reporting the data.
            if (StringUtils.isEmpty(organization)) {
                organization = reportedLocation.getManagingOrganization().getReference();
            }

            // Again, we need to have a reporting organization, so go get it.
            reportedOrganization = validateResource(organization, Organization.class);
        }

        public void setParameters(Transformer transformer) throws ParserConfigurationException, SAXException, IOException {
            transformer.setParameter("csvInputData", csvText);
            transformer.setParameter("periodStart", periodStart);
            transformer.setParameter("periodEnd", periodEnd);
            transformer.setParameter("reporter", organization);
            transformer.setParameter("reporter-display", StringUtils.defaultString(reportedOrganization.getName()));

            if (reportedOrganization.hasIdentifier()) {
                transformer.setParameter("reporter-identifier",
                    String.format("%s#%s",
                        reportedOrganization.getIdentifier().get(0).getSystem(),
                        reportedOrganization.getIdentifier().get(0).getValue()
                    )
                );
            }

            transformer.setParameter("subject", subject);
            transformer.setParameter("subject-display", StringUtils.defaultString(reportedLocation.getName()));
            if (reportedLocation.hasIdentifier()) {
                transformer.setParameter("subject-identifier",
                    String.format("%s#%s",
                        reportedLocation.getIdentifier().get(0).getSystem(),
                        reportedLocation.getIdentifier().get(0).getValue()
                    )
                );
            }
            if (reportedLocation.hasPosition()) {
                Location.LocationPositionComponent pos = reportedLocation.getPosition();
                if (pos.hasLatitude()) {
                    transformer.setParameter("Lat", pos.getLatitude().toPlainString());
                }
                if (pos.hasLongitude()) {
                    transformer.setParameter("Long", pos.getLongitude().toPlainString());
                }
            }
            DocumentBuilder docBuilder = dfactory.newDocumentBuilder();
            String measureText = parser.encodeResourceToString(reportedMeasure);
            org.w3c.dom.Document measureDocument = docBuilder.parse(new InputSource(new StringReader(measureText)));
            // Apparently have to call getDocumentElement() before SAXON can read it.
            transformer.setParameter("measure", measureDocument.getDocumentElement().getOwnerDocument());

            org.w3c.dom.Document mapDocument = getCsvMappingDocument(reportedMeasure, docBuilder, overrides);
            transformer.setParameter("map", mapDocument.getDocumentElement().getOwnerDocument());
        }

        void writeOutput(HttpServletRequest theRequest, HttpServletResponse theServletResponse, StringWriter outWriter, boolean created) throws IOException {
            MeasureReport mr = parser.parseResource(MeasureReport.class, outWriter.toString());
            writeOutput(theRequest, theServletResponse, mr, created);
        }

        void writeOutput(HttpServletRequest theRequest, HttpServletResponse theServletResponse, DomainResource mr, boolean created) throws IOException {
            theServletResponse.setContentType(outputContentType);
            if (created) {
                theServletResponse.setStatus(HttpServletResponse.SC_CREATED);

                // TODO: This is a cheat, fix it.
                String serverBase = StringUtils.substringBefore(theRequest.getRequestURI(), "/fhir/");
                theServletResponse.setHeader(HttpHeaders.CONTENT_LOCATION, serverBase + "/fhir/" + mr.getId());
            }
            if (outputContentType.endsWith("html")) {
                String html = mr.getText().getDivAsString();
                theServletResponse.getOutputStream().write(html.getBytes(StandardCharsets.UTF_8));
            } else {
                theServletResponse.getOutputStream()
                    .write(new ClientUtils().getResourceAsByteArray(mr, true, outputContentType.endsWith("json")));
            }
            theServletResponse.getOutputStream().close();
        }
    }


    /** Perform post construction initialization steps, mostly
     * involving initialization operations depending on autowired
     * components.
     */
    @PostConstruct
    private void init() {
        System.setProperty("javax.xml.transform.TransformerFactory", "net.sf.saxon.TransformerFactoryImpl");
        resDao = (IFhirResourceDao<Measure>) dao.getResourceDao(Measure.class);
        xsltResource = resourceLoader.getResource("classpath:csvToMeasureReport.xslt");
        xmlToCsvResource = resourceLoader.getResource("classpath:xmlToCsv.xslt");
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
        convert(theServletRequest, theServletResponse, false);
    }

    @Operation(name = "$report", manualResponse = true, manualRequest = true)
    public void report(HttpServletRequest theServletRequest, HttpServletResponse theServletResponse) {
        convert(theServletRequest, theServletResponse, true);
    }

    private void convert(HttpServletRequest theServletRequest, HttpServletResponse theServletResponse, boolean create) {
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
        convert(theServletRequest, theServletResponse, (Measure)l.get(0), create);
    }

    @Operation(name = "$convert", manualResponse = true, manualRequest = true)
    public void convert(HttpServletRequest theServletRequest, HttpServletResponse theServletResponse,
        @IdParam IdType measureId) {
        convert(theServletRequest, theServletResponse, validateResource(measureId, Measure.class), false);
    }

    @Operation(name = "$report", manualResponse = true, manualRequest = true)
    public void report(HttpServletRequest theServletRequest, HttpServletResponse theServletResponse,
        @IdParam IdType measureId) {
        convert(theServletRequest, theServletResponse, validateResource(measureId, Measure.class), true);
    }

    private void convert(HttpServletRequest theServletRequest, HttpServletResponse theServletResponse, Measure reportedMeasure, boolean create) {
        try {
            ConversionRequest conversionRequest = new ConversionRequest(theServletRequest, reportedMeasure);
            conversionRequest.validateParameters();

            // Get a new transformer
            Transformer transformer = getTransformer(xsltResource);

            // Set all the paremeters for the transformation
            conversionRequest.setParameters(transformer);

            StringWriter outWriter = new StringWriter();

            // Perform the transformation
            transformer.transform(new StreamSource(new StringReader("<test/>")), new StreamResult(outWriter));

            if (!create) {
                // Write the output
                conversionRequest.writeOutput(theServletRequest, theServletResponse, outWriter, false);
            }

            // TODO: Check to see if there is already a report for this time period
            // and measure for the specified location and reporter, if there is
            // update it instead of just creating a new one.

            String result = outWriter.toString();
            MeasureReport report = fhirContext.newXmlParser().parseResource(MeasureReport.class, result);
            DaoMethodOutcome oc = dao.getResourceDao(MeasureReport.class).create(report);
            report.setId(oc.getId());

            conversionRequest.writeOutput(theServletRequest, theServletResponse, report, true);
        } catch (Exception e) {
            throw new SanerCsvParserException(e);
        }
    }

    /**
     * Given an XSLT Stylesheet obtain a transformer that will apply it to an input.
     * @param xsltResource  The resource to apply as a transformation
     * @return  A Transformer
     * @throws TransformerConfigurationException
     * @throws IOException
     * @throws TransformerFactoryConfigurationError
     * @throws XPathException
     */

    private Transformer getTransformer(Resource xsltResource)
        throws TransformerConfigurationException, IOException, TransformerFactoryConfigurationError, XPathException {
        StreamSource stylesource = new StreamSource(xsltResource.getInputStream());
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setURIResolver(new ClasspathResourceURIResolver());
        Transformer transformer = transformerFactory.newTransformer(stylesource);
        transformer.setErrorListener(new ErrorListener() {
            public void error(TransformerException exception) throws TransformerException {
                System.err.println("error: " + exception.getMessage());
            }

            public void fatalError(TransformerException exception) throws TransformerException {
                System.err.println("fatal error: " + exception.getMessage());
            }

            public void warning(TransformerException exception) throws TransformerException {
                System.err.println("warning: " + exception.getMessage());
            }
        });

        MessageWarner mw = new MessageWarner();
        mw.setWriter(new StringWriter());
        ((TransformerImpl) transformer).getUnderlyingXsltTransformer().getUnderlyingController()
            .setMessageEmitter(mw);
        return transformer;
    }

    /**
     * Given a measure, convert to an XML DOM Document containing
     * column to report mappings.
     *
     * @param reportedMeasure The Measure
     * @param docBuilder    The mappings
     * @return  An XML Document containing mappings
     * @throws SAXException If there's an error performing the document conversion
     * @throws IOException  Not very likely, it's hard for StringReader to throw an exception.
     */
    private org.w3c.dom.Document getCsvMappingDocument(Measure reportedMeasure, DocumentBuilder docBuilder, Map<String, String> overrides)
        throws SAXException, IOException {
        Map<String, String> mapData = getDefaultCsvColumnMappings(reportedMeasure);
        // Now overwrite any default mappings with values from override table
        mapData.putAll(overrides);
        // Convert map to a string;
        String mapText = getMapText(mapData);
        org.w3c.dom.Document mapDocument = docBuilder.parse(new InputSource(new StringReader(mapText)));
        return mapDocument;
    }

    /**
     * Convert a map into an XML String in the format
     * <map>
     *   <key value='mappedKey'/>
     *      ...
     * </map>
     *
     * @param mapData   A map of key/value pairs to map.
     * @return  The mapped values.
     */
    private String getMapText(Map<?, ?> mapData) {
        StringBuilder b = new StringBuilder();
        b.append("<map>");
        for (Map.Entry<?, ?> e : mapData.entrySet()) {
            b.append("<").append(e.getKey()).append(" value='").append(e.getValue()).append("'/>");
        }
        b.append("</map>");
        String mapText = b.toString();
        return mapText;
    }

    /**
     * Given a measure, get the default mappings for the columns in it.
     * @param reportedMeasure   The measure
     * @return  A map from key to value that maps based on group.code and population.code
     */
    private Map<String, String> getDefaultCsvColumnMappings(Measure reportedMeasure) {
        Map<String, String> mapData = new HashMap<String, String>();
        // For each code
        for (Measure.MeasureGroupComponent group : reportedMeasure.getGroup()) {
            // Put the group code into the map.
            for (Coding groupCode : group.getCode().getCoding()) {
                if (StringUtils.startsWith(groupCode.getSystem(), "http://hl7.org/fhir/us/saner/CodeSystem/Measure")) {
                    String code = groupCode.getCode();
                    if (code != null) {
                        mapData.put(code, code);
                        break;
                    }
                }
            }
            // For each population in the group, put the population code into the map
            for (Measure.MeasureGroupPopulationComponent pop : group.getPopulation()) {
                for (Coding popCode : pop.getCode().getCoding()) {
                    if (StringUtils.startsWith(popCode.getSystem(),
                        "http://hl7.org/fhir/us/saner/CodeSystem/Measure")) {
                        String code = popCode.getCode();
                        if (code != null) {
                            mapData.put(code, code);
                            break;
                        }
                    }
                }
            }
        }
        return mapData;
    }

    /**
     * Given an identifier, go get the resource it references.
     *
     * @param <T>        The type of resource
     * @param resourceId The resource identifier
     * @param type       The type of resource expected
     * @return The referenced resource
     */
    private <T extends org.hl7.fhir.r4.model.Resource> T validateResource(IdType resourceId, Class<T> type) {
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
    private <T extends org.hl7.fhir.r4.model.Resource> T validateResource(String reference, Class<T> type) {
        return validateResource(new IdType(reference), type);
    }

    class ClasspathResourceURIResolver implements URIResolver {
        @Override
        public Source resolve(String href, String base) throws TransformerException {
            return new StreamSource(getClass().getClassLoader().getResourceAsStream(href));
        }
    }
}
