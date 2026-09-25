package de.medizininformatik_initiative.process.report.service;

import java.util.*;
import java.util.Objects;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.process.report.util.SearchQueryCheckService;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Variables;

public class CheckSearchBundle implements ServiceTask, InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(CheckSearchBundle.class);

	private final SearchQueryCheckService searchQueryCheckService;

	private boolean reportDistributeAsBroker;
	private String reportWaitBeforeAggregate;

	public CheckSearchBundle(SearchQueryCheckService searchQueryCheckService, boolean reportDistributeAsBroker,
			String reportWaitBeforeAggregate)
	{
		this.searchQueryCheckService = searchQueryCheckService;
		this.reportDistributeAsBroker = reportDistributeAsBroker;
		this.reportWaitBeforeAggregate = reportWaitBeforeAggregate;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		Objects.requireNonNull(searchQueryCheckService, "searchQueryCheckService");
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		logger.info("CheckSearchBundle doExecute");

		Task task = variables.getStartTask();
		Target target = variables.getTarget();
		Bundle bundle = variables.getFhirResource(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE);

		logger.info("Checking downloaded search Bundle from HRP '{}' for Task '{}'",
				target.getOrganizationIdentifierValue(), api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));

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
					"Search Bundle downloaded from HRP '{}' contains only valid requests of type GET and valid search params {} for Task '{}' ",
					target.getOrganizationIdentifierValue(), searchQueryCheckService.getValidSearchParams(),
					api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));
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
