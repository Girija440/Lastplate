/**
 * LASTPLATE - Admin Management Operations
 * Supports Admin Dashboard, Food Inventory Management, and Reservation Approvals
 */

document.addEventListener('DOMContentLoaded', () => {
  // Ensure user is logged in as ADMIN
  const user = getUser();
  if (!user || user.role !== 'ADMIN') {
    showToast('Admin privileges required. Please sign in as Administrator.', 'error');
    window.location.href = '/login.html';
    return;
  }

  const path = window.location.pathname;
  if (path.includes('admin-dashboard.html')) {
    initAdminDashboard();
  } else if (path.includes('admin-foods.html')) {
    initAdminFoods();
  } else if (path.includes('admin-reservations.html')) {
    initAdminReservations();
  }
});

// ===================================================================
// ADMIN DASHBOARD
// ===================================================================
async function initAdminDashboard() {
  try {
    const stats = await apiCall('/admin/dashboard');

    // Populate stat counters
    document.getElementById('stat-total-foods').textContent = stats.totalFoods || 0;
    document.getElementById('stat-available-foods').textContent = stats.availableFoods || 0;
    document.getElementById('stat-total-res').textContent = stats.totalReservations || 0;
    document.getElementById('stat-pending-res').textContent = stats.pendingReservations || 0;
    document.getElementById('stat-approved-res').textContent = stats.approvedReservations || 0;
    document.getElementById('stat-total-revenue').textContent = formatCurrency(stats.totalRevenue || 0);
    document.getElementById('stat-total-students').textContent = stats.totalStudents || 0;

    // Load pending reservations queue
    loadPendingQueue();

    // Load registered students list
    loadRegisteredStudents();

  } catch (err) {
    showToast('Failed to load dashboard metrics: ' + err.message, 'error');
  }
}

async function loadPendingQueue() {
  const tbody = document.getElementById('pending-queue-body');
  if (!tbody) return;

  try {
    const list = await apiCall('/admin/reservations?status=PENDING');
    if (list.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="6" class="empty-state">
            <div class="empty-state-icon">☕</div>
            <p>All caught up! No pending food reservations waiting for approval.</p>
          </td>
        </tr>
      `;
      return;
    }

    tbody.innerHTML = list.map(r => {
      const firstItem = (r.items && r.items[0]) ? r.items[0] : null;
      const foodName = firstItem ? `${firstItem.foodName} (×${firstItem.quantity})` : 'Food Item';

      return `
        <tr>
          <td><strong>#RES-${r.reservationId}</strong></td>
          <td>
            <div style="font-weight: 600;">${r.studentName || 'Student'}</div>
            <span style="font-size: 0.8rem; color: var(--color-text-muted);">${r.studentEmail || ''}</span>
          </td>
          <td>${foodName}</td>
          <td><strong style="color: var(--color-violet);">${formatCurrency(r.totalAmount)}</strong></td>
          <td>${formatDate(r.reservationDate)}</td>
          <td>
            <div style="display: flex; gap: 0.5rem;">
              <button class="btn btn-success btn-sm" onclick="approveReservation(${r.reservationId})">
                ✓ Approve
              </button>
              <button class="btn btn-danger btn-sm" onclick="rejectReservation(${r.reservationId})">
                ✕ Reject
              </button>
            </div>
          </td>
        </tr>
      `;
    }).join('');

  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="6" class="empty-state"><p>${err.message}</p></td></tr>`;
  }
}

async function loadRegisteredStudents() {
  const tbody = document.getElementById('registered-students-body');
  if (!tbody) return;

  try {
    const students = await apiCall('/admin/students');
    if (students.length === 0) {
      tbody.innerHTML = `<tr><td colspan="5" class="empty-state"><p>No students registered yet.</p></td></tr>`;
      return;
    }

    tbody.innerHTML = students.map(s => `
      <tr>
        <td><strong>${s.name}</strong></td>
        <td>${s.email}</td>
        <td>${formatDate(s.createdAt)}</td>
        <td><span class="badge badge-cancelled">${s.totalReservations} orders</span></td>
        <td><strong>${formatCurrency(s.totalSpent)}</strong></td>
      </tr>
    `).join('');
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="5" class="empty-state"><p>${err.message}</p></td></tr>`;
  }
}

// ===================================================================
// ADMIN FOODS (INVENTORY MANAGEMENT)
// ===================================================================
let adminFoodsList = [];
let categoriesList = [];

async function initAdminFoods() {
  try {
    categoriesList = await apiCall('/categories');
    populateCategoryDropdowns();
  } catch (err) {
    console.warn('Failed to load categories:', err);
  }

  loadAdminFoods();
  setupFoodModals();
}

function populateCategoryDropdowns() {
  const categorySelects = [
    document.getElementById('food-category-select'),
    document.getElementById('edit-food-category-select')
  ];

  categorySelects.forEach(select => {
    if (!select) return;
    select.innerHTML = categoriesList.map(c => `
      <option value="${c.categoryId}">${c.categoryName}</option>
    `).join('');
  });
}

async function loadAdminFoods() {
  const tbody = document.getElementById('admin-foods-body');
  if (!tbody) return;

  try {
    adminFoodsList = await apiCall('/foods');
    if (adminFoodsList.length === 0) {
      tbody.innerHTML = `<tr><td colspan="7" class="empty-state"><p>No food items currently in inventory. Click "Add Food Item" above.</p></td></tr>`;
      return;
    }

    tbody.innerHTML = adminFoodsList.map(f => {
      const isAvailable = f.status === 'AVAILABLE' && f.quantity > 0;
      const statusBadge = isAvailable 
        ? `<span class="badge badge-approved">● Available (${f.quantity})</span>`
        : `<span class="badge badge-rejected">● Sold Out (${f.quantity})</span>`;

      const imgSrc = f.imageUrl && f.imageUrl.trim().length > 0 
        ? f.imageUrl 
        : 'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=100&auto=format&fit=crop';

      return `
        <tr>
          <td>
            <img src="${imgSrc}" alt="${f.foodName}" style="width: 48px; height: 48px; border-radius: var(--radius-sm); object-fit: cover;" onerror="this.onerror=null; this.src='https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=100&auto=format&fit=crop';">
          </td>
          <td>
            <div style="font-weight: 700; color: var(--color-deep-plum);">${f.foodName}</div>
            <span style="font-size: 0.8rem; color: var(--color-text-muted);">${f.description || ''}</span>
          </td>
          <td><span class="badge badge-cancelled">${f.categoryName || 'Food'}</span></td>
          <td><strong>${formatCurrency(f.price)}</strong></td>
          <td>
            <strong>${f.quantity} units</strong>
          </td>
          <td>${statusBadge}</td>
          <td>
            <div style="display: flex; gap: 0.5rem;">
              <button class="btn btn-secondary btn-sm" onclick='openEditFoodModal(${JSON.stringify(f)})'>
                Edit
              </button>
              <button class="btn btn-danger btn-sm" onclick="deleteFoodItem(${f.foodId}, '${f.foodName.replace(/'/g, "\\'")}')">
                Delete
              </button>
            </div>
          </td>
        </tr>
      `;
    }).join('');

  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="7" class="empty-state"><p>${err.message}</p></td></tr>`;
  }
}

function setupFoodModals() {
  // Add modal
  const addModal = document.getElementById('add-food-modal');
  const openAddBtn = document.getElementById('open-add-food-btn');
  const closeAddBtn = document.getElementById('close-add-food-btn');
  const addForm = document.getElementById('add-food-form');

  if (openAddBtn && addModal) {
    openAddBtn.onclick = () => addModal.classList.add('show');
  }
  if (closeAddBtn && addModal) {
    closeAddBtn.onclick = () => addModal.classList.remove('show');
  }
  if (addForm) {
    addForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const payload = {
        foodName: document.getElementById('add-food-name').value.trim(),
        description: document.getElementById('add-food-desc').value.trim(),
        categoryId: parseInt(document.getElementById('food-category-select').value, 10),
        price: parseFloat(document.getElementById('add-food-price').value),
        quantity: parseInt(document.getElementById('add-food-qty').value, 10),
        imageUrl: document.getElementById('add-food-image').value.trim(),
        status: document.getElementById('add-food-status').value
      };

      try {
        await apiCall('/admin/foods', 'POST', payload);
        showToast('Food item created successfully!', 'success');
        addModal.classList.remove('show');
        addForm.reset();
        loadAdminFoods();
      } catch (err) {
        showToast(err.message, 'error');
      }
    });
  }

  // Edit modal
  const editModal = document.getElementById('edit-food-modal');
  const closeEditBtn = document.getElementById('close-edit-food-btn');
  const editForm = document.getElementById('edit-food-form');

  if (closeEditBtn && editModal) {
    closeEditBtn.onclick = () => editModal.classList.remove('show');
  }
  if (editForm) {
    editForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const foodId = parseInt(document.getElementById('edit-food-id').value, 10);
      const payload = {
        foodName: document.getElementById('edit-food-name').value.trim(),
        description: document.getElementById('edit-food-desc').value.trim(),
        categoryId: parseInt(document.getElementById('edit-food-category-select').value, 10),
        price: parseFloat(document.getElementById('edit-food-price').value),
        quantity: parseInt(document.getElementById('edit-food-qty').value, 10),
        imageUrl: document.getElementById('edit-food-image').value.trim(),
        status: document.getElementById('edit-food-status').value
      };

      try {
        await apiCall(`/admin/foods/${foodId}`, 'PUT', payload);
        showToast('Food item updated successfully!', 'success');
        editModal.classList.remove('show');
        loadAdminFoods();
      } catch (err) {
        showToast(err.message, 'error');
      }
    });
  }
}

window.openEditFoodModal = function(food) {
  const editModal = document.getElementById('edit-food-modal');
  if (!editModal) return;

  document.getElementById('edit-food-id').value = food.foodId;
  document.getElementById('edit-food-name').value = food.foodName;
  document.getElementById('edit-food-desc').value = food.description || '';
  document.getElementById('edit-food-category-select').value = food.categoryId;
  document.getElementById('edit-food-price').value = food.price;
  document.getElementById('edit-food-qty').value = food.quantity;
  document.getElementById('edit-food-image').value = food.imageUrl || '';
  document.getElementById('edit-food-status').value = food.status || 'AVAILABLE';

  editModal.classList.add('show');
};

window.deleteFoodItem = async function(foodId, foodName) {
  if (!confirm(`Are you sure you want to delete food item "${foodName}"?`)) {
    return;
  }

  try {
    const res = await apiCall(`/admin/foods/${foodId}`, 'DELETE');
    showToast(res.message || 'Food item removed.', 'success');
    loadAdminFoods();
  } catch (err) {
    showToast(err.message, 'error');
  }
};

// ===================================================================
// ADMIN RESERVATIONS (ALL RESERVATIONS MANAGEMENT)
// ===================================================================
let adminReservationsFilter = 'ALL';

async function initAdminReservations() {
  loadAdminReservations();

  const filterBtns = document.querySelectorAll('.admin-res-filter-btn');
  filterBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      filterBtns.forEach(b => b.classList.remove('btn-primary'));
      filterBtns.forEach(b => b.classList.add('btn-secondary'));
      btn.classList.remove('btn-secondary');
      btn.classList.add('btn-primary');

      adminReservationsFilter = btn.dataset.status;
      loadAdminReservations();
    });
  });
}

async function loadAdminReservations() {
  const tbody = document.getElementById('admin-reservations-body');
  if (!tbody) return;

  let url = '/admin/reservations';
  if (adminReservationsFilter !== 'ALL') {
    url += `?status=${encodeURIComponent(adminReservationsFilter)}`;
  }

  try {
    const list = await apiCall(url);
    if (list.length === 0) {
      tbody.innerHTML = `<tr><td colspan="7" class="empty-state"><p>No reservations found matching this filter.</p></td></tr>`;
      return;
    }

    tbody.innerHTML = list.map(r => {
      const firstItem = (r.items && r.items[0]) ? r.items[0] : null;
      const itemName = firstItem ? `${firstItem.foodName} (×${firstItem.quantity})` : 'Food Item';

      let actionHtml = '—';
      if (r.status === 'PENDING') {
        actionHtml = `
          <div style="display: flex; gap: 0.4rem;">
            <button class="btn btn-success btn-sm" onclick="approveReservation(${r.reservationId})">
              Approve
            </button>
            <button class="btn btn-danger btn-sm" onclick="rejectReservation(${r.reservationId})">
              Reject
            </button>
          </div>
        `;
      } else if (r.status === 'APPROVED') {
        actionHtml = `
          <button class="btn btn-secondary btn-sm" onclick="cancelAdminReservation(${r.reservationId})">
            Cancel & Restore Stock
          </button>
        `;
      }

      return `
        <tr>
          <td><strong>#RES-${r.reservationId}</strong></td>
          <td>
            <div style="font-weight: 600;">${r.studentName || 'Student'}</div>
            <span style="font-size: 0.8rem; color: var(--color-text-muted);">${r.studentEmail || ''}</span>
          </td>
          <td>${itemName}</td>
          <td><strong style="color: var(--color-violet);">${formatCurrency(r.totalAmount)}</strong></td>
          <td>${formatDate(r.reservationDate)}</td>
          <td>${renderStatusBadge(r.status)}</td>
          <td>${actionHtml}</td>
        </tr>
      `;
    }).join('');

  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="7" class="empty-state"><p>${err.message}</p></td></tr>`;
  }
}

window.approveReservation = async function(resId) {
  try {
    const res = await apiCall(`/admin/reservations/${resId}/approve`, 'PUT');
    showToast(res.message || `Reservation #${resId} approved! Stock deducted.`, 'success');

    // Refresh current view
    const path = window.location.pathname;
    if (path.includes('admin-dashboard.html')) {
      initAdminDashboard();
    } else {
      loadAdminReservations();
    }
  } catch (err) {
    showToast(err.message, 'error');
  }
};

window.rejectReservation = async function(resId) {
  if (!confirm(`Are you sure you want to reject reservation #RES-${resId}?`)) {
    return;
  }

  try {
    const res = await apiCall(`/admin/reservations/${resId}/reject`, 'PUT');
    showToast(res.message || `Reservation #${resId} rejected.`, 'info');

    const path = window.location.pathname;
    if (path.includes('admin-dashboard.html')) {
      initAdminDashboard();
    } else {
      loadAdminReservations();
    }
  } catch (err) {
    showToast(err.message, 'error');
  }
};

window.cancelAdminReservation = async function(resId) {
  if (!confirm(`Cancel approved reservation #RES-${resId}? Stock will be restored to inventory.`)) {
    return;
  }

  try {
    const res = await apiCall(`/reservations/${resId}/cancel`, 'PUT');
    showToast(res.message || `Reservation #${resId} cancelled and stock restored!`, 'success');
    loadAdminReservations();
  } catch (err) {
    showToast(err.message, 'error');
  }
};

