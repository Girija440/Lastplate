/**
 * LASTPLATE - Global Utilities & Shared API Client
 */

const API_BASE = '/api';

// --- Session & Token Helpers ---
function getToken() {
  return localStorage.getItem('lp_token');
}

function setToken(token) {
  if (token) {
    localStorage.setItem('lp_token', token);
  } else {
    localStorage.removeItem('lp_token');
  }
}

function getUser() {
  const u = localStorage.getItem('lp_user');
  if (!u) return null;
  try {
    return JSON.parse(u);
  } catch (e) {
    return null;
  }
}

function setUser(user) {
  if (user) {
    localStorage.setItem('lp_user', JSON.stringify(user));
  } else {
    localStorage.removeItem('lp_user');
  }
}

function clearSession() {
  localStorage.removeItem('lp_token');
  localStorage.removeItem('lp_user');
}

// --- Unified API Call Function ---
async function apiCall(endpoint, method = 'GET', body = null) {
  const headers = {
    'Content-Type': 'application/json',
  };

  const token = getToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
    headers['X-Session-Token'] = token;
  }

  const options = {
    method: method.toUpperCase(),
    headers: headers,
  };

  if (body && ['POST', 'PUT', 'PATCH'].includes(options.method)) {
    options.body = JSON.stringify(body);
  }

  try {
    const res = await fetch(`${API_BASE}${endpoint}`, options);
    const contentType = res.headers.get('content-type') || '';
    let data;

    if (contentType.includes('application/json')) {
      data = await res.json();
    } else {
      data = { text: await res.text() };
    }

    if (!res.ok) {
      const errMsg = (data && (data.error || data.message)) || `Server returned ${res.status}`;
      // If unauthorized, redirect to login if not already on login/register/index
      if (res.status === 401) {
        const path = window.location.pathname;
        if (!path.includes('login.html') && !path.includes('register.html') && !path.endsWith('/') && !path.includes('index.html')) {
          clearSession();
          window.location.href = '/login.html';
        }
      }
      throw new Error(errMsg);
    }

    return data;
  } catch (err) {
    console.error(`[API ${method} ${endpoint}] error:`, err);
    throw err;
  }
}

// --- Toast Notifications ---
function showToast(message, type = 'info') {
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    container.className = 'toast-container';
    document.body.appendChild(container);
  }

  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;

  const icon = type === 'success' ? '✓' : type === 'error' ? '✕' : 'ℹ';
  toast.innerHTML = `<span style="font-weight: bold; font-size: 1.1rem;">${icon}</span> <span>${message}</span>`;

  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(10px)';
    toast.style.transition = 'all 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

// --- Formatting Helpers ---
function formatCurrency(amount) {
  const num = parseFloat(amount) || 0;
  return '₹' + num.toFixed(2);
}

function formatDate(dateStr) {
  if (!dateStr) return '—';
  try {
    const d = new Date(dateStr.replace(' ', 'T'));
    if (isNaN(d.getTime())) return dateStr;
    return d.toLocaleString('en-IN', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  } catch (e) {
    return dateStr;
  }
}

function renderStatusBadge(status) {
  const s = (status || '').toUpperCase();
  switch (s) {
    case 'APPROVED':
      return `<span class="badge badge-approved">● Approved</span>`;
    case 'PENDING':
      return `<span class="badge badge-pending">⏱ Pending Approval</span>`;
    case 'REJECTED':
      return `<span class="badge badge-rejected">✕ Rejected</span>`;
    case 'CANCELLED':
      return `<span class="badge badge-cancelled">⊘ Cancelled</span>`;
    default:
      return `<span class="badge badge-cancelled">${status}</span>`;
  }
}

// --- Global User & Logout Initializer ---
async function initGlobalNav() {
  const user = getUser();
  const userNameElem = document.getElementById('user-display-name');
  const userRoleElem = document.getElementById('user-display-role');
  const userInitialElem = document.getElementById('user-display-initial');

  if (user && userNameElem) {
    userNameElem.textContent = user.name;
    if (userRoleElem) userRoleElem.textContent = user.role;
    if (userInitialElem) userInitialElem.textContent = (user.name || 'U').charAt(0).toUpperCase();
  }

  const logoutBtns = document.querySelectorAll('.logout-btn');
  logoutBtns.forEach(btn => {
    btn.addEventListener('click', async (e) => {
      e.preventDefault();
      try {
        await apiCall('/logout', 'POST');
      } catch (ignored) {}
      clearSession();
      showToast('Logged out successfully', 'info');
      setTimeout(() => {
        window.location.href = '/login.html';
      }, 500);
    });
  });
}

document.addEventListener('DOMContentLoaded', initGlobalNav);

