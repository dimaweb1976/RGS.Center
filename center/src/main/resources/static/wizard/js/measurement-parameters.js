(() => {
    "use strict";

    document.addEventListener("submit", async event => {
        const form = event.target.closest("#wizard-measurement-parameters-form");
        if (!form) return;
        event.preventDefault();

        const button = form.querySelector("button[type='submit']");
        button.disabled = true;
        try {
            const headers = {
                ...(window.rgsCsrfHeaders ? window.rgsCsrfHeaders() : {}),
                ...(window.rgsLockHeaders ? window.rgsLockHeaders() : {})
            };
            const response = await fetch(form.action, {
                method: "POST",
                credentials: "same-origin",
                headers,
                body: new FormData(form)
            });
            if (!response.ok) {
                const message = response.status === 423
                    ? "Exclusive access lock is required to save parameters."
                    : `Saving failed: HTTP ${response.status}`;
                throw new Error(message);
            }
            const panel = document.getElementById("wizard-measurement-form-panel");
            panel.outerHTML = await response.text();
        } catch (error) {
            button.disabled = false;
            window.alert(error.message);
        }
    });
})();
