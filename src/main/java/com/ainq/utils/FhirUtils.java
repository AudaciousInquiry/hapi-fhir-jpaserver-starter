package com.ainq.utils;

import java.util.Calendar;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Property;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.codesystems.DataAbsentReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.context.FhirContext;

/**
 * The DomUtils class provides some utility functions for working with XML
 * documents.
 *
 * @author Keith W. Boone
 *
 */
public class FhirUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(FhirUtils.class);
    public static final String
            DATA_ABSENT_REASON = "http://hl7.org/fhir/StructureDefinition/data-absent-reason";
    public static final String
            ORIGINAL_TEXT = "http://hl7.org/fhir/StructureDefinition/originalText";
    public static final FhirContext ctx = FhirContext.forR4();

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


    /**
     * Set a quantity to a string value, or report the reason why
     * it wasn't set.
     * @param <Q>   The type of the quantity value
     * @param value The value to set
     * @param qty   The place to store the value
     * @return  The stored value.
     */
    public static <Q extends Quantity> Q storeDecimal(String value, Q qty) {
        if (!StringUtils.isBlank(value)) {
            // Hurray! There's a value for this quantity.
            // Convert to DecimalType
            DecimalType dt = null;
            try {
                dt = new DecimalType(value);
            } catch (Exception nfex) {
                LOGGER.debug("{} is not a valid number", value);
            }
            if (dt != null) {
                qty.setValueElement(dt);
            } else {
                qty.addExtension()
                    .setUrl(FhirUtils.DATA_ABSENT_REASON)
                    .setValue(new CodeType(DataAbsentReason.NOTANUMBER.toCode()));
                qty.addExtension()
                    .setUrl(FhirUtils.ORIGINAL_TEXT)
                    .setValue(new StringType(value));
            }
        } else if (value != null) {
            // There's a column, but no value for it.
            qty.addExtension()
                .setUrl(FhirUtils.DATA_ABSENT_REASON)
                .setValue(new CodeType(DataAbsentReason.UNKNOWN.toCode()));
        }
        return qty;
    }

    /**
     * Set an Integer to a int value, or report the reason why
     * it wasn't set.
     * @param value The value to set
     * @param i   The place to store the value
     * @return The stored value.
     */
    public static IntegerType storeInteger(String value, IntegerType i) {
        if (!StringUtils.isBlank(value)) {
            Integer it = null;
            try {
                it = CsvUtils.parseCSVInteger(value);
            } catch (Exception nfex) {
                LOGGER.debug("{} is not a valid number", value);
            }
            if (it != null) {
                i.setValue(it);
            } else {
                i.addExtension()
                    .setUrl(FhirUtils.DATA_ABSENT_REASON)
                    .setValue(new CodeType(DataAbsentReason.NOTANUMBER.toCode()));
                i.addExtension()
                    .setUrl(FhirUtils.ORIGINAL_TEXT)
                    .setValue(new StringType(value));
            }

            return i;
        } else if (value != null) {
            // The column is present, but it has no value
            i.addExtension()
                .setUrl(FhirUtils.DATA_ABSENT_REASON)
                .setValue(new CodeType(DataAbsentReason.UNKNOWN.toCode()));
        } else {
            // The column has no value
            i.addExtension()
                .setUrl(FhirUtils.DATA_ABSENT_REASON)
                .setValue(new CodeType(DataAbsentReason.UNSUPPORTED.toCode()));
        }
        return i;
     }

    public static boolean isResource(String resourceType) {
        try {
            ctx.getResourceDefinition(resourceType);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
