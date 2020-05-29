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
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportGroupComponent;
import org.hl7.fhir.r4.model.MeasureReport.MeasureReportGroupPopulationComponent;
import org.springframework.beans.factory.annotation.Autowired;

import ca.uhn.fhir.jpa.dao.DaoRegistry;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.server.IResourceProvider;


public class SanerServerMeasureReportTransformOperation implements IResourceProvider {

	/** A registry of data access objects used to talk to the back end data store */
	@Autowired
	private DaoRegistry dao;

	@Operation(name = "$convert", idempotent=true)
	public Binary convert1(@IdParam IdType theMeasureReportId) {

		MeasureReport mr = dao.getResourceDao(MeasureReport.class).read(theMeasureReportId);

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

			for (MeasureReportGroupPopulationComponent population: group.getPopulation()) {
				columnNamesList = columnNamesList+population.getCode().getCoding().get(0).getCode()+",";
				if(population.getCountElement().hasValue()) {
					columnNamesValues = columnNamesValues+population.getCount()+",";
				}
				else {
					columnNamesValues = columnNamesValues+" "+",";
				}
			}
		}

		builder.append(columnNamesList +"\n");
		builder.append(columnNamesValues);
		builder.append('\n');
		pw.write(builder.toString());
		pw.close();

		Binary b = new Binary();
		b.setContentType("application/csv");

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

