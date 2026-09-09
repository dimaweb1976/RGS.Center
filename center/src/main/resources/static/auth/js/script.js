const formView = document.getElementById("formView");
const busyView = document.getElementById("busyView");
const authForm = document.getElementById("authForm");
const passwordInput = document.getElementById("password");
const errorMessage = document.getElementById("errorMessage");
const busyMessage = document.querySelector(".busyMessage");

const TOKEN_KEY = "token";
const EXPIRES_KEY = "expiresAt";

let busyTimer = null;


function getToken() {
  return sessionStorage.getItem(TOKEN_KEY);
}

function saveSession(token, expiresAt) {
  sessionStorage.setItem(TOKEN_KEY, token);

  if (expiresAt) {
    sessionStorage.setItem(EXPIRES_KEY, expiresAt);
  } else {
    sessionStorage.removeItem(EXPIRES_KEY);
  }
}


function showError(message) {
  errorMessage.textContent = message;
  errorMessage.hidden = false;
}

function clearError() {
  errorMessage.textContent = "";
  errorMessage.hidden = true;
}


async function checkBusy() {
  try {
    const response = await fetch("/api/onlyOne/checkBusy", {
      method: "GET",
      cache: "no-store"
    });

    if (!response.ok) {
      showError(
        `Unable to check system status (${response.status}).`
      );
      return;
    }

    const data = await response.json();

    if (data.busy === true) {
      formView.hidden = true;
      busyView.hidden = false;

      busyMessage.textContent = data.clientIp
        ? `Web interface is already in use from the IP ${data.clientIp}`
        : "Web interface is already in use";

      return;
    }

    formView.hidden = false;
    busyView.hidden = true;

  } catch (error) {
    console.error("checkBusy error:", error);

    showError(
      "Unable to connect to the spectrometer."
    );
  }
}

authForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  clearError();

  const password = passwordInput.value;

  if (!password.trim()) {
    showError("Please enter your password.");
    return;
  }

  try {
    const response = await fetch("/api/onlyOne/enter", {
      method: "POST",

      headers: {
        "Content-Type": "application/json"
      },

      body: JSON.stringify({
        password: password
      })
    });

    const data = await response
      .json()
      .catch(() => null);

    if (response.ok) {
      if (!data?.token) {
        showError(
          "Invalid response from the server."
        );
        return;
      }

      saveSession(
        data.token,
        data.expiresAt
      );

      if (busyTimer !== null) {
        clearInterval(busyTimer);
        busyTimer = null;
      }

      window.location.replace(
        "/process/index.html"
      );

      return;
    }

    if (response.status === 401) {
      showError("Incorrect password.");
      return;
    }

    if (response.status === 423) {
      showError(
        data?.clientIP
          ? `System is busy. Active session IP: ${data.clientIP}`
          : "System is busy."
      );

      await checkBusy();

      return;
    }

    showError(
      `Login failed (${response.status}).`
    );

  } catch (error) {
    console.error("Login error:", error);

    showError(
      "Unable to connect to the spectrometer."
    );
  }
});

passwordInput.addEventListener(
  "input",
  clearError
);

function init() {

  if (getToken()) {
    window.location.replace(
      "/process/index.html"
    );

    return;
  }

  checkBusy();

  busyTimer = setInterval(
    checkBusy,
    5000
  );
}

init();