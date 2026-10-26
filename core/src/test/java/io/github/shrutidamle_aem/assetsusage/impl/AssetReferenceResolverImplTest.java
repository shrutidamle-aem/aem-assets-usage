package io.github.shrutidamle_aem.assetsusage.impl;

import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.day.cq.replication.ReplicationStatus;
import com.day.cq.search.Predicate;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.msm.api.LiveRelationshipManager;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import io.wcm.testing.mock.aem.junit5.AemContextBuilder;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import io.github.shrutidamle_aem.assetsusage.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(AemContextExtension.class)
class AssetReferenceResolverImplTest {

    private static final String ASSET = "/content/dam/wknd-shared/en/adventures/bali-surf-camp/surfing.jpg";
    private static final String PAGE = "/content/wknd/language-masters/en";
    private static final String CONTAINER = PAGE + "/jcr:content/root/container";
    private static final String RT_CAROUSEL = "wknd/components/carousel";
    private static final String RT_IMAGE = "wknd/components/image";
    private static final String RT_TEXT = "wknd/components/text";

    // Session adapters need a JCR-backed resolver; the default resource-resolver mock has none.
    private final AemContext context = new AemContextBuilder(ResourceResolverType.JCR_MOCK).build();

    private final QueryBuilder queryBuilder = mock(QueryBuilder.class);
    private final LiveRelationshipManager liveRelationshipManager = mock(LiveRelationshipManager.class);
    private final List<Map<String, String>> propertyQueries = new ArrayList<>();
    private final List<String> propertyHits = new ArrayList<>();
    private final List<String> richTextHits = new ArrayList<>();
    private final List<String> fragmentHits = new ArrayList<>();
    private final Map<String, ContentFragment> fragments = new HashMap<>();
    private final Set<String> activated = new HashSet<>();
    private boolean hitsFail;

    private AssetReferenceResolverImpl resolver;

    @BeforeEach
    void setUp() {
        // Answer each of the resolver's three queries by its shape: the rich-text LIKE,
        // the content-fragment listing under /content/dam, or the asset-property lookup.
        when(queryBuilder.createQuery(any(PredicateGroup.class), any(Session.class))).thenAnswer(invocation -> {
            PredicateGroup group = invocation.getArgument(0);
            Map<String, String> properties = new HashMap<>();
            collectPropertyPredicates(group, properties);
            if ("like".equals(operationOf(group))) {
                return queryReturning(richTextHits);
            }
            if (properties.containsKey("jcr:content/contentFragment")) {
                return queryReturning(fragmentHits);
            }
            propertyQueries.add(properties);
            return queryReturning(propertyHits);
        });
        context.registerService(QueryBuilder.class, queryBuilder);
        context.registerService(LiveRelationshipManager.class, liveRelationshipManager);
        context.registerAdapter(Resource.class, ContentFragment.class,
            (Function<Resource, ContentFragment>) resource -> fragments.get(resource.getPath()));
        context.registerAdapter(Resource.class, ReplicationStatus.class,
            (Function<Resource, ReplicationStatus>) resource -> replicationStatus(resource.getPath()));
        context.registerAdapter(Page.class, ReplicationStatus.class,
            (Function<Page, ReplicationStatus>) page -> replicationStatus(page.getPath()));

        context.create().page(PAGE, null, "English");
        resolver = context.registerInjectActivateService(new AssetReferenceResolverImpl());
    }

    @Test
    void looksUpTheAssetInCoreComponentPropertiesByDefault() {
        resolver.findReferences(ASSET, context.resourceResolver());

        assertEquals(1, propertyQueries.size());
        assertEquals(Map.of("fileReference", ASSET, "fileReferences", ASSET), propertyQueries.get(0));
    }

    @Test
    void looksUpTheAssetInConfiguredProperties() {
        AssetReferenceResolverImpl configured = context.registerInjectActivateService(
            new AssetReferenceResolverImpl(), "assetProperties", new String[] {"fileReference", "imageUrl"});

        configured.findReferences(ASSET, context.resourceResolver());

        assertEquals(Map.of("fileReference", ASSET, "imageUrl", ASSET), propertyQueries.get(0));
    }

    @Test
    void skipsThePropertyLookupWhenNoPropertiesAreConfigured() {
        AssetReferenceResolverImpl configured = context.registerInjectActivateService(
            new AssetReferenceResolverImpl(), "assetProperties", new String[0]);

        configured.findReferences(ASSET, context.resourceResolver());

        assertTrue(propertyQueries.isEmpty());
    }

    @Test
    void reportsTheComponentForAPropertyHit() {
        context.create().resource(CONTAINER + "/image", "sling:resourceType", RT_IMAGE, "fileReference", ASSET);
        propertyHits.add(CONTAINER + "/image");

        List<Reference> refs = resolver.findReferences(ASSET, context.resourceResolver());

        assertEquals(1, refs.size());
        Reference ref = refs.get(0);
        assertEquals("PAGE", ref.getType());
        assertEquals("English", ref.getTitle());
        assertEquals(PAGE, ref.getContainerPath());
        assertEquals(CONTAINER + "/image", ref.getComponentPath());
        assertEquals(RT_IMAGE, ref.getComponentType());
        assertEquals("/editor.html" + PAGE + ".html", ref.getEditUrl());
    }

    @Test
    void reportsMultifieldEntriesAsTheirOwningComponentOnce() {
        context.create().resource(CONTAINER + "/carousel", "sling:resourceType", RT_CAROUSEL);
        context.create().resource(CONTAINER + "/carousel/slides");
        context.create().resource(CONTAINER + "/carousel/slides/item0", "fileReference", ASSET);
        context.create().resource(CONTAINER + "/carousel/slides/item1", "fileReference", ASSET);
        propertyHits.add(CONTAINER + "/carousel/slides/item0");
        propertyHits.add(CONTAINER + "/carousel/slides/item1");

        List<Reference> refs = resolver.findReferences(ASSET, context.resourceResolver());

        assertEquals(1, refs.size());
        assertEquals(CONTAINER + "/carousel", refs.get(0).getComponentPath());
        assertEquals(RT_CAROUSEL, refs.get(0).getComponentType());
    }

    @Test
    void reportsExperienceFragmentHitsAsExperienceFragments() {
        String xf = "/content/experience-fragments/wknd/language-masters/en/site/footer/master";
        context.create().page(xf);
        context.create().resource(xf + "/jcr:content/root/image", "sling:resourceType", RT_IMAGE, "fileReference", ASSET);
        propertyHits.add(xf + "/jcr:content/root/image");

        List<Reference> refs = resolver.findReferences(ASSET, context.resourceResolver());

        assertEquals(1, refs.size());
        assertEquals("EXPERIENCE_FRAGMENT", refs.get(0).getType());
        assertEquals(xf, refs.get(0).getContainerPath());
    }

    @Test
    void doesNotReportAComponentTwiceWhenPropertyAndRichTextBothMatch() {
        context.create().resource(CONTAINER + "/text", "sling:resourceType", RT_TEXT,
            "text", "<p><img src=\"" + ASSET + "\"></p>", "fileReference", ASSET);
        propertyHits.add(CONTAINER + "/text");
        richTextHits.add(CONTAINER + "/text");

        List<Reference> refs = resolver.findReferences(ASSET, context.resourceResolver());

        assertEquals(1, refs.size());
    }

    @Test
    void returnsNothingForABlankAssetPath() {
        assertTrue(resolver.findReferences(null, context.resourceResolver()).isEmpty());
        assertTrue(resolver.findReferences("", context.resourceResolver()).isEmpty());
        verify(queryBuilder, never()).createQuery(any(PredicateGroup.class), any(Session.class));
    }

    @Test
    void returnsNothingWithoutAJcrSession() {
        ResourceResolver noSession = mock(ResourceResolver.class);

        assertTrue(resolver.findReferences(ASSET, noSession).isEmpty());
        verify(queryBuilder, never()).createQuery(any(PredicateGroup.class), any(Session.class));
    }

    @Test
    void returnsNothingWhenTheQueriesFail() {
        context.create().resource(CONTAINER + "/image", "sling:resourceType", RT_IMAGE, "fileReference", ASSET);
        propertyHits.add(CONTAINER + "/image");
        richTextHits.add(CONTAINER + "/image");
        fragmentHits.add("/content/dam/wknd-shared/en/magazine/press");
        hitsFail = true;

        assertTrue(resolver.findReferences(ASSET, context.resourceResolver()).isEmpty());
    }

    @Test
    void reportsPublishedLiveCopies() {
        String liveCopy = "/content/wknd/us/en";
        context.create().page(liveCopy, null, "English (US)");
        context.create().resource(liveCopy + "/jcr:content/root/image", "sling:resourceType", RT_IMAGE, "fileReference", ASSET);
        propertyHits.add(liveCopy + "/jcr:content/root/image");
        activated.add(liveCopy);
        when(liveRelationshipManager.isLiveCopy(any(Resource.class))).thenReturn(true);

        Reference ref = resolver.findReferences(ASSET, context.resourceResolver()).get(0);

        assertTrue(ref.isPublished());
        assertTrue(ref.isLiveCopy());
        assertEquals(liveCopy + ".html", ref.getViewUrl());
    }

    @Test
    void skipsHitsThatAreMissingOrInsideTheDam() {
        context.create().resource("/content/dam/wknd-shared/en/metadata", "fileReference", ASSET);
        propertyHits.add("/content/dam/wknd-shared/en/metadata");
        propertyHits.add(CONTAINER + "/deleted-meanwhile");

        assertTrue(resolver.findReferences(ASSET, context.resourceResolver()).isEmpty());
    }

    @Test
    void reportsAnUntypedHitOnAPageAsItself() {
        context.create().resource(CONTAINER + "/legacy", "fileReference", ASSET);
        propertyHits.add(CONTAINER + "/legacy");

        Reference ref = resolver.findReferences(ASSET, context.resourceResolver()).get(0);

        assertEquals(CONTAINER + "/legacy", ref.getComponentPath());
        assertEquals(PAGE, ref.getContainerPath());
    }

    @Test
    void fallsBackToTheNodeForHitsOutsideAPage() {
        context.create().resource("/content/wknd/loose/node", "fileReference", ASSET);
        propertyHits.add("/content/wknd/loose/node");

        Reference ref = resolver.findReferences(ASSET, context.resourceResolver()).get(0);

        assertEquals("PAGE", ref.getType());
        assertEquals("node", ref.getTitle());
        assertEquals("/content/wknd/loose/node", ref.getContainerPath());
        assertFalse(ref.isPublished());
        assertFalse(ref.isLiveCopy());
    }

    @Test
    void usesThePageNameWhenThePageHasNoTitle() {
        String untitled = "/content/wknd/language-masters/untitled";
        context.create().page(untitled);
        context.create().resource(untitled + "/jcr:content/root/image", "sling:resourceType", RT_IMAGE, "fileReference", ASSET);
        propertyHits.add(untitled + "/jcr:content/root/image");

        assertEquals("untitled", resolver.findReferences(ASSET, context.resourceResolver()).get(0).getTitle());
    }

    @Test
    void reportsContentFragmentsWhoseElementsReferenceTheAsset() {
        String exact = "/content/dam/wknd-shared/en/magazine/press-release";
        String inText = "/content/dam/wknd-shared/en/magazine/announcement";
        String unrelated = "/content/dam/wknd-shared/en/magazine/other";
        String notAFragment = "/content/dam/wknd-shared/en/magazine/plain-asset";
        for (String path : List.of(exact, inText, unrelated, notAFragment)) {
            context.create().resource(path);
        }
        Map<String, String> pressElements = new LinkedHashMap<>();
        pressElements.put("summary", null);
        pressElements.put("heroImage", ASSET);
        fragments.put(exact, fragment("Press release", pressElements));
        fragments.put(inText, fragment(null, Map.of("body", "<p><img src=\"" + ASSET + "\"></p>")));
        fragments.put(unrelated, fragment("Other", Map.of("body", "no images here")));
        activated.add(exact);
        fragmentHits.addAll(List.of(exact, exact, inText, unrelated, notAFragment,
            "/content/dam/wknd-shared/en/magazine/deleted-meanwhile"));

        List<Reference> refs = resolver.findReferences(ASSET, context.resourceResolver());

        assertEquals(2, refs.size());
        Reference press = refs.get(0);
        assertEquals("CONTENT_FRAGMENT", press.getType());
        assertEquals("Press release", press.getTitle());
        assertEquals(exact, press.getContainerPath());
        assertEquals("Content Fragment element: heroImage", press.getComponentType());
        assertEquals("/editor.html" + exact, press.getEditUrl());
        assertNull(press.getViewUrl());
        assertTrue(press.isPublished());
        assertFalse(press.isLiveCopy());

        Reference announcement = refs.get(1);
        assertEquals("announcement", announcement.getTitle());
        assertEquals("Content Fragment element: body", announcement.getComponentType());
        assertFalse(announcement.isPublished());
    }

    private static void collectPropertyPredicates(PredicateGroup group, Map<String, String> into) {
        for (Predicate predicate : group) {
            if (predicate instanceof PredicateGroup) {
                collectPropertyPredicates((PredicateGroup) predicate, into);
            } else if ("property".equals(predicate.getType())) {
                into.put(predicate.get("property"), predicate.get("value"));
            }
        }
    }

    private static String operationOf(PredicateGroup group) {
        for (Predicate predicate : group) {
            if ("property".equals(predicate.getType())) {
                return predicate.get("operation");
            }
        }
        return null;
    }

    private ReplicationStatus replicationStatus(String path) {
        ReplicationStatus status = mock(ReplicationStatus.class);
        when(status.isActivated()).thenReturn(activated.contains(path));
        return status;
    }

    private static ContentFragment fragment(String title, Map<String, String> elements) {
        List<ContentElement> contentElements = new ArrayList<>();
        elements.forEach((name, content) -> {
            ContentElement element = mock(ContentElement.class);
            when(element.getName()).thenReturn(name);
            when(element.getContent()).thenReturn(content);
            contentElements.add(element);
        });
        ContentFragment fragment = mock(ContentFragment.class);
        when(fragment.getTitle()).thenReturn(title);
        when(fragment.getElements()).thenAnswer(invocation -> contentElements.iterator());
        return fragment;
    }

    private Query queryReturning(List<String> paths) throws RepositoryException {
        List<Hit> hits = new ArrayList<>();
        for (String path : paths) {
            Hit hit = mock(Hit.class);
            if (hitsFail) {
                when(hit.getPath()).thenThrow(new RepositoryException("index unavailable"));
            } else {
                when(hit.getPath()).thenReturn(path);
            }
            hits.add(hit);
        }
        SearchResult result = mock(SearchResult.class);
        when(result.getHits()).thenReturn(hits);
        Query query = mock(Query.class);
        when(query.getResult()).thenReturn(result);
        return query;
    }
}
