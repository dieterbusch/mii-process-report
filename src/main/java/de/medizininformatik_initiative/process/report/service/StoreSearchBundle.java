package de.medizininformatik_initiative.process.report.service;

import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.process.report.SaveOrUpdateBundle;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.client.dsf.DsfClient;
import dev.dsf.bpe.v2.variables.Variables;

public class StoreSearchBundle implements ServiceTask, InitializingBean, SaveOrUpdateBundle
{
	private static final Logger logger = LoggerFactory.getLogger(StoreSearchBundle.class);

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		logger.info("StoreSearchBundle doExecute");

		DsfClient localClient = api.getDsfClientProvider().getLocal();

		Bundle bundle = variables.getFhirResource(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE);
		// Bundle bundle = variables.getResource(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE);

		String searchBundleIdentifier = bundle.getIdentifier().getSystem() + "|" + bundle.getIdentifier().getValue();

		logger.info("Search for bundle on the local DSF FHIR: {}", searchBundleIdentifier);

		saveOrUpdate(localClient, bundle, searchBundleIdentifier);
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		logger.info("StoreSearchBundle afterPropertiesSet");
	}

}
