package io.github.shrutidamle_aem.assetsusage;

/**
 * One place an asset is used. Its JSON shape is what the Usage panel consumes,
 * so keep it stable and add fields additively.
 *
 * <p>Public getters are all Jackson needs to serialize this — no annotations.</p>
 */
public class Reference {

    /** PAGE | EXPERIENCE_FRAGMENT | CONTENT_FRAGMENT */
    private final String type;
    /** Human-friendly title of the page / XF / CF. */
    private final String title;
    /** Path of the containing page / XF / CF (what you open). */
    private final String containerPath;
    /** Exact node that holds the reference (which component on the page). */
    private final String componentPath;
    /** Resource type (or CF element) that holds the reference — the "where within". */
    private final String componentType;
    /** Deep link that opens the right editor for this container. */
    private final String editUrl;
    /** Deep link to view/preview the container (null when not applicable, e.g. CF). */
    private final String viewUrl;
    /** True if the container has been activated to publish. */
    private final boolean published;
    /** True if the container (PAGE only) is an MSM live copy, not the language master. */
    private final boolean liveCopy;

    public Reference(String type, String title, String containerPath, String componentPath,
                     String componentType, String editUrl, String viewUrl, boolean published) {
        this(type, title, containerPath, componentPath, componentType, editUrl, viewUrl, published, false);
    }

    public Reference(String type, String title, String containerPath, String componentPath,
                     String componentType, String editUrl, String viewUrl, boolean published, boolean liveCopy) {
        this.type = type;
        this.title = title;
        this.containerPath = containerPath;
        this.componentPath = componentPath;
        this.componentType = componentType;
        this.editUrl = editUrl;
        this.viewUrl = viewUrl;
        this.published = published;
        this.liveCopy = liveCopy;
    }

    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getContainerPath() { return containerPath; }
    public String getComponentPath() { return componentPath; }
    public String getComponentType() { return componentType; }
    public String getEditUrl() { return editUrl; }
    public String getViewUrl() { return viewUrl; }
    public boolean isPublished() { return published; }
    public boolean isLiveCopy() { return liveCopy; }
}
