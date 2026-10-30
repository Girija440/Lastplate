/**
 * LASTPLATE - Authentication & Access Guard
 */

document.addEventListener('DOMContentLoaded', () => {
  // Check if already authenticated on login/register pages
  const existingUser = getUser();
  const path = window.location.pathname;

  if (existingUser && (path.endsWith('login.html') || path.endsWith('register.html'))) {
    if (existingUser.role === 'ADMIN') {
      window.location.href = '/admin-dashboard.html';
    } else {
      window.location.href = '/student-dashboard.html';
    }
    return;
  }

  // --- LOGIN FORM HANDLER ---
  const loginForm = document.getElementById('login-form');
  if (loginForm) {
    loginForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const submitBtn = loginForm.querySelector('button[type="submit"]');
      const originalText = submitBtn.innerHTML;

      const email = document.getElementById('email').value.trim();
      const password = document.getElementById('password').value;

      if (!email || !password) {
        showToast('Please fill in both email and password.', 'error');
        return;
      }

      try {
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="loading-spinner"></span> Signing in...';

        const res = await apiCall('/login', 'POST', { email, password });

        setToken(res.token);
        setUser(res.user);

        showToast('Welcome back, ' + res.user.name + '!', 'success');

        setTimeout(() => {
          if (res.user.role === 'ADMIN') {
            window.location.href = '/admin-dashboard.html';
          } else {
            window.location.href = '/student-dashboard.html';
          }
        }, 600);

      } catch (err) {
        showToast(err.message, 'error');
        submitBtn.disabled = false;
        submitBtn.innerHTML = originalText;
      }
    });

    // Quick demo autofill buttons
    const demoAdminBtn = document.getElementById('demo-admin-btn');
    if (demoAdminBtn) {
      demoAdminBtn.addEventListener('click', () => {
        document.getElementById('email').value = 'admin@lastplate.com';
        document.getElementById('password').value = 'admin123';
        showToast('Admin credentials filled!', 'info');
      });
    }
  }

  // --- REGISTER FORM HANDLER ---
  const registerForm = document.getElementById('register-form');
  if (registerForm) {
    registerForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const submitBtn = registerForm.querySelector('button[type="submit"]');
      const originalText = submitBtn.innerHTML;

      const name = document.getElementById('name').value.trim();
      const email = document.getElementById('email').value.trim();
      const password = document.getElementById('password').value;
      const confirmPassword = document.getElementById('confirm-password').value;

      if (!name || !email || !password) {
        showToast('All fields are required.', 'error');
        return;
      }

      if (password !== confirmPassword) {
        showToast('Passwords do not match.', 'error');
        return;
      }

      if (password.length < 4) {
        showToast('Password must be at least 4 characters long.', 'error');
        return;
      }

      try {
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="loading-spinner"></span> Creating Account...';

        const res = await apiCall('/register', 'POST', { name, email, password });

        setToken(res.token);
        setUser(res.user);

        showToast('Account created successfully! Welcome to LastPlate.', 'success');

        setTimeout(() => {
          window.location.href = '/student-dashboard.html';
        }, 700);

      } catch (err) {
        showToast(err.message, 'error');
        submitBtn.disabled = false;
        submitBtn.innerHTML = originalText;
      }
    });
  }
});

