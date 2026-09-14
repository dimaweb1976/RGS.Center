(() => {
    "use strict";

    let requestInProgress = false;

    function commandHeaders() {
        return {
            "X-Requested-With": "XMLHttpRequest",
            ...(window.rgsCsrfHeaders ? window.rgsCsrfHeaders() : {}),
            ...(window.rgsLockHeaders ? window.rgsLockHeaders() : {})
        };
    }

    async function replaceContent(response) {
        if (response.status === 401) {
            window.location.assign("/login");
            return;
        }
        if (!response.ok) {
            throw new Error(response.status === 423
                ? "Exclusive access lock is required for this command."
                : `Service request failed: HTTP ${response.status}`);
        }
        const current = document.getElementById("wizard-service-content");
        if (current) current.outerHTML = await response.text();
    }

    async function refresh() {
        const current = document.getElementById("wizard-service-content");
        const url = current?.dataset.serviceRefreshUrl;
        if (!url || requestInProgress || document.hidden) return;
        requestInProgress = true;
        try {
            await replaceContent(await fetch(url, {
                cache: "no-store",
                credentials: "same-origin",
                headers: {"X-Requested-With": "XMLHttpRequest"}
            }));
        } catch (error) {
            console.warn("Service device refresh failed", error);
        } finally {
            requestInProgress = false;
        }
    }

    async function execute(button) {
        const path = button.dataset.serviceAction;
        if (!path || requestInProgress || button.disabled) return;
        requestInProgress = true;
        const label = button.textContent;
        button.disabled = true;
        button.textContent = "Please wait...";
        try {
            await replaceContent(await fetch(path, {
                method: "POST",
                credentials: "same-origin",
                headers: commandHeaders()
            }));
        } catch (error) {
            button.disabled = false;
            button.textContent = label;
            window.alert(error.message);
        } finally {
            requestInProgress = false;
        }
    }

    document.addEventListener("click", event => {
        const button = event.target.closest("[data-service-action]");
        if (button) execute(button);
    });
    window.setInterval(refresh, 3000);
})();
