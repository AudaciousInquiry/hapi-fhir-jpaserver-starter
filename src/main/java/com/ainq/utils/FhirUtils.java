package com.ainq.utils;

import java.util.Calendar;

import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Property;
import org.hl7.fhir.r4.model.Reference;

/**
 * The DomUtils class provides some utility functions for working with XML
 * documents.
 *
 * @author Keith W. Boone
 *
 */
public class FhirUtils {

    public static final String
            DATA_ABSENT_REASON = "http://hl7.org/fhir/StructureDefinition/data-absent-reason";
    public static final String
            ORIGINAL_TEXT = "http://hl7.org/fhir/StructureDefinition/originalText";


    private FhirUtils() {

    }

    /**
     * Convert a resource into a reference with display populated
     * with the name, and identifier populated with the identifer (when either
     * are present).
     *
     * @param resource  The resource to generate a reference to.
     * @return  The generated reference.
     */
    public static Reference getReference(DomainResource resource) {
        Reference ref = new Reference(resource.getIdElement());
        Property name = resource.getNamedProperty("name");
        if (name.hasValues()) {
            ref.setDisplay(name.getValues().get(0).primitiveValue());
        }
        Property ident = resource.getNamedProperty("identifier");
        if (ident.hasValues()) {
            ref.setIdentifier((Identifier)ident.getValues().get(0));
        }
        return ref;
    }


    /**
     * Get the suggested precision for the DateTimeType
     * @param start The dateTime
     * @return The suggested degree of precision for which this item was recorded.
     */
    public static int getCalendarPrecision(BaseDateTimeType start) {
        String value = start.asStringValue();
        value = value.substring(0, value.length()-6);
        if (value.contains(".") && !value.matches("^[^\\.]*\\.0+")) {
            return Calendar.MILLISECOND;
        }
        if (!value.endsWith(":00")) {
            return Calendar.SECOND;
        }
        if (!value.endsWith(":00:00")) {
            return Calendar.MINUTE;
        }
        if (!value.endsWith("T00:00:00")) {
            return Calendar.HOUR;
        }
        return Calendar.DAY_OF_MONTH;
    }

}
