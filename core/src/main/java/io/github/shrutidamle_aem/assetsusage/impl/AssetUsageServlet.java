package io.github.shrutidamle_aem.assetsusage.impl;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;

import io.github.shrutidamle_aem.assetsusage.AssetReferenceResolver;
import io.github.shrutidamle_aem.assetsusage.Reference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Exposes {@link AssetReferenceResolver} as JSON.
 *
 * <p>Bound by resource type + selector (not a /bin path), so it's called as
 * {@code {assetPath}.usage.json} and needs no path-servlet allowlisting on
 * AEM as a Cloud Service. Example:</p>
 *
 * <pre>/content/dam/wknd-shared/en/adventures/bali-surf-camp/adobestock-175749320.jpg.usage.json</pre>
 */
@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = "dam:Asset",
        selectors = "usage",
        extensions = "json",
        methods = "GET")
public class AssetUsageServlet extends SlingSafeMethodsServlet {

    private static final long serialVersionUID = 1L;
    private final transient ObjectMapper mapper = new ObjectMapper();

    // Fully-qualified to avoid shadowing the assetsusage.Reference POJO
    // (a single-type-import of this annotation would shadow that class name for the whole file).
    @org.osgi.service.component.annotations.Reference
    private transient AssetReferenceResolver referenceResolver;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String assetPath = request.getResource().getPath();
        List<Reference> references = referenceResolver.findReferences(assetPath, request.getResourceResolver());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("asset", assetPath);
        payload.put("count", references.size());
        payload.put("references", references);

        mapper.writeValue(response.getWriter(), payload);
    }
}
