package de.medizininformatik_initiative.process.report.service;

import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Task;

import de.medizininformatik_initiative.process.report.HrpExtracter;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.constants.CodeSystems;
import dev.dsf.bpe.v2.constants.NamingSystems;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Variables;

public class SelectTargetDic implements ServiceTask, HrpExtracter
{
	private final String reportReceiveOrganizationIdentifier;

	public SelectTargetDic(String reportReceiveOrganizationIdentifier)
	{
		this.reportReceiveOrganizationIdentifier = reportReceiveOrganizationIdentifier;

	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		logger.info("SelectTargetDic doExecute");

		Task task = variables.getStartTask();
		Identifier dicIdentifier = getDicOrganizationIdentifier(task);
		Endpoint dicEndpoint = getDicEndpoint(api, dicIdentifier);
		Target dicTarget = createTarget(variables, dicIdentifier, dicEndpoint);

		variables.setTarget(dicTarget);
	}

	private Identifier getDicOrganizationIdentifier(Task task)
	{
		return task.getRequester().getIdentifier();
	}

	private Endpoint getDicEndpoint(ProcessPluginApi api, Identifier dicIdentifier)
	{
		return api.getEndpointProvider().getEndpoint(NamingSystems.OrganizationIdentifier
				.withValue(reportReceiveOrganizationIdentifier != null && !reportReceiveOrganizationIdentifier.isEmpty()
						? reportReceiveOrganizationIdentifier
						: ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM),
				dicIdentifier, CodeSystems.OrganizationRole.dic())
				.orElseThrow(() -> new RuntimeException(
						"Could not find default endpoint of organization '" + dicIdentifier.getValue() + "'"));

		// Identifier parentIdentifier = NamingSystems.OrganizationIdentifier
		// .withValue(reportReceiveOrganizationIdentifier != null && !reportReceiveOrganizationIdentifier.isEmpty()
		// ? reportReceiveOrganizationIdentifier
		// : ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM);
		//
		// Coding role = new Coding().setSystem(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE)
		// .setCode(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE_VALUE_DIC);
		//
		// return getEndpoint(api, parentIdentifier, dicIdentifier, role);
	}

	private Target createTarget(Variables variables, Identifier dicIdentifier, Endpoint dicEndpoint)
	{
		String dicEndpointIdentifier = extractEndpointIdentifier(dicEndpoint);
		return variables.createTarget(dicIdentifier.getValue(), dicEndpointIdentifier, dicEndpoint.getAddress());
	}

	public String extractEndpointIdentifier(Endpoint endpoint)
	{
		return endpoint.getIdentifier().stream().filter(i -> NamingSystems.EndpointIdentifier.SID.equals(i.getSystem()))
				.map(Identifier::getValue).findFirst()
				.orElseThrow(() -> new RuntimeException("Endpoint '" + endpoint.getId()
						+ "' is missing identifier.system '" + NamingSystems.EndpointIdentifier.SID + "'"));
	}
}
