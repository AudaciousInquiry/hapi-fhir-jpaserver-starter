package com.ainq.saner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportGroupComponent;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportGroupPopulationComponent;
import org.springframework.beans.factory.annotation.Autowired;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.IResourceProvider;


public class SanerServerMeasureReportTransformOperation implements IResourceProvider {
	/** The servers fhirContext */
	@Autowired
	private FhirContext fhirContext;



	@Operation(name = "$convert", idempotent=true)
	public MeasureReport convert(@IdParam IdType theMeasureReportId) {		


		FhirContext ctx = FhirContext.forR4();
		String serverBase = "http://localhost:8080/hapi-fhir-jpaserver/fhir";
		IGenericClient client = ctx.newRestfulGenericClient(serverBase);

		MeasureReport mr = client.read(MeasureReport.class, theMeasureReportId.getIdPart());

		return mr;

	}

	@Operation(name = "$convert1", idempotent=true)
	public Binary convert1(@IdParam IdType theMeasureReportId) {

		FhirContext ctx = FhirContext.forR4();
		String serverBase = "http://localhost:8080/hapi-fhir-jpaserver/fhir";
		IGenericClient client = ctx.newRestfulGenericClient(serverBase);

		MeasureReport mr = client.read(MeasureReport.class, theMeasureReportId.getIdPart());

	//	String output = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(mr);

	//	System.out.println(output);
		
		
		String columnNamesList = "";
		String columnNamesValues = "";
		PrintWriter pw = null;
        try {
            pw = new PrintWriter(new File(theMeasureReportId.getIdPart()+".csv"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        StringBuilder builder = new StringBuilder();
        
        List<MeasureReportGroupComponent> grouplist = mr.getGroup();
		for (MeasureReportGroupComponent group : grouplist) {
			System.out.println("\n");
			System.out.println(group.getCode().getCoding().get(0).getCode());

			for (MeasureReportGroupPopulationComponent population: group.getPopulation()) {
				if(population.getCode().getCoding().size() == 2) {
				System.out.println(population.getCode().getCoding().get(0).getCode()+"/"+population.getCode().getCoding().get(1).getCode());
				columnNamesList = columnNamesList+","+population.getCode().getCoding().get(0).getCode()+"/"+population.getCode().getCoding().get(1).getCode();
				}
				else {
					System.out.println(population.getCode().getCoding().get(0).getCode());
					columnNamesList = columnNamesList+","+population.getCode().getCoding().get(0).getCode();
				}
				if(population.getCountElement().hasValue()) {
					System.out.println(population.getCount());
					columnNamesValues = columnNamesValues+","+population.getCount();
				}
				else {
					System.out.println("No Count");
					columnNamesValues = columnNamesValues+","+"No Count";
				}
			}
		}
		
	        builder.append(columnNamesList +"\n");
	        builder.append(columnNamesValues);
	        builder.append('\n');
	        pw.write(builder.toString());
	        pw.close();
	        
	       
	        
	        Binary b = new Binary();
	        Bundle bn = new Bundle();
	        
	       
	        try {
				b.setData(IOUtils.toByteArray(new FileInputStream(new File(theMeasureReportId.getIdPart()+".csv"))));
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        
	        return b;
	}

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return MeasureReport.class;
	}



}

