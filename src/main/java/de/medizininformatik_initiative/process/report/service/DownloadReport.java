package de.medizininformatik_initiative.process.report.service;

import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.client.dsf.BasicDsfClient;
import dev.dsf.bpe.v2.client.dsf.DelayStrategy;
import dev.dsf.bpe.v2.error.ErrorBoundaryEvent;
import dev.dsf.bpe.v2.error.ServiceTaskErrorHandler;
import dev.dsf.bpe.v2.variables.Variables;

public class DownloadReport implements ServiceTask
{
	private static final Logger logger = LoggerFactory.getLogger(DownloadReport.class);

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		logger.info("DownloadReport doExecute");

		Task task = variables.getStartTask();
		IdType reportReference = getReportReference(api, task);

		variables.setString(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE_RESPONSE_REFERENCE,
				reportReference.getValue());

		logger.info("Downloading report '{}' from organization '{}' in Task '{}'", reportReference.getValue(),
				task.getRequester().getIdentifier().getValue(),
				api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));

		try
		{
			Bundle reportBundle = downloadReportBundle(api, reportReference);
			variables.setFhirResource(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE, reportBundle);
		}
		catch (Exception exception)
		{
			logger.error(
					"Downloading report '{}' from organization '{}' in Task '{}' failed - {} - throwing error boundary event",
					reportReference.getValue(), task.getRequester().getIdentifier().getValue(),
					api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task), exception.getMessage());

			String message = "Download report failed" + ConstantsBase.EXCEPTION_MESSAGE_DIVIDER
					+ exception.getMessage();
			throw new ErrorBoundaryEvent(ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_RECEIVE_ERROR, message);
		}
	}

	@Override
	public ServiceTaskErrorHandler getErrorHandler()
	{
		return ServiceTask.super.getErrorHandler();
	}

	private IdType getReportReference(ProcessPluginApi api, Task task)
	{
		List<String> reportReferences = api.getTaskHelper()
				.getInputParameterValues(task, ConstantsReport.CODESYSTEM_REPORT,
						ConstantsReport.CODESYSTEM_REPORT_VALUE_SEARCH_BUNDLE_RESPONSE_REFERENCE, Reference.class)
				.filter(Reference::hasReference).map(Reference::getReference).toList();

		if (reportReferences.isEmpty())
			throw new IllegalArgumentException("No report reference present in Task '"
					+ api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task) + "'");

		if (reportReferences.size() > 1)
			logger.warn("Found {} report references in Task '{}', using only the first", reportReferences.size(),
					api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));

		return new IdType(reportReferences.getFirst());
	}

	private Bundle downloadReportBundle(ProcessPluginApi api, IdType reportReference)
	{
		BasicDsfClient client = api.getDsfClientProvider().getByEndpointUrl(reportReference.getBaseUrl()).withRetry(
				ConstantsBase.DSF_CLIENT_RETRY_6_TIMES,
				DelayStrategy.constant(ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN));

		if (reportReference.hasVersionIdPart())
			return client.read(Bundle.class, reportReference.getIdPart(), reportReference.getVersionIdPart());
		else
			return client.read(Bundle.class, reportReference.getIdPart());
	}
}
