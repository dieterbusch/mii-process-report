package de.medizininformatik_initiative.process.report;

import java.util.Collections;
import java.util.Map;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.dsf.fhir.client.FhirWebserviceClient;

public interface SaveOrUpdateBundle
{
	Logger logger = LoggerFactory.getLogger(SaveOrUpdateBundle.class);

	default Resource saveOrUpdate(FhirWebserviceClient localWebserviceClient, Bundle bundle,
			String searchBundleIdentifier)
	{
		Bundle localSearchBundle = searchBundleLocal(localWebserviceClient, searchBundleIdentifier);

		if (localSearchBundle == null || localSearchBundle.getEntry().isEmpty())
		{
			logger.info("Store report bundle on local dsf fhir server finished. Bundle identifier: {}",
					searchBundleIdentifier);
			return localWebserviceClient.create(bundle.setId((String) null));
		}
		else if (localSearchBundle.getEntry().iterator().next().getResource() instanceof Bundle innerBundle)
		{
			logger.info("Update report bundle on local dsf fhir server finished. Bundle identifier: {}",
					searchBundleIdentifier);
			bundle.getMeta().setVersionId(innerBundle.getMeta().getVersionId());
			return localWebserviceClient.update(bundle.setId(innerBundle.getId()));
		}
		return null;
	}

	default Bundle searchBundleLocal(FhirWebserviceClient localWebserviceClient, String searchBundleIdentifier)
	{
		return localWebserviceClient.searchWithStrictHandling(Bundle.class,
				Map.of("identifier", Collections.singletonList(searchBundleIdentifier)));
	}
}
