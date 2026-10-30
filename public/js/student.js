/**
 * LASTPLATE - Student Frontend Operations
 * Supports Student Dashboard, Menu Browsing, and Personal Reservations
 */

document.addEventListener('DOMContentLoaded', () => {
  // Ensure user is logged in
  const user = getUser();
  if (!user) {
    window.location.href = '/login.html';
    return;
  }

  // Route to page specific initializers
  const path = window.location.pathname;
  if (path.includes('student-dashboard.html')) {
    initStudentDashboard();
  } else if (path.includes('menu.html')) {
    initMenuPage();
  } else if (path.includes('reservations.html')) {
    initReservationsPage();
  }
});

// ===================================================================
// STUDENT DASHBOARD
// ===================================================================
async function initStudentDashboard() {
  const statsContainer = document.getElementById('dashboard-stats');
  const recentTableBody = document.getElementById('recent-reservations-body');

  try {
    const data = await apiCall('/student/dashboard');

    // Populate stat counters
    document.getElementById('stat-total-res').textContent = data.totalReservations || 0;
    document.getElementById('stat-pending-res').textContent = data.pendingCount || 0;
    document.getElementById('stat-approved-res').textContent = data.approvedCount || 0;
    document.getElementById('stat-total-spent').textContent = formatCurrency(data.totalSpent || 0);

    const availableBadge = document.getElementById('stat-available-foods');
    if (availableBadge) {
      availableBadge.textContent = `${data.availableFoodsCount || 0} items currently ready in cafeteria`;
    }

    // Populate recent reservations
    const reservations = data.recentReservations || [];
    if (reservations.length === 0) {
      recentTableBody.innerHTML = `
        <tr>
          <td colspan="6" class="empty-state">
            <div class="empty-state-icon">🍽️</div>
            <p>No food reservations yet. Explore the campus cafeteria menu!</p>
            <a href="/menu.html" class="btn btn-primary btn-sm" style="margin-top: 0.75rem;">View Menu</a>
          </td>
        </tr>`;
      return;
    }

    recentTableBody.innerHTML = reservations.map(r => {
      const firstItem = (r.items && r.items[0]) ? r.items[0] : null;
      const itemName = firstItem ? `${firstItem.foodName} (×${firstItem.quantity})` : 'Food Item';
      const canCancel = (r.status === 'PENDING' || r.status === 'APPROVED');

      return `
        <tr>
          <td><strong>#RES-${r.reservationId}</strong></td>
          <td>${itemName}</td>
          <td><strong>${formatCurrency(r.totalAmount)}</strong></td>
          <td>${renderStatusBadge(r.status)}</td>
          <td>${formatDate(r.reservationDate)}</td>
          <td>
            ${canCancel ? `
              <button class="btn btn-secondary btn-sm" onclick="cancelStudentReservation(${r.reservationId})">
                Cancel
              </button>
            ` : `<span style="color: var(--color-text-muted); font-size: 0.85rem;">None</span>`}
          </td>
        </tr>
      `;
    }).join('');

  } catch (err) {
    showToast('Failed to load dashboard: ' + err.message, 'error');
  }
}

// ===================================================================
// MENU PAGE (DISCOVERY, SEARCH, FILTER, & RESERVATION MODAL)
// ===================================================================
let allCategories = [];
let currentCategoryId = null;
let currentSearchTerm = '';
let activeFoodItem = null;

async function initMenuPage() {
  const categoryFilters = document.getElementById('category-filters');
  const searchInput = document.getElementById('menu-search');

  // Load categories
  try {
    allCategories = await apiCall('/categories');
    renderCategoryFilters();
  } catch (err) {
    console.warn('Could not load categories:', err);
  }

  // Load food items
  loadFoods();

  // Search input with debounce
  if (searchInput) {
    let debounceTimer;
    searchInput.addEventListener('input', (e) => {
      clearTimeout(debounceTimer);
      debounceTimer = setTimeout(() => {
        currentSearchTerm = e.target.value.trim();
        loadFoods();
      }, 300);
    });
  }

  // Modal setup
  setupReserveModal();
}

function renderCategoryFilters() {
  const container = document.getElementById('category-filters');
  if (!container) return;

  let html = `<button class="btn btn-sm ${currentCategoryId === null ? 'btn-primary' : 'btn-secondary'}" onclick="selectCategory(null)">All Categories</button>`;

  allCategories.forEach(cat => {
    const isActive = currentCategoryId === cat.categoryId;
    html += `
      <button class="btn btn-sm ${isActive ? 'btn-primary' : 'btn-secondary'}" onclick="selectCategory(${cat.categoryId})">
        ${cat.categoryName}
      </button>
    `;
  });

  container.innerHTML = html;
}

window.selectCategory = function(catId) {
  currentCategoryId = catId;
  renderCategoryFilters();
  loadFoods();
};

async function loadFoods() {
  const grid = document.getElementById('food-grid');
  if (!grid) return;

  grid.innerHTML = `
    <div style="grid-column: 1/-1; text-align: center; padding: 3rem;">
      <div class="loading-spinner"></div>
      <p style="margin-top: 0.5rem; color: var(--color-text-muted);">Fetching freshly prepared campus food...</p>
    </div>
  `;

  let url = '/foods?';
  if (currentSearchTerm) url += `search=${encodeURIComponent(currentSearchTerm)}&`;
  if (currentCategoryId) url += `category=${encodeURIComponent(currentCategoryId)}&`;

  try {
    const items = await apiCall(url);

    if (!items || items.length === 0) {
      grid.innerHTML = `
        <div style="grid-column: 1/-1;" class="empty-state">
          <div class="empty-state-icon">🔍</div>
          <h3>No food items found</h3>
          <p>Try searching with another keyword or selecting a different category.</p>
        </div>
      `;
      return;
    }

    grid.innerHTML = items.map(item => {
      const isAvailable = item.status === 'AVAILABLE' && item.quantity > 0;
      const stockClass = isAvailable ? (item.quantity <= 5 ? 'low-stock' : 'in-stock') : 'sold-out';
      const stockText = isAvailable ? `${item.quantity} available` : 'Sold Out';

      const imgSrc = item.imageUrl && item.imageUrl.trim().length > 0 
        ? item.imageUrl 
        : 'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=500&auto=format&fit=crop';

      return `
        <div class="food-card">
          <div class="food-image-wrapper">
            <img src="${imgSrc}" alt="${item.foodName}" class="food-image" onerror="this.onerror=null; this.src='https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=500&auto=format&fit=crop';">
            <span class="category-tag">${item.categoryName || 'Food'}</span>
          </div>
          <div class="food-details">
            <h3 class="food-title">${item.foodName}</h3>
            <p class="food-desc">${item.description || 'Prepared fresh today at the campus kitchen.'}</p>
            <div class="food-meta">
              <span class="food-price">${formatCurrency(item.price)}</span>
              <span class="food-stock ${stockClass}">● ${stockText}</span>
            </div>
            <button 
              class="btn ${isAvailable ? 'btn-primary' : 'btn-secondary'}" 
              style="width: 100%;" 
              ${!isAvailable ? 'disabled' : ''} 
              onclick='openReserveModal(${JSON.stringify(item)})'>
              ${isAvailable ? 'Reserve Food' : 'Unavailable'}
            </button>
          </div>
        </div>
      `;
    }).join('');

  } catch (err) {
    grid.innerHTML = `
      <div style="grid-column: 1/-1;" class="empty-state">
        <div class="empty-state-icon">⚠️</div>
        <h3>Error loading food items</h3>
        <p>${err.message}</p>
      </div>
    `;
  }
}

// Modal handling
function setupReserveModal() {
  const modal = document.getElementById('reserve-modal');
  const closeBtn = document.getElementById('modal-close-btn');
  const cancelBtn = document.getElementById('modal-cancel-btn');
  const form = document.getElementById('reserve-form');
  const qtyInput = document.getElementById('reserve-quantity');

  if (!modal) return;

  function closeModal() {
    modal.classList.remove('show');
    activeFoodItem = null;
  }

  if (closeBtn) closeBtn.onclick = closeModal;
  if (cancelBtn) cancelBtn.onclick = closeModal;

  if (qtyInput) {
    qtyInput.addEventListener('input', () => {
      updateModalTotal();
    });
  }

  if (form) {
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      if (!activeFoodItem) return;

      const qty = parseInt(qtyInput.value, 10);
      if (isNaN(qty) || qty <= 0) {
        showToast('Please enter a valid quantity of at least 1.', 'error');
        return;
      }
      if (qty > activeFoodItem.quantity) {
        showToast(`Only ${activeFoodItem.quantity} portions currently available in stock.`, 'error');
        return;
      }

      const submitBtn = form.querySelector('button[type="submit"]');
      const originalText = submitBtn.innerHTML;

      try {
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="loading-spinner"></span> Submitting...';

        await apiCall('/reservations', 'POST', {
          foodId: activeFoodItem.foodId,
          quantity: qty
        });

        showToast(`Reserved ${qty} portion(s) of ${activeFoodItem.foodName}! Awaiting cafeteria approval.`, 'success');
        closeModal();

        // Refresh menu so quantities are up to date
        loadFoods();

      } catch (err) {
        showToast(err.message, 'error');
      } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = originalText;
      }
    });
  }
}

window.openReserveModal = function(item) {
  activeFoodItem = item;
  const modal = document.getElementById('reserve-modal');
  if (!modal) return;

  document.getElementById('modal-food-name').textContent = item.foodName;
  document.getElementById('modal-food-price').textContent = formatCurrency(item.price);
  document.getElementById('modal-food-stock').textContent = `${item.quantity} available in stock`;

  const qtyInput = document.getElementById('reserve-quantity');
  qtyInput.value = 1;
  qtyInput.max = item.quantity;
  qtyInput.min = 1;

  updateModalTotal();
  modal.classList.add('show');
};

function updateModalTotal() {
  if (!activeFoodItem) return;
  const qty = parseInt(document.getElementById('reserve-quantity').value, 10) || 1;
  const total = qty * activeFoodItem.price;
  document.getElementById('modal-total-price').textContent = formatCurrency(total);
}

// ===================================================================
// RESERVATIONS PAGE (MY RESERVATIONS)
// ===================================================================
let myReservationsList = [];
let currentStatusFilter = 'ALL';

async function initReservationsPage() {
  loadMyReservations();

  const filterBtns = document.querySelectorAll('.res-filter-btn');
  filterBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      filterBtns.forEach(b => b.classList.remove('btn-primary'));
      filterBtns.forEach(b => b.classList.add('btn-secondary'));
      btn.classList.remove('btn-secondary');
      btn.classList.add('btn-primary');

      currentStatusFilter = btn.dataset.status;
      renderMyReservations();
    });
  });
}

async function loadMyReservations() {
  const tbody = document.getElementById('my-reservations-body');
  if (!tbody) return;

  try {
    myReservationsList = await apiCall('/my-reservations');
    renderMyReservations();
  } catch (err) {
    tbody.innerHTML = `
      <tr>
        <td colspan="7" class="empty-state">
          <p>Failed to load reservations: ${err.message}</p>
        </td>
      </tr>
    `;
  }
}

function renderMyReservations() {
  const tbody = document.getElementById('my-reservations-body');
  if (!tbody) return;

  const filtered = myReservationsList.filter(r => {
    if (currentStatusFilter === 'ALL') return true;
    return r.status === currentStatusFilter;
  });

  if (filtered.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="7" class="empty-state">
          <div class="empty-state-icon">📋</div>
          <h3>No reservations found</h3>
          <p>You have no reservations matching this filter.</p>
          <a href="/menu.html" class="btn btn-primary btn-sm" style="margin-top: 0.5rem;">Explore Menu</a>
        </td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = filtered.map(r => {
    const firstItem = (r.items && r.items[0]) ? r.items[0] : null;
    const foodName = firstItem ? firstItem.foodName : 'Food Item';
    const qty = firstItem ? firstItem.quantity : 1;
    const unitPrice = firstItem ? firstItem.priceAtBooking : r.totalAmount;
    const canCancel = (r.status === 'PENDING' || r.status === 'APPROVED');

    return `
      <tr>
        <td><strong>#RES-${r.reservationId}</strong></td>
        <td>
          <div style="font-weight: 600; color: var(--color-deep-plum);">${foodName}</div>
          <span style="font-size: 0.8rem; color: var(--color-text-muted);">${formatCurrency(unitPrice)} each</span>
        </td>
        <td><strong>× ${qty}</strong></td>
        <td><strong style="color: var(--color-violet);">${formatCurrency(r.totalAmount)}</strong></td>
        <td>${formatDate(r.reservationDate)}</td>
        <td>${renderStatusBadge(r.status)}</td>
        <td>
          ${canCancel ? `
            <button class="btn btn-danger btn-sm" onclick="cancelStudentReservation(${r.reservationId})">
              Cancel Order
            </button>
          ` : `<span style="color: var(--color-text-muted); font-size: 0.85rem;">—</span>`}
        </td>
      </tr>
    `;
  }).join('');
}

window.cancelStudentReservation = async function(resId) {
  if (!confirm(`Are you sure you want to cancel reservation #RES-${resId}?`)) {
    return;
  }

  try {
    const res = await apiCall(`/reservations/${resId}/cancel`, 'PUT');
    showToast(res.message || 'Reservation cancelled successfully.', 'success');

    // Reload either dashboard or reservations table
    const path = window.location.pathname;
    if (path.includes('student-dashboard.html')) {
      initStudentDashboard();
    } else {
      loadMyReservations();
    }
  } catch (err) {
    showToast(err.message, 'error');
  }
};

