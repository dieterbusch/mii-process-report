package de.medizininformatik_initiative.process.report.message;

import java.util.List;
import java.util.Optional;

import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.processes.common.activity.RetryTaskSender;
import de.medizininformatik_initiative.processes.common.error.MessageIntermediateThrowEventErrorHandlerContinuingProcess;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.MessageIntermediateThrowEvent;
import dev.dsf.bpe.v2.activity.task.BusinessKeyStrategies;
import dev.dsf.bpe.v2.activity.task.TaskSender;
import dev.dsf.bpe.v2.activity.values.SendTaskValues;
import dev.dsf.bpe.v2.error.MessageIntermediateThrowEventErrorHandler;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Variables;

public class StartSendReport implements MessageIntermediateThrowEvent
{

	@Override
	public TaskSender getTaskSender(ProcessPluginApi api, Variables variables, SendTaskValues sendTaskValues)
	{
		return new RetryTaskSender(api, variables, sendTaskValues, BusinessKeyStrategies.NEW,
				(target) -> getAdditionalInputParameters(api, variables, sendTaskValues, target));
	}

	@Override
	public List<Task.ParameterComponent> getAdditionalInputParameters(ProcessPluginApi api, Variables variables,
			SendTaskValues sendTaskValues, Target target)
	{
		Task startTask = variables.getStartTask();
		Optional<Reference> hrpIdentifier = api.getTaskHelper().getFirstInputParameterValue(startTask,
				ConstantsReport.CODESYSTEM_REPORT, ConstantsReport.CODESYSTEM_REPORT_VALUE_HRP_IDENTIFIER,
				Reference.class);

		return hrpIdentifier.stream()
				.map(r -> api.getTaskHelper().createInput(r, ConstantsReport.CODESYSTEM_REPORT,
						ConstantsReport.CODESYSTEM_REPORT_VALUE_HRP_IDENTIFIER,
						api.getProcessPluginDefinition().getResourceVersion()))
				.toList();
	}

	@Override
	public MessageIntermediateThrowEventErrorHandler getErrorHandler()
	{
		return new MessageIntermediateThrowEventErrorHandlerContinuingProcess();
	}
}
