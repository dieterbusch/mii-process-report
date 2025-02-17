package de.medizininformatik_initiative.process.report.fhir.profile;

import static org.junit.Assert.assertEquals;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.Task.TaskIntent;
import org.hl7.fhir.r4.model.Task.TaskStatus;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.ValidationResult;
import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.process.report.ReportProcessPluginDefinition;
import de.medizininformatik_initiative.process.report.util.ReportStatusGenerator;
import dev.dsf.bpe.v1.constants.CodeSystems;
import dev.dsf.bpe.v1.constants.NamingSystems;
import dev.dsf.fhir.validation.ResourceValidator;
import dev.dsf.fhir.validation.ResourceValidatorImpl;
import dev.dsf.fhir.validation.ValidationSupportRule;

public class TaskProfileTest
{
	private static final Logger logger = LoggerFactory.getLogger(TaskProfileTest.class);
	private static final ReportProcessPluginDefinition def = new ReportProcessPluginDefinition();

	@ClassRule
	public static final ValidationSupportRule validationRule = new ValidationSupportRule(def.getResourceVersion(),
			def.getResourceReleaseDate(),
			List.of("dsf-task-base-1.0.0.xml", "extension-report-status-error.xml", "search-bundle-report.xml",
					"search-bundle-response-report.xml", "task-report-autostart-start.xml",
					"task-report-autostart-stop.xml", "task-report-receive.xml", "task-report-send.xml",
					"task-report-send-start.xml"),
			List.of("dsf-read-access-tag-1.0.0.xml", "dsf-bpmn-message-1.0.0.xml", "report.xml", "report-status.xml"),
			List.of("dsf-read-access-tag-1.0.0.xml", "dsf-bpmn-message-1.0.0.xml", "report.xml",
					"report-status-receive.xml", "report-status-send.xml"));

	private final ResourceValidator resourceValidator = new ResourceValidatorImpl(validationRule.getFhirContext(),
			validationRule.getValidationSupport());

	@Test
	public void testTaskAutostartStartProcessProfileValid()
	{
		Task task = createValidTaskAutostartStartProcess();

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	@Test
	public void testTaskAutostartStartProcessProfileValidTimerInterval()
	{
		Task task = createValidTaskAutostartStartProcess();
		task.addInput().setValue(new StringType("P30D")).getType().addCoding()
				.setSystem(ConstantsReport.CODESYSTEM_REPORT)
				.setCode(ConstantsReport.CODESYSTEM_REPORT_VALUE_TIMER_INTERVAL);

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	@Test
	public void testTaskStartAutostartProcessProfileNotValidTimerInterval()
	{
		Task task = createValidTaskAutostartStartProcess();
		task.addInput().setValue(new StringType("P10X")).getType().addCoding()
				.setSystem(ConstantsReport.CODESYSTEM_REPORT)
				.setCode(ConstantsReport.CODESYSTEM_REPORT_VALUE_TIMER_INTERVAL);

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(1, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	@Test
	public void testTaskAutostartStartProcessProfileValidHrpIdentifier()
	{
		Task task = createValidTaskAutostartStartProcess();
		task.addInput()
				.setValue(new Reference().setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_HRP"))
						.setType(ResourceType.Organization.name()))
				.getType().addCoding().setSystem(ConstantsReport.CODESYSTEM_REPORT)
				.setCode(ConstantsReport.CODESYSTEM_REPORT_VALUE_HRP_IDENTIFIER);

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	private Task createValidTaskAutostartStartProcess()
	{
		Task task = new Task();
		task.getMeta().addProfile(ConstantsReport.PROFILE_TASK_REPORT_AUTOSTART_START + "|" + def.getResourceVersion());
		task.setInstantiatesCanonical(
				ConstantsReport.PROFILE_TASK_REPORT_AUTOSTART_START_PROCESS_URI + "|" + def.getResourceVersion());
		task.setStatus(TaskStatus.REQUESTED);
		task.setIntent(TaskIntent.ORDER);
		task.setAuthoredOn(new Date());
		task.getRequester().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_DIC"));
		task.getRestriction().addRecipient().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_DIC"));

		task.addInput().setValue(new StringType(ConstantsReport.PROFILE_TASK_REPORT_AUTOSTART_START_MESSAGE_NAME))
				.getType().addCoding(CodeSystems.BpmnMessage.messageName());

		return task;
	}

	@Test
	public void testTaskAutostartStopProcessProfileValid()
	{
		Task task = createValidTaskAutostartStopProcess();

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	private Task createValidTaskAutostartStopProcess()
	{
		Task task = new Task();
		task.getMeta().addProfile(ConstantsReport.PROFILE_TASK_REPORT_AUTOSTART_STOP + "|" + def.getResourceVersion());
		task.setInstantiatesCanonical(
				ConstantsReport.PROFILE_TASK_REPORT_AUTOSTART_STOP_PROCESS_URI + "|" + def.getResourceVersion());
		task.setStatus(TaskStatus.REQUESTED);
		task.setIntent(TaskIntent.ORDER);
		task.setAuthoredOn(new Date());
		task.getRequester().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("DIC"));
		task.getRestriction().addRecipient().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("DIC"));

		task.addInput().setValue(new StringType(ConstantsReport.PROFILE_TASK_REPORT_AUTOSTART_STOP_MESSAGE_NAME))
				.getType().addCoding(CodeSystems.BpmnMessage.messageName());

		return task;
	}

	@Test
	public void testTaskSendStartProcessProfileValid()
	{
		Task task = createValidTaskSendStartProcess();

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	@Test
	public void testTaskSendStartProcessProfileValidAsDryRun()
	{
		Task task = createValidTaskSendStartProcess();
		task.addInput().setValue(new BooleanType(true)).getType().addCoding()
				.setSystem(ConstantsReport.CODESYSTEM_REPORT).setCode(ConstantsReport.CODESYSTEM_REPORT_VALUE_DRY_RUN);
		task.addOutput(new ReportStatusGenerator()
				.createReportStatusOutput(ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_DRY_RUN));

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	@Test
	public void testTaskSendStartProcessProfileValidWithHrpIdentifier()
	{
		Task task = createValidTaskSendStartProcess();
		task.addInput()
				.setValue(new Reference().setIdentifier(NamingSystems.OrganizationIdentifier.withValue("Test_HRP"))
						.setType(ResourceType.Organization.name()))
				.getType().addCoding().setSystem(ConstantsReport.CODESYSTEM_REPORT)
				.setCode(ConstantsReport.CODESYSTEM_REPORT_VALUE_HRP_IDENTIFIER);

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	@Test
	public void testTaskSendStartProcessProfileValidWithReportStatusOutput()
	{
		Task task = createValidTaskSendStartProcess();
		task.addOutput(new ReportStatusGenerator()
				.createReportStatusOutput(ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_RECEIPT_OK));

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	@Test
	public void testTaskSendStartProcessProfileValidWithReportStatusErrorOutput()
	{
		Task task = createValidTaskSendStartProcess();
		task.addOutput(new ReportStatusGenerator().createReportStatusOutput(
				ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_NOT_REACHABLE, "some error message"));

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	private Task createValidTaskSendStartProcess()
	{
		Task task = new Task();
		task.getMeta().addProfile(ConstantsReport.PROFILE_TASK_REPORT_SEND_START + "|" + def.getResourceVersion());
		task.setInstantiatesCanonical(
				ConstantsReport.PROFILE_TASK_REPORT_SEND_START_PROCESS_URI + "|" + def.getResourceVersion());
		task.setStatus(TaskStatus.REQUESTED);
		task.setIntent(TaskIntent.ORDER);
		task.setAuthoredOn(new Date());
		task.getRequester().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("DIC"));
		task.getRestriction().addRecipient().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("DIC"));

		task.addInput().setValue(new StringType(ConstantsReport.PROFILE_TASK_REPORT_SEND_START_MESSAGE_NAME)).getType()
				.addCoding(CodeSystems.BpmnMessage.messageName());

		return task;
	}

	@Test
	public void testTaskSendProcessProfileValid()
	{
		Task task = createValidTaskSendProcess();

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	@Test
	public void testTaskSendProcessProfileValidWithReportStatusOutput()
	{
		Task task = createValidTaskSendProcess();
		task.addOutput(new ReportStatusGenerator()
				.createReportStatusOutput(ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_RECEIVE_OK));

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	@Test
	public void testTaskSendProcessProfileValidWithReportStatusErrorOutput()
	{
		Task task = createValidTaskSendProcess();
		task.addOutput(new ReportStatusGenerator().createReportStatusOutput(
				ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_RECEIVE_ERROR, "some error message"));

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	private Task createValidTaskSendProcess()
	{
		Task task = new Task();
		task.getMeta().addProfile(ConstantsReport.PROFILE_TASK_REPORT_SEND + "|" + def.getResourceVersion());
		task.setInstantiatesCanonical(
				ConstantsReport.PROFILE_TASK_REPORT_RECEIVE_PROCESS_URI + "|" + def.getResourceVersion());
		task.setStatus(TaskStatus.REQUESTED);
		task.setIntent(TaskIntent.ORDER);
		task.setAuthoredOn(new Date());
		task.getRequester().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("DIC"));
		task.getRestriction().addRecipient().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("DIC"));

		task.addInput().setValue(new StringType(ConstantsReport.PROFILE_TASK_REPORT_SEND_MESSAGE_NAME)).getType()
				.addCoding(CodeSystems.BpmnMessage.messageName());
		task.addInput().setValue(new StringType(UUID.randomUUID().toString())).getType()
				.addCoding(CodeSystems.BpmnMessage.businessKey());

		task.addInput()
				.setValue(new Reference("http://foo.bar/fhir/Bundle/" + UUID.randomUUID())
						.setType(ResourceType.Bundle.name()))
				.getType().addCoding().setSystem(ConstantsReport.CODESYSTEM_REPORT)
				.setCode(ConstantsReport.CODESYSTEM_REPORT_VALUE_SEARCH_BUNDLE_RESPONSE_REFERENCE);

		return task;
	}

	@Test
	public void testTaskReceiveProcessProfileValidWithResponseInput()
	{
		Task task = createValidTaskReceiveProcess();
		task.addInput(new ReportStatusGenerator()
				.createReportStatusInput(ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_RECEIPT_OK));

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	@Test
	public void testTaskReceiveProcessProfileValidWithResponseInputError()
	{
		Task task = createValidTaskReceiveProcess();
		task.addInput(new ReportStatusGenerator().createReportStatusInput(
				ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_RECEIPT_ERROR, "some error message"));

		ValidationResult result = resourceValidator.validate(task);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	private Task createValidTaskReceiveProcess()
	{
		Task task = new Task();
		task.getMeta().addProfile(ConstantsReport.PROFILE_TASK_REPORT_RECEIVE + "|" + def.getResourceVersion());
		task.setInstantiatesCanonical(
				ConstantsReport.PROFILE_TASK_REPORT_SEND_PROCESS_URI + "|" + def.getResourceVersion());
		task.setStatus(TaskStatus.REQUESTED);
		task.setIntent(TaskIntent.ORDER);
		task.setAuthoredOn(new Date());
		task.getRequester().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("DIC"));
		task.getRestriction().addRecipient().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue("DIC"));

		task.addInput().setValue(new StringType(ConstantsReport.PROFILE_TASK_REPORT_RECEIVE_MESSAGE_NAME)).getType()
				.addCoding(CodeSystems.BpmnMessage.messageName());
		task.addInput().setValue(new StringType(UUID.randomUUID().toString())).getType()
				.addCoding(CodeSystems.BpmnMessage.businessKey());

		return task;
	}
}
