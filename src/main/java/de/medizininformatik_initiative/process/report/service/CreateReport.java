package de.medizininformatik_initiative.process.report.service;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.client.dsf.DelayStrategy;
import dev.dsf.bpe.v2.client.dsf.DsfClient;
import dev.dsf.bpe.v2.client.dsf.PreferReturnMinimal;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Variables;
import jakarta.ws.rs.WebApplicationException;

public class CreateReport implements ServiceTask, InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(CreateReport.class);

	private static final String PIPE_ENCODED = "%7C";
	private static final String RESPONSE_OK = "200";

	private final String fhirStoreId;

	public CreateReport(String fhirStoreId)
	{
		this.fhirStoreId = fhirStoreId;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		Objects.requireNonNull(fhirStoreId, "fhirStoreId");
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		logger.info("CreateReport doExecute");

		Task task = variables.getStartTask();
		Bundle searchBundle = variables.getFhirResource(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE);
		Target target = variables.getTarget();
		boolean isDryRun = variables.getBoolean(ConstantsReport.BPMN_EXECUTION_VARIABLE_IS_DRY_RUN);

		logger.info("Executing report search queries for HRP '{}' in Task '{}', this could take a while..",
				target.getOrganizationIdentifierValue(), api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));

		DsfClient client = getDsfClient(api);
		Bundle responseBundle = executeSearchBundle(client, searchBundle, target.getOrganizationIdentifierValue(),
				api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));

		Bundle reportBundle = transformToReportBundle(api, searchBundle, responseBundle, target, isDryRun);
		api.getDataLogger().log(
				"Report Bundle for Task '" + api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task) + "'",
				reportBundle);

		checkReportBundle(searchBundle, reportBundle, target.getOrganizationIdentifierValue());

		String reportReference = storeReportBundle(api, reportBundle, target.getOrganizationIdentifierValue(), task);
		variables.setString(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE_RESPONSE_REFERENCE,
				reportReference);

		String timerInterval = variables.getString(ConstantsReport.BPMN_EXECUTION_VARIABLE_STATUS_TIMER_INTERVAL);
		logger.info("Waiting for report receipt for {} in Task '{}'", timerInterval,
				api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));
	}

	private DsfClient getDsfClient(ProcessPluginApi api)
	{
		return api.getDsfClientProvider().getById(fhirStoreId)
				.orElseThrow(() -> new RuntimeException("DSF FHIR client '" + fhirStoreId + "' not configured"));
	}

	private Bundle executeSearchBundle(DsfClient client, Bundle searchBundle, String hrpIdentifier,
			String taskReference)
	{
		Bundle responseBundle = new Bundle();
		responseBundle.setType(Bundle.BundleType.BATCHRESPONSE);

		searchBundle.getEntry().stream().filter(Bundle.BundleEntryComponent::hasRequest)
				.map(Bundle.BundleEntryComponent::getRequest)
				.filter(r -> r.hasUrl() && r.hasMethod() && Bundle.HTTPVerb.GET.equals(r.getMethod()))
				.map(Bundle.BundleEntryRequestComponent::getUrl).map(url -> executeRequest(client, url, taskReference))
				.forEach(responseBundle::addEntry);

		return responseBundle;
	}

	private Bundle.BundleEntryComponent executeRequest(DsfClient client, String url, String taskReference)
	{
		Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
		String urlWithBase = URI.create(client.getBaseUrl())
				.resolve(URLDecoder.decode(url, StandardCharsets.UTF_8).replace("|", PIPE_ENCODED)).toString();

		try
		{
			Resource result = doExecuteRequest(client, urlWithBase);

			entry.setResource(result);
			entry.setResponse(new Bundle.BundleEntryResponseComponent().setStatus(RESPONSE_OK));
		}
		catch (Exception exception)
		{
			logger.warn("Could not execute report search request '{}' for Task '{}'{}{}", urlWithBase, taskReference,
					ConstantsBase.EXCEPTION_MESSAGE_DIVIDER, exception.getMessage());

			OperationOutcome outcome = new OperationOutcome();
			outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR)
					.setCode(OperationOutcome.IssueType.EXCEPTION).setDiagnostics(exception.getMessage());
			Bundle.BundleEntryResponseComponent response = new Bundle.BundleEntryResponseComponent()
					.setStatus(getStatusCode(exception)).setOutcome(outcome);

			entry.setResponse(response);
		}

		return entry;
	}

	private Resource doExecuteRequest(DsfClient client, String urlWithBase)
			throws ExecutionException, InterruptedException
	{
		if (urlWithBase.contains("metadata"))
			return client.getConformance();
		else
			return client.searchAsync(urlWithBase).get();
	}

	private String getStatusCode(Exception exception)
	{
		return switch (exception)
		{
			case WebApplicationException e -> String.valueOf(e.getResponse().getStatus());
			case BaseServerResponseException e -> String.valueOf(e.getStatusCode());

			default -> "400";
		};
	}

	private Bundle transformToReportBundle(ProcessPluginApi api, Bundle searchBundle, Bundle responseBundle,
			Target target, boolean isDryRun)
	{
		Bundle report = new Bundle();
		report.setMeta(responseBundle.getMeta());
		report.getMeta().addProfile(ConstantsReport.PROFILE_REPORT_SEARCH_BUNDLE_RESPONSE + "|"
				+ api.getProcessPluginDefinition().getResourceVersion());
		report.getMeta().setLastUpdated(new Date());
		report.setType(responseBundle.getType());

		report.setIdentifier(new Identifier().setSystem(ConstantsReport.NAMINGSYSTEM_CDS_REPORT_IDENTIFIER)
				.setValue(api.getOrganizationProvider().getLocalOrganizationIdentifierValue()
						.orElseThrow(() -> new RuntimeException("LocalOrganizationIdentifierValue empty"))));

		api.getReadAccessHelper().addLocal(report);
		if (!isDryRun)
			api.getReadAccessHelper().addOrganization(report, target.getOrganizationIdentifierValue());

		for (int i = 0; i < searchBundle.getEntry().size(); i++)
		{
			Bundle.BundleEntryComponent responseEntry = responseBundle.getEntry().get(i);
			Bundle.BundleEntryComponent reportEntry = new Bundle.BundleEntryComponent();

			if (responseEntry.getResource() instanceof Bundle || !responseEntry.hasResource())
			{
				toEntryComponentBundleResource(responseEntry, reportEntry,
						searchBundle.getEntry().get(i).getRequest().getUrl());
			}

			if (responseEntry.getResource() instanceof CapabilityStatement)
			{
				toEntryComponentCapabilityStatementResource(responseEntry, reportEntry);
			}

			reportEntry.setResponse(responseEntry.getResponse());
			report.addEntry(reportEntry);
		}

		return report;
	}

	private void toEntryComponentBundleResource(Bundle.BundleEntryComponent responseEntry,
			Bundle.BundleEntryComponent reportEntry, String url)
	{
		Bundle reportEntryBundle = new Bundle();
		reportEntryBundle.getMeta().setLastUpdated(new Date());
		reportEntryBundle.addLink().setRelation("self").setUrl(url);
		reportEntryBundle.setType(Bundle.BundleType.SEARCHSET);
		reportEntryBundle.setTotal(0);

		if (responseEntry.getResource() instanceof Bundle responseEntryBundle)
		{
			responseEntryBundle = flattenBundle(responseEntryBundle);
			reportEntryBundle.setTotal(responseEntryBundle.getTotal());
			reportEntryBundle.getMeta().setLastUpdated(responseEntryBundle.getMeta().getLastUpdated());
		}

		reportEntry.setResource(reportEntryBundle);
	}

	private Bundle flattenBundle(Bundle bundle)
	{
		// the structure of a async response is nested in multiple levels. Therefore this flattening is needed.
		// see http://hl7.org/fhir/R5/async-bundle.html#3.2.6.2.4.0.3
		if (bundle.hasEntry() && bundle.getEntryFirstRep().hasResource()
				&& bundle.getEntryFirstRep().getResource() instanceof Bundle child)
			return flattenBundle(child);
		else
			return bundle;
	}

	private void toEntryComponentCapabilityStatementResource(Bundle.BundleEntryComponent responseEntry,
			Bundle.BundleEntryComponent reportEntry)
	{
		CapabilityStatement responseEntryCapabilityStatement = (CapabilityStatement) responseEntry.getResource();
		CapabilityStatement reportEntryCapabilityStatement = new CapabilityStatement();

		reportEntryCapabilityStatement.setKind(CapabilityStatement.CapabilityStatementKind.CAPABILITY);
		reportEntryCapabilityStatement.setStatus(responseEntryCapabilityStatement.getStatus());
		reportEntryCapabilityStatement.setDate(responseEntryCapabilityStatement.getDate());
		reportEntryCapabilityStatement.setName("Server");

		reportEntryCapabilityStatement.getSoftware().setName(responseEntryCapabilityStatement.getSoftware().getName());
		reportEntryCapabilityStatement.getSoftware()
				.setVersion(responseEntryCapabilityStatement.getSoftware().getVersion());

		reportEntryCapabilityStatement.setFhirVersion(responseEntryCapabilityStatement.getFhirVersion());

		reportEntryCapabilityStatement.setFormat(responseEntryCapabilityStatement.getFormat().stream()
				.filter(f -> "application/fhir+xml".equals(f.getCode()) || "application/fhir+json".equals(f.getCode()))
				.collect(Collectors.toList()));

		for (CapabilityStatement.CapabilityStatementRestComponent oldRestComponent : responseEntryCapabilityStatement
				.getRest())
		{
			List<CapabilityStatement.CapabilityStatementRestResourceComponent> resources = oldRestComponent
					.getResource().stream().map(r -> new CapabilityStatement.CapabilityStatementRestResourceComponent()
							.setType(r.getType()).setSearchParam(removeDocumentation(r.getSearchParam())))
					.toList();

			CapabilityStatement.CapabilityStatementRestComponent newRestComponent = new CapabilityStatement.CapabilityStatementRestComponent()
					.setResource(resources).setMode(oldRestComponent.getMode())
					.setSearchParam(removeDocumentation(oldRestComponent.getSearchParam()));

			reportEntryCapabilityStatement.addRest(newRestComponent);
		}

		reportEntry.setResource(reportEntryCapabilityStatement);
	}

	private List<CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent> removeDocumentation(
			List<CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent> searchParams)
	{
		return searchParams.stream().map(s -> s.setDocumentation(null)).toList();
	}

	private void checkReportBundle(Bundle searchBundle, Bundle reportBundle, String hrpIdentifier)
	{
		int requests = searchBundle.getEntry().size();

		List<String> errorCodes = reportBundle.getEntry().stream().filter(Bundle.BundleEntryComponent::hasResponse)
				.map(Bundle.BundleEntryComponent::getResponse).filter(Bundle.BundleEntryResponseComponent::hasStatus)
				.map(Bundle.BundleEntryResponseComponent::getStatus).filter(s -> !s.contains(RESPONSE_OK)).toList();

		if (errorCodes.size() >= requests)
			throw new RuntimeException(
					"Report Bundle for HRP '" + hrpIdentifier + "' only contains error status codes");
	}

	private String storeReportBundle(ProcessPluginApi api, Bundle responseBundle, String hrpIdentifier, Task task)
	{

		PreferReturnMinimal client = api.getDsfClientProvider().getLocal().withMinimalReturn().withRetry(
				ConstantsBase.DSF_CLIENT_RETRY_6_TIMES,
				DelayStrategy.constant(ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN));

		String localOrganizationIdentifier = api.getOrganizationProvider().getLocalOrganizationIdentifierValue()
				.orElseThrow(() -> new RuntimeException("LocalOrganizationIdentifierValue empty"));
		IdType bundleIdType = client.updateConditionaly(responseBundle, Map.of("identifier", Collections.singletonList(
				ConstantsReport.NAMINGSYSTEM_CDS_REPORT_IDENTIFIER + "|" + localOrganizationIdentifier)));

		String absoluteId = new IdType(api.getEndpointProvider().getLocalEndpointAddress(), ResourceType.Bundle.name(),
				bundleIdType.getIdPart(), bundleIdType.getVersionIdPart()).getValue();

		logger.info("Stored report Bundle '{}' for HRP '{}' and Task '{}'", absoluteId, hrpIdentifier,
				api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));

		return absoluteId;
	}
}
