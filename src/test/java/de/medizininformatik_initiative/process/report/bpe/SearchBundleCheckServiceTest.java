package de.medizininformatik_initiative.process.report.bpe;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;

import org.hl7.fhir.r4.model.Bundle;
import org.junit.Test;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatik_initiative.process.report.util.SearchQueryCheckService;

public class SearchBundleCheckServiceTest
{
	@Test
	public void testValid()
	{
		testValid("/fhir/Bundle/search-bundle-valid.xml");
	}

	@Test
	public void testValidEncounterType()
	{
		testValid("/fhir/Bundle/search-bundle-valid-encounter-type.xml");
	}

	@Test
	public void testInvalidResource()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-resource.xml", "resources");
	}

	@Test
	public void testInvalidRequestMethod()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-request-method.xml", "GET");
	}

	@Test
	public void testInvalidResourceId()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-resource-id.xml", "request url with forbidden path");
	}

	@Test
	public void testInvalidResourceIdDouble()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-resource-id-double.xml", "request url with forbidden path");
	}

	@Test
	public void testInvalidNoSummary()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-summary-not-exists.xml", "without _summary parameter");
	}

	@Test
	public void testInvalidDoubleSummary()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-summary-double.xml", "more than one _summary parameter");
	}

	@Test
	public void testInvalidSummaryUrlEncoded()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-summary-url-encoded.xml", "invalid search params");
	}

	@Test
	public void testInvalidSummaryUrlEncodedFull()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-summary-url-encoded-full.xml", "invalid search params");
	}

	@Test
	public void testInvalidUnexpectedSummary()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-summary-not-allowed.xml", "unexpected _summary parameter");
	}

	@Test
	public void testInvalidParam()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-param.xml", "invalid search params");
	}

	@Test
	public void testInvalidDateFilter()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-date-filter.xml", "not starting with 'eq'");
	}

	@Test
	public void testInvalidDateValue()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-date-value-single.xml", "not limited to a year");
	}

	@Test
	public void testInvalidDoubleDateValue()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-date-value-double.xml", "not limited to a year");
	}

	@Test
	public void testInvalidSingleCode()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-code-single.xml", "not limited to system");
	}

	@Test
	public void testInvalidDoubleCode()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-code-double.xml", "not limited to system");
	}

	@Test
	public void testInvalidCodeIngredient()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-code-ingredient.xml", "not limited to system");
	}

	private void testValid(String pathToBundle)
	{
		try (InputStream in = getClass().getResourceAsStream(pathToBundle))
		{
			Bundle bundle = FhirContext.forR4().newXmlParser().parseResource(Bundle.class, in);
			new SearchQueryCheckService().checkBundle(bundle);
		}
		catch (Exception exception)
		{
			fail();
		}
	}

	private void testInvalid(String pathToBundle, String errorContains)
	{
		try (InputStream in = getClass().getResourceAsStream(pathToBundle))
		{
			Bundle bundle = FhirContext.forR4().newXmlParser().parseResource(Bundle.class, in);
			new SearchQueryCheckService().checkBundle(bundle);

			// test expected to throw error
			fail();
		}
		catch (Exception exception)
		{
			assertTrue(exception.getMessage().contains(errorContains));
		}
	}
}
