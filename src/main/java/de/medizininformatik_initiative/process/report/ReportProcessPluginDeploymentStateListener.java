package de.medizininformatik_initiative.process.report;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.processes.common.fhir.client.FhirClientFactory;
import de.medizininformatik_initiative.processes.common.util.MetadataResourceConverter;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.ProcessPluginDeploymentStateListener;

public class ReportProcessPluginDeploymentStateListener
		implements ProcessPluginDeploymentStateListener, InitializingBean
{
	private final ProcessPluginApi api;

	private final FhirClientFactory fhirClientFactory;
	private final boolean reportDistributeAsBroker;
	private final String resourcesVersion;
	private final MetadataResourceConverter metadataResourceConverter;


	public ReportProcessPluginDeploymentStateListener(ProcessPluginApi api, FhirClientFactory fhirClientFactory,
			MetadataResourceConverter metadataResourceConverter, String resourcesVersion,
			boolean reportDistributeAsBroker)
	{
		this.api = api;
		this.fhirClientFactory = fhirClientFactory;
		this.metadataResourceConverter = metadataResourceConverter;
		this.resourcesVersion = resourcesVersion;
		this.reportDistributeAsBroker = reportDistributeAsBroker;
	}

	@Override
	public void afterPropertiesSet()
	{
		Objects.requireNonNull(api, "api");
		Objects.requireNonNull(fhirClientFactory, "fhirClientFactory");
		Objects.requireNonNull(metadataResourceConverter, "metadataResourceConverter");
		Objects.requireNonNull(resourcesVersion, "resourcesVersion");
	}

	@Override
	public void onProcessesDeployed(List<String> activeProcesses)
	{
		// TODO: functions metadataResourceConverter.searchAndConvertOlderResourcesIfCurrentIsNewestResource and
		// updateDraftTaskReportSendStart added because CodeSystems with different versions cannot be used in
		// DSF API 1.x. Remove for DSF API 2.x API where CodeSystem versioning is fixed.

		metadataResourceConverter.searchAndConvertOlderResourcesIfCurrentIsNewestResource(
				ConstantsReport.CODESYSTEM_REPORT, CodeSystem.class, this::filterCodeSystemsWithNonMatchingConceptCodes,
				this::adaptCodeSystemsReplacingConcepts);

		metadataResourceConverter.searchAndConvertOlderResourcesIfCurrentIsNewestResource(
				ConstantsReport.CODESYSTEM_REPORT_STATUS, CodeSystem.class,
				this::filterCodeSystemsWithNonMatchingConceptCodes, this::adaptCodeSystemsReplacingConcepts);

		if (!reportDistributeAsBroker && activeProcesses.contains(ConstantsReport.PROCESS_NAME_FULL_REPORT_SEND))
		{
			metadataResourceConverter.searchAndConvertOlderResourcesIfCurrentIsNewestResource(
					ConstantsReport.VALUESET_REPORT_STATUS_SEND, ValueSet.class,
					this::filterValueSetsWithNonMatchingConceptCodes, this::adaptValueSetsReplacingConcepts);

			updateDraftTaskReportSendStart();

			fhirClientFactory.testConnection();
		}
	}

	private void adaptCodeSystemsReplacingConcepts(CodeSystem currentResource, CodeSystem olderResource)
	{
		olderResource.setConcept(currentResource.getConcept());
		updateResource(olderResource);
	}

	private boolean filterCodeSystemsWithNonMatchingConceptCodes(CodeSystem currentResource, CodeSystem olderResource)
	{
		return !getConceptCodes(currentResource).equals(getConceptCodes(olderResource));
	}

	private Set<String> getConceptCodes(CodeSystem resource)
	{
		return resource.getConcept().stream().map(CodeSystem.ConceptDefinitionComponent::getCode)
				.collect(Collectors.toSet());
	}

	private void adaptValueSetsReplacingConcepts(ValueSet currentResource, ValueSet olderResource)
	{
		olderResource.getCompose().setInclude(currentResource.getCompose().getInclude());
		olderResource.getCompose().getInclude().forEach(include -> include.setVersion(olderResource.getVersion()));
		updateResource(olderResource);
	}

	private boolean filterValueSetsWithNonMatchingConceptCodes(ValueSet currentResource, ValueSet olderResource)
	{
		return !getConceptCodes(currentResource).equals(getConceptCodes(olderResource));
	}

	private Set<String> getConceptCodes(ValueSet resource)
	{
		ValueSet.ValueSetComposeComponent compose = resource.getCompose();
		return Stream.concat(
				compose.getInclude().stream().flatMap(c -> c.getConcept().stream())
						.map(ValueSet.ConceptReferenceComponent::getCode),
				compose.getExclude().stream().flatMap(c -> c.getConcept().stream())
						.map(ValueSet.ConceptReferenceComponent::getCode))
				.collect(Collectors.toSet());
	}

	private void updateDraftTaskReportSendStart()
	{
		Bundle searchResult = searchDraftTask("http://medizininformatik-initiative.de/bpe/Process/reportSend/"
				+ resourcesVersion + "/reportSendStart");

		extractTask(searchResult).ifPresent(this::addDryRunInputIfMissingAndUpdate);
	}

	private Bundle searchDraftTask(String identifier)
	{
		return api.getFhirWebserviceClientProvider().getLocalWebserviceClient().search(Task.class,
				Map.of("status", List.of("draft"), "identifier", List.of(identifier)));
	}

	private Optional<Task> extractTask(Bundle bundle)
	{
		return bundle.getEntry().stream().filter(Bundle.BundleEntryComponent::hasResource)
				.map(Bundle.BundleEntryComponent::getResource).filter(r -> r instanceof Task).map(r -> (Task) r)
				.findFirst();
	}

	private void addDryRunInputIfMissingAndUpdate(Task task)
	{
		if (dryRunInputMissing(task))
		{
			Task.ParameterComponent dryRun = new Task.ParameterComponent().setValue(new BooleanType(false));
			dryRun.getType().addCoding().setSystem(ConstantsReport.CODESYSTEM_REPORT)
					.setCode(ConstantsReport.CODESYSTEM_REPORT_VALUE_DRY_RUN);
			task.getInput().add(dryRun);

			updateResource(task);
		}
	}

	private boolean dryRunInputMissing(Task task)
	{
		return task
				.getInput().stream().filter(
						i -> i.getType().getCoding().stream()
								.anyMatch(c -> ConstantsReport.CODESYSTEM_REPORT_VALUE_DRY_RUN.equals(c.getCode())
										&& ConstantsReport.CODESYSTEM_REPORT.equals(c.getSystem())))
				.findAny().isEmpty();
	}

	private void updateResource(Resource resource)
	{
		api.getFhirWebserviceClientProvider().getLocalWebserviceClient().update(resource);
	}
}
