package de.medizininformatik_initiative.process.report.service;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.process.report.SaveOrUpdateBundle;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;
import dev.dsf.fhir.client.FhirWebserviceClient;

public class StoreSearchBundle extends AbstractServiceDelegate implements InitializingBean, SaveOrUpdateBundle
{
	private static final Logger logger = LoggerFactory.getLogger(StoreSearchBundle.class);


	private FhirWebserviceClient localWebserviceClient;

	public StoreSearchBundle(ProcessPluginApi api)
	{
		super(api);
		this.localWebserviceClient = api.getFhirWebserviceClientProvider().getLocalWebserviceClient();
	}

	@Override
	protected void doExecute(DelegateExecution delegateExecution, Variables variables) throws BpmnError, Exception
	{
		logger.info("StoreSearchBundle doExecute");

		Bundle bundle = variables.getResource(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE);

		String searchBundleIdentifier = bundle.getIdentifier().getSystem() + "|" + bundle.getIdentifier().getValue();

		logger.info("Search for bundle on the local DSF FHIR: {}", searchBundleIdentifier);

		saveOrUpdate(localWebserviceClient, bundle, searchBundleIdentifier);

	}

}
