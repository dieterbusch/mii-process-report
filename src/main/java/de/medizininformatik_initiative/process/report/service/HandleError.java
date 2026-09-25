package de.medizininformatik_initiative.process.report.service;

import java.util.Objects;

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
import dev.dsf.bpe.v2.variables.Variables;

public class HandleError implements ServiceTask, InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(HandleError.class);

	private final ReportStatusGenerator statusGenerator;
	private final boolean hrpEmailEnabled;

	public HandleError(ReportStatusGenerator statusGenerator, boolean hrpEmailEnabled)
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
		logger.info("HandleError doExecute");

		Task task = variables.getStartTask();
		String errorCode = variables.getString(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_RECEIVE_ERROR);
		String errorMessage = variables.getString(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_RECEIVE_ERROR_MESSAGE);

		if (hrpEmailEnabled)
			sendMail(api, variables, task);

		failAndAddOutputTask(api, task, errorCode, errorMessage, variables);
	}

	private void sendMail(ProcessPluginApi api, Variables variables, Task task)
	{
		String error = variables.getString(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_RECEIVE_ERROR_MESSAGE);
		String reportLocation = variables
				.getString(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE_RESPONSE_REFERENCE);

		String subject = "Error in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_RECEIVE + "'";
		String message = "Could not download or insert new report from '" + reportLocation + "' in process '"
				+ ConstantsReport.PROCESS_NAME_FULL_REPORT_RECEIVE + "' and Task '"
				+ api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task) + "' from organization '"
				+ task.getRequester().getIdentifier().getValue() + "':\n" + "- status code: "
				+ ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_RECEIVE_ERROR + "\n" + "- error: "
				+ (error == null ? "none" : error);

		api.getMailService().send(subject, message);
	}

	private void failAndAddOutputTask(ProcessPluginApi api, Task task, String errorCode, String errorMessage,
			Variables variables)
	{
		task.setStatus(Task.TaskStatus.FAILED);
		task.addOutput(statusGenerator.createReportStatusOutput(api.getProcessPluginDefinition().getResourceVersion(),
				errorCode, errorMessage));
		variables.updateTask(task);

		api.getDsfClientProvider().getLocal().withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES,
				DelayStrategy.constant(ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)).update(task);
	}
}
