package de.medizininformatik_initiative.process.report;

import java.util.Collections;
import java.util.Map;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.dsf.bpe.v2.client.dsf.DsfClient;

public interface SaveOrUpdateBundle
{
	Logger logger = LoggerFactory.getLogger(SaveOrUpdateBundle.class);

	default Resource saveOrUpdate(DsfClient localDsfClient, Bundle bundle, String searchBundleIdentifier)
	{
		Bundle localSearchBundle = searchBundleLocal(localDsfClient, searchBundleIdentifier);

		if (localSearchBundle == null || localSearchBundle.getEntry().isEmpty())
		{
			logger.info("Store report bundle on local dsf fhir server finished. Bundle identifier: {}",
					searchBundleIdentifier);

			return localDsfClient.create(bundle.setId((String) null));
		}
		else if (localSearchBundle.getEntry().iterator().next().getResource() instanceof Bundle innerBundle)
		{
			logger.info("Update report bundle on local dsf fhir server finished. Bundle identifier: {}",
					searchBundleIdentifier);

			bundle.getMeta().setVersionId(innerBundle.getMeta().getVersionId());

			return localDsfClient.update(bundle.setId(innerBundle.getId()));
		}

		return null;
	}

	default Bundle searchBundleLocal(DsfClient localDsfClient, String searchBundleIdentifier)
	{
		return localDsfClient.searchWithStrictHandling(Bundle.class,
				Map.of("identifier", Collections.singletonList(searchBundleIdentifier)));
	}
}