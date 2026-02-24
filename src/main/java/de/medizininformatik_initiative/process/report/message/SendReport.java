package de.medizininformatik_initiative.process.report.message;

import java.util.Objects;
import java.util.stream.Stream;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.process.report.service.AggregateReports;
import de.medizininformatik_initiative.process.report.util.ReportStatusGenerator;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractTaskMessageSend;
import dev.dsf.bpe.v1.variables.Variables;
import dev.dsf.fhir.client.FhirWebserviceClient;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class SendReport extends AbstractTaskMessageSend implements InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(SendReport.class);

	private final ReportStatusGenerator statusGenerator;

	public SendReport(ProcessPluginApi api, ReportStatusGenerator statusGenerator)
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
	protected Stream<Task.ParameterComponent> getAdditionalInputParameters(DelegateExecution execution,
			Variables variables)
	{
		String bundleId = variables
				.getString(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE_RESPONSE_REFERENCE);

		Task.ParameterComponent parameterComponent = new Task.ParameterComponent();
		parameterComponent.getType().addCoding().setSystem(ConstantsReport.CODESYSTEM_REPORT)
				.setCode(ConstantsReport.CODESYSTEM_REPORT_VALUE_SEARCH_BUNDLE_RESPONSE_REFERENCE);
		parameterComponent.setValue(new Reference(bundleId).setType(ResourceType.Bundle.name()));

		return Stream.of(parameterComponent);
	}

	@Override
	protected IdType doSend(FhirWebserviceClient client, Task task)
	{
		logger.info("SendReport doSend");

		return client.withMinimalReturn()
				.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES, ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)
				.create(task);
	}

	@Override
	protected void handleIntermediateThrowEventError(DelegateExecution execution, Variables variables,
			Exception exception, String errorMessage)
	{
		Task task = variables.getStartTask();

		if (task != null)
		{
			String statusCode = ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_NOT_REACHABLE;
			if (exception instanceof WebApplicationException webApplicationException
					&& webApplicationException.getResponse() != null
					&& webApplicationException.getResponse().getStatus() == Response.Status.FORBIDDEN.getStatusCode())
			{
				statusCode = ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_NOT_ALLOWED;
			}

			task.addOutput(statusGenerator.createReportStatusOutput(statusCode, "Send report failed"));
			variables.updateTask(task);
		}

		super.handleIntermediateThrowEventError(execution, variables, exception, errorMessage);
	}

	@Override
	protected void addErrorMessage(Task task, String errorMessage)
	{
		// Override in order not to add error message of AbstractTaskMessageSend
	}
}
