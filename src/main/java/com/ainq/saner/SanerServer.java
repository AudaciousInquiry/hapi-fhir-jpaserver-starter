package com.ainq.saner;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.starter.HapiProperties;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.WebAppContext;
import java.nio.file.Paths;

/**
 * This class is a simple test server for debugging.
 *
 * @author Keith W. Boone
 *
 */
public class SanerServer {

    private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(SanerServer.class);
    private static IGenericClient ourClient;
    private static FhirContext ourCtx;
    private static int ourPort;
    private static Server ourServer;

    static {
        HapiProperties.forceReload();
        HapiProperties.setProperty(HapiProperties.DATASOURCE_URL, "jdbc:h2:mem:dbr4");
        HapiProperties.setProperty(HapiProperties.FHIR_VERSION, "R4");
        HapiProperties.setProperty(HapiProperties.SUBSCRIPTION_WEBSOCKET_ENABLED, "true");
        ourCtx = FhirContext.forR4();
    }

    public static void beforeClass() throws Exception {
        String path = Paths.get("").toAbsolutePath().toString();

        ourLog.info("Project base path is: {}", path);

        ourServer = new Server(ourPort);

        WebAppContext webAppContext = new WebAppContext();
        webAppContext.setContextPath("/");
        webAppContext.setDisplayName("HAPI FHIR");
        webAppContext.setDescriptor(path + "/src/main/webapp/WEB-INF/web.xml");
        webAppContext.setResourceBase(path + "/target/hapi-fhir-jpaserver-starter");
        webAppContext.setParentLoaderPriority(true);

        ourServer.setHandler(webAppContext);
        ourServer.start();

        ourPort = getPortForStartedServer(ourServer);

        ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        ourCtx.getRestfulClientFactory().setSocketTimeout(1200 * 1000);
        String ourServerBase = HapiProperties.getServerAddress();
        ourServerBase = "http://localhost:" + ourPort + "/fhir/";

        ourClient = ourCtx.newRestfulGenericClient(ourServerBase);
        ourClient.registerInterceptor(new LoggingInterceptor(true));
    }

    public static void main(String[] theArgs) throws Exception {
        ourPort = 8080;
        beforeClass();
    }

    public static int getPortForStartedServer(Server server) {
        assert server.isStarted();
        Connector[] connectors = server.getConnectors();
        assert connectors.length == 1;
        return ((ServerConnector) (connectors[0])).getLocalPort();
    }
}
