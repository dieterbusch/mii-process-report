package de.medizininformatik_initiative.process.report.util;

import java.util.stream.Stream;

import org.hl7.fhir.r4.model.BackboneElement;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.Task.ParameterComponent;
import org.hl7.fhir.r4.model.Task.TaskOutputComponent;

import de.medizininformatik_initiative.process.report.ConstantsReport;

public class ReportStatusGenerator
{
	public ParameterComponent createReportStatusInput(String statusVersion, String statusCode)
	{
		return createReportStatusInput(statusVersion, statusCode, null);
	}

	public ParameterComponent createReportStatusInput(String statusVersion, String statusCode, String errorMessage)
	{
		ParameterComponent input = new ParameterComponent();
		input.setValue(new Coding().setSystem(ConstantsReport.CODESYSTEM_REPORT_STATUS).setVersion(statusVersion)
				.setCode(statusCode));
		input.getType().addCoding().setSystem(ConstantsReport.CODESYSTEM_REPORT).setVersion(statusVersion)
				.setCode(ConstantsReport.CODESYSTEM_REPORT_VALUE_REPORT_STATUS);

		if (errorMessage != null)
			addErrorExtension(input, errorMessage);

		return input;
	}

	public TaskOutputComponent createReportStatusOutput(String statusVersion, String statusCode)
	{
		return createReportStatusOutput(statusVersion, statusCode, null);
	}

	public TaskOutputComponent createReportStatusOutput(String statusVersion, String statusCode, String errorMessage)
	{
		TaskOutputComponent output = new TaskOutputComponent();
		output.setValue(new Coding().setSystem(ConstantsReport.CODESYSTEM_REPORT_STATUS).setVersion(statusVersion)
				.setCode(statusCode));
		output.getType().addCoding().setSystem(ConstantsReport.CODESYSTEM_REPORT).setVersion(statusVersion)
				.setCode(ConstantsReport.CODESYSTEM_REPORT_VALUE_REPORT_STATUS);

		if (errorMessage != null)
			addErrorExtension(output, errorMessage);

		return output;
	}

	private void addErrorExtension(BackboneElement element, String errorMessage)
	{
		element.addExtension().setUrl(ConstantsReport.EXTENSION_REPORT_STATUS_ERROR_URL)
				.setValue(new StringType(errorMessage));
	}

	public void transformInputToOutput(Task inputTask, Task outputTask)
	{
		transformInputToOutputComponents(inputTask).forEach(outputTask::addOutput);
	}

	public Stream<Task.TaskOutputComponent> transformInputToOutputComponents(Task inputTask)
	{
		return inputTask.getInput().stream()
				.filter(i -> i.getType().getCoding().stream()
						.anyMatch(c -> ConstantsReport.CODESYSTEM_REPORT.equals(c.getSystem())
								&& ConstantsReport.CODESYSTEM_REPORT_VALUE_REPORT_STATUS.equals(c.getCode())))
				.map(this::toTaskOutputComponent);
	}

	private TaskOutputComponent toTaskOutputComponent(ParameterComponent inputComponent)
	{
		TaskOutputComponent outputComponent = new TaskOutputComponent().setType(inputComponent.getType())
				.setValue(inputComponent.getValue().copy());
		outputComponent.setExtension(inputComponent.getExtension());

		return outputComponent;
	}

	public void transformOutputToInput(Task outputTask, Task inputTask)
	{
		transformOutputToInputComponent(outputTask).forEach(inputTask::addInput);
	}

	public Stream<ParameterComponent> transformOutputToInputComponent(Task outputTask)
	{
		return outputTask.getOutput().stream()
				.filter(i -> i.getType().getCoding().stream()
						.anyMatch(c -> ConstantsReport.CODESYSTEM_REPORT.equals(c.getSystem())
								&& ConstantsReport.CODESYSTEM_REPORT_VALUE_REPORT_STATUS.equals(c.getCode())))
				.map(this::toTaskInputComponent);
	}

	private ParameterComponent toTaskInputComponent(TaskOutputComponent outputComponent)
	{
		ParameterComponent inputComponent = new ParameterComponent().setType(outputComponent.getType())
				.setValue(outputComponent.getValue().copy());
		inputComponent.setExtension(outputComponent.getExtension());

		return inputComponent;
	}
}
