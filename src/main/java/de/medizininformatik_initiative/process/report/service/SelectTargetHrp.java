package de.medizininformatik_initiative.process.report.service;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.constants.NamingSystems;
import dev.dsf.bpe.v1.variables.Target;
import dev.dsf.bpe.v1.variables.Variables;

public class SelectTargetHrp extends AbstractServiceDelegate
{
	private static final Logger logger = LoggerFactory.getLogger(SelectTargetHrp.class);

	private final String hrpIdentifierEnvVariable;

	public SelectTargetHrp(ProcessPluginApi api, String hrpIdentifierEnvVariable)
	{
		super(api);
		this.hrpIdentifierEnvVariable = hrpIdentifierEnvVariable;
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task startTask = variables.getStartTask();

		Identifier parentIdentifier = NamingSystems.OrganizationIdentifier.withValue(
				ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM);
		Coding hrpRole = new Coding().setSystem(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE)
				.setCode(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE_VALUE_HRP);

		// 1. use hrp-identifier provided from task, if not present
		// 2. use hrp-identifier provided from ENV variable, if not present
		// 3. search hrp-identifier for mii-parent-organization and use first found
		String hrpIdentifier = extractHrpIdentifierFromTask(startTask)
				.or(extractHrpIdentifierFromEnv(hrpIdentifierEnvVariable))
				.orElse(searchHrpIdentifier(parentIdentifier, hrpRole, startTask));

		Endpoint endpoint = getHrpEndpoint(parentIdentifier, hrpIdentifier, hrpRole);
		String endpointIdentifier = extractEndpointIdentifier(endpoint);

		Target target = variables.createTarget(hrpIdentifier, endpointIdentifier, endpoint.getAddress());
		variables.setTarget(target);

		boolean isDryRun = isDryRun(variables);
		if (isDryRun)
			logger.info("Creating new report as dry-run for HRP '{}'", hrpIdentifier);

		variables.setBoolean(ConstantsReport.BPMN_EXECUTION_VARIABLE_IS_DRY_RUN, isDryRun);
	}

	private Optional<String> extractHrpIdentifierFromTask(Task task)
	{
		Optional<String> hrpIdentifier = api.getTaskHelper()
				.getFirstInputParameterValue(task, ConstantsReport.CODESYSTEM_REPORT,
						ConstantsReport.CODESYSTEM_REPORT_VALUE_HRP_IDENTIFIER, Reference.class)
				.filter(Reference::hasIdentifier).map(Reference::getIdentifier)
				.filter(i -> NamingSystems.OrganizationIdentifier.SID.equals(i.getSystem())).map(Identifier::getValue);

		hrpIdentifier.ifPresent(
				hrp -> logger.info("Using HRP '{}' from Task with id '{}' as report target", hrp, task.getId()));

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

	private String searchHrpIdentifier(Identifier parentIdentifier, Coding hrpRole, Task task)
	{
		logger.debug(
				"HRP not defined in Task with id '{}' or ENV variable - searching HRP for mii-consortium as report target",
				task.getId());

		Organization organization = getHrpOrganization(parentIdentifier, hrpRole);
		return extractHrpIdentifierFromOrganization(organization);
	}

	private Organization getHrpOrganization(Identifier parentIdentifier, Coding role)
	{
		List<Organization> hrps = api.getOrganizationProvider().getOrganizations(parentIdentifier, role);

		if (hrps.size() < 1)
			throw new RuntimeException("Could not find any organization with role '" + role.getCode()
					+ "' and parent organization '" + parentIdentifier.getValue() + "'");

		if (hrps.size() > 1)
			logger.warn(
					"Found more than 1 ({}) organization with role '{}' and parent organization '{}', using the first ('{}')",
					hrps.size(), role.getCode(), parentIdentifier.getValue(),
					hrps.get(0).getIdentifierFirstRep().getValue());

		return hrps.get(0);
	}

	private String extractHrpIdentifierFromOrganization(Organization organization)
	{
		return NamingSystems.OrganizationIdentifier.findFirst(organization)
				.orElseThrow(() -> new RuntimeException("organization with id '" + organization.getId()
						+ "' is missing identifier with system '" + NamingSystems.OrganizationIdentifier.SID + "'"))
				.getValue();
	}

	private Endpoint getHrpEndpoint(Identifier parentIdentifier, String organizationIdentifierValue, Coding role)
	{
		Identifier organizationIdentifier = NamingSystems.OrganizationIdentifier.withValue(organizationIdentifierValue);
		return api.getEndpointProvider().getEndpoint(parentIdentifier, organizationIdentifier, role)
				.orElseThrow(() -> new RuntimeException("Could not find any endpoint of '" + role.getCode()
						+ "' with identifier '" + organizationIdentifier.getValue() + "'"));
	}

	private String extractEndpointIdentifier(Endpoint endpoint)
	{
		return endpoint.getIdentifier().stream().filter(i -> NamingSystems.EndpointIdentifier.SID.equals(i.getSystem()))
				.map(Identifier::getValue).findFirst()
				.orElseThrow(() -> new RuntimeException("Endpoint with id '" + endpoint.getId()
						+ "' is missing identifier with system '" + NamingSystems.EndpointIdentifier.SID + "'"));
	}

	private boolean isDryRun(Variables variables)
	{
		return api.getTaskHelper()
				.getFirstInputParameterValue(variables.getStartTask(), ConstantsReport.CODESYSTEM_REPORT,
						ConstantsReport.CODESYSTEM_REPORT_VALUE_DRY_RUN, BooleanType.class)
				.map(BooleanType::booleanValue).orElse(false);
	}

}
