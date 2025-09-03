package de.medizininformatik_initiative.process.report;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Task;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.processes.common.fhir.client.FhirClientFactory;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.ProcessPluginDeploymentStateListener;

public class ReportProcessPluginDeploymentStateListener
		implements ProcessPluginDeploymentStateListener, InitializingBean
{
	private final ProcessPluginApi api;
	private final FhirClientFactory fhirClientFactory;
	private final String resourcesVersion;

	private record MinorMajorVersion(int major, int minor)
	{
	}

	public ReportProcessPluginDeploymentStateListener(ProcessPluginApi api, FhirClientFactory fhirClientFactory,
			String resourcesVersion)
	{
		this.api = api;
		this.fhirClientFactory = fhirClientFactory;
		this.resourcesVersion = resourcesVersion;
	}

	@Override
	public void afterPropertiesSet()
	{
		Objects.requireNonNull(api, "api");
		Objects.requireNonNull(fhirClientFactory, "fhirClientFactory");
		Objects.requireNonNull(resourcesVersion, "resourcesVersion");
	}

	@Override
	public void onProcessesDeployed(List<String> activeProcesses)
	{
		// TODO: functions updateOlderCodeSystemsIfCurrentIsNewestCodeSystem and updateDraftTaskReportSendStart
		// 	     added because CodeSystems with different versions cannot be used in DSF API 1.x.
		//       Remove for DSF API 2.x API where CodeSystem versioning is fixed.

		updateOlderCodeSystemsIfCurrentIsNewestCodeSystem(ConstantsReport.CODESYSTEM_REPORT);

		if (activeProcesses.contains(ConstantsReport.PROCESS_NAME_FULL_REPORT_SEND))
		{
			updateDraftTaskReportSendStart();
			fhirClientFactory.testConnection();
		}
	}

	private void updateOlderCodeSystemsIfCurrentIsNewestCodeSystem(String url)
	{
		Bundle searchResult = searchCodeSystem(url);
		List<CodeSystem> allCodeSystems = extractCodeSystems(searchResult);

		CodeSystem currentCodeSystem = filterCurrentCodeSystem(allCodeSystems);
		List<CodeSystem> olderNewerCodeSystems = filterOlderNewerCodeSystems(allCodeSystems);

		if (currentIsNewestCodeSystem(olderNewerCodeSystems))
		{
			List<CodeSystem> codeSystemsWithNonMatchingConceptCodes = filterCodeSystemsWithNonMatchingConceptCodesAndAdaptToCurrentCodeSystemConceptCodes(
					currentCodeSystem, olderNewerCodeSystems);
			updateCodeSystemsWithNonMatchingConceptCodes(codeSystemsWithNonMatchingConceptCodes);
		}
	}

	private Bundle searchCodeSystem(String url)
	{
		return api.getFhirWebserviceClientProvider().getLocalWebserviceClient().search(CodeSystem.class,
				Map.of("url", List.of(url)));
	}

	private List<CodeSystem> extractCodeSystems(Bundle bundle)
	{
		return bundle.getEntry().stream().filter(Bundle.BundleEntryComponent::hasResource)
				.map(Bundle.BundleEntryComponent::getResource).filter(r -> r instanceof CodeSystem)
				.map(r -> (CodeSystem) r).filter(c -> ConstantsReport.CODESYSTEM_REPORT.equals(c.getUrl())).toList();
	}

	private CodeSystem filterCurrentCodeSystem(List<CodeSystem> all)
	{
		return all.stream().filter(c -> resourcesVersion.equals(c.getVersion())).findFirst().orElseThrow(
				() -> new RuntimeException("CodeSystem " + ConstantsReport.CODESYSTEM_REPORT + "|" + resourcesVersion));
	}

	private List<CodeSystem> filterOlderNewerCodeSystems(List<CodeSystem> all)
	{
		return all.stream().filter(c -> !resourcesVersion.equals(c.getVersion())).toList();
	}

	private boolean currentIsNewestCodeSystem(List<CodeSystem> olderNewerCodeSystems)
	{
		return olderNewerCodeSystems.stream().noneMatch(this::isNewerCodeSystem);
	}

	private boolean isNewerCodeSystem(CodeSystem codeSystem)
	{
		MinorMajorVersion current = getMajorMinorVersion(resourcesVersion);
		MinorMajorVersion olderNewer = getMajorMinorVersion(codeSystem.getVersion());

		return current.major <= olderNewer.major && current.minor < olderNewer.minor;
	}

	private MinorMajorVersion getMajorMinorVersion(String version)
	{
		if (version.matches("\\d\\.\\d"))
		{
			String[] minorMajor = version.split("\\.");
			return new MinorMajorVersion(Integer.parseInt(minorMajor[0]), Integer.parseInt(minorMajor[1]));
		}

		throw new RuntimeException("Fhir resource version " + version + " does not match regex \\d\\.\\d");
	}

	private List<CodeSystem> filterCodeSystemsWithNonMatchingConceptCodesAndAdaptToCurrentCodeSystemConceptCodes(
			CodeSystem currentCodeSystem, List<CodeSystem> olderCodeSystems)
	{
		Set<String> currentConceptCodes = getConceptCodes(currentCodeSystem);
		return olderCodeSystems.stream().filter(c -> !currentConceptCodes.equals(getConceptCodes(c)))
				.map(c -> c.setConcept(currentCodeSystem.getConcept())).toList();
	}

	private Set<String> getConceptCodes(CodeSystem codeSystem)
	{
		return codeSystem.getConcept().stream().map(CodeSystem.ConceptDefinitionComponent::getCode)
				.collect(Collectors.toSet());
	}

	private void updateCodeSystemsWithNonMatchingConceptCodes(List<CodeSystem> codeSystems)
	{
		codeSystems.forEach(c -> api.getFhirWebserviceClientProvider().getLocalWebserviceClient().update(c));
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

			api.getFhirWebserviceClientProvider().getLocalWebserviceClient().update(task);
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
}
