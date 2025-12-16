package de.medizininformatik_initiative.process.report.service;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.process.report.HrpExtracter;
import de.medizininformatik_initiative.process.report.SaveOrUpdateBundle;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.constants.NamingSystems;
import dev.dsf.bpe.v1.variables.Variables;
import dev.dsf.fhir.client.FhirWebserviceClient;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.medizininformatik_initiative.process.report.ConstantsReport.DIC;

public class AggregateReports extends AbstractServiceDelegate
		implements InitializingBean, SaveOrUpdateBundle, HrpExtracter
{
	private static final Logger logger = LoggerFactory.getLogger(AggregateReports.class);


	private final String reportReceiveOrganizationIdentifier;
	private final String hrpIdentifierEnvVariable;

	private FhirWebserviceClient localWebserviceClient;

	public AggregateReports(ProcessPluginApi api, String hrpIdentifierEnvVariable,
			String reportReceiveOrganizationIdentifier)
	{
		super(api);


		this.reportReceiveOrganizationIdentifier = reportReceiveOrganizationIdentifier;
		this.hrpIdentifierEnvVariable = hrpIdentifierEnvVariable;
		this.localWebserviceClient = api.getFhirWebserviceClientProvider().getLocalWebserviceClient();
	}

	@Override
	protected void doExecute(DelegateExecution delegateExecution, Variables variables) throws BpmnError, Exception
	{
		logger.info("AggregateReports doExecute");

		Identifier parentIdentifier = NamingSystems.OrganizationIdentifier
				.withValue(reportReceiveOrganizationIdentifier != null && !reportReceiveOrganizationIdentifier.isEmpty()
						? reportReceiveOrganizationIdentifier
						: ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM);


		api.getOrganizationProvider().getLocalOrganizationIdentifierValue()
				.ifPresent(organizationIdentifierValue -> api.getOrganizationProvider()
						.getOrganizations(parentIdentifier, DIC).stream().filter(Organization::hasEndpoint)
						.filter(Organization::hasIdentifier).flatMap(org ->
						{
							String identifierValue = ConstantsReport.NAMINGSYSTEM_CDS_REPORT_IDENTIFIER + "|"
									+ org.getIdentifierFirstRep().getValue();

							Bundle search = searchBundleLocal(localWebserviceClient, identifierValue);
							if (search == null || search.getEntry().isEmpty())
							{
								logger.warn("No matching bundle found for identifier: " + identifierValue);
								return Stream.empty();
							}
							if (search.getEntry().size() > 1)
							{
								logger.error("Found more than one merge bundle for organization identifier: "
										+ identifierValue);
								return Stream.empty(); // Organisation überspringen
							}
							// genau 1 Entry vorhanden
							var res = search.getEntry().get(0).getResource();
							return (res instanceof Bundle b) ? Stream.of(b) : Stream.empty();
						}).reduce((base, next) ->
						{
							mergeBundles(base, next); // wird für jedes weitere Bundle aufgerufen
							return base; // base bleibt die Merge-Basis (erstes gefundene Bundle)
						}).ifPresent(mergeBundle ->
						{
							Coding hrpRole = new Coding().setSystem(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE)
									.setCode(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE_VALUE_HRP);

							String hrpIdentifier = hrpExtract(api, variables.getStartTask(), hrpIdentifierEnvVariable,
									hrpRole, parentIdentifier);

							addLocalIdentityToBundle(mergeBundle, organizationIdentifierValue, hrpIdentifier);

							String searchBundleIdentifier = ConstantsReport.NAMINGSYSTEM_CDS_REPORT_IDENTIFIER + "|"
									+ organizationIdentifierValue;
							Resource r = saveOrUpdate(localWebserviceClient, mergeBundle, searchBundleIdentifier);

							setReportSearchBundleResponseReference(variables, r.getIdElement().getIdPart(),
									r.getMeta().getVersionId(), organizationIdentifierValue);
						}));

	}

	private void setReportSearchBundleResponseReference(Variables variables, String id, String versionId,
			String brokerHrpId)
	{
		String absoluteId = new IdType(api.getEndpointProvider().getLocalEndpointAddress(), ResourceType.Bundle.name(),
				id, versionId).getValue();
		variables.setString(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE_RESPONSE_REFERENCE,
				absoluteId);

		logger.info("Stored report Bundle with id '{}' for Broker-HRP '{}' and Task with id '{}'", absoluteId,
				brokerHrpId, variables.getStartTask().getId());
	}

	private void addLocalIdentityToBundle(Bundle mergeBundle, String organizationIdentifierValue, String hrpIdentifier)
	{
		mergeBundle.getIdentifier().setValue(organizationIdentifierValue);
		mergeBundle.getMeta().getTag().stream()
				.filter(coding -> ConstantsReport.META_TAG_CODE_ORGANIZATION.equals(coding.getCode())).findFirst()
				.flatMap(tag -> tag.getExtension().stream().findFirst()).ifPresent(extension ->
				{
					if (extension.getValue() instanceof Identifier identifier)
						identifier.setValue(hrpIdentifier);
				});
	}

	private void mergeBundles(Bundle base, Bundle nextBundle)
	{
		if (nextBundle != null && base != null && !nextBundle.getEntry().isEmpty() && !base.getEntry().isEmpty())
		{
			Map<String, Integer> nextBundleUrlsAndTotals = nextBundle.getEntry().stream()
					.collect(Collectors.toMap(url ->
					{
						if (url.getResource() != null && url.getResource() instanceof Bundle bundle
								&& !bundle.getLink().isEmpty())
						{
							return bundle.getLink().get(0).getUrl();

						}
						if (checkSkipEntryBundle(url))
						{
							return UUID.randomUUID().toString();
						}
						throw new RuntimeException("Bundle " + url.fhirType() + " not supported");
					}, total ->
					{
						if (total.getResource() != null && total.getResource() instanceof Bundle bundle
								&& bundle.getTotal() > -1)
						{
							return bundle.getTotal();
						}
						if (checkSkipEntryBundle(total))
						{
							return -1;
						}
						throw new RuntimeException("Bundle " + total.fhirType() + " not supported");
					}));
			base.getEntry().forEach(entry ->
			{
				if (entry.getResource() != null && entry.getResource() instanceof Bundle bundle
						&& !bundle.getLink().isEmpty())
				{
					String url = bundle.getLink().get(0).getUrl();
					if (nextBundleUrlsAndTotals.containsKey(url))
					{
						Integer total = nextBundleUrlsAndTotals.get(url);
						if (total != null && total > -1)
						{
							int baseTotal = bundle.getTotal();
							bundle.setTotal(baseTotal + nextBundleUrlsAndTotals.get(url));
						}
					}
				}
			});
		}
	}

	private boolean checkSkipEntryBundle(Bundle.BundleEntryComponent entry)
	{
		if (entry.getResource() == null || entry.getResource() instanceof CapabilityStatement)
		{
			logger.debug("Skipping merge bundle with resource type CapabilityStatement or null");
			return true;
		}
		return false;
	}

}
