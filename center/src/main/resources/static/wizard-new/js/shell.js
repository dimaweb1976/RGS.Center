// Presentational navigation only. Device state and commands belong to the backend.
document.addEventListener("DOMContentLoaded", () => {
    const toggle = document.querySelector(".wn-nav-toggle");
    const sidebar = document.querySelector(".wn-sidebar");
    if (toggle && sidebar) {
        toggle.addEventListener("click", () => {
            const open = sidebar.classList.toggle("is-open");
            toggle.setAttribute("aria-expanded", String(open));
        });
    }

    let refreshing = false;
    async function refreshStatus() {
        if (refreshing || document.hidden) return;
        const current = document.getElementById("wn-live-status");
        if (!current) return;
        refreshing = true;
        try {
            const response = await fetch("/wizard-new/status", {
                headers: { "Accept": "text/html" },
                cache: "no-store"
            });
            if (!response.ok) throw new Error(`Status HTTP ${response.status}`);
            const documentFragment = new DOMParser().parseFromString(
                await response.text(), "text/html"
            );
            const updated = documentFragment.getElementById("wn-live-status");
            if (!updated) throw new Error("Status fragment is missing");
            current.replaceWith(updated);
            const badge = document.getElementById("wn-workflow-badge");
            if (badge) {
                badge.querySelector("strong").textContent = updated.dataset.workflowState;
                badge.querySelector("small:last-child").textContent = updated.dataset.workflowDetail;
                badge.title = updated.dataset.workflowDetail;
            }
        } catch {
            const warning = document.getElementById("wn-status-refresh-error");
            if (warning) warning.hidden = false;
        } finally {
            refreshing = false;
        }
    }
    window.setInterval(refreshStatus, 5000);
});
