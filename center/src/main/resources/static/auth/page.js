// Login UI only. Implement authentication against your own REST API here.
function initializePage() {
  document.getElementById('loginForm').addEventListener('submit', signIn);
}
function signIn(event) {
  event.preventDefault();
  // Add authentication, token handling and successful navigation here.

  // This placeholder does not accept credentials or issue a demo token.
  //const message = document.getElementById('loginMessage');
  //message.textContent = 'Sign-in is not configured.';
  //message.hidden = false;

  window.location.replace('/automode')
}
