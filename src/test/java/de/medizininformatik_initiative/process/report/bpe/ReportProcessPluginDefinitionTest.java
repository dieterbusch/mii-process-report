package de.medizininformatik_initiative.process.report.bpe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.process.report.ReportProcessPluginDefinition;
import dev.dsf.bpe.v1.ProcessPluginDefinition;

public class ReportProcessPluginDefinitionTest
{
	@Test
	public void testResourceLoading()
	{
		ProcessPluginDefinition definition = new ReportProcessPluginDefinition();
		Map<String, List<String>> resourcesByProcessId = definition.getFhirResourcesByProcessId();

		var reportAutostart = resourcesByProcessId.get(ConstantsReport.PROCESS_NAME_FULL_REPORT_AUTOSTART);
		assertNotNull(reportAutostart);
		assertEquals(7, reportAutostart.stream().filter(this::exists).count());

		var reportReceive = resourcesByProcessId.get(ConstantsReport.PROCESS_NAME_FULL_REPORT_RECEIVE);
		assertNotNull(reportReceive);
		assertEquals(11, reportReceive.stream().filter(this::exists).count());

		var reportSend = resourcesByProcessId.get(ConstantsReport.PROCESS_NAME_FULL_REPORT_SEND);
		assertNotNull(reportSend);
		assertEquals(13, reportSend.stream().filter(this::exists).count());
	}

	private boolean exists(String file)
	{
		return getClass().getClassLoader().getResourceAsStream(file) != null;
	}
}
