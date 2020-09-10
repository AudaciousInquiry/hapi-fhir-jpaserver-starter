package com.ainq.saner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.Measure.MeasureGroupComponent;
import org.hl7.fhir.r4.model.Measure.MeasureGroupPopulationComponent;
import org.springframework.beans.factory.annotation.Autowired;

import ca.uhn.fhir.jpa.dao.DaoRegistry;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.server.IResourceProvider;


public class SanerServerMeasureTransformOperation implements IResourceProvider {

	/** A registry of data access objects used to talk to the back end data store */
	@Autowired
	private DaoRegistry dao;

	/** Generate a CSV Template from a Measure */
	@Operation(name = "$template", idempotent=true)
	public Binary convert1(@IdParam IdType theMeasureId) {

		Measure mr = dao.getResourceDao(Measure.class).read(theMeasureId);

		StringBuffer rows[] = { new StringBuffer(), new StringBuffer(), new StringBuffer(), new StringBuffer() };
		PrintWriter pw = null;
		try {
			pw = new PrintWriter(new File(theMeasureId.getIdPart()+".csv"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		List<MeasureGroupComponent> grouplist = mr.getGroup();
		for (MeasureGroupComponent group : grouplist) {
		    Coding coding = group.getCode().getCoding().get(0);
		    rows[0].append(coding.getCode()).append(",");
            rows[1].append('"').append(coding.getDisplay()).append("\",");
            rows[2].append('"').append(group.getCode().getText()).append("\",");
            rows[3].append('"').append(StringUtils.defaultString(group.getDescription())).append("\",");
			for (MeasureGroupPopulationComponent population: group.getPopulation()) {
	            coding = population.getCode().getCoding().get(0);
	            rows[0].append(coding.getCode()).append(",");
	            rows[1].append('"').append(coding.getDisplay()).append("\",");
	            rows[2].append('"').append(population.getCode().getText()).append("\",");
	            rows[3].append('"').append(StringUtils.defaultString(population.getDescription())).append("\",");
			}
		}
		for (StringBuffer b: rows) {
		    b.setCharAt(b.length()-1, '\n');
	        pw.write(b.toString());
		}
		pw.close();

		Binary b = new Binary();
		b.setContentType("application/csv");

		try {
			b.setData(IOUtils.toByteArray(new FileInputStream(new File(theMeasureId.getIdPart()+".csv"))));
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
		return Measure.class;
	}

}

