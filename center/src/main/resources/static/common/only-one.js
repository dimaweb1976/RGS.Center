// // Single user connection check, token management, and heartbeat
// function startHeartbeat() {
//   const token = sessionStorage.getItem('onlyOneToken');
//   if (!token) return;

//   setInterval(async () => {
//     try {
//       const response = await fetch('/api/onlyOne/updateBusy', {
//         method: 'POST',
//         headers: {
//           'X-Lock-Token': token
//         }
//       });

//       if (!response.ok) {
//         sessionStorage.removeItem('onlyOneToken');
//         window.location.href = '/auth/index.html';
//       }
//     } catch (error) {
//       console.error('Heartbeat failed:', error);
//     }
//   }, 60000);
// }

// document.addEventListener('DOMContentLoaded', () => {
//   checkActiveSession();
//   const currentPath = window.location.pathname;
//   if (!currentPath.includes('auth') && !currentPath.endsWith('index.html')) {
//     startHeartbeat();
//   }
// });