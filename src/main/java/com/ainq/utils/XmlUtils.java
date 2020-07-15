package com.ainq.utils;

import java.io.IOException;
import java.io.StringWriter;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

import org.springframework.core.io.Resource;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


import net.sf.saxon.jaxp.TransformerImpl;
import net.sf.saxon.serialize.MessageWarner;
import net.sf.saxon.trans.XPathException;

/**
 * The DomUtils class provides some utility functions for working with XML
 * documents.
 *
 * @author Keith W. Boone
 *
 */
public class XmlUtils {

    private XmlUtils() {

    }

    public static class ClasspathResourceURIResolver implements URIResolver {
        @Override
        public Source resolve(String href, String base) throws TransformerException {
            return new StreamSource(getClass().getClassLoader().getResourceAsStream(href));
        }
    }

    /**
     * Count the prior children that match this node
     *
     * @param elem The element whose children are to be counted.
     * @return
     */
    public static int countPriorMatchingElements(Element elem) {
        String name = elem.getNodeName();
        String ns = elem.getNamespaceURI();
        int count = 0;
        for (elem = (Element) elem.getPreviousSibling(); elem != null; elem = (Element) elem.getPreviousSibling()) {
            if (name.equals(elem.getNodeName()) && ns.equals(elem.getNamespaceURI())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Count the number of prior text nodes.
     *
     * @param node
     * @return The number of prior text nodes.
     */
    public static int countPriorTextNodes(Node node) {
        int count = 0;
        for (node = node.getPreviousSibling(); node != null; node = node.getPreviousSibling()) {
            int type = node.getNodeType();
            if (type == Node.CDATA_SECTION_NODE || type == Node.TEXT_NODE) {
                count++;
            }
        }
        return count;
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

    public static Transformer getTransformer(Resource xsltResource)
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

}

