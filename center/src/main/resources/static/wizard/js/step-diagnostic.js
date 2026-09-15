(() => {
    "use strict";

    const base = "/wizard/automatic/step-test";
    const lockTokenKey = "rgs.onlyOne.lockToken";
    let refreshInProgress = false;

    function readCookie(name) {
        const prefix = `${encodeURIComponent(name)}=`;
        return document.cookie.split(";").map(value => value.trim())
            .filter(value => value.startsWith(prefix))
            .map(value => decodeURIComponent(value.substring(prefix.length)))[0] || "";
    }

    function requestHeaders() {
        const headers = {"X-Requested-With": "XMLHttpRequest"};
        const token = window.localStorage.getItem(lockTokenKey)
            || window.sessionStorage.getItem(lockTokenKey)
            || window.sessionStorage.getItem("token");
        const csrf = readCookie("XSRF-TOKEN");
        if (token) headers["X-Lock-Token"] = token;
        if (csrf) headers["X-XSRF-TOKEN"] = csrf;
        return headers;
    }

    async function refresh() {
        if (refreshInProgress) return;
        const panel = document.getElementById("wizard-step-diagnostic");
        if (!panel) return;
        refreshInProgress = true;
        try {
            const response = await fetch(`${base}/status`, {cache: "no-store"});
            if (response.status === 401) {
                window.location.assign("/login");
                return;
            }
            if (!response.ok) throw new Error(`Refresh failed: HTTP ${response.status}`);
            panel.outerHTML = await response.text();
        } finally {
            refreshInProgress = false;
        }
    }

    async function perform(button) {
        if (button.disabled) return;
        const prior = button.textContent;
        button.disabled = true;
        button.textContent = "Please wait…";
        try {
            const action = button.dataset.stepTestAction;
            const params = new URLSearchParams();
            if (action === "step") {
                params.set("sessionId", button.dataset.sessionId);
                params.set("objectNumber", button.dataset.objectNumber);
                params.set("step", button.dataset.step);
                const placement = button.closest("li")?.querySelector("[data-manual-placement]");
                params.set("samplePositionConfirmed", placement?.checked ? "true" : "false");
            } else if (action !== "session") {
                params.set("sessionId", button.dataset.sessionId);
                if (action === "skip-error") {
                    params.set("objectNumber", button.dataset.objectNumber);
                }
            }
            const path = action === "session" ? "/session"
                : action === "skip-error" ? "/step/skip-error"
                : action === "cancel" ? "/scan/cancel" : "/step";
            const response = await fetch(`${base}${path}?${params}`, {
                method: "POST", headers: requestHeaders()
            });
            if (!response.ok) {
                const message = await response.text();
                throw new Error(response.status === 423
                    ? "Exclusive access lock is required for this action."
                    : message || `Action failed: HTTP ${response.status}`);
            }
            await refresh();
        } catch (error) {
            button.disabled = false;
            button.textContent = prior;
            window.alert(error.message);
        }
    }

    document.addEventListener("click", event => {
        const button = event.target.closest("[data-step-test-action]");
        if (button) perform(button);
    });
    window.setInterval(() => {
        const panel = document.getElementById("wizard-step-diagnostic");
        if (panel?.dataset.running === "true" && !document.hidden) {
            refresh().catch(error => console.warn(error.message));
        }
    }, 1000);
})();
