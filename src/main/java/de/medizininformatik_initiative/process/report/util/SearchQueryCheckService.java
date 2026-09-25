package de.medizininformatik_initiative.process.report.util;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ResourceType;

public class SearchQueryCheckService
{
	private static final String PIPE_ENCODED = "%7C";

	private static final Pattern MODIFIERS = Pattern.compile(":.*");
	private static final Pattern YEAR_ONLY = Pattern.compile("\\b20\\d{2}(?!\\S)");
	private static final String DATE_EQUALITY_FILTER = "eq";

	private static final String CAPABILITY_STATEMENT_PATH = "metadata";

	private static final String SUMMARY_SEARCH_PARAM = "_summary";
	private static final String SUMMARY_SEARCH_PARAM_VALUE_COUNT = "count";

	private static final String CATEGORY_SEARCH_PARAM = "category";
	private static final String CLASS_SEARCH_PARAM = "class";
	private static final String MII_PROVISION_PROVISION_CODE_TYPE_SEARCH_PARAM = "mii-provision-provision-code-type";
	private static final String STATUS_SEARCH_PARAM = "status";
	private static final String TYPE_SEARCH_PARAM = "type";

	private static final Set<String> ALL_RESOURCE_TYPES = EnumSet.allOf(ResourceType.class).stream()
			.map(ResourceType::name).collect(Collectors.toSet());

	private static final List<String> DATE_SEARCH_PARAMS = List.of("authored", "collected", "date", "effective",
			"effective-time", "issued", "location-period", "occurrence", "onset-date", "period", "recorded-date");
	private static final List<String> TOKEN_SEARCH_PARAMS = List.of("category", "class", "code", "ingredient-code",
			"mii-provision-provision-code-type", "status", "type");
	private static final List<String> OTHER_SEARCH_PARAMS = List.of("_profile", "_summary");
	private static final List<String> VALID_SEARCH_PARAMS = Stream
			.of(DATE_SEARCH_PARAMS.stream(), TOKEN_SEARCH_PARAMS.stream(), OTHER_SEARCH_PARAMS.stream()).flatMap(s -> s)
			.toList();

	public void checkBundle(Bundle bundle)
	{
		List<Bundle.BundleEntryComponent> searches = bundle.getEntry();

		testNoResources(searches);
		testRequestMethod(searches);
		testRequestUrls(searches);
	}

	public List<String> getValidSearchParams()
	{
		return VALID_SEARCH_PARAMS;
	}

	private void testNoResources(List<Bundle.BundleEntryComponent> searches)
	{
		if (searches.stream().map(Bundle.BundleEntryComponent::getResource).anyMatch(Objects::nonNull))
			throw new RuntimeException("Search Bundle contains resources");
	}

	private void testRequestMethod(List<Bundle.BundleEntryComponent> searches)
	{
		long searchesCount = searches.size();
		long httpGetCount = searches.stream().filter(Bundle.BundleEntryComponent::hasRequest)
				.map(Bundle.BundleEntryComponent::getRequest).filter(Bundle.BundleEntryRequestComponent::hasMethod)
				.map(Bundle.BundleEntryRequestComponent::getMethod).filter(Bundle.HTTPVerb.GET::equals).count();

		if (searchesCount != httpGetCount)
			throw new RuntimeException("Search Bundle contains HTTP method other then GET");
	}

	private void testRequestUrls(List<Bundle.BundleEntryComponent> searches)
	{
		int searchesCount = searches.size();
		List<Bundle.BundleEntryRequestComponent> requests = searches.stream()
				.filter(Bundle.BundleEntryComponent::hasRequest).map(Bundle.BundleEntryComponent::getRequest)
				.filter(Bundle.BundleEntryRequestComponent::hasUrl).toList();
		int requestCount = requests.size();

		if (searchesCount != requestCount)
			throw new RuntimeException("Search Bundle contains request without url");

		// "|" is invalid for java.net.URI and is therefore replaces by "%7C"
		List<URI> uris = requests.stream()
				.map(r -> URI.create(URLDecoder.decode(r.getUrl(), StandardCharsets.UTF_8).replace("|", PIPE_ENCODED)))
				.toList();

		testContainsOnlyResourcePath(uris);
		testContainsValidSummaryCount(uris);
		testContainsValidSearchParams(uris);
		testContainsValidDateSearchParams(uris);
		testContainsValidTokenSearchParams(uris);
	}

	private void testContainsOnlyResourcePath(List<URI> uris)
	{
		uris.stream().filter(u -> !CAPABILITY_STATEMENT_PATH.equals(u.getPath())).forEach(this::testPath);
	}

	private void testPath(URI uri)
	{
		String resourceType = uri.getPath();
		if (!ALL_RESOURCE_TYPES.contains(resourceType))
		{
			throw new RuntimeException(
					"Search Bundle contains request url with forbidden path - [" + uri.getPath() + "]");
		}
	}

	private void testContainsValidSummaryCount(List<URI> uris)
	{
		uris.stream().filter(u -> !CAPABILITY_STATEMENT_PATH.equals(u.getPath())).map(URI::getQuery)
				.forEach(this::testSummaryCount);
	}

	private void testSummaryCount(String query)
	{
		List<String> summaryParams = getSummaryParams(query);

		if (summaryParams.isEmpty())
		{
			throw new RuntimeException("Search Bundle contains request url without _summary parameter");
		}

		if (summaryParams.size() > 1)
		{
			throw new RuntimeException("Search Bundle contains request url with more than one _summary parameter");
		}

		if (!SUMMARY_SEARCH_PARAM_VALUE_COUNT.equals(summaryParams.get(0)))
		{
			throw new RuntimeException(
					"Search Bundle contains request url with unexpected _summary parameter value (expected: count, actual: "
							+ summaryParams.get(0) + ")");
		}
	}

	private List<String> getSummaryParams(String query)
	{
		return getKeyValueEntries(query).filter(e -> SUMMARY_SEARCH_PARAM.equals(e.getKey()))
				.flatMap(e -> e.getValue().stream()).toList();
	}

	private Stream<Map.Entry<String, List<String>>> getKeyValueEntries(String query)
	{
		if (query == null)
			return Stream.empty();

		Map<String, List<String>> map = new HashMap<>();

		for (String param : query.split("&"))
		{
			String[] parts = param.split("=", 2);
			String key = parts[0];
			String value = parts.length > 1 ? parts[1] : "";

			for (String v : value.split(","))
			{
				map.computeIfAbsent(key, _ -> new ArrayList<>()).add(v);
			}
		}

		return map.entrySet().stream();
	}

	private void testContainsValidSearchParams(List<URI> uris)
	{
		uris.stream().filter(u -> !CAPABILITY_STATEMENT_PATH.equals(u.getPath())).map(URI::getQuery)
				.forEach(this::testSearchParamNames);
	}

	private void testSearchParamNames(String query)
	{
		if (getKeyValueEntries(query).map(s -> MODIFIERS.matcher(s.getKey()).replaceAll(""))
				.anyMatch(s -> !VALID_SEARCH_PARAMS.contains(s)))
			throw new RuntimeException("Search Bundle contains invalid search params, only allowed search params are "
					+ VALID_SEARCH_PARAMS);
	}

	private void testContainsValidDateSearchParams(List<URI> uris)
	{
		uris.stream().filter(u -> !CAPABILITY_STATEMENT_PATH.equals(u.getPath())).map(URI::getQuery)
				.forEach(this::testSearchParamDateValues);
	}

	private void testSearchParamDateValues(String query)
	{
		List<Map.Entry<String, String>> dateParams = getKeyValueEntries(query)
				.filter(e -> DATE_SEARCH_PARAMS.contains(MODIFIERS.matcher(e.getKey()).replaceAll("")))
				.flatMap(e -> e.getValue().stream().map(v -> Map.entry(e.getKey(), v))).toList();

		List<Map.Entry<String, String>> erroneousDateFilters = dateParams.stream()
				.filter(e -> !e.getValue().startsWith(DATE_EQUALITY_FILTER)).toList();

		if (!erroneousDateFilters.isEmpty())
			throw new RuntimeException(
					"Search Bundle contains date search params not starting with 'eq' - [" + erroneousDateFilters
							.stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(",")) + "]");

		List<Map.Entry<String, String>> erroneousDateValues = dateParams.stream()
				.filter(e -> !YEAR_ONLY.matcher(e.getValue().replace(DATE_EQUALITY_FILTER, "")).matches()).toList();

		if (!erroneousDateValues.isEmpty())
			throw new RuntimeException(
					"Search Bundle contains date search params not limited to a year - [" + erroneousDateValues.stream()
							.map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(",")) + "]");
	}

	private void testContainsValidTokenSearchParams(List<URI> uris)
	{
		uris.stream().filter(u -> !CAPABILITY_STATEMENT_PATH.equals(u.getPath())).map(URI::getQuery)
				.forEach(this::testSearchParamTokenValues);
	}

	private void testSearchParamTokenValues(String query)
	{
		List<Map.Entry<String, String>> codeParams = getKeyValueEntries(query)
				.filter(e -> TOKEN_SEARCH_PARAMS.contains(MODIFIERS.matcher(e.getKey()).replaceAll("")))
				.flatMap(e -> e.getValue().stream().map(v -> Map.entry(e.getKey(), v))).toList();

		List<Map.Entry<String, String>> erroneousCodeValues = codeParams.stream()
				.filter(e -> !e.getValue().endsWith("|")).filter(e -> !isValidException(e.getKey())).toList();

		if (!erroneousCodeValues.isEmpty())
			throw new RuntimeException(
					"Search Bundle contains code search params not limited to system - [" + erroneousCodeValues.stream()
							.map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(",")) + "]");
	}

	private boolean isValidException(String paramName)
	{
		return CATEGORY_SEARCH_PARAM.equals(paramName) || CLASS_SEARCH_PARAM.equals(paramName)
				|| MII_PROVISION_PROVISION_CODE_TYPE_SEARCH_PARAM.equals(paramName)
				|| STATUS_SEARCH_PARAM.equals(paramName) || TYPE_SEARCH_PARAM.equals(paramName);
	}
}
