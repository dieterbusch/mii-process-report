package de.medizininformatik_initiative.process.report.service;

import java.util.Objects;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Target;
import dev.dsf.bpe.v1.variables.Variables;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.process.report.util.SearchQueryCheckService;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Target;
import dev.dsf.bpe.v1.variables.Variables;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CheckSearchBundle extends AbstractServiceDelegate implements InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(CheckSearchBundle.class);

	private final SearchQueryCheckService searchQueryCheckService;

	private boolean reportDistributeAsBroker;
	private String reportWaitBeforeAggregate;

	public CheckSearchBundle(ProcessPluginApi api, SearchQueryCheckService searchQueryCheckService,
			boolean reportDistributeAsBroker, String reportWaitBeforeAggregate)
	{
		super(api);
		this.searchQueryCheckService = searchQueryCheckService;
		this.reportDistributeAsBroker = reportDistributeAsBroker;
		this.reportWaitBeforeAggregate = reportWaitBeforeAggregate;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();
		Objects.requireNonNull(searchQueryCheckService, "searchQueryCheckService");
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		logger.info("CheckSearchBundle doExecute");

		Task task = variables.getStartTask();
		Target target = variables.getTarget();
		Bundle bundle = variables.getResource(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE);

		logger.info("Checking downloaded search Bundle from HRP '{}' as part of Task with id '{}'",
				target.getOrganizationIdentifierValue(), task.getId());

		variables.setBoolean(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_DISTRIBUTION, reportDistributeAsBroker);
		if (reportDistributeAsBroker)
		{
			logger.info("Initiate task for waiting for distributed results from other locations");
		}
		variables.setString(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_DISTRIBUTION_WAIT_AGGREGATE_TIMER_INTERVAL,
				reportWaitBeforeAggregate);
		logger.info("Set the execution interval before the aggregation of the received reports starts to {}",
				reportWaitBeforeAggregate);
		try
		{
			searchQueryCheckService.checkBundle(bundle);

			logger.info(
					"Search Bundle downloaded from HRP '{}' as part of Task with id '{}' contains only valid requests of type GET and valid search params {}",
					target.getOrganizationIdentifierValue(), task.getId(),
					searchQueryCheckService.getValidSearchParams());
		}
		catch (Exception exception)
		{
			logger.warn("Error while checking search Bundle from HRP '{}' in Task with id '{}' - {}",
					target.getOrganizationIdentifierValue(), task.getId(), exception.getMessage());
			throw new RuntimeException(
					"Error while checking search Bundle from HRP '" + target.getOrganizationIdentifierValue()
							+ "' in Task with id '" + task.getId() + "' - " + exception.getMessage(),
					exception);
		}
	}
}
