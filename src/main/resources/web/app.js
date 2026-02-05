// Import htmx
import 'htmx.org';

// Service Worker Registration
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js')
      .then(registration => console.log('Service Worker registered'))
      .catch(error => console.log('Service Worker registration failed:', error));
  });
}

// Offline Indicator
const offlineIndicator = document.getElementById('offline-indicator');

function updateOnlineStatus() {
  if (navigator.onLine) {
    offlineIndicator.classList.remove('visible');
  } else {
    offlineIndicator.classList.add('visible');
  }
}

window.addEventListener('online', updateOnlineStatus);
window.addEventListener('offline', updateOnlineStatus);
updateOnlineStatus();

// htmx Configuration
document.body.addEventListener('htmx:configRequest', (event) => {
  // Add headers for htmx requests
  event.detail.headers['X-Requested-With'] = 'htmx';
});

document.body.addEventListener('htmx:beforeSwap', (event) => {
  // Handle errors from server
  if (event.detail.xhr.status >= 400) {
    console.error('Request failed:', event.detail.xhr);
  }
});

// Show bookmark form function
function showBookmarkForm() {
  htmx.ajax('GET', '/bookmarks/new', {target: '#bookmark-list'});
}
