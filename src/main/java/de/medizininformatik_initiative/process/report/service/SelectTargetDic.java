package de.medizininformatik_initiative.process.report.service;

import de.medizininformatik_initiative.process.report.HrpExtracter;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.constants.NamingSystems;
import dev.dsf.bpe.v1.variables.Target;
import dev.dsf.bpe.v1.variables.Variables;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Task;

public class SelectTargetDic extends AbstractServiceDelegate implements HrpExtracter
{
	private final String reportReceiveOrganizationIdentifier;

	public SelectTargetDic(ProcessPluginApi api, String reportReceiveOrganizationIdentifier)
	{
		super(api);
		this.reportReceiveOrganizationIdentifier = reportReceiveOrganizationIdentifier;

	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		logger.info("SelectTargetDic doExecute");

		Task task = variables.getStartTask();
		Identifier dicIdentifier = getDicOrganizationIdentifier(task);
		Endpoint dicEndpoint = getDicEndpoint(dicIdentifier);
		Target dicTarget = createTarget(variables, dicIdentifier, dicEndpoint);

		variables.setTarget(dicTarget);
	}

	private Identifier getDicOrganizationIdentifier(Task task)
	{
		return task.getRequester().getIdentifier();
	}

	private Endpoint getDicEndpoint(Identifier dicIdentifier)
	{

		Identifier parentIdentifier = NamingSystems.OrganizationIdentifier
				.withValue(reportReceiveOrganizationIdentifier != null && !reportReceiveOrganizationIdentifier.isEmpty()
						? reportReceiveOrganizationIdentifier
						: ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM);

		Coding role = new Coding().setSystem(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE)
				.setCode(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE_VALUE_DIC);

		return getEndpoint(api, parentIdentifier, dicIdentifier, role);
	}

	private Target createTarget(Variables variables, Identifier dicIdentifier, Endpoint dicEndpoint)
	{
		String dicEndpointIdentifier = extractEndpointIdentifier(dicEndpoint);
		return variables.createTarget(dicIdentifier.getValue(), dicEndpointIdentifier, dicEndpoint.getAddress());
	}
}
