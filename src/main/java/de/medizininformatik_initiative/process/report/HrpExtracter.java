package de.medizininformatik_initiative.process.report;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.constants.NamingSystems;

public interface HrpExtracter
{
	Logger logger = LoggerFactory.getLogger(HrpExtracter.class);

	default String hrpExtract(ProcessPluginApi api, Task startTask, String hrpIdentifierEnvVariable, Coding hrpRole,
			Identifier parentIdentifier)
	{
		// 1. use hrp-identifier provided from task, if not present
		// 2. use hrp-identifier provided from ENV variable, if not present
		// 3. search hrp-identifier for mii-parent-organization and use first found
		return extractHrpIdentifierFromTask(api, startTask).or(extractHrpIdentifierFromEnv(hrpIdentifierEnvVariable))
				.orElse(searchHrpIdentifier(api, parentIdentifier, hrpRole, startTask));
	}

	default Optional<String> extractHrpIdentifierFromTask(ProcessPluginApi api, Task task)
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

	default Supplier<Optional<String>> extractHrpIdentifierFromEnv(String hrpIdentifierEnvVariable)
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

	default String searchHrpIdentifier(ProcessPluginApi api, Identifier parentIdentifier, Coding hrpRole, Task task)
	{
		logger.debug(
				"HRP not defined in Task with id '{}' or ENV variable - searching HRP for mii-consortium as report target",
				task.getId());

		Organization organization = getHrpOrganization(api, parentIdentifier, hrpRole);
		return extractHrpIdentifierFromOrganization(organization);
	}

	default Organization getHrpOrganization(ProcessPluginApi api, Identifier parentIdentifier, Coding role)
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

	default String extractHrpIdentifierFromOrganization(Organization organization)
	{
		return NamingSystems.OrganizationIdentifier.findFirst(organization)
				.orElseThrow(() -> new RuntimeException("organization with id '" + organization.getId()
						+ "' is missing identifier with system '" + NamingSystems.OrganizationIdentifier.SID + "'"))
				.getValue();
	}

	default Endpoint getEndpoint(ProcessPluginApi api, Identifier parentIdentifier, Identifier organizationIdentifier,
			Coding role)
	{
		return api.getEndpointProvider().getEndpoint(parentIdentifier, organizationIdentifier, role)
				.orElseThrow(() -> new RuntimeException("Could not find any endpoint of '" + role.getCode()
						+ "' with identifier '" + organizationIdentifier.getValue() + "'"));
	}


	default String extractEndpointIdentifier(Endpoint endpoint)
	{
		return endpoint.getIdentifier().stream().filter(i -> NamingSystems.EndpointIdentifier.SID.equals(i.getSystem()))
				.map(Identifier::getValue).findFirst()
				.orElseThrow(() -> new RuntimeException("Endpoint with id '" + endpoint.getId()
						+ "' is missing identifier with system '" + NamingSystems.EndpointIdentifier.SID + "'"));
	}


}
