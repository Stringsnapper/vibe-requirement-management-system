// Minimal progressive enhancement (no build step): show only the fields relevant to the
// selected item type on the new-item form. Everything still works without JS — the server
// ignores fields that don't apply to the chosen type.
(function () {
    "use strict";

    function syncTypeFields() {
        var select = document.getElementById("type-select");
        if (!select) return;
        var type = select.value;
        var fields = document.querySelectorAll(".type-field");
        fields.forEach(function (el) {
            var types = (el.getAttribute("data-types") || "").split(",");
            var show = types.indexOf(type) !== -1;
            el.style.display = show ? "" : "none";
            // Disable hidden inputs so they aren't submitted at all.
            el.querySelectorAll("input, select, textarea").forEach(function (input) {
                input.disabled = !show;
            });
        });
    }

    document.addEventListener("DOMContentLoaded", function () {
        var select = document.getElementById("type-select");
        if (!select) return;
        select.addEventListener("change", syncTypeFields);
        syncTypeFields();
    });
})();
