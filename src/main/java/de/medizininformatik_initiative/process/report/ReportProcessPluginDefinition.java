package de.medizininformatik_initiative.process.report;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import de.medizininformatik_initiative.process.report.spring.config.FhirClientConfig;
import de.medizininformatik_initiative.process.report.spring.config.ReportConfig;
import dev.dsf.bpe.v1.ProcessPluginDefinition;

public class ReportProcessPluginDefinition implements ProcessPluginDefinition
{
	public static final String VERSION = "1.2.1.0";
	public static final LocalDate RELEASE_DATE = LocalDate.of(2025, 9, 12);

	@Override
	public String getName()
	{
		return "mii-process-report";
	}

	@Override
	public String getVersion()
	{
		return VERSION;
	}

	@Override
	public LocalDate getReleaseDate()
	{
		return RELEASE_DATE;
	}

	@Override
	public List<String> getProcessModels()
	{
		return List.of("bpe/report-autostart.bpmn", "bpe/report-send.bpmn", "bpe/report-receive.bpmn");
	}

	@Override
	public List<Class<?>> getSpringConfigurations()
	{
		return List.of(ReportConfig.class, FhirClientConfig.class);
	}

	public Map<String, List<String>> getFhirResourcesByProcessId()
	{
		var aAutostart = "fhir/ActivityDefinition/report-autostart.xml";
		var aReceive = "fhir/ActivityDefinition/report-receive.xml";
		var aSend = "fhir/ActivityDefinition/report-send.xml";

		var cReport = "fhir/CodeSystem/report.xml";
		var cReportStatus = "fhir/CodeSystem/report-status.xml";

		var eReportStatusError = "fhir/StructureDefinition/extension-report-status-error.xml";

		var nReportIdent = "fhir/NamingSystem/cds-report-identifier.xml";
		var nSearchBundleIdent = "fhir/NamingSystem/search-bundle-identifier.xml";

		var sAutostartStart = "fhir/StructureDefinition/task-report-autostart-start.xml";
		var sAutostartStop = "fhir/StructureDefinition/task-report-autostart-stop.xml";
		var sReceive = "fhir/StructureDefinition/task-report-receive.xml";
		var sSearchBundle = "fhir/StructureDefinition/search-bundle-report.xml";
		var sSearchBundleResponse = "fhir/StructureDefinition/search-bundle-response-report.xml";
		var sSend = "fhir/StructureDefinition/task-report-send.xml";
		var sSendStart = "fhir/StructureDefinition/task-report-send-start.xml";

		var tAutostartStart = "fhir/Task/task-report-autostart-start.xml";
		var tAutostartStop = "fhir/Task/task-report-autostart-stop.xml";
		var tSendStart = "fhir/Task/task-report-send-start.xml";

		var vReport = "fhir/ValueSet/report.xml";
		var vReportStatusReceive = "fhir/ValueSet/report-status-receive.xml";
		var vReportStatusSend = "fhir/ValueSet/report-status-send.xml";

		return Map.of(ConstantsReport.PROCESS_NAME_FULL_REPORT_AUTOSTART,
				List.of(aAutostart, cReport, sAutostartStart, sAutostartStop, tAutostartStart, tAutostartStop, vReport),
				ConstantsReport.PROCESS_NAME_FULL_REPORT_RECEIVE,
				List.of(aReceive, cReport, cReportStatus, eReportStatusError, nReportIdent, nSearchBundleIdent,
						sSearchBundle, sSearchBundleResponse, sSend, vReport, vReportStatusReceive),
				ConstantsReport.PROCESS_NAME_FULL_REPORT_SEND,
				List.of(aSend, cReport, cReportStatus, eReportStatusError, nReportIdent, nSearchBundleIdent, sReceive,
						sSearchBundle, sSearchBundleResponse, sSendStart, tSendStart, vReport, vReportStatusSend));
	}
}
