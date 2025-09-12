package de.medizininformatik_initiative.process.report;

import de.medizininformatik_initiative.processes.common.util.ConstantsBase;

public interface ConstantsReport
{
	String PROCESS_NAME_REPORT_AUTOSTART = "reportAutostart";
	String PROCESS_NAME_REPORT_RECEIVE = "reportReceive";
	String PROCESS_NAME_REPORT_SEND = "reportSend";

	String PROCESS_NAME_FULL_REPORT_AUTOSTART = ConstantsBase.PROCESS_MII_NAME_BASE + PROCESS_NAME_REPORT_AUTOSTART;
	String PROCESS_NAME_FULL_REPORT_RECEIVE = ConstantsBase.PROCESS_MII_NAME_BASE + PROCESS_NAME_REPORT_RECEIVE;
	String PROCESS_NAME_FULL_REPORT_SEND = ConstantsBase.PROCESS_MII_NAME_BASE + PROCESS_NAME_REPORT_SEND;

	String PROFILE_TASK_REPORT_AUTOSTART_START = "http://medizininformatik-initiative.de/fhir/StructureDefinition/task-report-autostart-start";
	String PROFILE_TASK_REPORT_AUTOSTART_START_PROCESS_URI = ConstantsBase.PROCESS_MII_URI_BASE
			+ PROCESS_NAME_REPORT_AUTOSTART;
	String PROFILE_TASK_REPORT_AUTOSTART_START_MESSAGE_NAME = "reportAutostartStart";

	String PROFILE_TASK_REPORT_AUTOSTART_STOP = "http://medizininformatik-initiative.de/fhir/StructureDefinition/task-report-autostart-stop";
	String PROFILE_TASK_REPORT_AUTOSTART_STOP_PROCESS_URI = ConstantsBase.PROCESS_MII_URI_BASE
			+ PROCESS_NAME_REPORT_AUTOSTART;
	String PROFILE_TASK_REPORT_AUTOSTART_STOP_MESSAGE_NAME = "reportAutostartStop";

	String PROFILE_TASK_REPORT_SEND_START = "http://medizininformatik-initiative.de/fhir/StructureDefinition/task-report-send-start";
	String PROFILE_TASK_REPORT_SEND_START_PROCESS_URI = ConstantsBase.PROCESS_MII_URI_BASE + PROCESS_NAME_REPORT_SEND;
	String PROFILE_TASK_REPORT_SEND_START_MESSAGE_NAME = "reportSendStart";

	String PROFILE_TASK_REPORT_SEND = "http://medizininformatik-initiative.de/fhir/StructureDefinition/task-report-send";
	String PROFILE_TASK_REPORT_SEND_PROCESS_URI = ConstantsBase.PROCESS_MII_URI_BASE + PROCESS_NAME_REPORT_SEND;
	String PROFILE_TASK_REPORT_SEND_MESSAGE_NAME = "reportSend";

	String PROFILE_TASK_REPORT_RECEIVE = "http://medizininformatik-initiative.de/fhir/StructureDefinition/task-report-receive";
	String PROFILE_TASK_REPORT_RECEIVE_PROCESS_URI = ConstantsBase.PROCESS_MII_URI_BASE + PROCESS_NAME_REPORT_RECEIVE;
	String PROFILE_TASK_REPORT_RECEIVE_MESSAGE_NAME = "reportReceive";

	String BPMN_EXECUTION_VARIABLE_REPORT_TIMER_INTERVAL = "reportTimerInterval";
	String BPMN_EXECUTION_VARIABLE_REPORT_FIRST_EXECUTION = "reportFirstExecution";
	String BPMN_EXECUTION_VARIABLE_REPORT_FIRST_EXECUTION_DELAYED = "reportFirstExecutionDelayed";
	String BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE = "reportSearchBundle";
	String BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE_RESPONSE_REFERENCE = "reportSearchBundleResponseReference";
	String BPMN_EXECUTION_VARIABLE_REPORT_RECEIVE_ERROR = "reportReceiveError";
	String BPMN_EXECUTION_VARIABLE_REPORT_RECEIVE_ERROR_MESSAGE = "reportReceiveErrorMessage";
	String BPMN_EXECUTION_VARIABLE_IS_DRY_RUN = "isDryRun";

	String CODESYSTEM_REPORT = "http://medizininformatik-initiative.de/fhir/CodeSystem/report";
	String CODESYSTEM_REPORT_VALUE_SEARCH_BUNDLE_RESPONSE_REFERENCE = "search-bundle-response-reference";
	String CODESYSTEM_REPORT_VALUE_REPORT_STATUS = "report-status";
	String CODESYSTEM_REPORT_VALUE_TIMER_INTERVAL = "timer-interval";
	String CODESYSTEM_REPORT_VALUE_FIRST_EXECUTION = "first-execution";
	String CODESYSTEM_REPORT_VALUE_HRP_IDENTIFIER = "hrp-identifier";
	String CODESYSTEM_REPORT_VALUE_DRY_RUN = "dry-run";

	String CODESYSTEM_REPORT_STATUS = "http://medizininformatik-initiative.de/fhir/CodeSystem/report-status";
	String CODESYSTEM_REPORT_STATUS_VALUE_NOT_ALLOWED = "not-allowed";
	String CODESYSTEM_REPORT_STATUS_VALUE_NOT_REACHABLE = "not-reachable";
	String CODESYSTEM_REPORT_STATUS_VALUE_RECEIPT_MISSING = "receipt-missing";
	String CODESYSTEM_REPORT_STATUS_VALUE_RECEIPT_OK = "receipt-ok";
	String CODESYSTEM_REPORT_STATUS_VALUE_RECEIPT_ERROR = "receipt-error";
	String CODESYSTEM_REPORT_STATUS_VALUE_RECEIVE_OK = "receive-ok";
	String CODESYSTEM_REPORT_STATUS_VALUE_RECEIVE_ERROR = "receive-error";
	String CODESYSTEM_REPORT_STATUS_VALUE_DRY_RUN = "dry-run";

	String VALUESET_REPORT_STATUS_SEND = "http://medizininformatik-initiative.de/fhir/ValueSet/report-status-send";

	String NAMINGSYSTEM_CDS_REPORT_IDENTIFIER = "http://medizininformatik-initiative.de/sid/cds-report-identifier";
	String NAMINGSYSTEM_SEARCH_BUNDLE_IDENTIFIER = "http://medizininformatik-initiative.de/sid/search-bundle-identifier";
	String NAMINGSYSTEM_SEARCH_BUNDLE_IDENTIFIER_VALUE_PREFIX = "search-bundle-v";

	String PROFILE_REPORT_SEARCH_BUNDLE_RESPONSE = "http://medizininformatik-initiative.de/fhir/Bundle/search-bundle-response-report";
	String EXTENSION_REPORT_STATUS_ERROR_URL = "http://medizininformatik-initiative.de/fhir/StructureDefinition/extension-report-status-error";

	String REPORT_TIMER_INTERVAL_DEFAULT_VALUE = "P7D";
}
