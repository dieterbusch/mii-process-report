package de.medizininformatik_initiative.process.report;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import com.google.common.base.Strings;

import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.ProcessPluginDeploymentListener;
import dev.dsf.bpe.v2.client.dsf.DsfClient;
import dev.dsf.bpe.v2.service.DsfClientProvider;

public class ReportProcessPluginDeploymentListener implements ProcessPluginDeploymentListener, InitializingBean
{
	private final ProcessPluginApi api;

	private final String fhirStoreId, reportSendOrganizationIdentifier, reportReceiveOrganizationIdentifier;
	private final boolean reportDistributeAsBroker;
	private static final Logger logger = LoggerFactory.getLogger(ReportProcessPluginDeploymentListener.class);

	public ReportProcessPluginDeploymentListener(ProcessPluginApi api, String fhirStoreId,
			boolean reportDistributeAsBroker, String reportSendOrganizationIdentifier,
			String reportReceiveOrganizationIdentifier)
	{
		this.api = api;
		this.fhirStoreId = fhirStoreId;
		this.reportDistributeAsBroker = reportDistributeAsBroker;
		this.reportSendOrganizationIdentifier = reportSendOrganizationIdentifier;
		this.reportReceiveOrganizationIdentifier = reportReceiveOrganizationIdentifier;
	}

	@Override
	public void afterPropertiesSet()
	{
		Objects.requireNonNull(api, "api");
	}

	@Override
	public void onProcessesDeployed(List<String> activeProcesses)
	{
		logger.info("ReportProcessPlugin deployed as {}", reportDistributeAsBroker ? "broker" : "dic");

		DsfClientProvider dsfClientProvider = api.getDsfClientProvider();
		if (!reportDistributeAsBroker && activeProcesses.contains(ConstantsReport.PROCESS_NAME_FULL_REPORT_SEND))
		{
			CapabilityStatement conformance = dsfClientProvider.getById(fhirStoreId)
					.orElseThrow(() -> new RuntimeException("DSF FHIR client '" + fhirStoreId + "' not configured"))
					.getConformance();

			Objects.requireNonNull(conformance, "Connection test for DSF FHIR Client '" + fhirStoreId + "' failed"
					+ ConstantsBase.EXCEPTION_MESSAGE_DIVIDER + "CapabilityStatement is null");
		}
		DsfClient dsfClient = dsfClientProvider.getLocal();

		CompletableFuture<Bundle> result = dsfClient.searchAsyncWithStrictHandling(ActivityDefinition.class,
				Map.of("url", List.of(ConstantsReport.REPORT_SEND_URL)));
		replaceOrganizationIdentifier(result, reportSendOrganizationIdentifier, dsfClient);

		result = dsfClient.searchAsyncWithStrictHandling(ActivityDefinition.class,
				Map.of("url", List.of(ConstantsReport.REPORT_AUTOSTART_URL)));
		replaceOrganizationIdentifier(result, reportSendOrganizationIdentifier, dsfClient);

		result = dsfClient.searchAsyncWithStrictHandling(ActivityDefinition.class,
				Map.of("url", List.of(ConstantsReport.REPORT_RECEIVE_URL)));
		replaceOrganizationIdentifier(result, reportReceiveOrganizationIdentifier, dsfClient);
	}

	private void replaceOrganizationIdentifier(CompletableFuture<Bundle> result, String replace, DsfClient dsfClient)
	{
		if (result == null || Strings.isNullOrEmpty(replace) || dsfClient == null)
		{
			return;
		}
		result.thenAccept(bundle -> bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource)
				.filter(ActivityDefinition.class::isInstance).map(ActivityDefinition.class::cast)
				.forEach(activityDefinition ->
				{
					AtomicBoolean changed = new AtomicBoolean(false);
					activityDefinition.getExtension().stream().flatMap(extension -> extension.getExtension().stream())
							.map(Extension::getValue).filter(Coding.class::isInstance).map(Coding.class::cast)
							.flatMap(coding -> coding.getExtension().stream())
							.flatMap(extension -> extension.getExtension().stream())
							.filter(extension -> "parent-organization".equals(extension.getUrl()))
							.map(Extension::getValue).filter(Identifier.class::isInstance).map(Identifier.class::cast)
							.filter(identifier -> "http://dsf.dev/sid/organization-identifier"
									.equals(identifier.getSystem()))
							.forEach(identifier ->
							{
								if (!Objects.equals(identifier.getValue(), replace))
								{
									logger.info(
											"Processing ActivityDefinition {}. Replace current organization identifier {} with {}",
											activityDefinition.getUrl(), identifier.getValue(), replace);
									identifier.setValue(replace);
									changed.set(true);
								}
								else
								{
									logger.info(
											"Processing ActivityDefinition {}. No replace of organization identifier {}",
											activityDefinition.getUrl(), identifier.getValue());
								}
							});
					if (changed.get())
					{
						dsfClient.update(activityDefinition);
					}
				}));
	}
}
