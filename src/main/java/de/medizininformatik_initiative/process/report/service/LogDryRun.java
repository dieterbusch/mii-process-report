package de.medizininformatik_initiative.process.report.service;

import java.util.Objects;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.process.report.util.ReportStatusGenerator;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;

public class LogDryRun extends AbstractServiceDelegate implements InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(LogDryRun.class);

	private final ReportStatusGenerator statusGenerator;

	public LogDryRun(ProcessPluginApi api, ReportStatusGenerator statusGenerator)
	{
		super(api);
		this.statusGenerator = statusGenerator;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();
		Objects.requireNonNull(statusGenerator, "statusGenerator");
	}

	@Override
	protected void doExecute(DelegateExecution delegateExecution, Variables variables)
	{
		String recipient = variables.getTarget().getOrganizationIdentifierValue();
		String reportLocation = variables
				.getString(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE_RESPONSE_REFERENCE);

		logger.info("Report dry-run successful for HRP '{}' at '{}' and task with id '{}'", recipient, reportLocation,
				variables.getStartTask().getId());
		sendSuccessfulMail(recipient, reportLocation);

		addOutputToStartTask(variables);
	}

	private void sendSuccessfulMail(String recipient, String reportLocation)
	{
		String subject = "New successful dry-run report in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_SEND
				+ "'";
		String message = "A new report has been successfully created as dry-run for HRP '" + recipient
				+ "' in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_SEND
				+ "' and can be accessed using the following link:\n" + "- " + reportLocation;

		api.getMailService().send(subject, message);
	}

	private void addOutputToStartTask(Variables variables)
	{
		Task task = variables.getStartTask();
		task.addOutput(
				statusGenerator.createReportStatusOutput(ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_DRY_RUN));

		variables.updateTask(task);
	}
}
