(() => {
    "use strict";

    let requestInProgress = false;

    function mutationHeaders(json = false) {
        return {
            Accept: "application/json",
            ...(json ? {"Content-Type": "application/json"} : {}),
            ...(window.rgsCsrfHeaders ? window.rgsCsrfHeaders() : {}),
            ...(window.rgsLockHeaders ? window.rgsLockHeaders() : {})
        };
    }

    function showMessage(text, error = false) {
        const message = document.getElementById("wizard-settings-message");
        if (!message) return;
        message.textContent = text;
        message.classList.toggle("is-error", error);
        message.hidden = false;
        message.scrollIntoView({behavior: "smooth", block: "nearest"});
    }

    async function body(response) {
        const text = await response.text();
        if (!text) return {};
        try {
            return JSON.parse(text);
        } catch (ignored) {
            return {message: text};
        }
    }

    async function checked(response) {
        if (response.status === 401) {
            window.location.assign("/login");
            throw new Error("Authentication is required");
        }
        const result = await body(response);
        if (!response.ok) {
            const fallback = response.status === 423
                ? "Acquire exclusive access before changing settings."
                : `Settings request failed: HTTP ${response.status}`;
            throw new Error(result.message || result.error || fallback);
        }
        return result;
    }

    function inputValue(input) {
        if (input.dataset.settingType === "LONG") {
            return Number.parseInt(input.value, 10);
        }
        if (input.dataset.settingType === "DOUBLE") {
            return Number.parseFloat(input.value);
        }
        if (input.dataset.settingType === "BOOLEAN") {
            return input.checked;
        }
        return input.value.trim();
    }

    async function save(form) {
        const inputs = [...form.querySelectorAll("[data-setting-id]")];
        if (!form.reportValidity() || requestInProgress) return;
        const settings = inputs.map(input => ({
            stringId: input.dataset.settingId,
            value: inputValue(input),
            version: Number.parseInt(input.dataset.settingVersion, 10)
        }));
        requestInProgress = true;
        try {
            await checked(await fetch(
                `/api/device-settings/${encodeURIComponent(form.dataset.deviceCode)}`,
                {
                    method: "PUT",
                    credentials: "same-origin",
                    headers: mutationHeaders(true),
                    body: JSON.stringify({settings})
                }
            ));
            showMessage("Settings saved. Apply them to restart this device connection.");
            window.setTimeout(() => window.location.reload(), 500);
        } catch (error) {
            showMessage(error.message, true);
        } finally {
            requestInProgress = false;
        }
    }

    async function apply(button) {
        const form = document.querySelector("[data-settings-form]");
        if (!form || requestInProgress) return;
        requestInProgress = true;
        button.disabled = true;
        try {
            const result = await checked(await fetch(
                `/api/device-settings/${encodeURIComponent(form.dataset.deviceCode)}/apply`,
                {
                    method: "POST",
                    credentials: "same-origin",
                    headers: mutationHeaders()
                }
            ));
            showMessage(result.message || "Connection settings applied.");
            window.setTimeout(() => window.location.reload(), 500);
        } catch (error) {
            showMessage(error.message, true);
            button.disabled = false;
        } finally {
            requestInProgress = false;
        }
    }

    document.addEventListener("submit", event => {
        const form = event.target.closest("[data-settings-form]");
        if (!form) return;
        event.preventDefault();
        save(form);
    });

    document.addEventListener("click", event => {
        const button = event.target.closest("[data-settings-apply]");
        if (button) apply(button);
    });
})();
