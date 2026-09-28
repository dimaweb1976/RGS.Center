// Login UI only. Implement authentication against your own REST API here.
function initializePage() {
  document.getElementById('loginForm').addEventListener('submit', signIn);
}
async function signIn(event) {
  event.preventDefault();
  
  const password = document.getElementById('password').value;
  const message = document.getElementById('loginMessage');

  try {
    const response = await fetch('/api/onlyOne/enter', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ password: password })
    });

    if (!response.ok) {
      const errorData = await response.json();
      message.textContent = errorData.error || 'Access denied';
      message.hidden = false;
      return;
    }

    const data = await response.json();
    if (data.token) {
      sessionStorage.setItem('onlyOneToken', data.token);
      window.location.replace('/F.automode/index.html');
    }
  } catch (error) {
    console.error('Sign-in error:', error);
    message.textContent = 'Connection error';
    message.hidden = false;
  }
}
