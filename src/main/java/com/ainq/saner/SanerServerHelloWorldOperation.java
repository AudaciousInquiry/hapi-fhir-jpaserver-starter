package com.ainq.saner;

import ca.uhn.fhir.rest.annotation.Operation;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;

public class SanerServerHelloWorldOperation {

  @Operation(name = "$helloWorld", idempotent = true)
  public List<IBaseResource> helloWorldOperation() {
    // Create an organization
    Organization org = new Organization();
    org.setId("Organization/65546");
    org.setName("Test Organization");

    // Create a patient
    Patient patient = new Patient();
    patient.setId("Patient/1333");
    patient.addIdentifier().setSystem("urn:mrns").setValue("253345");
    patient.getManagingOrganization().setResource(org);

    // Here we return only the patient object, which has links to other resources
    List<IBaseResource> retVal = new ArrayList<>();
    retVal.add(patient);
    return retVal;
  }
}
