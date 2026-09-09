const TOKEN_KEY = "token";
const EXPIRES_KEY = "expiresAt";

let heartbeatTimer = null;

export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY);
}

export function clearSession() {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(EXPIRES_KEY);
}

function redirectToLogin() {
  clearSession();
  window.location.replace("/auth/index.html");
}

export async function checkSession() {
  const token = getToken();

  if (!token) {
    redirectToLogin();
    return false;
  }

  try {
    const response = await fetch("/api/onlyOne/checkMe", {
      method: "GET",
      headers: {
        "X-Lock-Token": token
      },
      cache: "no-store"
    });

    if (response.status === 401) {
      redirectToLogin();
      return false;
    }

    if (!response.ok) {
      throw new Error(`checkMe failed: ${response.status}`);
    }

    const data = await response.json();

    if (!data.active) {
      redirectToLogin();
      return false;
    }

    if (data.expiresAt) {
      sessionStorage.setItem(EXPIRES_KEY, data.expiresAt);
    }

    return true;

  } catch (error) {
    console.error("Session check error:", error);
    throw error;
  }
}

async function heartbeat(onError, onSuccess) {
  const token = getToken();

  if (!token) {
    redirectToLogin();
    return;
  }

  try {
    const response = await fetch("/api/onlyOne/updateBusy", {
      method: "POST",
      headers: {
        "X-Lock-Token": token
      }
    });

    if (response.status === 400 || response.status === 401) {
      redirectToLogin();
      return;
    }

    if (!response.ok) {
      throw new Error(`Heartbeat failed: ${response.status}`);
    }

    const data = await response.json();

    if (data.expiresAt) {
      sessionStorage.setItem(EXPIRES_KEY, data.expiresAt);
    }

    onSuccess?.();

  } catch (error) {
    console.error("Heartbeat error:", error);
    onError?.(error);
  }
}

export async function startSession({
  heartbeatInterval = 5000,
  onConnectionError,
  onConnectionRestored
} = {}) {

  const active = await checkSession();

  if (!active) {
    return false;
  }

  const runHeartbeat = async () => {
    await heartbeat(
      onConnectionError,
      onConnectionRestored
    );

    heartbeatTimer = setTimeout(
      runHeartbeat,
      heartbeatInterval
    );
  };

  runHeartbeat();

  return true;
}

export function stopHeartbeat() {
  if (heartbeatTimer) {
    clearTimeout(heartbeatTimer);
    heartbeatTimer = null;
  }
}