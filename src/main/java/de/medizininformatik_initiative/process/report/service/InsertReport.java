package de.medizininformatik_initiative.process.report.service;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
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
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;
import dev.dsf.fhir.client.PreferReturnMinimal;

public class InsertReport extends AbstractServiceDelegate implements InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(InsertReport.class);

	private final ReportStatusGenerator statusGenerator;

	public InsertReport(ProcessPluginApi api, ReportStatusGenerator statusGenerator)
	{
		super(api);
		this.statusGenerator = statusGenerator;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();
		Objects.requireNonNull(statusGenerator, "reportStatusGenerator");
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		logger.info("InsertReport doExecute");

		Task task = variables.getStartTask();
		String sendingOrganization = task.getRequester().getIdentifier().getValue();
		Identifier reportIdentifier = getReportIdentifier(task);

		Bundle report = variables.getResource(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE);
		report.setId("").getMeta().setVersionId("").setTag(null);
		report.setIdentifier(reportIdentifier);

		api.getReadAccessHelper().addLocal(report);
		api.getReadAccessHelper().addOrganization(report, task.getRequester().getIdentifier().getValue());

		PreferReturnMinimal client = api.getFhirWebserviceClientProvider().getLocalWebserviceClient()
				.withMinimalReturn()
				.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES, ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN);
		try
		{
			IdType reportId = client.updateConditionaly(report, Map.of("identifier",
					Collections.singletonList(reportIdentifier.getSystem() + "|" + reportIdentifier.getValue())));

			task.addOutput(statusGenerator
					.createReportStatusOutput(ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_RECEIVE_OK));
			variables.updateTask(task);

			String absoluteReportId = new IdType(api.getEndpointProvider().getLocalEndpointAddress(),
					ResourceType.Bundle.name(), reportId.getIdPart(), reportId.getVersionIdPart()).getValue();

			logger.info("Stored report with id '{}' from organization '{}' for Task with id '{}'", absoluteReportId,
					sendingOrganization, task.getId());
			sendMail(sendingOrganization, absoluteReportId);
		}
		catch (Exception exception)
		{
			task.setStatus(Task.TaskStatus.FAILED);
			task.addOutput(statusGenerator.createReportStatusOutput(
					ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_RECEIVE_ERROR, "Insert report failed"));
			variables.updateTask(task);

			logger.warn("Storing report from organization '{}' for Task with id '{}' failed - {}", sendingOrganization,
					task.getId(), exception.getMessage());
			throw new BpmnError(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_RECEIVE_ERROR,
					"Insert report failed - " + exception.getMessage());
		}
	}

	private Identifier getReportIdentifier(Task task)
	{
		return new Identifier().setSystem(ConstantsReport.NAMINGSYSTEM_CDS_REPORT_IDENTIFIER)
				.setValue(task.getRequester().getIdentifier().getValue());
	}

	private void sendMail(String sendingOrganization, String reportLocation)
	{
		String subject = "New report stored in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_RECEIVE + "'";
		String message = "A new report has been stored in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_RECEIVE
				+ "' from organization '" + sendingOrganization + "' and can be accessed using the following link:\n"
				+ "- " + reportLocation;

		api.getMailService().send(subject, message);
	}
}
