// Single user connection check, token management, and heartbeat

async function checkActiveSession() {
  const token = sessionStorage.getItem('onlyOneToken');
  const currentPath = window.location.pathname;
  const isLoginPage = currentPath.includes('/auth/');

  if (!token) {
    if (!isLoginPage) {
      window.location.href = '/auth/index.html';
    }
    return;
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
        window.location.href = '/auth/index.html';
      }
    } else if (isLoginPage) {
      window.location.href = '/F.automode/index.html';
    }
  } catch (error) {
    console.error('Session check failed:', error);
  }
}

function startHeartbeat() {
  const token = sessionStorage.getItem('onlyOneToken');
  if (!token) return;

  setInterval(async () => {
    try {
      const response = await fetch('/api/onlyOne/updateBusy', {
        method: 'POST',
        headers: {
          'X-Lock-Token': token
        }
      });

      if (!response.ok) {
        sessionStorage.removeItem('onlyOneToken');
        window.location.href = '/auth/index.html';
      }
    } catch (error) {
      console.error('Heartbeat failed:', error);
    }
  }, 60000);
}

document.addEventListener('DOMContentLoaded', () => {
  checkActiveSession();
  const currentPath = window.location.pathname;
  if (!currentPath.includes('auth') && !currentPath.endsWith('index.html')) {
    startHeartbeat();
  }
});

async function performLogout() {
  const token = sessionStorage.getItem('onlyOneToken');
  if (token) {
    try {
      await fetch('/api/onlyOne/exit', {
        method: 'POST',
        headers: {
          'X-Lock-Token': token
        }
      });
    } catch (error) {
      console.error('Logout request failed:', error);
    }
    sessionStorage.removeItem('onlyOneToken');
  }
  window.location.href = '/auth/index.html';
}

document.addEventListener('click', (e) => {
  const logoutBtn = e.target.closest('#logoutBtn');
  if (logoutBtn) {
    e.preventDefault();
    performLogout();
  }
});