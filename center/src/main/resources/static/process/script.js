import { startSession } from "/common/js/session.js";
import { renderMainHeader } from "/common/js/header.js";

const header = document.getElementById("mainHeader");
const connectionError =
  document.getElementById("connectionError");

async function init() {
  try {
    const active = await startSession({
      heartbeatInterval: 5000,

      onConnectionError() {
        connectionError.textContent =
          "Connection with the spectrometer has been lost.";

        connectionError.hidden = false;
      },

      onConnectionRestored() {
        connectionError.hidden = true;
      }
    });

    if (!active) {
      return;
    }

    renderMainHeader(header);

    // запуск логики конкретно process_screen
  } catch (error) {
    connectionError.textContent =
      "Unable to connect to the spectrometer.";

    connectionError.hidden = false;
  }
}

init();