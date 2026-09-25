package de.medizininformatik_initiative.process.report.message;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.process.report.util.ReportStatusGenerator;
import de.medizininformatik_initiative.processes.common.activity.RetryTaskSender;
import de.medizininformatik_initiative.processes.common.error.MessageEndEventErrorHandlerWithTaskOutput;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.MessageEndEvent;
import dev.dsf.bpe.v2.activity.task.TaskSender;
import dev.dsf.bpe.v2.activity.values.SendTaskValues;
import dev.dsf.bpe.v2.error.MessageEndEventErrorHandler;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Variables;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class SendReceipt implements MessageEndEvent, InitializingBean
{
	private final ProcessPluginApi api;
	private static final Logger logger = LoggerFactory.getLogger(SendReceipt.class);

	private final ReportStatusGenerator statusGenerator;

	public SendReceipt(ProcessPluginApi api, ReportStatusGenerator statusGenerator)
	{
		this.api = api;
		this.statusGenerator = statusGenerator;
	}


	@Override
	public void afterPropertiesSet() throws Exception
	{
		logger.info("SendReceipt afterPropertiesSet");

		Objects.requireNonNull(api, "api");
		Objects.requireNonNull(statusGenerator, "reportStatusGenerator");
	}

	@Override
	public TaskSender getTaskSender(ProcessPluginApi api, Variables variables, SendTaskValues sendTaskValues)
	{
		return new RetryTaskSender(api, variables, sendTaskValues, getBusinessKeyStrategy(),
				(target) -> getAdditionalInputParameters(api, variables, sendTaskValues, target));
	}

	@Override
	public List<Task.ParameterComponent> getAdditionalInputParameters(ProcessPluginApi api, Variables variables,
			SendTaskValues sendTaskValues, Target target)
	{
		if (variables.getString(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_RECEIVE_ERROR) != null)
			return createReceiptError(api, variables);
		else
			return createReceiptOk(api);
	}

	private List<Task.ParameterComponent> createReceiptError(ProcessPluginApi api, Variables variables)
	{
		return statusGenerator.transformOutputToInputComponent(variables.getStartTask())
				.map(pc -> receiveToReceiptStatus(api, pc)).toList();
	}

	private Task.ParameterComponent receiveToReceiptStatus(ProcessPluginApi api,
			Task.ParameterComponent parameterComponent)
	{
		Type value = parameterComponent.getValue();
		if (value instanceof Coding coding
				&& ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_RECEIVE_ERROR.equals(coding.getCode()))
		{
			coding.setCode(ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_RECEIPT_ERROR)
					.setVersion(api.getProcessPluginDefinition().getResourceVersion());
		}

		parameterComponent.getExtensionsByUrl(ConstantsReport.EXTENSION_REPORT_STATUS_ERROR_URL).stream()
				.map(Extension::getValue).filter(StringType.class::isInstance).map(StringType.class::cast)
				.forEach(v -> v.setValue(v.getValue().split(ConstantsBase.EXCEPTION_MESSAGE_DIVIDER, 2)[0]));

		return parameterComponent;
	}

	private List<Task.ParameterComponent> createReceiptOk(ProcessPluginApi api)
	{
		return List.of(statusGenerator.createReportStatusInput(api.getProcessPluginDefinition().getResourceVersion(),
				ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_RECEIPT_OK));
	}

	@Override
	public MessageEndEventErrorHandler getErrorHandler()
	{
		return new MessageEndEventErrorHandlerWithTaskOutput(getOutputGenerator());
	}

	private Function<Exception, Task.TaskOutputComponent> getOutputGenerator()
	{
		return (exception) ->
		{
			String statusCode = ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_NOT_REACHABLE;
			if (exception instanceof WebApplicationException webApplicationException
					&& webApplicationException.getResponse() != null
					&& webApplicationException.getResponse().getStatus() == Response.Status.FORBIDDEN.getStatusCode())
			{
				statusCode = ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_NOT_ALLOWED;
			}

			return statusGenerator.createReportStatusOutput(api.getProcessPluginDefinition().getResourceVersion(),
					statusCode, "Send receipt failed");
		};
	}
}
