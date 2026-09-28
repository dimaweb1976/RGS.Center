let checkActiveSessionTimer = null;
let updateBusyTimer = null;


async function checkActiveSession() {
    const token = sessionStorage.getItem('onlyOneToken');
    const currentPath = window.location.pathname;
    const isLoginPage = currentPath.includes('/auth/');

    if (!token) {
        if (!isLoginPage) {
            window.location.replace('/auth/index.html');
            return false;
        }

        return true;
    }

    try {
        const response = await fetch('/api/onlyOne/checkMe', {
            method: 'GET',
            headers: {
                'X-Lock-Token': token
            }
        });

        if (!response.ok) {
            sessionStorage.removeItem('onlyOneToken');

            if (!isLoginPage) {
                window.location.replace('/auth/index.html');
                return false;
            }

            return true;
        }

        if (isLoginPage) {
            window.location.replace('/F.automode/index.html');
            return false;
        }

        return true;

    } catch (error) {
        console.error('Session check failed:', error);
        return false;
    }
}

async function updateBusy() {
    const token = sessionStorage.getItem('onlyOneToken');

    if (!token) {
        return;
    }

    try {
        await fetch('/api/onlyOne/updateBusy', {
            method: 'POST',
            headers: {
                'X-Lock-Token': token
            }
        });
    } catch (error) {
        console.error('updateBusy failed:', error);
    }
}


// Called once before all six pages loaded
async function beforeLoad(){
  try{
    const allowed = await checkActiveSession();
    if (allowed) {
       document.documentElement.classList.remove("page-loading");
    }
  }catch(e){
    console.log(e);
  }
}

// Called once at DOMContentLoaded on ALL six pages, including Login.
function afterLoad() {
  const isLoginPage = document.body.dataset.page === 'login';

  // Add your token check and REST initialization here.
  // Exclude isLoginPage from the token check to avoid a redirect loop.
  // Example redirect syntax: window.location.replace('/dashboard');
  // Return immediately after a redirect. No token checks or API calls are supplied.

  initializePage();
}

beforeLoad();

checkActiveSessionTimer = setInterval(
  checkActiveSession,
  1000
);

updateBusy();

updateBusyTimer = setInterval(
    updateBusy,
    60_000
);


window.addEventListener('DOMContentLoaded', afterLoad, { once: true });

