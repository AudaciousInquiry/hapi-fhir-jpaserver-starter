package com.ainq.saner;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.hl7.fhir.r4.model.BaseResource;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Element;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.SimpleQuantity;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportGroupComponent;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportGroupPopulationComponent;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportStatus;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportType;
import org.hl7.fhir.r4.model.codesystems.DataAbsentReason;
import org.hl7.fhir.r4.utils.client.ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.ainq.utils.CsvUtils;
import com.ainq.utils.FhirUtils;
import com.ainq.utils.JpaUtils;
import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.CSVReaderHeaderAwareBuilder;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.dao.DaoMethodOutcome;
import ca.uhn.fhir.jpa.dao.DaoRegistry;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;

/** This class handles a conversion request */
class ConversionRequest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConversionRequest.class);
    private String organization;
    private String subject;
    private String periodStart;
    private String periodEnd;
    String csvText;
    private String map;
    private Organization reportedOrganization;
    private Location reportedLocation;
    private Measure reportedMeasure;
    @Autowired
    private DaoRegistry dao;
    @Autowired
    private FhirContext fhirContext;
    private IParser parser;
    private String inputContentType;
    private String outputContentType;
    private String serverBase;
    private int count = 0;
    private Map<String, String> overrides = new HashMap<>();
    transient private HttpServletRequest theServletRequest;
    transient private HttpServletResponse theServletResponse;
    private boolean forCreate = false;
    private Parameters parameterResource = null;
    private Bundle result = new Bundle();
    private static final String
            PERIOD = "period";
    private static final String
            PERIOD_END = "period.end";
    private static final String
            PERIOD_START = "period.start";
    private static final String
            REPORTER = "reporter";
    private static final String
            SUBJECT = "subject";


    ConversionRequest(DaoRegistry dao, FhirContext fhirContext, HttpServletRequest theServletRequest, HttpServletResponse theServletResponse, boolean forCreate, Measure measure) throws IOException {
        this.parser = fhirContext.newXmlParser();
        this.theServletRequest = theServletRequest;
        this.theServletResponse = theServletResponse;
        this.forCreate = forCreate;
        this.dao = dao;
        this.fhirContext = fhirContext;
        this.reportedMeasure = measure;

        // TODO: This is a cheat, fix it.
        serverBase = StringUtils.substringBefore(
            theServletRequest.getRequestURL().toString(), "/fhir/"
        );

        setParametersFromRequest();

    }

    /**
     * Set the parameters from the HttpServletRequest for the conversion.
     * @throws IOException if there is an error reading the request body.
     */
    private void setParametersFromRequest() throws IOException {
        inputContentType = theServletRequest.getContentType();
        outputContentType = theServletRequest.getParameter("_format");
        csvText = IOUtils.toString(theServletRequest.getInputStream(), StandardCharsets.UTF_8);
        // Remove the Byte Order Mark if Present
        if (csvText.charAt(0) == '\uFEFF') {
            csvText = csvText.substring(1);
        }
        handleContentType();

        organization = getParameter("reporter");
        subject = getParameter("subject");
        periodStart = getParameter("period.start");
        periodEnd = getParameter("period.end");
        map = getParameter("map");

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
                csvText = CsvUtils.translateXmlToCSV(csvText);
            } else if (contentType.endsWith("json")) {
                // For json content, convert to CSV as follows:
                // each primitive value has a name that is the path from root to containing element
                // in the form doc.elem1[1]/.../elemN
                csvText = CsvUtils.translateJsonToCSV(csvText);
            }
        } catch (DataFormatException dfex) {
            // It's not a Parameters, it must be native JSON or XML
            // TODO: that needs to be converted in some way.
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

        /* If the output is to be created (stored), ensure that all required components
         * are provided.
         */
        if (forCreate) {
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
            // does not exist, an exception will be thrown which will report back to the user.
            if (!StringUtils.isBlank(subject)) {
                reportedLocation = JpaUtils.validateResource(dao, subject, Location.class);
            }

            // If no organization was given, we assume that the managing location
            // was reporting the data.
            if (StringUtils.isEmpty(organization) && reportedLocation != null) {
                organization = reportedLocation.getManagingOrganization().getReference();
            } else if (!StringUtils.isBlank(organization)) {
                // Again, we need to have a reporting organization, so go get it.
                reportedOrganization = JpaUtils.validateResource(dao, organization, Organization.class);
            }
        }
    }

    void writeOutput() throws IOException {
        writeOutput(theServletRequest, theServletResponse, result, forCreate);
    }

    void writeOutput(HttpServletRequest theRequest, HttpServletResponse theServletResponse, Bundle mr, boolean created) throws IOException {
        mr.setType(BundleType.COLLECTION);
        mr.setTimestamp(new Date());
        mr.setTotal(count);
        theServletResponse.setContentType(outputContentType);
        if (created) {
            theServletResponse.setStatus(HttpServletResponse.SC_CREATED);
        }
        if (outputContentType.endsWith("html")) {
            for (Bundle.BundleEntryComponent comp: mr.getEntry()) {
                String html = ((DomainResource) comp.getResource()).getText().getDivAsString();
                theServletResponse.getOutputStream().write(html.getBytes(StandardCharsets.UTF_8));
            }
        } else {
            theServletResponse.getOutputStream()
                .write(new ClientUtils().getResourceAsByteArray(mr, false, outputContentType.endsWith("json")));
        }
        theServletResponse.getOutputStream().close();
    }

    private MeasureReport createMeasureReport() {
        MeasureReport mr = new MeasureReport();
        mr.addIdentifier().setSystem("urn:ietf:rfc:3986").setValue("urn:uuid:" + UUID.randomUUID().toString());
        mr.setStatus(MeasureReportStatus.COMPLETE);
        mr.setType(MeasureReportType.SUMMARY);
        mr.setMeasure(reportedMeasure.getUrl());

        if (reportedLocation != null) {
            mr.setSubject(FhirUtils.getReference(reportedLocation));
        }
        if (reportedOrganization != null) {
            mr.setReporter(FhirUtils.getReference(reportedOrganization));
        }
        if (periodStart != null || periodEnd != null) {
            Period period = new Period();
            if (periodStart != null) {
                period.setStartElement(new DateTimeType(periodStart));
            }
            if (periodEnd != null) {
                period.setEndElement(new DateTimeType(periodEnd));
            }
        }
        return mr;
    }


    public void map(List<Map<String, String>> r) {
        MeasureReport mr = createMeasureReport();

        // For now, just deal with a single row.
        // TODO: Address the issue that there could be multiple rows with different stratifiers and strata
        Map<String,String> row = r.get(0);

        // For each group in the Measure
        for (Measure.MeasureGroupComponent group: reportedMeasure.getGroup()) {
            MeasureReportGroupComponent mrGroup = mr.addGroup();
            // Set the code of the first group in the report to the code of the first group in the measure.
            CodeableConcept codeValue = group.getCode();
            mrGroup.setCode(codeValue);

            String heading = mapHeading(codeValue);
            String value = row.get(heading);
            if (!StringUtils.isBlank(value)) {
                // Hurray! There's a value for this measure.
                // Convert to DecimalType
                DecimalType dt = null;
                try {
                    dt = new DecimalType(value);
                } catch (Exception nfex) {
                    // TODO: Log the error;
                }
                if (dt != null) {
                    mrGroup.setMeasureScore(new SimpleQuantity().setValueElement(dt));
                } else {
                    mrGroup.getMeasureScore().addExtension()
                        .setUrl(FhirUtils.DATA_ABSENT_REASON)
                        .setValue(new CodeType(DataAbsentReason.NOTANUMBER.toCode()));
                    mrGroup.getMeasureScore().addExtension()
                        .setUrl(FhirUtils.ORIGINAL_TEXT)
                        .setValue(new StringType(value));
                }
            } else if (value != null) {
                // There's a column, but no value for it.
                mrGroup.getMeasureScore().addExtension()
                    .setUrl(FhirUtils.DATA_ABSENT_REASON)
                    .setValue(new CodeType(DataAbsentReason.UNKNOWN.toCode()));
            }


            // For each population in the group
            // This is the part that gets done for the rows with no stratifiers  values.
            for (Measure.MeasureGroupPopulationComponent population: group.getPopulation()) {
                codeValue = population.getCode();
                MeasureReportGroupPopulationComponent mrPopulation = mrGroup.addPopulation();
                mrPopulation.setCode(codeValue);
                heading = mapHeading(codeValue);
                value = row.get(heading);
                if (!StringUtils.isBlank(value)) {

                    IntegerType it = null;
                    try {
                        it = new IntegerType(value);
                    } catch (Exception nfex) {
                        LOGGER.debug("{} {} is not a valid number", heading, value);
                    }
                    if (it != null) {
                        mrPopulation.setCountElement(it);
                    } else {
                        mrPopulation.getCountElement().addExtension()
                            .setUrl(FhirUtils.DATA_ABSENT_REASON)
                            .setValue(new CodeType(DataAbsentReason.NOTANUMBER.toCode()));
                        mrPopulation.getCountElement().addExtension()
                            .setUrl(FhirUtils.ORIGINAL_TEXT)
                            .setValue(new StringType(value));
                    }

                    mrPopulation.setCountElement(new IntegerType(value));
                } else if (value != null) {
                    // The column is present, but it has no value
                    mrPopulation.getCountElement().addExtension()
                        .setUrl(FhirUtils.DATA_ABSENT_REASON)
                        .setValue(new CodeType(DataAbsentReason.UNKNOWN.toCode()));
                } else {
                    // The column has no value
                    mrPopulation.getCountElement().addExtension()
                        .setUrl(FhirUtils.DATA_ABSENT_REASON)
                        .setValue(new CodeType(DataAbsentReason.UNSUPPORTED.toCode()));
                }
            }
        }
        getMissingParts(mr, row);

        boolean failed = false;
        try {
            if (forCreate) {
                // TODO: Verify that subject and reporter have been set in measureReport.
                if (mr.hasSubject() && mr.hasReporter()) {
                    // TODO: Check to see if there is already a report for this time period
                    // and measure for the specified location and reporter, if there is
                    // update it instead of just creating a new one.
                    DaoMethodOutcome oc = dao.getResourceDao(MeasureReport.class).create(mr);
                    mr.setId(oc.getId());
                } else {
                    LOGGER.error("Missing subject or reporter in MeasureReport");
                    failed = true;
                }
            }
        } catch (Exception ex) {
            LOGGER.error("Creation failed for MeasureReport");
            failed = true;
        }
        if (!failed) {
            // Set FullUrl Value in bundle
            BundleEntryComponent comp = result.addEntry().setResource(mr);
            count++;
            if (mr.hasIdElement()) {
                comp.setFullUrl(serverBase + "/fhir/" + mr.getId());
            }
        }
    }

    /** Get missing parts of a MeasureReport from the CSV rows
     *
     * @param mr    The MeasureReport to update
     * @param row   The row that may contain the missing pieces.
     */
    private void getMissingParts(MeasureReport mr, Map<String, String> row) {
        @SuppressWarnings("unused")
        String heading = null, value = null;
        if (this.reportedLocation == null) {
            Location loc = findResourceFromCSV(SUBJECT, mr.getSubject(), row, Location.class);;
            if (loc != null) {
                mr.setSubject(FhirUtils.getReference(loc));
            }
        }

        if (this.reportedOrganization == null) {
            Organization org = findResourceFromCSV(REPORTER, mr.getReporter(), row, Organization.class);;
            if (org != null) {
                mr.setReporter(FhirUtils.getReference(org));
            }
        }

        if (this.periodEnd == null || this.periodStart == null) {
            DateTimeType start = null, end = null;
            Period period = mr.getPeriod();
            Element errorLoc = period;
            if (!StringUtils.isBlank(value = row.get(heading = mapHeading(PERIOD)))) {
                start = CsvUtils.parseCSVDate(value, errorLoc);
                if (start != null) {
                    period.setStartElement(start);
                    end = start.copy();
                    end.add(FhirUtils.getCalendarPrecision(start), 1);
                    period.setEndElement(end);
                    mr.setPeriod(period);
                }
            } else {
                errorLoc = mr.getPeriod().getStartElement();
                start = CsvUtils.parseCSVDate(value = row.get(heading = mapHeading(PERIOD_START)), errorLoc);
                if (start != null) {
                    mr.getPeriod().setStartElement(start);
                }
                errorLoc = mr.getPeriod().getEndElement();
                end = CsvUtils.parseCSVDate(value = row.get(heading = mapHeading(PERIOD_END)), errorLoc);
                if (end != null) {
                    mr.getPeriod().setEndElement(end);
                }
            }
        }
    }

    private <T extends org.hl7.fhir.r4.model.Resource> T findResourceFromCSV(String name, Reference field, Map<String, String> row, Class<T> type) {
        @SuppressWarnings("unused")
        String value = null, heading = null;
        Element errorLoc = field;
        try {
            if (!StringUtils.isBlank(value = row.get(heading = mapHeading(name)))) {
                // There's a subject mapping
                return JpaUtils.validateResource(dao, value, type);
            } else if (!StringUtils.isBlank(value = row.get(heading = mapHeading(name + ".name")))) {
                errorLoc = field.getDisplayElement();
                // There's a subject name mapping
                field.setDisplay(value);
                return JpaUtils.lookupByName(dao, value, type);
            } else if (!StringUtils.isBlank(value = row.get(heading = mapHeading(name + ".identifier")))) {
                // There's a subject identifier mapping
                errorLoc = field.getIdentifier();
                field.setIdentifier(new Identifier().setValue(value));
                return JpaUtils.lookupByIdentifier(dao, value, type);
            }
        } catch (Exception ex) {
            errorLoc.addExtension(FhirUtils.DATA_ABSENT_REASON, new CodeType(
                (ex instanceof ResourceNotFoundException ? DataAbsentReason.UNKNOWN : DataAbsentReason.ERROR)
                .toCode()));
            errorLoc.addExtension(FhirUtils.ORIGINAL_TEXT, new StringType(value));
        }
        return null;
    }
    /**
     * Given a CodeableConcept, find the heading that maps to it.
     * @param codeValue
     * @return
     */
    private String mapHeading(CodeableConcept codeValue) {
        // Start first with the system|value pair of the first code,
        // then look for simply value of the first code, then
        // repeat the process for each code.
        for (Coding coding : codeValue.getCoding()) {
            String heading = overrides.get(String.format("%s|%s", coding.getSystem(), coding.getCode()));
            if (heading != null) {
                return heading;
            }
            heading = overrides.get(coding.getCode());
            if (heading != null) {
                return heading;
            }
        }
        // If there were no overrides stored, then the heading is simply the first coding.code
        return codeValue.getCoding().get(0).getCode();
    }

    private String mapHeading(String codeValue) {
        String heading = overrides.get(codeValue);
        if (heading != null) {
            return heading;
        }
        return codeValue;
    }

    static void convert(DaoRegistry dao, FhirContext fhirContext, HttpServletRequest theServletRequest, HttpServletResponse theServletResponse, IdType measureId, boolean create) {
        convert(dao, fhirContext, theServletRequest, theServletResponse,
                JpaUtils.validateResource(dao, measureId, Measure.class), create);
    }

    static void convert(DaoRegistry dao, FhirContext fhirContext, HttpServletRequest theServletRequest, HttpServletResponse theServletResponse, Measure reportedMeasure, boolean create) {
        try {
            ConversionRequest conversionRequest = new ConversionRequest(dao, fhirContext, theServletRequest, theServletResponse, create, reportedMeasure);
            conversionRequest.validateParameters();

            CSVReaderHeaderAware r = new CSVReaderHeaderAwareBuilder(new StringReader(conversionRequest.csvText)).build();

            Map<String, String> row;

            // For Each row
            // TODO: Handle Strata: for each set of rows with matching period,
            // reporter and subject (e.g., each unique report)
            while ((row = r.readMap()) != null) {
                conversionRequest.map(Collections.singletonList(row));
            }

            // Write the output
            conversionRequest.writeOutput();
        } catch (Exception e) {
            throw new SanerCsvParserException(e);
        }
    }
}