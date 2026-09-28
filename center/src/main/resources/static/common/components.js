    // Shared header, navigation, module indicators and dialogs. No network requests.
    (function(){
     const root=new URL('../',document.currentScript.src);
     window.RGSRoot=root;
     const template=document.createElement('template');
     template.innerHTML = `
      <main id="appView" class="app-shell">

        <header class="topbar">

          <div class="brand">
            <img
              class="brand-logo"
              src="Common/assets/logo.svg"
              alt="Baltic Scientific Instruments"
            >

            <span class="product-title">
              <strong>RGS CENTER</strong>

              <small class="interface-version">
                Robotic Gamma-Ray Spectrometer · Control Web Interface v.12
              </small>
            </span>
          </div>


          <div
            id="headerProcess"
            class="header-process"
            role="group"
            aria-label="Active measurement controls"
            hidden
          >

            <button
              id="headerPause"
              class="process-icon"
              type="button"
              aria-label="Pause measurement"
              title="Pause measurement"
            >
              <span aria-hidden="true">Ⅱ</span>
            </button>


            <div class="header-progress-wrap">

              <progress
                id="headerProgress"
                max="100"
                value="0"
                aria-label="Measurement progress"
              ></progress>

              <span id="headerProgressText">
                0%
              </span>

            </div>


            <button
              id="headerStop"
              class="process-icon process-stop"
              type="button"
              aria-label="Stop measurement"
              title="Stop measurement"
            >
              <span aria-hidden="true">■</span>
            </button>

          </div>


          <div class="ownership">

            <span
                id="robotState"
                class="auto-state gray header-auto-state"
                role="status"
                aria-live="polite"
              >
                <i aria-hidden="true"></i>

                <span>
                  <b id="robotStateLabel">Unknown</b>

                  <small id="robotStatusDescription">-</small>

                  <small
                    id="robotStatusSerial"
                    class="rgs-serial"
                  >
                    -
                  </small>
                </span>
              </span>


            <span
              class="legacy-robot-status"
              hidden
            >
              <b id="robotStatus">
                <span id="robotStatusLabel"></span>
              </b>
            </span>


            <button
              id="logoutBtn"
              class="text-btn"
              type="button"
            >
              Log out
            </button>

          </div>

        </header>


        <div class="workspace">

          <nav
            class="sidenav"
            aria-label="Main navigation"
          >

            <a
              class="nav-item"
              data-view="auto"
              href="automode"
            >
              Auto mode
            </a>

            <a
              class="nav-item"
              data-view="manual"
              href="manualmode"
            >
              Manual mode
            </a>

            <a
              class="nav-item"
              data-view="database"
              href="measurements"
            >
              Measurement database
            </a>

            <a
              class="nav-item"
              data-view="settings"
              href="settings"
            >
              Settings
            </a>

            <a
              class="nav-item"
              data-view="help"
              href="helpandabout"
            >
              Help &amp; About
            </a>

          </nav>


          <section class="content">

            <section
              id="conditionNotice"
              class="condition-notice"
              role="alert"
              hidden
            >

              <div>
                <strong id="conditionTitle">
                  FAULT
                </strong>

                <p id="conditionMessage"></p>
              </div>


              <button
                id="acknowledgeFault"
                type="button"
              >
                Confirm issue resolved
              </button>


              <button
                id="endRecoveryHold"
                type="button"
                hidden
              >
                End paused process
              </button>

            </section>


            <section
              class="system-strip"
              aria-label="Module status"
            >

              <button
                id="indicator_plc"
                class="module ok hv-off"
                type="button"
              >
                <span>PLC</span>
                <b>Undefined</b>
              </button>


              <button
                id="indicator_gammapro"
                class="module ok hv-off"
                type="button"
              >
                <span>GammaPro</span>
                <b>Online</b>
              </button>


              <button
                id="indicator_shield"
                class="module ok hv-off"
                type="button"
              >
                <span>Shield</span>
                <b>Undefined</b>
              </button>


              <button
                id="indicator_detector"
                class="module hv-off"
                type="button"
              >
                <span>Detector HV</span>

                <b
                  id="detectorHvStatus"
                  role="status"
                  aria-live="polite"
                >
                  HV OFF
                </b>
              </button>


              <button
                id="indicator_mca"
                class="module ok"
                type="button"
              >
                <span>MCA</span>
                <b>Online</b>
              </button>


              <button
                id="indicator_cooler"
                class="module ok"
                type="button"
              >
                <span>Cooling system</span>
                <b>Online</b>
              </button>


              <button
                id="viewDiagnostics"
                class="diagnostics-link"
                type="button"
              >
                View diagnostics
                <span aria-hidden="true">→</span>
              </button>

            </section>


            <div id="pageViews"></div>

          </section>

        </div>

      </main>


      <dialog
        id="diagnosticsDialog"
        class="diagnostics-dialog"
        aria-labelledby="diagnosticsTitle"
      >

        <div class="diagnostics-head">

          <h2 id="diagnosticsTitle">
            System diagnostics
          </h2>

          <button
            id="closeDiagnostics"
            type="button"
            aria-label="Close diagnostics"
          >
            ×
          </button>

        </div>


        <div
          id="diagnosticsModules"
          class="diagnostics-modules"
        ></div>

      </dialog>


      <div
        id="hvConfirmModal"
        class="modal"
        hidden
      >

        <div
          class="modal-card"
          role="alertdialog"
          aria-labelledby="hvConfirmTitle"
          aria-describedby="hvConfirmText"
        >

          <p class="eyebrow">
            Detector protection
          </p>


          <h2 id="hvConfirmTitle">
            Turn detector HV OFF?
          </h2>


          <p id="hvConfirmText">
            Measurements will be unavailable until the detector voltage is restored.
          </p>


          <div class="button-row">

            <button
              id="cancelHvOff"
              class="secondary"
              type="button"
            >
              Cancel
            </button>


            <button
              id="confirmHvOff"
              class="danger-outline"
              type="button"
            >
              Turn HV OFF
            </button>

          </div>

        </div>

      </div>
    `;
     const fragment=template.content;
     fragment.querySelectorAll('[src],[href]').forEach(el=>{for(
        const name of ['src','href']){
        const value=el.getAttribute(name);
        if(value&&!value.startsWith('#'))
        el.setAttribute(name,new URL(value,root).href)
    }});
     const content=document.getElementById('pageContent');
     fragment.querySelector('#pageViews').replaceWith(...content.childNodes);
     content.replaceWith(fragment);
    })();
