package de.medizininformatik_initiative.process.report.service;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.process.report.HrpExtracter;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.constants.NamingSystems;
import dev.dsf.bpe.v1.variables.Target;
import dev.dsf.bpe.v1.variables.Variables;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SelectTargetHrp extends AbstractServiceDelegate implements HrpExtracter
{
	private static final Logger logger = LoggerFactory.getLogger(SelectTargetHrp.class);

	private final String hrpIdentifierEnvVariable;
	private final String reportSendOrganizationIdentifier;

	public SelectTargetHrp(ProcessPluginApi api, String hrpIdentifierEnvVariable,
			String reportSendOrganizationIdentifier)
	{
		super(api);
		this.hrpIdentifierEnvVariable = hrpIdentifierEnvVariable;
		this.reportSendOrganizationIdentifier = reportSendOrganizationIdentifier;
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		logger.info("SelectTargetHrp doExecute");

		Task startTask = variables.getStartTask();

		Identifier parentIdentifier = NamingSystems.OrganizationIdentifier
				.withValue(reportSendOrganizationIdentifier != null && !reportSendOrganizationIdentifier.isEmpty()
						? reportSendOrganizationIdentifier
						: ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM);

		Coding hrpRole = new Coding().setSystem(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE)
				.setCode(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE_VALUE_HRP);

		String hrpIdentifier = hrpExtract(api, startTask, hrpIdentifierEnvVariable, hrpRole, parentIdentifier);

		Identifier organizationIdentifier = NamingSystems.OrganizationIdentifier.withValue(hrpIdentifier);

		Endpoint endpoint = getEndpoint(api, parentIdentifier, organizationIdentifier, hrpRole);

		String endpointIdentifier = extractEndpointIdentifier(endpoint);

		Target target = variables.createTarget(hrpIdentifier, endpointIdentifier, endpoint.getAddress());
		variables.setTarget(target);

		boolean isDryRun = isDryRun(variables);
		if (isDryRun)
			logger.info("Creating new report as dry-run for HRP '{}'", hrpIdentifier);

		variables.setBoolean(ConstantsReport.BPMN_EXECUTION_VARIABLE_IS_DRY_RUN, isDryRun);
	}


	private boolean isDryRun(Variables variables)
	{
		return api.getTaskHelper()
				.getFirstInputParameterValue(variables.getStartTask(), ConstantsReport.CODESYSTEM_REPORT,
						ConstantsReport.CODESYSTEM_REPORT_VALUE_DRY_RUN, BooleanType.class)
				.map(BooleanType::booleanValue).orElse(false);
	}

}
