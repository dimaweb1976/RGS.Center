(() => {
    if (window.rgsAuthStatusInitialized) return;
    window.rgsAuthStatusInitialized = true;

    const lockTokenKey = "rgs.onlyOne.lockToken";

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

    window.rgsLockHeaders = () => {
        const token = window.sessionStorage.getItem(lockTokenKey);
        return token ? {"X-Lock-Token": token} : {};
    };

    function updateLockControls(state, message) {
        document.querySelectorAll(".onlyone-state").forEach(element => {
            element.textContent = message;
            element.className = `onlyone-state ${state}`;
        });
        document.querySelectorAll(".onlyone-password, .onlyone-enter")
            .forEach(element => element.hidden = state === "active");
        document.querySelectorAll(".onlyone-exit")
            .forEach(element => element.hidden = state !== "active");
    }

    function clearLockToken() {
        window.sessionStorage.removeItem(lockTokenKey);
    }

    async function acquireLock() {
        const passwordInput = document.querySelector(".onlyone-password");
        const password = passwordInput?.value || "";
        if (!password) {
            updateLockControls("error", "Введите мастер-пароль");
            return;
        }

        try {
            const response = await fetch("/api/onlyOne/enter", {
                method: "POST",
                credentials: "same-origin",
                headers: {"Content-Type": "application/json", Accept: "application/json"},
                body: JSON.stringify({password})
            });
            if (!response.ok) {
                updateLockControls(
                    response.status === 423 ? "busy" : "error",
                    response.status === 423
                        ? "Монопольный доступ занят"
                        : "Неверный мастер-пароль"
                );
                return;
            }

            const lease = await response.json();
            window.sessionStorage.setItem(lockTokenKey, lease.token);
            passwordInput.value = "";
            updateLockControls("active", "Монопольный доступ активен");
        } catch (error) {
            console.error("Failed to acquire exclusive access", error);
            updateLockControls("error", "Ошибка монопольного доступа");
        }
    }

    async function releaseLock() {
        const headers = window.rgsLockHeaders();
        try {
            if (headers["X-Lock-Token"]) {
                await fetch("/api/onlyOne/exit", {
                    method: "POST",
                    credentials: "same-origin",
                    headers
                });
            }
        } finally {
            clearLockToken();
            updateLockControls("free", "Монопольный доступ свободен");
        }
    }

    async function refreshLock() {
        const headers = window.rgsLockHeaders();
        try {
            if (headers["X-Lock-Token"]) {
                const response = await fetch("/api/onlyOne/checkMe", {
                    cache: "no-store",
                    headers
                });
                if (response.ok) {
                    updateLockControls("active", "Монопольный доступ активен");
                    return;
                }
                clearLockToken();
            }

            const response = await fetch("/api/onlyOne/checkBusy", {cache: "no-store"});
            if (!response.ok) return;
            const status = await response.json();
            updateLockControls(
                status.busy ? "busy" : "free",
                status.busy
                    ? "Монопольный доступ занят"
                    : "Монопольный доступ свободен"
            );
        } catch (error) {
            console.error("Failed to refresh exclusive-access status", error);
        }
    }

    async function extendLock() {
        const headers = window.rgsLockHeaders();
        if (!headers["X-Lock-Token"]) return;
        try {
            const response = await fetch("/api/onlyOne/updateBusy", {
                method: "POST",
                credentials: "same-origin",
                headers
            });
            if (!response.ok) {
                clearLockToken();
                await refreshLock();
            }
        } catch (error) {
            console.error("Failed to extend exclusive access", error);
        }
    }

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

    document.querySelectorAll(".onlyone-enter")
        .forEach(element => element.addEventListener("click", acquireLock));
    document.querySelectorAll(".onlyone-exit")
        .forEach(element => element.addEventListener("click", releaseLock));
    document.querySelectorAll(".auth-status form")
        .forEach(form => form.addEventListener("submit", () => {
            const headers = window.rgsLockHeaders();
            if (!headers["X-Lock-Token"]) return;
            fetch("/api/onlyOne/exit", {
                method: "POST",
                credentials: "same-origin",
                headers,
                keepalive: true
            });
            clearLockToken();
        }));

    refreshConnections();
    refreshLock();
    window.setInterval(refreshConnections, 30000);
    window.setInterval(extendLock, 60000);
})();
