package de.medizininformatik_initiative.process.report.service;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.constants.CodeSystems;
import dev.dsf.bpe.v2.constants.NamingSystems;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Variables;

public class SelectTargetHrp implements ServiceTask, InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(SelectTargetHrp.class);

	private static final String ISO_8601_DURATION_STRING = "^P(?:([0-9]+)Y)?(?:([0-9]+)M)?(?:([0-9]+)D)?(T(?:([0-9]+)H)?(?:([0-9]+)M)?(?:([0-9]+)(?:[.,]([0-9]{0,9}))?S)?)?$";
	private static final Pattern ISO_8601_DURATION = Pattern.compile(ISO_8601_DURATION_STRING);

	private final String statusTimerInterval;
	private final String hrpIdentifierEnvVariable;
	private final String reportSendOrganizationIdentifier;


	public SelectTargetHrp(String statusTimerInterval, String hrpIdentifierEnvVariable,
			String reportSendOrganizationIdentifier)
	{
		this.statusTimerInterval = statusTimerInterval;
		this.hrpIdentifierEnvVariable = hrpIdentifierEnvVariable;
		this.reportSendOrganizationIdentifier = reportSendOrganizationIdentifier;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		if (!ISO_8601_DURATION.matcher(statusTimerInterval).matches())
			throw new IllegalArgumentException(
					"statusTimerInterval '" + statusTimerInterval + "' not in ISO 8601 time duration format");
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		logger.info("SelectTargetHrp doExecute");

		variables.setString(ConstantsReport.BPMN_EXECUTION_VARIABLE_STATUS_TIMER_INTERVAL, statusTimerInterval);

		Task startTask = variables.getStartTask();
		Identifier consortiumIdentifier = NamingSystems.OrganizationIdentifier
				.withValue(reportSendOrganizationIdentifier != null && !reportSendOrganizationIdentifier.isEmpty()
						? reportSendOrganizationIdentifier
						: ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM);

		Coding hrpRole = CodeSystems.OrganizationRole.hrp();

		// 1. use hrp-identifier provided from task, if not present
		// 2. use hrp-identifier provided from ENV variable, if not present
		// 3. search hrp-identifier for mii-parent-organization and use first found
		String hrpIdentifier = extractHrpIdentifierFromTask(api, startTask)
				.or(extractHrpIdentifierFromEnv(hrpIdentifierEnvVariable))
				.orElse(searchHrpIdentifier(api, consortiumIdentifier, hrpRole, startTask));

		Endpoint endpoint = getHrpEndpoint(api, consortiumIdentifier, hrpIdentifier, hrpRole);
		String endpointIdentifier = extractEndpointIdentifier(endpoint);

		Target target = variables.createTarget(hrpIdentifier, endpointIdentifier, endpoint.getAddress());
		variables.setTarget(target);

		boolean isDryRun = isDryRun(api, variables);
		if (isDryRun)
			logger.info("Creating new report as dry-run for HRP '{}' in Task '{}'", hrpIdentifier,
					api.getTaskHelper().getLocalVersionlessAbsoluteUrl(startTask));
		else
			logger.info("Executing report for HRP '{}' with status timer interval '{}' in Task '{}'", hrpIdentifier,
					statusTimerInterval, api.getTaskHelper().getLocalVersionlessAbsoluteUrl(startTask));

		variables.setBoolean(ConstantsReport.BPMN_EXECUTION_VARIABLE_IS_DRY_RUN, isDryRun);
	}

	private Optional<String> extractHrpIdentifierFromTask(ProcessPluginApi api, Task task)
	{
		Optional<String> hrpIdentifier = api.getTaskHelper()
				.getFirstInputParameterValue(task, ConstantsReport.CODESYSTEM_REPORT,
						ConstantsReport.CODESYSTEM_REPORT_VALUE_HRP_IDENTIFIER, Reference.class)
				.filter(Reference::hasIdentifier).map(Reference::getIdentifier)
				.filter(i -> NamingSystems.OrganizationIdentifier.SID.equals(i.getSystem())).map(Identifier::getValue);

		hrpIdentifier.ifPresent(hrp -> logger.info("Using HRP '{}' from Task '{}' as report target", hrp,
				api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task)));

		return hrpIdentifier;
	}

	private Supplier<Optional<String>> extractHrpIdentifierFromEnv(String hrpIdentifierEnvVariable)
	{
		return () ->
		{
			if (hrpIdentifierEnvVariable != null)
			{
				logger.info("Using HRP '{}' from ENV variable as report target", hrpIdentifierEnvVariable);
				return Optional.of(hrpIdentifierEnvVariable);
			}
			else
				return Optional.empty();
		};
	}

	private String searchHrpIdentifier(ProcessPluginApi api, Identifier consortiumIdentifier, Coding hrpRole, Task task)
	{
		logger.info("HRP not defined in Task '{}' or ENV variable{}searching HRP for mii-consortium as report target",
				api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task), ConstantsBase.EXCEPTION_MESSAGE_DIVIDER);

		Organization organization = getHrpOrganization(api, consortiumIdentifier, hrpRole, task);
		return extractHrpIdentifierFromOrganization(organization);
	}

	private Organization getHrpOrganization(ProcessPluginApi api, Identifier parentIdentifier, Coding role, Task task)
	{
		List<Organization> hrps = api.getOrganizationProvider().getOrganizations(parentIdentifier, role);

		if (hrps.isEmpty())
			throw new RuntimeException("Could not find any organization with role '" + role.getCode()
					+ "' and parent organization '" + parentIdentifier.getValue() + "'");

		if (hrps.size() > 1)
			logger.warn(
					"Found more than 1 ({}) organization with role '{}' and parent organization '{}', using the first ('{}') for Task '{}'",
					hrps.size(), role.getCode(), parentIdentifier.getValue(),
					hrps.getFirst().getIdentifierFirstRep().getValue(),
					api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));

		return hrps.getFirst();
	}

	private String extractHrpIdentifierFromOrganization(Organization organization)
	{
		return NamingSystems.OrganizationIdentifier.findFirst(organization)
				.orElseThrow(() -> new RuntimeException("organization '" + organization.getId()
						+ "' is missing identifier.system '" + NamingSystems.OrganizationIdentifier.SID + "'"))
				.getValue();
	}

	private Endpoint getHrpEndpoint(ProcessPluginApi api, Identifier parentIdentifier,
			String organizationIdentifierValue, Coding role)
	{
		Identifier organizationIdentifier = NamingSystems.OrganizationIdentifier.withValue(organizationIdentifierValue);
		return api.getEndpointProvider().getEndpoint(parentIdentifier, organizationIdentifier, role)
				.orElseThrow(() -> new RuntimeException("Could not find any Endpoint for organization '"
						+ organizationIdentifier.getValue() + "' and role " + role.getCode() + " '"));
	}

	private String extractEndpointIdentifier(Endpoint endpoint)
	{
		return endpoint.getIdentifier().stream().filter(i -> NamingSystems.EndpointIdentifier.SID.equals(i.getSystem()))
				.map(Identifier::getValue).findFirst()
				.orElseThrow(() -> new RuntimeException("Endpoint '" + endpoint.getId()
						+ "' is missing identifier.system '" + NamingSystems.EndpointIdentifier.SID + "'"));
	}

	private boolean isDryRun(ProcessPluginApi api, Variables variables)
	{
		return api.getTaskHelper()
				.getFirstInputParameterValue(variables.getStartTask(), ConstantsReport.CODESYSTEM_REPORT,
						ConstantsReport.CODESYSTEM_REPORT_VALUE_DRY_RUN, BooleanType.class)
				.map(BooleanType::booleanValue).orElse(false);
	}

}
