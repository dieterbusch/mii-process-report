package de.medizininformatik_initiative.process.report.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.TimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Variables;

public class SetTimer implements ServiceTask
{
	private static final Logger logger = LoggerFactory.getLogger(SetTimer.class);

	public SetTimer()
	{
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		logger.info("SetTimer doExecute");

		Task task = variables.getStartTask();

		String timerInterval = getTimerInterval(api, variables);
		logger.info("Executing report send process with timer interval '{}' for Task '{}'", timerInterval,
				api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));
		variables.setString(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_TIMER_INTERVAL, timerInterval);

		Optional<TimeType> firstExecutionTime = getFirstExecution(api, variables);
		if (firstExecutionTime.isPresent())
		{
			String firstExecutionDateTime = calculateFirstExecutionDateTime(firstExecutionTime.get());
			variables.setString(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_FIRST_EXECUTION, firstExecutionDateTime);
			variables.setBoolean(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_FIRST_EXECUTION_DELAYED, true);

			logger.info("First execution of report send process set to '{}' for Task '{}'", firstExecutionDateTime,
					api.getTaskHelper().getLocalVersionlessAbsoluteUrl(variables.getStartTask()));

		}
		else
		{
			variables.setBoolean(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_FIRST_EXECUTION_DELAYED, false);
		}

		Target target = createLocalTarget(api, variables);
		variables.setTarget(target);
	}

	private String getTimerInterval(ProcessPluginApi api, Variables variables)
	{
		return api.getTaskHelper()
				.getFirstInputParameterStringValue(variables.getStartTask(), ConstantsReport.CODESYSTEM_REPORT,
						ConstantsReport.CODESYSTEM_REPORT_VALUE_TIMER_INTERVAL)
				.orElse(ConstantsReport.REPORT_TIMER_INTERVAL_DEFAULT_VALUE);
	}

	private Optional<TimeType> getFirstExecution(ProcessPluginApi api, Variables variables)
	{
		return api.getTaskHelper().getFirstInputParameterValue(variables.getStartTask(),
				new Coding().setSystem(ConstantsReport.CODESYSTEM_REPORT)
						.setVersion(api.getProcessPluginDefinition().getResourceVersion())
						.setCode(ConstantsReport.CODESYSTEM_REPORT_VALUE_FIRST_EXECUTION),
				TimeType.class);
	}

	private String calculateFirstExecutionDateTime(TimeType time)
	{
		LocalDateTime dateTime = LocalDateTime.now().with(LocalTime.parse(time.getValue()));

		if (dateTime.isBefore(LocalDateTime.now()))
			dateTime = dateTime.plusDays(1);

		return dateTime.toString();
	}

	private Target createLocalTarget(ProcessPluginApi api, Variables variables)
	{
		return variables.createTarget(
				api.getOrganizationProvider().getLocalOrganizationIdentifierValue()
						.orElseThrow(() -> new RuntimeException("LocalOrganizationIdentifierValue empty")),
				api.getEndpointProvider().getLocalEndpointIdentifierValue()
						.orElseThrow(() -> new RuntimeException("LocalEndpointIdentifierValue empty")),
				api.getEndpointProvider().getLocalEndpointAddress());
	}
}
