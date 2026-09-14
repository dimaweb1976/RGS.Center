(() => {
    "use strict";

    const lockTokenKey = "rgs.onlyOne.lockToken";
    let refreshInProgress = false;

    function readCookie(name) {
        const prefix = `${encodeURIComponent(name)}=`;
        return document.cookie.split(";")
            .map(value => value.trim())
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

    async function refreshMonitor() {
        if (refreshInProgress || document.hidden) return;
        const monitor = document.getElementById("wizard-run-monitor");
        if (!monitor) return;
        refreshInProgress = true;
        try {
            const response = await fetch("/wizard/automatic/run/status", {
                headers: {"X-Requested-With": "XMLHttpRequest"},
                cache: "no-store"
            });
            if (response.status === 401) {
                window.location.assign("/login");
                return;
            }
            if (!response.ok) throw new Error(`Monitor refresh failed: HTTP ${response.status}`);
            monitor.outerHTML = await response.text();
        } catch (error) {
            console.warn(error.message);
        } finally {
            refreshInProgress = false;
        }
    }

    async function control(action, button) {
        if (button.disabled) return;
        const previousText = button.textContent;
        button.disabled = true;
        button.textContent = "Please wait…";
        try {
            const response = await fetch(`/wizard/automatic/run/${action}`, {
                method: "POST",
                headers: requestHeaders()
            });
            if (!response.ok) {
                const responseMessage = await response.text();
                throw new Error(response.status === 423
                    ? "Exclusive access lock is required for this action."
                    : responseMessage || `Action failed: HTTP ${response.status}`);
            }
            await refreshMonitor();
        } catch (error) {
            button.disabled = false;
            button.textContent = previousText;
            window.alert(error.message);
        }
    }

    document.addEventListener("click", event => {
        const button = event.target.closest("[data-workflow-action]");
        if (button) control(button.dataset.workflowAction, button);
    });
    document.addEventListener("visibilitychange", () => {
        if (!document.hidden) refreshMonitor();
    });
    window.setInterval(refreshMonitor, 1000);
})();
