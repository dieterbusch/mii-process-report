package de.medizininformatik_initiative.process.report.service;

import java.util.Objects;

import org.hl7.fhir.r4.model.Coding;
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
import dev.dsf.bpe.v2.service.MailService;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Variables;

public class StoreReceipt implements ServiceTask, InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(StoreReceipt.class);

	private final ReportStatusGenerator statusGenerator;
	private final boolean dicEmailEnabled;

	public StoreReceipt(ReportStatusGenerator statusGenerator, boolean dicEmailEnabled)
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
		logger.info("StoreReceipt doExecute");

		String reportLocation = variables
				.getString(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE_RESPONSE_REFERENCE);

		Task startTask = variables.getStartTask();
		Task currentTask = variables.getLatestTask();
		Target target = variables.getTarget();

		if (!currentTask.getId().equals(startTask.getId()))
			handleReceivedResponse(startTask, currentTask);
		else
			handleMissingResponse(startTask, api.getProcessPluginDefinition().getResourceVersion());

		writeStatusLogAndSendMail(api, startTask, reportLocation, target.getOrganizationIdentifierValue());

		variables.updateTask(startTask);

		// Failed tasks are not automatically updated on process end listener
		if (Task.TaskStatus.FAILED.equals(startTask.getStatus()))
		{
			api.getDsfClientProvider().getLocal().withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES,
					DelayStrategy.constant(ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)).update(startTask);
		}
	}

	private void handleReceivedResponse(Task startTask, Task currentTask)
	{
		statusGenerator.transformInputToOutput(currentTask, startTask);

		if (startTask.getOutput().stream().filter(Task.TaskOutputComponent::hasExtension)
				.flatMap(o -> o.getExtension().stream())
				.anyMatch(e -> ConstantsReport.EXTENSION_REPORT_STATUS_ERROR_URL.equals(e.getUrl())))
			startTask.setStatus(Task.TaskStatus.FAILED);
	}

	private void handleMissingResponse(Task startTask, String resourcesVersion)
	{
		startTask.setStatus(Task.TaskStatus.FAILED);
		startTask.addOutput(statusGenerator.createReportStatusOutput(resourcesVersion,
				ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_RECEIPT_MISSING));
	}

	private void writeStatusLogAndSendMail(ProcessPluginApi api, Task startTask, String reportLocation,
			String hrpIdentifier)
	{
		startTask.getOutput().stream().filter(o -> o.getValue() instanceof Coding)
				.filter(o -> ConstantsReport.CODESYSTEM_REPORT_STATUS.equals(((Coding) o.getValue()).getSystem()))
				.forEach(o -> doWriteStatusLogAndSendMail(api, o, startTask, reportLocation, hrpIdentifier));
	}

	private void doWriteStatusLogAndSendMail(ProcessPluginApi api, Task.TaskOutputComponent output, Task task,
			String reportLocation, String hrpIdentifier)
	{
		Coding status = (Coding) output.getValue();
		String code = status.getCode();
		String error = output.hasExtension() ? output.getExtensionFirstRep().getValueAsPrimitive().getValueAsString()
				: "none";
		String errorLog = error.isBlank() ? "" : ConstantsBase.EXCEPTION_MESSAGE_DIVIDER + error;

		if (ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_RECEIPT_OK.equals(code))
		{
			logger.info("Report has status code '{}' for HRP '{}' and Task '{}'", code, hrpIdentifier,
					api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));

			if (dicEmailEnabled)
				sendSuccessfulMail(api.getMailService(), api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task),
						reportLocation, code, hrpIdentifier);
		}
		else
		{
			logger.warn("Task has status code '{}'{} for HRP '{}' and Task '{}'", code, errorLog, hrpIdentifier,
					api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));

			if (dicEmailEnabled)
				sendErrorMail(api.getMailService(), api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task),
						reportLocation, code, error, hrpIdentifier);
		}
	}

	private void sendSuccessfulMail(MailService mailService, String taskReference, String reportLocation, String code,
			String hrpIdentifier)
	{
		String subject = "New successful report in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_SEND + "'";
		String message = "A new report has been successfully created and retrieved by the HRP '" + hrpIdentifier
				+ "' with status code '" + code + "' in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_SEND
				+ "' and Task '" + taskReference + "'. It can be accessed using the following link:\n" + "- "
				+ reportLocation;

		mailService.send(subject, message);
	}

	private void sendErrorMail(MailService mailService, String taskReference, String reportLocation, String code,
			String error, String hrpIdentifier)
	{
		String subject = "Error in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_SEND + "'";
		String message = "HRP '" + hrpIdentifier + "' could not download or insert new report from '" + reportLocation
				+ "' in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_SEND + "' and Task  '" + taskReference
				+ "':\n" + "- status code: " + code + "\n" + "- error: " + error;

		mailService.send(subject, message);
	}
}
