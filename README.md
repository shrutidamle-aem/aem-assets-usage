# AEM Assets Usage

**Where is this image used?** A *Usage* action for the AEM as a Cloud Service Assets console.
Select one asset, click **Usage**, and get every page, experience fragment and content fragment
that references it, with the published state and direct Edit / View links.

It also covers usages that are easy to miss:

- images inserted inline through the rich-text editor
- assets referenced from content fragment elements
- pages grouped into language masters and MSM live copies
- multifield entries reported as the component that owns them

The examples use the [WKND](https://github.com/adobe/aem-guides-wknd) reference site.

## How it works

```
Usage button ──► GET {asset}.usage.json ──► AssetReferenceResolver ──► JSON ──► Usage panel
                                              ├─ 1. asset properties (exact match)
                                              ├─ 2. rich text (LIKE on "text")
                                              └─ 3. content fragment elements
```

| Piece | Where |
| --- | --- |
| Lookup service | `core/.../impl/AssetReferenceResolverImpl.java` |
| JSON servlet, bound to `dam:Asset` + selector `usage` (no `/bin` path, so nothing to allowlist on AEMaaCS) | `core/.../impl/AssetUsageServlet.java` |
| Usage button: a single node overlaid on the Assets console | `ui.apps/.../apps/dam/gui/content/assets/.content.xml` |
| Usage panel (plain JS + CSS clientlib) | `ui.apps/.../apps/assets-usage/clientlibs/usage` |

Try the endpoint directly on an author instance:

```
/content/dam/wknd-shared/en/adventures/bali-surf-camp/<image>.jpg.usage.json
```

## Build and install

Requires Java 11+ and Maven 3.8+.

```bash
mvn clean install                               # build + unit tests
mvn clean install -PautoInstallSinglePackage    # also install on a local AEM SDK author (localhost:4502)
```

To add the feature to your own project instead, copy the `core` classes and the `ui.apps` content
into your modules and rename the package and clientlib category.

## Configuration

Components that store an asset path in their own property names are invisible to the property
lookup until you list those names. By default only the Core Components properties are searched.
Add yours through an OSGi config:

`io.github.shrutidamle_aem.assetsusage.impl.AssetReferenceResolverImpl.cfg.json`

```json
{
  "assetProperties": ["fileReference", "fileReferences", "imageUrl", "videoPath"]
}
```

## Limitations

- **Indexes:** on large repositories, add Oak property indexes for the asset properties and for
  `text`, or the queries traverse.
- **Content fragments:** the lookup reads at most 500 fragments, and pages that show a fragment
  are not followed (asset → CF → page).
- **Rich text:** `_` in a file name is a `LIKE` wildcard and can over-match in rare cases.
- **Overlay upkeep:** the overlay repeats the console's clientlib list. Re-check it after AEM
  updates.

## License

[Apache License 2.0](LICENSE)
