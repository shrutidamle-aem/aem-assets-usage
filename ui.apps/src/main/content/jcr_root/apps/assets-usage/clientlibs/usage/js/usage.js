(function ($, window, document) {
  "use strict";

  var SELECTOR = ".usage.json";
  var SECTIONS = [
    { type: "PAGE", label: "Pages", badge: "au-badge--page", name: "Page" },
    { type: "EXPERIENCE_FRAGMENT", label: "Experience Fragments", badge: "au-badge--xf", name: "Experience Fragment" },
    { type: "CONTENT_FRAGMENT", label: "Content Fragments", badge: "au-badge--cf", name: "Content Fragment" }
  ];

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }

  function open(assetPath) {
    if (!assetPath) { return; }
    var overlay = document.createElement("div");
    overlay.className = "au-overlay";
    overlay.innerHTML =
      '<div class="au-modal" role="dialog" aria-modal="true">' +
        '<div class="au-head">' +
          '<span class="au-title">Usage</span>' +
          '<span class="au-count"></span>' +
          '<button class="au-x" aria-label="Close">&times;</button>' +
        '</div>' +
        '<div class="au-asset">' + esc(assetPath) + '</div>' +
        '<div class="au-body"><div class="au-empty">Loading references…</div></div>' +
      '</div>';
    document.body.appendChild(overlay);

    function close() {
      overlay.remove();
      document.removeEventListener("keydown", onKey);
    }
    function onKey(e) { if (e.key === "Escape") { close(); } }
    overlay.addEventListener("click", function (e) { if (e.target === overlay) { close(); } });
    overlay.querySelector(".au-x").addEventListener("click", close);
    document.addEventListener("keydown", onKey);

    var body = overlay.querySelector(".au-body");
    var count = overlay.querySelector(".au-count");

    fetch(encodeURI(assetPath + SELECTOR), {
      headers: { Accept: "application/json" }, credentials: "same-origin"
    })
      .then(function (r) { if (!r.ok) { throw new Error("HTTP " + r.status); } return r.json(); })
      .then(function (data) {
        var refs = data.references || [];
        count.textContent = refs.length + (refs.length === 1 ? " reference" : " references");
        body.innerHTML = refs.length ? render(refs) : '<div class="au-empty">This asset is not used anywhere.</div>';
      })
      .catch(function (err) {
        body.innerHTML = '<div class="au-error">Could not load references (' + esc(err.message) + ').</div>';
      });
  }

  function render(refs) {
    return SECTIONS.map(function (s) {
      var rows = refs.filter(function (r) { return r.type === s.type; });
      if (!rows.length) { return ""; }
      var heading = '<div class="au-section-h">' + s.label + " (" + rows.length + ")</div>";

      // Pages come from an MSM setup (language master + rolled-out live copies) --
      // split them into their own sub-groups rather than one flat list.
      if (s.type === "PAGE") {
        var masters = rows.filter(function (r) { return !r.liveCopy; });
        var copies = rows.filter(function (r) { return r.liveCopy; });
        return heading + subgroup("Language Master", masters, s) + subgroup("Live Copies", copies, s);
      }

      return heading + rows.map(function (r) { return card(r, s); }).join("");
    }).join("");
  }

  function subgroup(label, rows, s) {
    if (!rows.length) { return ""; }
    return '<div class="au-subsection-h">' + label + " (" + rows.length + ")</div>" +
      rows.map(function (r) { return card(r, s); }).join("");
  }

  function card(r, s) {
    var pub = r.published
      ? '<span class="au-chip au-chip--pub">Published</span>'
      : '<span class="au-chip">Draft</span>';
    var links = '<div class="au-links">';
    if (r.editUrl) { links += '<a href="' + esc(r.editUrl) + '" target="_blank" rel="noopener">Edit</a>'; }
    if (r.viewUrl) { links += '<a href="' + esc(r.viewUrl) + '" target="_blank" rel="noopener">View</a>'; }
    links += "</div>";
    return '<div class="au-card">' +
      '<div class="au-card-top">' +
        '<span class="au-badge ' + s.badge + '">' + esc(s.name) + "</span>" +
        '<span class="au-card-title">' + esc(r.title || r.containerPath) + "</span>" +
        pub +
      "</div>" +
      '<div class="au-path"><span class="au-label">Path:</span><span class="au-value">' + esc(r.containerPath) + "</span></div>" +
      '<div class="au-loc"><span class="au-label">Component:</span><span class="au-value">' + esc(r.componentType || "") + "</span></div>" +
      links +
    "</div>";
  }

  // Manual trigger (e.g. from the browser console): AssetsUsage.open('/content/dam/.../image.jpg')
  window.AssetsUsage = { open: open };

  // Assets console grid (assets.html): the button lives inside a real
  // foundation-collection selection toolbar there, so it fires through the
  // standard registry API -- the action node's `action="assetsusage.open"`
  // must match the `name` registered here.
  if ($(window).adaptTo) {
    var registry = $(window).adaptTo("foundation-registry");
    if (registry) {
      registry.register("foundation.collection.action.action", {
        name: "assetsusage.open",
        handler: function (name, el, config, collection, selections) {
          var sel = selections && selections[0];
          var path = sel && (sel.dataset
            ? sel.dataset.foundationCollectionItemId
            : sel.getAttribute("data-foundation-collection-item-id"));
          open(path);
        }
      });
    }
  }
})(Granite.$, window, document);
