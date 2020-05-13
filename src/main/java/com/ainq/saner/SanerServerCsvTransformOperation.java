package com.ainq.saner;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.apache.commons.io.IOUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLFilter;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

import net.sf.saxon.jaxp.TransformerImpl;
import net.sf.saxon.s9api.XsltTransformer;
import net.sf.saxon.serialize.MessageWarner;
import net.sf.saxon.trace.XSLTTraceListener;

public class SanerServerCsvTransformOperation {

  @Autowired
  private ResourceLoader resourceLoader;

  @Autowired
  private FhirContext fhirContext;

  @Operation(name="manualReport", manualResponse=true, manualRequest=true)
  public void manualInputAndOutput(HttpServletRequest theServletRequest, HttpServletResponse theServletResponse) throws IOException {
    String contentType = theServletRequest.getContentType();
    byte[] bytes = IOUtils.toByteArray(theServletRequest.getInputStream());

    System.out.println("Received call with content type {} and {} bytes " + contentType + bytes.length);

    theServletResponse.setContentType(contentType);
    theServletResponse.getOutputStream().write(bytes);
    theServletResponse.getOutputStream().close();
  }

  @Operation(name = "$report", manualResponse=true, manualRequest=true)
  public MeasureReport report(HttpServletRequest theServletRequest, HttpServletResponse theServletResponse) {
    String organization = theServletRequest.getParameter("reporter");
    if(organization != null){
      System.out.println("DEBUG ---> got this reporter " + organization);
    }else{
      System.out.println("DEBUG ---> org is null");
    }

    System.setProperty("javax.xml.transform.TransformerFactory",
      "net.sf.saxon.TransformerFactoryImpl");
    Resource xsltResource = resourceLoader.getResource("classpath:stateLabReportingExamples2ToFsh.xslt");
    StringWriter outWriter = new StringWriter();
      try {
        SAXTransformerFactory tFactory = new net.sf.saxon.TransformerFactoryImpl();
        javax.xml.transform.sax.TemplatesHandler templatesHandler =
          tFactory.newTemplatesHandler();

        String xsltText = IOUtils.toString(xsltResource.getInputStream(), StandardCharsets.UTF_8);

        StreamSource stylesource = new StreamSource(xsltResource.getInputStream());
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setURIResolver(new ClasspathResourceURIResolver());

        Transformer transformer = transformerFactory.newTransformer(stylesource);
        Source src = new StreamSource(new StringReader("<test/>"));

        transformer.setErrorListener(new ErrorListener() {
          public void error(TransformerException exception) throws TransformerException
          {
            System.err.println("error: " + exception.getMessage());
          }

          public void fatalError(TransformerException exception) throws TransformerException
          {
            System.err.println("fatal error: " + exception.getMessage());
          }

          public void warning(TransformerException exception) throws TransformerException
          {
            System.err.println("warning: " + exception.getMessage());
          }
        });

        //String mappingText = IOUtils.toString(mappingFile.getInputStream(), StandardCharsets.UTF_8);
        String csvText = IOUtils.toString(theServletRequest.getInputStream(), StandardCharsets.UTF_8);


        StreamResult result = new StreamResult( outWriter );

        MessageWarner mw = new MessageWarner();
        mw.setWriter(new StringWriter());
        ((TransformerImpl) transformer).getUnderlyingXsltTransformer().getUnderlyingController().setMessageEmitter(mw);
        //transformer.setParameter("mapping", mappingText);
        transformer.setParameter("csvInputData", csvText);
        //transformer.setParameter("format", format);
        transformer.transform(src, result);
      } catch (TransformerException e) {
        throw new SanerCsvParserException(e);
      } catch (NullPointerException e) {
        throw new SanerCsvParserException(e);
      } catch (IOException e) {
        e.printStackTrace();
      }
    //return new ResponseEntity(stream, HttpStatus.OK);
    StringBuffer sb = outWriter.getBuffer();
    String finalstring = sb.toString();

    System.out.println("DEBUG ---> gonna try to turn this into a measure " + finalstring);

    IParser parser = fhirContext.newXmlParser();

    MeasureReport mr = parser.parseResource(MeasureReport.class, finalstring);
    return mr;
  }


  private static XMLReader makeXMLReader() throws ParserConfigurationException, SAXException, org.xml.sax.SAXException {
    SAXParserFactory factory = SAXParserFactory.newInstance();
    factory.setNamespaceAware(true);
    return factory.newSAXParser().getXMLReader();
  }

//  @Override
//  public Class<? extends IBaseResource> getResourceType() {
//    return Measure.class;
//  }

  class ClasspathResourceURIResolver implements URIResolver {
    @Override
    public Source resolve(String href, String base) throws TransformerException {
      return new StreamSource(getClass().getClassLoader().getResourceAsStream(href));
    }
  }
}
