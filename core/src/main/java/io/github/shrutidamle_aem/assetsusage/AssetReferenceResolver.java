package io.github.shrutidamle_aem.assetsusage;

import java.util.List;
import org.apache.sling.api.resource.ResourceResolver;

/**
 * Finds everywhere an asset is used: pages, experience fragments and content
 * fragments. The servlet depends only on this interface, so the lookup can be
 * reused (or replaced) without touching the HTTP layer.
 */
public interface AssetReferenceResolver {

    /**
     * @param assetPath absolute DAM path, e.g. {@code /content/dam/wknd-shared/en/adventures/.../image.jpg}
     * @param resolver  caller's resolver (used for the queries and for reading hits)
     * @return every place the asset is referenced; empty list if none
     */
    List<Reference> findReferences(String assetPath, ResourceResolver resolver);
}
