package de.medizininformatik_initiative.process.report.service;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.process.report.util.ReportStatusGenerator;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.client.dsf.BasicDsfClient;
import dev.dsf.bpe.v2.client.dsf.DelayStrategy;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Variables;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class DownloadSearchBundle implements ServiceTask, InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(DownloadSearchBundle.class);

	private final ReportStatusGenerator statusGenerator;

	public DownloadSearchBundle(ReportStatusGenerator statusGenerator)
	{
		this.statusGenerator = statusGenerator;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		Objects.requireNonNull(statusGenerator, "statusGenerator");
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		logger.info("DownloadSearchBundle doExecute");

		Task task = variables.getStartTask();
		Target target = variables.getTarget();
		String searchBundleIdentifier = ConstantsReport.NAMINGSYSTEM_SEARCH_BUNDLE_IDENTIFIER + "|"
				+ ConstantsReport.NAMINGSYSTEM_SEARCH_BUNDLE_IDENTIFIER_VALUE_PREFIX
				+ api.getProcessPluginDefinition().getResourceVersion();

		logger.info("Downloading search Bundle '{}' from HRP '{}' for Task '{}'", searchBundleIdentifier,
				target.getOrganizationIdentifierValue(), api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));

		try
		{
			Bundle bundle = searchSearchBundle(api, target, searchBundleIdentifier);
			api.getDataLogger().log(
					"Search response for Task '" + api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task) + "'",
					bundle);

			Bundle searchBundle = extractSearchBundle(bundle, searchBundleIdentifier,
					target.getOrganizationIdentifierValue());
			api.getDataLogger().log(
					"Search Bundle for Task '" + api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task) + "'",
					searchBundle);

			variables.setFhirResource(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE, searchBundle);
		}
		catch (Exception exception)
		{
			if (exception instanceof WebApplicationException webException)
			{
				String statusCode = ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_NOT_REACHABLE;

				if (webException.getResponse() != null
						&& webException.getResponse().getStatus() == Response.Status.FORBIDDEN.getStatusCode())
				{
					statusCode = ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_NOT_ALLOWED;
				}

				task.addOutput(
						statusGenerator.createReportStatusOutput(api.getProcessPluginDefinition().getResourceVersion(),
								statusCode, "Download search bundle failed"));
				variables.updateTask(task);
			}

			throw exception;
		}
	}

	private Bundle searchSearchBundle(ProcessPluginApi api, Target target, String searchBundleIdentifier)
	{
		BasicDsfClient client = api.getDsfClientProvider().getByEndpointUrl(target.getEndpointUrl()).withRetry(
				ConstantsBase.DSF_CLIENT_RETRY_6_TIMES,
				DelayStrategy.constant(ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN));

		return client.searchWithStrictHandling(Bundle.class,
				Map.of("identifier", Collections.singletonList(searchBundleIdentifier)));
	}

	private Bundle extractSearchBundle(Bundle bundle, String searchBundleIdentifier, String hrpIdentifier)
	{
		if (bundle.getTotal() != 1 && !(bundle.getEntryFirstRep().getResource() instanceof Bundle))
			throw new IllegalStateException("Expected a Bundle from HRP '" + hrpIdentifier
					+ "' with one entry being a search Bundle with identifier '" + searchBundleIdentifier
					+ "' but found " + bundle.getTotal());

		return (Bundle) bundle.getEntryFirstRep().getResource();
	}
}
