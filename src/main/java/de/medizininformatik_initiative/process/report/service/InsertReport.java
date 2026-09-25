package de.medizininformatik_initiative.process.report.service;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.process.report.util.ReportStatusGenerator;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.client.dsf.DelayStrategy;
import dev.dsf.bpe.v2.client.dsf.PreferReturnMinimal;
import dev.dsf.bpe.v2.error.ErrorBoundaryEvent;
import dev.dsf.bpe.v2.service.MailService;
import dev.dsf.bpe.v2.variables.Variables;

public class InsertReport implements ServiceTask, InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(InsertReport.class);

	private final ReportStatusGenerator statusGenerator;
	private final boolean hrpEmailEnabled;

	public InsertReport(ReportStatusGenerator statusGenerator, boolean hrpEmailEnabled)
	{
		this.statusGenerator = statusGenerator;
		this.hrpEmailEnabled = hrpEmailEnabled;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		Objects.requireNonNull(statusGenerator, "reportStatusGenerator");
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		logger.info("InsertReport doExecute");

		Task task = variables.getStartTask();
		String sendingOrganization = task.getRequester().getIdentifier().getValue();
		Identifier reportIdentifier = getReportIdentifier(task);

		Bundle report = variables.getFhirResource(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE);
		report.setId("").getMeta().setVersionId("").setTag(null);
		report.setIdentifier(reportIdentifier);

		api.getReadAccessHelper().addLocal(report);
		api.getReadAccessHelper().addOrganization(report, task.getRequester().getIdentifier().getValue());

		PreferReturnMinimal client = api.getDsfClientProvider().getLocal().withMinimalReturn().withRetry(
				ConstantsBase.DSF_CLIENT_RETRY_6_TIMES,
				DelayStrategy.constant(ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN));
		try
		{
			IdType reportId = client.updateConditionaly(report, Map.of("identifier",
					Collections.singletonList(reportIdentifier.getSystem() + "|" + reportIdentifier.getValue())));

			task.addOutput(
					statusGenerator.createReportStatusOutput(api.getProcessPluginDefinition().getResourceVersion(),
							ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_RECEIVE_OK));
			variables.updateTask(task);

			String absoluteReportId = new IdType(api.getEndpointProvider().getLocalEndpointAddress(),
					ResourceType.Bundle.name(), reportId.getIdPart(), reportId.getVersionIdPart()).getValue();

			logger.info("Stored report '{}' from organization '{}' and Task '{}'", absoluteReportId,
					sendingOrganization, api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));

			if (hrpEmailEnabled)
				sendMail(api.getMailService(), sendingOrganization, absoluteReportId);
		}
		catch (Exception exception)
		{
			logger.error(
					"Inserting report from organization '{}' in Task '{}' failed - {} - throwing error boundary event",
					task.getRequester().getIdentifier().getValue(),
					api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task), exception.getMessage());

			String message = "Insert report failed" + ConstantsBase.EXCEPTION_MESSAGE_DIVIDER + exception.getMessage();
			throw new ErrorBoundaryEvent(ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_RECEIVE_ERROR, message);
		}
	}

	private Identifier getReportIdentifier(Task task)
	{
		return new Identifier().setSystem(ConstantsReport.NAMINGSYSTEM_CDS_REPORT_IDENTIFIER)
				.setValue(task.getRequester().getIdentifier().getValue());
	}

	private void sendMail(MailService mailService, String sendingOrganization, String reportLocation)
	{
		String subject = "New report stored in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_RECEIVE + "'";
		String message = "A new report has been stored in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_RECEIVE
				+ "' from organization '" + sendingOrganization + "' and can be accessed using the following link:\n"
				+ "- " + reportLocation;

		mailService.send(subject, message);
	}
}
