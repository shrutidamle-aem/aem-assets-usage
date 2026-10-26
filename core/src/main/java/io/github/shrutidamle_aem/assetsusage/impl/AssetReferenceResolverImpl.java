package io.github.shrutidamle_aem.assetsusage.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.shrutidamle_aem.assetsusage.AssetReferenceResolver;
import io.github.shrutidamle_aem.assetsusage.Reference;
import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.day.cq.replication.ReplicationStatus;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.msm.api.LiveRelationshipManager;

@Component(service = AssetReferenceResolver.class)
@Designate(ocd = AssetReferenceResolverImpl.Config.class)
public class AssetReferenceResolverImpl implements AssetReferenceResolver {

    @ObjectClassDefinition(name = "Assets Usage - Reference Resolver")
    public @interface Config {

        /**
         * Properties that hold a DAM asset path as their whole value. {@code fileReference(s)}
         * is the Core Components convention; custom components often use their own names
         * ({@code imageUrl}, {@code videoPath}, ...) and must be added here, or their usages
         * are silently missed.
         */
        @AttributeDefinition(
                name = "Asset properties",
                description = "Component properties whose value is a DAM asset path (exact match).")
        String[] assetProperties() default {"fileReference", "fileReferences"};
    }

    private static final Logger LOG = LoggerFactory.getLogger(AssetReferenceResolverImpl.class);

    private static final String CONTENT_ROOT = "/content";
    private static final String DAM_ROOT = "/content/dam";
    private static final String XF_ROOT = "/content/experience-fragments";

    private static final String TYPE_PAGE = "PAGE";
    private static final String TYPE_XF = "EXPERIENCE_FRAGMENT";
    private static final String TYPE_CF = "CONTENT_FRAGMENT";

    private static final String RESOURCE_TYPE_PROPERTY = "sling:resourceType";

    // Fully-qualified to avoid shadowing the assetsusage.Reference POJO
    // (a single-type-import of this annotation would shadow that class name for the whole file).
    @org.osgi.service.component.annotations.Reference
    private QueryBuilder queryBuilder;

    @org.osgi.service.component.annotations.Reference
    private LiveRelationshipManager liveRelationshipManager;

    private String[] assetProperties;

    @Activate
    @Modified
    void activate(Config config) {
        assetProperties = config.assetProperties();
    }

    @Override
    public List<Reference> findReferences(String assetPath, ResourceResolver resolver) {
        List<Reference> refs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (assetPath == null || assetPath.isEmpty()) {
            return refs;
        }
        findPropertyReferences(assetPath, resolver, refs, seen);
        findRichTextReferences(assetPath, resolver, refs, seen);
        findContentFragmentReferences(assetPath, resolver, refs, seen);
        return refs;
    }

    /**
     * Pages and experience fragments reference assets through one of the configured
     * asset properties (exact match).
     */
    private void findPropertyReferences(String assetPath, ResourceResolver resolver,
                                        List<Reference> refs, Set<String> seen) {
        Session session = resolver.adaptTo(Session.class);
        if (session == null || assetProperties.length == 0) {
            return;
        }
        Map<String, String> map = new HashMap<>();
        map.put("path", CONTENT_ROOT);
        map.put("p.limit", "-1");
        map.put("1_group.p.or", "true");
        for (int i = 0; i < assetProperties.length; i++) {
            String predicate = "1_group." + (i + 1) + "_property";
            map.put(predicate, assetProperties[i]);
            map.put(predicate + ".value", assetPath);
        }

        try {
            Query query = queryBuilder.createQuery(PredicateGroup.create(map), session);
            SearchResult result = query.getResult();
            PageManager pageManager = resolver.adaptTo(PageManager.class);
            for (Hit hit : result.getHits()) {
                Resource res = resolver.getResource(hit.getPath());
                addPageOrXfReference(res, refs, seen, pageManager);
            }
        } catch (RepositoryException e) {
            LOG.error("Query for property references to {} failed", assetPath, e);
        }
    }

    /**
     * Inline images added through the rich-text editor are stored as
     * {@code <img src="/content/dam/...">} inside a Text component's {@code text}
     * property, so exact-property matching can't see them. A LIKE on {@code text}
     * catches them. (Also catches asset paths used as links in rich text, which is
     * still a genuine "usage".)
     *
     * <p>Note: a {@code _} in a file name is a LIKE wildcard, so it could very
     * rarely over-match; acceptable for this tool.</p>
     */
    private void findRichTextReferences(String assetPath, ResourceResolver resolver,
                                        List<Reference> refs, Set<String> seen) {
        Session session = resolver.adaptTo(Session.class);
        if (session == null) {
            return;
        }
        Map<String, String> map = new HashMap<>();
        map.put("path", CONTENT_ROOT);
        map.put("p.limit", "-1");
        map.put("property", "text");
        map.put("property.operation", "like");
        map.put("property.value", "%" + assetPath + "%");

        try {
            Query query = queryBuilder.createQuery(PredicateGroup.create(map), session);
            SearchResult result = query.getResult();
            PageManager pageManager = resolver.adaptTo(PageManager.class);
            for (Hit hit : result.getHits()) {
                Resource res = resolver.getResource(hit.getPath());
                addPageOrXfReference(res, refs, seen, pageManager);
            }
        } catch (RepositoryException e) {
            LOG.error("Rich-text query for references to {} failed", assetPath, e);
        }
    }

    /**
     * Turn a hit node (an image/text component) into a PAGE or EXPERIENCE_FRAGMENT
     * reference. Skips DAM nodes (content fragments are handled separately) and
     * anything already recorded.
     */
    private void addPageOrXfReference(Resource res, List<Reference> refs,
                                      Set<String> seen, PageManager pageManager) {
        if (res == null) {
            return;
        }
        res = owningComponent(res);
        String nodePath = res.getPath();
        if (nodePath.startsWith(DAM_ROOT)) {
            return;
        }
        if (!seen.add(nodePath)) {
            return;
        }
        boolean isXf = nodePath.startsWith(XF_ROOT);
        Page container = pageManager != null ? pageManager.getContainingPage(res) : null;
        String containerPath = container != null ? container.getPath() : nodePath;
        refs.add(new Reference(
                isXf ? TYPE_XF : TYPE_PAGE,
                titleOf(container, res),
                containerPath,
                nodePath,
                res.getResourceType(),
                "/editor.html" + containerPath + ".html",
                containerPath + ".html",
                isPublished(container, res),
                isLiveCopy(container)));
    }

    /**
     * Multifield entries (e.g. {@code slides/item0} of a hero component) have no
     * resource type of their own; report the component that owns them instead.
     * Several entries of one component then collapse into a single reference.
     */
    private Resource owningComponent(Resource res) {
        Resource current = res;
        while (current != null && !current.getValueMap().containsKey(RESOURCE_TYPE_PROPERTY)) {
            if ("jcr:content".equals(current.getName())) {
                return res;
            }
            current = current.getParent();
        }
        return current != null ? current : res;
    }

    /**
     * Distinguishes a language-master (blueprint) page from an MSM live copy, so the
     * UI can group PAGE references accordingly. XFs and standalone (non-MSM) pages
     * simply report {@code false} here.
     */
    private boolean isLiveCopy(Page container) {
        if (container == null || liveRelationshipManager == null) {
            return false;
        }
        Resource containerResource = container.adaptTo(Resource.class);
        return containerResource != null && liveRelationshipManager.isLiveCopy(containerResource);
    }

    /**
     * Content fragments hold image references inside author-named elements, not in
     * {@code fileReference}, so we list content fragments and ask the CF API what
     * each one references.
     */
    private void findContentFragmentReferences(String assetPath, ResourceResolver resolver,
                                               List<Reference> refs, Set<String> seen) {
        Session session = resolver.adaptTo(Session.class);
        if (session == null) {
            return;
        }
        Map<String, String> map = new HashMap<>();
        map.put("path", DAM_ROOT);
        map.put("type", "dam:Asset");
        map.put("property", "jcr:content/contentFragment");
        map.put("property.value", "true");
        map.put("p.limit", "500");

        try {
            Query query = queryBuilder.createQuery(PredicateGroup.create(map), session);
            for (Hit hit : query.getResult().getHits()) {
                Resource cfResource = resolver.getResource(hit.getPath());
                if (cfResource == null) {
                    continue;
                }
                ContentFragment cf = cfResource.adaptTo(ContentFragment.class);
                if (cf == null) {
                    continue;
                }
                String matchedElement = firstElementReferencing(cf, assetPath);
                if (matchedElement == null) {
                    continue;
                }
                String cfPath = cfResource.getPath();
                if (!seen.add(cfPath)) {
                    continue;
                }
                String title = cf.getTitle() != null ? cf.getTitle() : cfResource.getName();
                refs.add(new Reference(
                        TYPE_CF,
                        title,
                        cfPath,
                        cfPath,
                        "Content Fragment element: " + matchedElement,
                        "/editor.html" + cfPath,
                        null,
                        isPublished(null, cfResource)));
            }
        } catch (RepositoryException e) {
            LOG.error("Query for content-fragment references to {} failed", assetPath, e);
        }
    }

    private String firstElementReferencing(ContentFragment cf, String assetPath) {
        Iterator<ContentElement> elements = cf.getElements();
        while (elements.hasNext()) {
            ContentElement element = elements.next();
            String content = element.getContent();
            if (content != null && content.contains(assetPath)) {
                return element.getName();
            }
        }
        return null;
    }

    private String titleOf(Page page, Resource fallback) {
        if (page != null) {
            return page.getTitle() != null ? page.getTitle() : page.getName();
        }
        return fallback.getName();
    }

    private boolean isPublished(Page page, Resource fallback) {
        ReplicationStatus status = page != null
                ? page.adaptTo(ReplicationStatus.class)
                : fallback.adaptTo(ReplicationStatus.class);
        return status != null && status.isActivated();
    }
}
