package de.medizininformatik_initiative.process.report.service;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Task;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HandleError extends AbstractServiceDelegate
{
	private static final Logger logger = LoggerFactory.getLogger(HandleError.class);

	public HandleError(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution delegateExecution, Variables variables)
	{
		logger.info("HandleError doExecute");

		Task task = variables.getStartTask();

		if (Task.TaskStatus.FAILED.equals(task.getStatus()))
		{
			sendMail(task, variables);
			api.getFhirWebserviceClientProvider().getLocalWebserviceClient()
					.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES, ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)
					.update(task);
		}
	}

	private void sendMail(Task task, Variables variables)
	{
		String error = variables.getString(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_RECEIVE_ERROR_MESSAGE);
		String reportLocation = variables
				.getString(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE_RESPONSE_REFERENCE);

		String subject = "Error in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_RECEIVE + "'";
		String message = "Could not download or insert new report with reference '" + reportLocation + "' in process '"
				+ ConstantsReport.PROCESS_NAME_FULL_REPORT_RECEIVE + "' from organization '"
				+ task.getRequester().getIdentifier().getValue() + "' in Task with id '" + task.getId() + "':\n"
				+ "- status code: " + ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_RECEIVE_ERROR + "\n" + "- error: "
				+ (error == null ? "none" : error);

		api.getMailService().send(subject, message);
	}
}
