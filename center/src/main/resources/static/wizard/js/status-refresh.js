"use strict";

const WIZARD_STATUS_INTERVAL_MILLIS = 3000;

async function refreshWizardStatus() {
    const errorMessage = document.getElementById("wizard-refresh-error");
    try {
        const response = await fetch("/wizard/status", {
            cache: "no-store",
            credentials: "same-origin"
        });
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        const current = document.getElementById("wizard-live-status");
        if (current) {
            current.outerHTML = await response.text();
        }
        if (errorMessage) {
            errorMessage.hidden = true;
        }
    } catch (error) {
        if (errorMessage) {
            errorMessage.hidden = false;
        }
        console.warn("Wizard equipment status refresh failed", error);
    }
}

window.setInterval(refreshWizardStatus, WIZARD_STATUS_INTERVAL_MILLIS);
