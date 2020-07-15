package com.ainq.utils;

import java.io.IOException;
import java.io.StringReader;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.codesystems.DataAbsentReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;

/**
 * The DomUtils class provides some utility functions for working with XML
 * documents.
 *
 * @author Keith W. Boone
 *
 */
public class CsvUtils {

    private static final String DATE_TIME_FORMATS[] = { "yyyy-MM-dd HH:mm:ss", "MM/dd/yyyy HH:mm:ss",
    "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
    "yyyy-MM-dd'T'HH:mm:ss.SSSZ", "MM/dd/yyyy'T'HH:mm:ss.SSS'Z'", "MM/dd/yyyy'T'HH:mm:ss.SSSZ",
    "MM/dd/yyyy'T'HH:mm:ss.SSS", "MM/dd/yyyy'T'HH:mm:ssZ", "MM/dd/yyyy'T'HH:mm:ss" };
    private static final String DATE_FORMATS[] = { "MM/dd/yyyy", "dd MMM yyyy", "dd-MMM-yyyy", "yyyy-MM-dd",
    "yyyyMMdd" };
    private static final Logger LOGGER = LoggerFactory.getLogger(CsvUtils.class);

    private CsvUtils() {

    }

    public static DateTimeType parseCSVDate(String value, org.hl7.fhir.r4.model.Element errorLoc) {
        if (StringUtils.isBlank(value)) {
            errorLoc.addExtension(FhirUtils.DATA_ABSENT_REASON, new CodeType(DataAbsentReason.UNSUPPORTED.toCode()));
        } else {
            DateTimeType date = null;
            try {
                date = new DateTimeType(DateUtils.parseDate(value, value.contains(":") ? DATE_TIME_FORMATS : DATE_FORMATS));
                return date;
            } catch (Exception ex) {
                errorLoc.addExtension(FhirUtils.DATA_ABSENT_REASON, new CodeType(DataAbsentReason.ERROR.toCode()));
                errorLoc.addExtension(FhirUtils.ORIGINAL_TEXT, new StringType(value));
            }
        }
        return null;
    }

    public static String translateJsonToCSV(String json) {
        JsonParser parser = new JsonParser();
        JsonElement root = parser.parse(json);
        StringBuilder b = new StringBuilder();
        walkJson(root, "", b, true);
        b.append("\n");
        walkJson(root, "", b, false);
        b.append("\n");
        return b.toString();
    }

    public static String translateXmlToCSV(String xml) {
        DocumentBuilderFactory b = DocumentBuilderFactory.newInstance();
        b.setCoalescing(true);
        b.setIgnoringComments(true);
        b.setNamespaceAware(true);
        b.setValidating(false);
        try {
            Document doc = b.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            StringBuilder s = new StringBuilder();
            walkXml(doc, "", s, true);
            s.append("\n");
            walkXml(doc, "", s, false);
            s.append("\n");
            return b.toString();
        } catch (SAXException | IOException | ParserConfigurationException e) {
            LOGGER.debug("Error parsing XML", e);
            throw new InvalidRequestException("Error parsing XML: " + e);
        }

    }

    private static void walkJson(JsonElement node, String name, StringBuilder b, boolean isHeader) {
        if (node instanceof JsonArray) {
            JsonArray array = (JsonArray)node;
            int count = 0;
            for (JsonElement elem: array) {
                name += String.format("[%d]", count++);
                walkJson(elem, name, b, isHeader);
            }
        } else if (node instanceof JsonObject) {
            JsonObject object = (JsonObject)node;
            for (String key: object.keySet()) {
                if (name.length() != 0) {
                    name += ".";
                }
                walkJson(object.get(key), name + key, b, isHeader);
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

    private static void walkXml(Node node, String name, StringBuilder s, boolean isHeader) {
        switch (node.getNodeType()) {
        case Node.DOCUMENT_TYPE_NODE:
            node = ((Document)node).getDocumentElement();
            // Fall through
        case Node.ELEMENT_NODE:
            if (isHeader) {
                name += "/";
                name += node.getNodeName() + "[" + XmlUtils.countPriorMatchingElements((org.w3c.dom.Element) node) + "]";
            }
            for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
                walkXml(child, name, s, isHeader);
            }
            break;
        case Node.ATTRIBUTE_NODE:
            if (isHeader) {
                name += "@" + node.getNodeName();
            }
            s.append(isHeader ? name : StringEscapeUtils.escapeCsv(node.getNodeValue())).append(',');
            break;
        case Node.CDATA_SECTION_NODE:
        case Node.TEXT_NODE:
            if (isHeader && (node.getPreviousSibling() != null || node.getNextSibling() != null)) {
                name += "/text()[" + XmlUtils.countPriorTextNodes(node) + "]";
            }
            s.append(isHeader ? name : StringEscapeUtils.escapeCsv(node.getNodeValue()));
        }
    }

}

