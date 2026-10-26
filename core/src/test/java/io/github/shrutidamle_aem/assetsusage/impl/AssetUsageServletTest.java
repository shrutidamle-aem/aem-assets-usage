package io.github.shrutidamle_aem.assetsusage.impl;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import io.github.shrutidamle_aem.assetsusage.AssetReferenceResolver;
import io.github.shrutidamle_aem.assetsusage.Reference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(AemContextExtension.class)
class AssetUsageServletTest {

    private static final String ASSET = "/content/dam/wknd-shared/en/adventures/bali-surf-camp/surfing.jpg";

    private final AemContext context = new AemContext();

    @Test
    void writesTheReferencesOfTheRequestedAssetAsJson() throws Exception {
        AssetReferenceResolver referenceResolver = mock(AssetReferenceResolver.class);
        when(referenceResolver.findReferences(eq(ASSET), any())).thenReturn(List.of(new Reference(
            "PAGE", "English", "/content/wknd/language-masters/en",
            "/content/wknd/language-masters/en/jcr:content/root/image",
            "wknd/components/image",
            "/editor.html/content/wknd/language-masters/en.html",
            "/content/wknd/language-masters/en.html", true, false)));
        context.registerService(AssetReferenceResolver.class, referenceResolver);
        AssetUsageServlet servlet = context.registerInjectActivateService(new AssetUsageServlet());
        context.currentResource(context.create().resource(ASSET, "jcr:primaryType", "dam:Asset"));

        servlet.doGet(context.request(), context.response());

        assertEquals("application/json;charset=UTF-8", context.response().getContentType());
        assertEquals("{\"asset\":\"" + ASSET + "\",\"count\":1,\"references\":[{"
                + "\"type\":\"PAGE\",\"title\":\"English\","
                + "\"containerPath\":\"/content/wknd/language-masters/en\","
                + "\"componentPath\":\"/content/wknd/language-masters/en/jcr:content/root/image\","
                + "\"componentType\":\"wknd/components/image\","
                + "\"editUrl\":\"/editor.html/content/wknd/language-masters/en.html\","
                + "\"viewUrl\":\"/content/wknd/language-masters/en.html\","
                + "\"published\":true,\"liveCopy\":false}]}",
            context.response().getOutputAsString());
    }
}
