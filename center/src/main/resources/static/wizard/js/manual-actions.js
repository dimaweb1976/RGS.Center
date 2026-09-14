(() => {
    "use strict";

    const lockTokenKey = "rgs.onlyOne.lockToken";
    let requestInProgress = false;

    function readCookie(name) {
        const prefix = `${encodeURIComponent(name)}=`;
        return document.cookie.split(";")
            .map(value => value.trim())
            .filter(value => value.startsWith(prefix))
            .map(value => decodeURIComponent(value.substring(prefix.length)))[0] || "";
    }

    function headers() {
        const result = {"X-Requested-With": "XMLHttpRequest"};
        const token = window.localStorage.getItem(lockTokenKey)
            || window.sessionStorage.getItem(lockTokenKey)
            || window.sessionStorage.getItem("token");
        const csrf = readCookie("XSRF-TOKEN");
        if (token) result["X-Lock-Token"] = token;
        if (csrf) result["X-XSRF-TOKEN"] = csrf;
        return result;
    }

    async function replaceWorkspace(response) {
        if (response.status === 401) {
            window.location.assign("/login");
            return;
        }
        if (!response.ok) {
            throw new Error(response.status === 423
                ? "Exclusive access lock is required for this action."
                : `Manual action failed: HTTP ${response.status}`);
        }
        const workspace = document.getElementById("wizard-manual-workspace");
        if (workspace) workspace.outerHTML = await response.text();
    }

    async function post(path, body, button) {
        if (requestInProgress || button?.disabled) return;
        requestInProgress = true;
        const previousText = button?.textContent;
        if (button) {
            button.disabled = true;
            button.textContent = "Please wait…";
        }
        try {
            const response = await fetch(`/wizard/manual/${path}`, {
                method: "POST",
                headers: headers(),
                body
            });
            await replaceWorkspace(response);
        } catch (error) {
            if (button) {
                button.disabled = false;
                button.textContent = previousText;
            }
            window.alert(error.message);
        } finally {
            requestInProgress = false;
        }
    }

    async function poll() {
        const workspace = document.getElementById("wizard-manual-workspace");
        if (!workspace || workspace.dataset.polling !== "true"
                || requestInProgress || document.hidden) return;
        requestInProgress = true;
        try {
            await replaceWorkspace(await fetch("/wizard/manual/status", {
                headers: {"X-Requested-With": "XMLHttpRequest"},
                cache: "no-store"
            }));
        } catch (error) {
            console.warn(error.message);
        } finally {
            requestInProgress = false;
        }
    }

    document.addEventListener("click", event => {
        const button = event.target.closest("[data-manual-action]");
        if (button) post(button.dataset.manualAction, null, button);
    });
    document.addEventListener("submit", event => {
        const form = event.target.closest("[data-manual-form]");
        if (!form) return;
        event.preventDefault();
        post(form.dataset.manualForm, new URLSearchParams(new FormData(form)),
            form.querySelector("button[type='submit']"));
    });
    window.setInterval(poll, 1000);
})();
