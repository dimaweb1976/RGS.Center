(() => {
    "use strict";

    const lockTokenKey = "rgs.onlyOne.lockToken";

    function readCookie(name) {
        const prefix = `${encodeURIComponent(name)}=`;
        return document.cookie.split(";")
            .map(value => value.trim())
            .filter(value => value.startsWith(prefix))
            .map(value => decodeURIComponent(value.substring(prefix.length)))[0] || "";
    }

    async function runAction(path, button) {
        const workspace = document.getElementById("wizard-automatic-workspace");
        if (!workspace || button.disabled) return;

        const previousText = button.textContent;
        button.disabled = true;
        button.textContent = "Please wait…";
        try {
            const token = window.localStorage.getItem(lockTokenKey)
                || window.sessionStorage.getItem(lockTokenKey)
                || window.sessionStorage.getItem("token");
            const csrf = readCookie("XSRF-TOKEN");
            const headers = {};
            if (token) headers["X-Lock-Token"] = token;
            if (csrf) headers["X-XSRF-TOKEN"] = csrf;
            const response = await fetch(`/wizard/automatic/${path}`, {
                method: "POST",
                headers
            });
            if (!response.ok) {
                const responseMessage = await response.text();
                const message = response.status === 423
                    ? "Exclusive access lock is required for this action."
                    : responseMessage || `Action failed: HTTP ${response.status}`;
                throw new Error(message);
            }
            if (path === "run/start") {
                window.location.assign("/wizard/automatic/progress");
                return;
            }
            workspace.outerHTML = await response.text();
        } catch (error) {
            button.disabled = false;
            button.textContent = previousText;
            window.alert(error.message);
        }
    }

    document.addEventListener("click", event => {
        const button = event.target.closest("[data-wizard-action]");
        if (button) runAction(button.dataset.wizardAction, button);
    });
})();
