package de.medizininformatik_initiative.process.report.fhir.profile;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.ValidationResult;
import de.medizininformatik_initiative.process.report.ReportProcessPluginDefinition;
import dev.dsf.fhir.validation.ResourceValidator;
import dev.dsf.fhir.validation.ResourceValidatorImpl;
import dev.dsf.fhir.validation.ValidationSupportRule;

public class SearchBundleProfileTest
{
	private static final Logger logger = LoggerFactory.getLogger(TaskProfileTest.class);
	private static final ReportProcessPluginDefinition def = new ReportProcessPluginDefinition();

	@ClassRule
	public static final ValidationSupportRule validationRule = new ValidationSupportRule(def.getResourceVersion(),
			def.getResourceReleaseDate(), List.of("search-bundle-report.xml", "search-bundle-response-report.xml"),
			List.of("report.xml", "report-status.xml"),
			List.of("report.xml", "report-status-receive.xml", "report-status-send.xml"));

	private final ResourceValidator resourceValidator = new ResourceValidatorImpl(validationRule.getFhirContext(),
			validationRule.getValidationSupport());

	@Test
	public void testSearchBundleProfileValid()
	{
		Bundle bundle = readBundle("/fhir/Bundle/search-bundle-valid.xml");

		ValidationResult result = resourceValidator.validate(bundle);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	@Test
	public void testSearchBundleResponseProfileValid()
	{
		Bundle bundle = readBundle("/fhir/Bundle/search-bundle-response-valid.xml");

		ValidationResult result = resourceValidator.validate(bundle);
		ValidationSupportRule.logValidationMessages(logger, result);

		assertEquals(0, result.getMessages().stream().filter(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
				|| ResultSeverityEnum.FATAL.equals(m.getSeverity())).count());
	}

	private Bundle readBundle(String path)
	{
		try (InputStream in = getClass().getResourceAsStream(path))
		{
			return FhirContext.forR4().newXmlParser().parseResource(Bundle.class, in);
		}
		catch (Exception exception)
		{
			throw new RuntimeException("Reading bundle from path '" + path + "' failed", exception);
		}
	}
}
