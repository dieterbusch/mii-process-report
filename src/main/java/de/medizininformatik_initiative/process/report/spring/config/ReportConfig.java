package de.medizininformatik_initiative.process.report.spring.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import de.medizininformatik_initiative.process.report.ReportProcessPluginDefinition;
import de.medizininformatik_initiative.process.report.ReportProcessPluginDeploymentStateListener;
import de.medizininformatik_initiative.process.report.message.SendReceipt;
import de.medizininformatik_initiative.process.report.message.SendReport;
import de.medizininformatik_initiative.process.report.message.StartSendReport;
import de.medizininformatik_initiative.process.report.service.CheckSearchBundle;
import de.medizininformatik_initiative.process.report.service.CreateReport;
import de.medizininformatik_initiative.process.report.service.DownloadReport;
import de.medizininformatik_initiative.process.report.service.DownloadSearchBundle;
import de.medizininformatik_initiative.process.report.service.HandleError;
import de.medizininformatik_initiative.process.report.service.InsertReport;
import de.medizininformatik_initiative.process.report.service.LogDryRun;
import de.medizininformatik_initiative.process.report.service.SelectTargetDic;
import de.medizininformatik_initiative.process.report.service.SelectTargetHrp;
import de.medizininformatik_initiative.process.report.service.SetTimer;
import de.medizininformatik_initiative.process.report.service.StoreReceipt;
import de.medizininformatik_initiative.process.report.util.ReportStatusGenerator;
import de.medizininformatik_initiative.process.report.util.SearchQueryCheckService;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.ProcessPluginDeploymentStateListener;
import dev.dsf.bpe.v1.documentation.ProcessDocumentation;

@Configuration
public class ReportConfig
{
	@Autowired
	private ProcessPluginApi api;

	@Autowired
	private FhirClientConfig fhirClientConfig;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_reportSend" }, description = "The identifier of the HRP which should receive the report", recommendation = "Only configure if more than one HRP exists in your network", example = "forschen-fuer-gesundheit.de")
	@Value("${de.medizininformatik.initiative.report.dic.hrp.identifier:#{null}}")
	private String hrpIdentifier;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_reportSend" }, description = "To enable asynchronous request pattern when executing search bundle requests set to `true`")
	@Value("${de.medizininformatik.initiative.report.dic.fhir.server.async.enabled:false}")
	private boolean fhirAsyncEnabled;

	// all Processes

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public ReportStatusGenerator reportStatusGenerator()
	{
		return new ReportStatusGenerator();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public ProcessPluginDeploymentStateListener reportProcessPluginDeploymentStateListener()
	{
		return new ReportProcessPluginDeploymentStateListener(fhirClientConfig.fhirClientFactory());
	}

	// reportAutostart Process

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SetTimer setTimer()
	{
		return new SetTimer(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public StartSendReport startSendReport()
	{
		return new StartSendReport(api);
	}

	// reportSend Process

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SelectTargetHrp selectTargetHrp()
	{
		return new SelectTargetHrp(api, hrpIdentifier);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public DownloadSearchBundle downloadSearchBundle()
	{
		String processVersion = new ReportProcessPluginDefinition().getResourceVersion();
		return new DownloadSearchBundle(api, reportStatusGenerator(), fhirClientConfig.dataLogger(), processVersion);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public CheckSearchBundle checkSearchBundle()
	{
		return new CheckSearchBundle(api, searchQueryCheckService());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
	public SearchQueryCheckService searchQueryCheckService()
	{
		return new SearchQueryCheckService();
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public CreateReport createReport()
	{
		String resourceVersion = new ReportProcessPluginDefinition().getResourceVersion();
		return new CreateReport(api, resourceVersion, fhirClientConfig.fhirClientFactory(), fhirAsyncEnabled,
				fhirClientConfig.dataLogger());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public LogDryRun logDryRun()
	{
		return new LogDryRun(api, reportStatusGenerator());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SendReport sendReport()
	{
		return new SendReport(api, reportStatusGenerator());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public StoreReceipt storeReceipt()
	{
		return new StoreReceipt(api, reportStatusGenerator());
	}

	// reportReceive Process

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public DownloadReport downloadReport()
	{
		return new DownloadReport(api, reportStatusGenerator());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public InsertReport insertReport()
	{
		return new InsertReport(api, reportStatusGenerator());
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public HandleError handleError()
	{
		return new HandleError(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SelectTargetDic selectTargetDic()
	{
		return new SelectTargetDic(api);
	}

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public SendReceipt sendReceipt()
	{
		return new SendReceipt(api, reportStatusGenerator());
	}
}
