(() => {
    function readCookie(name) {
        const prefix = `${encodeURIComponent(name)}=`;
        const pair = document.cookie.split("; ")
            .find(cookie => cookie.startsWith(prefix));
        return pair ? decodeURIComponent(pair.substring(prefix.length)) : null;
    }

    window.rgsCsrfHeaders = () => {
        const csrfElement = document.querySelector(".auth-csrf");
        if (!csrfElement) return {};
        // Read the current cookie for every request: another request may have
        // refreshed it after this page rendered, making the HTML value stale.
        const token = readCookie("XSRF-TOKEN") || csrfElement.dataset.token;
        return {[csrfElement.dataset.header]: token};
    };

    async function refreshConnections() {
        try {
            const response = await fetch("/auth/connections", {
                cache: "no-store",
                credentials: "same-origin",
                headers: {Accept: "application/json"}
            });
            if (response.redirected || response.status === 401) {
                window.location.assign("/login");
                return;
            }
            if (!response.ok) return;

            const status = await response.json();
            document.querySelectorAll(".auth-connection-count")
                .forEach(element => element.textContent = status.connected);
            document.querySelectorAll(".auth-connection-warning")
                .forEach(element => element.hidden = !status.warning);
        } catch (error) {
            console.error("Failed to update connection count", error);
        }
    }

    refreshConnections();
    window.setInterval(refreshConnections, 30000);
})();
