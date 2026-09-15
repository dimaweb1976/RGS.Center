// Called once at DOMContentLoaded on ALL six pages, including Login.
function init() {
  const isLoginPage = document.body.dataset.page === 'login';

  // Add your token check and REST initialization here.
  // Exclude isLoginPage from the token check to avoid a redirect loop.
  // Example redirect syntax: window.location.replace('/dashboard');
  // Return immediately after a redirect. No token checks or API calls are supplied.

  initializePage();
}
window.addEventListener('DOMContentLoaded', init, { once: true });
