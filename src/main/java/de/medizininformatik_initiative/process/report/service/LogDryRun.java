package de.medizininformatik_initiative.process.report.service;

import java.util.Objects;

import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.process.report.util.ReportStatusGenerator;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.service.MailService;
import dev.dsf.bpe.v2.variables.Variables;

public class LogDryRun implements ServiceTask, InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(LogDryRun.class);

	private final ReportStatusGenerator statusGenerator;
	private final boolean dicEmailEnabled;

	public LogDryRun(ReportStatusGenerator statusGenerator, boolean dicEmailEnabled)
	{
		this.statusGenerator = statusGenerator;
		this.dicEmailEnabled = dicEmailEnabled;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		Objects.requireNonNull(statusGenerator, "statusGenerator");
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		Task task = variables.getStartTask();
		String recipient = variables.getTarget().getOrganizationIdentifierValue();
		String reportLocation = variables
				.getString(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE_RESPONSE_REFERENCE);

		logger.info("Report dry-run successful for HRP '{}' at '{}' and Task '{}'", recipient, reportLocation,
				api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));

		if (dicEmailEnabled)
			sendSuccessfulMail(api.getMailService(), recipient, reportLocation);

		addOutputToStartTask(api, variables, task);
	}

	private void sendSuccessfulMail(MailService mailService, String recipient, String reportLocation)
	{
		String subject = "New successful dry-run report in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_SEND
				+ "'";
		String message = "A new report has been successfully created as dry-run for HRP '" + recipient
				+ "' in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_SEND
				+ "' and can be accessed using the following link:\n" + "- " + reportLocation;

		mailService.send(subject, message);
	}

	private void addOutputToStartTask(ProcessPluginApi api, Variables variables, Task task)
	{
		task.addOutput(statusGenerator.createReportStatusOutput(api.getProcessPluginDefinition().getResourceVersion(),
				ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_DRY_RUN));

		variables.updateTask(task);
	}
}
