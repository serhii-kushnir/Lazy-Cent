/* =====================================================================
   LazyCent — settings panel (drawer + modals + timers + validation)
   ===================================================================== */

window.openSettingsDrawer = function(section) {
    const overlay = document.getElementById('settingsOverlay');
    const drawer = document.getElementById('settingsDrawer');
    if (!overlay || !drawer) return;

    drawer.classList.add('open');
    overlay.classList.add('open');
    document.body.style.overflow = 'hidden';

    const menuItem = document.querySelector('#drawerMenu .menu-item[data-section="' + section + '"]');
    if (menuItem) {
        menuItem.click();
    }
};

(function () {
    'use strict';

    // ==================== FAMILY PANEL LOGIC ====================
    window.FamilyPanel = {
        data: null,
        pendingActionId: null,

        init: function() { this.loadData(); },
        loadData: function() {
            var loading = document.getElementById('familyLoading');
            var content = document.getElementById('familyContent');
            if (!loading || !content) return;

            loading.style.display = 'block';
            content.style.display = 'none';

            fetch('/api/family/data')
                .then(res => res.json())
                .then(data => {
                    this.data = data;
                    this.render();
                })
                .catch(err => {
                    console.error(err);
                    loading.innerHTML = '<p class="text-danger small">Помилка завантаження</p>';
                });
        },
        render: function() {
            document.getElementById('familyLoading').style.display = 'none';
            document.getElementById('familyContent').style.display = 'block';

            const sysMsgBlock = document.getElementById('familySystemMessage');
            const sysMsgText = document.getElementById('familySystemMessageText');
            if (this.data.systemMessage) {
                sysMsgText.textContent = this.data.systemMessage;
                sysMsgBlock.style.display = 'block';
                this.showToast(this.data.systemMessage, false);
            } else {
                sysMsgBlock.style.display = 'none';
            }

            if (!this.data.inFamily) {
                document.getElementById('familyIn').style.display = 'none';
                document.getElementById('familyNotIn').style.display = 'block';

                const rej = document.getElementById('familyRejectedAlert');
                const pen = document.getElementById('familyPendingAlert');
                const forms = document.getElementById('familyForms');

                if (this.data.rejectedRequest) {
                    rej.style.display = 'block';
                    document.getElementById('rejectedFamilyName').textContent = this.data.rejectedRequest.familyName;
                    forms.style.display = 'none';
                } else { rej.style.display = 'none'; }

                if (this.data.pendingRequest) {
                    pen.style.display = 'block';
                    document.getElementById('pendingFamilyName').textContent = this.data.pendingRequest.familyName;
                    forms.style.display = 'none';
                } else { pen.style.display = 'none'; }

                if (!this.data.rejectedRequest && !this.data.pendingRequest) {
                    forms.style.display = 'block';
                }
            } else {
                document.getElementById('familyNotIn').style.display = 'none';
                document.getElementById('familyIn').style.display = 'block';

                document.getElementById('familyName').textContent = this.data.familyName;
                document.getElementById('familyInviteCode').textContent = this.data.inviteCode;

                const isOwner = this.data.isOwner;
                document.getElementById('editFamilyNameBtn').style.display = isOwner ? 'inline-block' : 'none';
                document.getElementById('regenCodeBtn').style.display = isOwner ? 'inline-block' : 'none';
                document.getElementById('deleteFamilyBtn').style.display = isOwner ? 'block' : 'none';
                document.getElementById('leaveFamilyBtn').style.display = isOwner ? 'none' : 'block';

                // --- РЕНДЕР УЧАСНИКІВ ---
                const list = document.getElementById('familyMembersList');
                list.innerHTML = '';
                this.data.members.forEach(m => {
                    const li = document.createElement('li');
                    li.className = 'list-group-item d-flex justify-content-between align-items-center px-2 py-2 mb-1 border-0 rounded bg-body-tertiary';

                    let badges = '';
                    if (m.isOwner) badges += '<span class="badge bg-primary ms-1" style="font-size:0.55rem;">Власник</span>';
                    if (m.id === this.data.currentUserId) badges += '<span class="badge bg-secondary ms-1" style="font-size:0.55rem;">(ви)</span>';

                    let html = `
                        <div class="flex-grow-1 text-truncate" style="min-width: 0;">
                            <div class="d-flex align-items-center">
                                <span class="fw-bold text-truncate" style="font-size:0.85rem;">${m.username}</span>
                                ${badges}
                            </div>
                            <div class="text-muted text-truncate" style="font-size:0.65rem;">${m.email}</div>
                        </div>
                    `;

                    if (isOwner && !m.isOwner) {
                        html += `
                            <div class="btn-group btn-group-sm ms-2 flex-shrink-0">
                                <button class="btn btn-outline-primary btn-sm py-1 px-3" onclick="FamilyPanel.openTransferModal(${m.id}, '${m.username}')" title="Передати права"><i class="fas fa-crown" style="font-size:0.75rem;"></i></button>
                                <button class="btn btn-outline-danger btn-sm py-1 px-3" onclick="FamilyPanel.openRemoveModal(${m.id}, '${m.username}')" title="Видалити"><i class="fas fa-user-minus" style="font-size:0.75rem;"></i></button>
                            </div>
                        `;
                    }
                    li.innerHTML = html;
                    list.appendChild(li);
                });

                // --- РЕНДЕР ЗАПИТІВ ---
                const reqBlock = document.getElementById('pendingRequestsBlock');
                if (isOwner && this.data.pendingRequests && this.data.pendingRequests.length > 0) {
                    reqBlock.style.display = 'block';
                    const reqList = document.getElementById('pendingRequestsList');
                    reqList.innerHTML = '';
                    this.data.pendingRequests.forEach(r => {
                        const li = document.createElement('li');
                        li.className = 'list-group-item d-flex justify-content-between align-items-center px-2 py-2 mb-1 border-0 rounded bg-warning-subtle';

                        li.innerHTML = `
                            <div class="flex-grow-1 text-truncate" style="min-width: 0;">
                                <div class="fw-bold text-truncate" style="font-size:0.85rem;">${r.username}</div>
                                <div class="text-muted text-truncate" style="font-size:0.65rem;">${r.email}</div>
                            </div>
                            <div class="btn-group btn-group-sm ms-2 flex-shrink-0">
                                <button class="btn btn-success btn-sm py-1 px-3" onclick="FamilyPanel.approveReq(${r.id})" title="Прийняти"><i class="fas fa-check" style="font-size:0.75rem;"></i></button>
                                <button class="btn btn-outline-danger btn-sm py-1 px-3" onclick="FamilyPanel.rejectReq(${r.id})" title="Відхилити"><i class="fas fa-times" style="font-size:0.75rem;"></i></button>
                            </div>
                        `;
                        reqList.appendChild(li);
                    });
                } else {
                    reqBlock.style.display = 'none';
                }
            }
        },
        post: function(url, body) {
            const headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
            if (window.csrfToken) {
                headers[window.csrfHeader || 'X-CSRF-TOKEN'] = window.csrfToken;
            }
            return fetch(url, { method: 'POST', headers: headers, body: new URLSearchParams(body) })
                .then(res => res.json().then(data => {
                    if (!res.ok) throw new Error(data.error || 'Помилка');
                    return data;
                }));
        },
        showToast: function(msg, isError) {
            const toastEl = document.getElementById('liveToast');
            if (toastEl && typeof bootstrap !== 'undefined') {
                const toastTitle = document.getElementById('toastTitle');
                const toastMsg = document.getElementById('toastMessage');
                const toastIcon = document.getElementById('toastIcon');

                toastEl.classList.remove('success', 'error');
                if (isError) {
                    toastEl.classList.add('error');
                    toastIcon.className = 'fas fa-exclamation-circle me-2';
                    toastTitle.textContent = 'Помилка';
                } else {
                    toastEl.classList.add('success');
                    toastIcon.className = 'fas fa-check-circle me-2';
                    toastTitle.textContent = 'Успіх';
                }
                toastMsg.textContent = msg;
                new bootstrap.Toast(toastEl, { autohide: true, delay: 4000 }).show();
            } else {
                alert(msg);
            }
        },

        // --- ACTIONS ---
        clearRejected: function() { this.post('/api/family/clear-rejected', {}).then(() => this.loadData()).catch(e => this.showToast(e.message, true)); },
        cancelRequest: function() { this.post('/api/family/cancel-request', {}).then(() => this.loadData()).catch(e => this.showToast(e.message, true)); },
        copyCode: function() {
            navigator.clipboard.writeText(this.data.inviteCode).then(() => this.showToast('Код скопійовано!', false));
        },

        // --- MODALS TRIGGERING ---
        openEditNameModal: function() {
            document.getElementById('editFamilyNameInput').value = this.data.familyName;
            document.getElementById('editFamilyNameInput').classList.remove('is-invalid');
            bootstrap.Modal.getOrCreateInstance(document.getElementById('editFamilyNameModal')).show();
        },
        openRegenModal: function() { bootstrap.Modal.getOrCreateInstance(document.getElementById('regenCodeModal')).show(); },
        openLeaveModal: function() { bootstrap.Modal.getOrCreateInstance(document.getElementById('leaveFamilyModal')).show(); },
        openDeleteModal: function() { bootstrap.Modal.getOrCreateInstance(document.getElementById('deleteFamilyModal')).show(); },

        openTransferModal: function(id, username) {
            this.pendingActionId = id;
            document.getElementById('transferOwnerName').textContent = username;
            bootstrap.Modal.getOrCreateInstance(document.getElementById('transferOwnerModal')).show();
        },
        openRemoveModal: function(id, username) {
            this.pendingActionId = id;
            document.getElementById('removeMemberName').textContent = username;
            bootstrap.Modal.getOrCreateInstance(document.getElementById('removeMemberModal')).show();
        },

        // --- API CALLS FROM MODALS ---
        confirmEditName: function() {
            const input = document.getElementById('editFamilyNameInput');
            const name = input.value.trim();
            // ВИПРАВЛЕНО: регулярний вираз без зайвих екрануючих слешів
            const pattern = /^[a-zA-Zа-яА-ЯіІїЇєЄґҐ0-9\s\-_'.]+$/;

            if (name.length < 2 || name.length > 20 || !pattern.test(name)) {
                input.classList.add('is-invalid');
                return;
            }
            input.classList.remove('is-invalid');

            this.post('/api/family/update-name', { name: name })
                .then(() => {
                    this.loadData();
                    bootstrap.Modal.getInstance(document.getElementById('editFamilyNameModal')).hide();
                })
                .catch(e => this.showToast(e.message, true));
        },
        confirmRegen: function() {
            this.post('/api/family/regenerate-code', {}).then(() => { this.loadData(); bootstrap.Modal.getInstance(document.getElementById('regenCodeModal')).hide(); }).catch(e => this.showToast(e.message, true));
        },
        confirmLeave: function() {
            this.post('/api/family/leave', {}).then(() => { this.loadData(); bootstrap.Modal.getInstance(document.getElementById('leaveFamilyModal')).hide(); }).catch(e => this.showToast(e.message, true));
        },
        confirmDelete: function() {
            this.post('/api/family/delete', {}).then(() => { this.loadData(); bootstrap.Modal.getInstance(document.getElementById('deleteFamilyModal')).hide(); }).catch(e => this.showToast(e.message, true));
        },
        confirmTransfer: function() {
            if (!this.pendingActionId) return;
            this.post('/api/family/transfer/' + this.pendingActionId, {}).then(() => { this.loadData(); bootstrap.Modal.getInstance(document.getElementById('transferOwnerModal')).hide(); }).catch(e => this.showToast(e.message, true));
        },
        confirmRemove: function() {
            if (!this.pendingActionId) return;
            this.post('/api/family/remove/' + this.pendingActionId, {}).then(() => { this.loadData(); bootstrap.Modal.getInstance(document.getElementById('removeMemberModal')).hide(); }).catch(e => this.showToast(e.message, true));
        },

        approveReq: function(id) { this.post('/api/family/approve/' + id, {}).then(() => this.loadData()).catch(e => this.showToast(e.message, true)); },
        rejectReq: function(id) { this.post('/api/family/reject/' + id, {}).then(() => this.loadData()).catch(e => this.showToast(e.message, true)); }
    };
    // ==============================================================

    function init() {
        const i18n = window.LazyCentSettingsI18n || {};

        const openBtn = document.getElementById('openSettingsBtn');
        const overlay = document.getElementById('settingsOverlay');
        const drawer = document.getElementById('settingsDrawer');
        const closeBtn = document.getElementById('closeSettingsBtn');

        if (!openBtn || !overlay || !drawer || !closeBtn) {
            return;
        }

        // ==================== ВІДКРИТТЯ / ЗАКРИТТЯ ====================
        function openPanel() {
            drawer.classList.add('open');
            overlay.classList.add('open');
            document.body.style.overflow = 'hidden';
        }

        function closePanel() {
            drawer.classList.remove('open');
            overlay.classList.remove('open');
            document.body.style.overflow = '';
        }

        openBtn.addEventListener('click', openPanel);
        closeBtn.addEventListener('click', closePanel);
        overlay.addEventListener('click', closePanel);

        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && drawer.classList.contains('open')) {
                closePanel();
            }
        });

        // ==================== ПЕРЕМИКАННЯ РОЗДІЛІВ ====================
        const menuItems = document.querySelectorAll('#drawerMenu .menu-item[data-section]');
        const sections = {
            profile: document.getElementById('d-section-profile'),
            categories: document.getElementById('d-section-categories'),
            family: document.getElementById('d-section-family'),
            currency: document.getElementById('d-section-currency'),
            language: document.getElementById('d-section-language'),
            theme: document.getElementById('d-section-theme'),
            data: document.getElementById('d-section-data'),
            about: document.getElementById('d-section-about')
        };

        function switchSection(sectionId) {
            menuItems.forEach(m => m.classList.remove('active'));
            const targetMenuItem = document.querySelector('#drawerMenu .menu-item[data-section="' + sectionId + '"]');
            if (targetMenuItem) targetMenuItem.classList.add('active');

            Object.keys(sections).forEach(key => {
                const el = sections[key];
                if (el) el.classList.remove('active');
            });
            const targetSection = sections[sectionId];
            if (targetSection) targetSection.classList.add('active');

            localStorage.setItem('settings-drawer-tab', sectionId);

            if (sectionId === 'family' && window.FamilyPanel) {
                window.FamilyPanel.loadData();
            }
        }

        const savedDrawerTab = localStorage.getItem('settings-drawer-tab') || 'profile';
        setTimeout(function () {
            switchSection(savedDrawerTab);
        }, 100);

        menuItems.forEach(item => {
            item.addEventListener('click', function () {
                if (this.classList.contains('logout-item')) return;
                switchSection(this.dataset.section);
            });
        });

        // ==================== FAMILY FORMS & VALIDATION ====================
        const createForm = document.getElementById('createFamilyForm');
        const createInput = document.getElementById('createFamilyNameInput');
        const createCharCount = document.getElementById('createFamilyCharCount');

        if (createInput && createCharCount) {
            createInput.addEventListener('input', function() {
                createCharCount.textContent = this.value.length;
                this.classList.remove('is-invalid');
            });
        }

        if (createForm) {
            createForm.onsubmit = function(e) {
                e.preventDefault();
                e.stopPropagation();

                // ВИПРАВЛЕНО: регулярний вираз без зайвих екрануючих слешів
                const namePattern = /^[a-zA-Zа-яА-ЯіІїЇєЄґҐ0-9\s\-_'.]+$/;
                const name = createInput.value.trim();
                let isValid = true;

                if (name.length < 2 || name.length > 20 || !namePattern.test(name)) {
                    createInput.classList.add('is-invalid');
                    isValid = false;
                } else {
                    createInput.classList.remove('is-invalid');
                }

                if (!isValid) return;

                FamilyPanel.post('/api/family/create', { name: name })
                    .then(() => {
                        FamilyPanel.showToast('Сім\'ю створено!', false);
                        this.reset();
                        if(createCharCount) createCharCount.textContent = '0';
                        FamilyPanel.loadData();
                    })
                    .catch(e => FamilyPanel.showToast(e.message, true));
            };
        }

        const joinForm = document.getElementById('joinFamilyForm');
        const joinInput = document.getElementById('joinFamilyCodeInput');

        if (joinForm) {
            joinForm.onsubmit = function(e) {
                e.preventDefault();
                e.stopPropagation();

                const code = joinInput.value.trim();
                let isValid = true;

                if (code.length === 0) {
                    joinInput.classList.add('is-invalid');
                    isValid = false;
                } else {
                    joinInput.classList.remove('is-invalid');
                }

                if (!isValid) return;

                FamilyPanel.post('/api/family/join', { inviteCode: code })
                    .then(() => {
                        FamilyPanel.showToast('Запит надіслано!', false);
                        this.reset();
                        FamilyPanel.loadData();
                    })
                    .catch(e => FamilyPanel.showToast(e.message, true));
            };

            joinInput.addEventListener('input', function() {
                this.classList.remove('is-invalid');
            });
        }

        // --- Прив'язка кнопок модальних вікон ---
        const bindBtn = (id, fn) => {
            const btn = document.getElementById(id);
            if (btn) btn.addEventListener('click', fn);
        };

        bindBtn('confirmEditNameBtn', () => FamilyPanel.confirmEditName());
        bindBtn('confirmRegenCodeBtn', () => FamilyPanel.confirmRegen());
        bindBtn('confirmLeaveFamilyBtn', () => FamilyPanel.confirmLeave());
        bindBtn('confirmDeleteFamilyBtn', () => FamilyPanel.confirmDelete());
        bindBtn('confirmTransferOwnerBtn', () => FamilyPanel.confirmTransfer());
        bindBtn('confirmRemoveMemberBtn', () => FamilyPanel.confirmRemove());

        const editNameInput = document.getElementById('editFamilyNameInput');
        if (editNameInput) {
            editNameInput.addEventListener('input', function() {
                this.classList.remove('is-invalid');
            });
        }

        // ==================== ВАЛЮТА ====================
        const currencyOptions = document.querySelectorAll('#drawerCurrencyGrid .currency-option');
        const selectedCurrencyInput = document.getElementById('drawerSelectedCurrency');

        if (currencyOptions.length && selectedCurrencyInput) {
            currencyOptions.forEach(opt => {
                if (opt.classList.contains('active')) {
                    selectedCurrencyInput.value = opt.dataset.currency;
                }
                opt.addEventListener('click', function () {
                    currencyOptions.forEach(o => o.classList.remove('active'));
                    this.classList.add('active');
                    selectedCurrencyInput.value = this.dataset.currency;
                });
            });
        }

        // ==================== ТЕМА ====================
        const themeBtns = document.querySelectorAll('#d-section-theme .theme-btn');
        const currentThemeLabel = document.getElementById('drawerCurrentTheme');
        const root = document.documentElement;

        const THEME_LABELS = {
            light: i18n.themeLight || 'Світла',
            dark: i18n.themeDark || 'Темна',
            system: i18n.themeSystem || 'Системна'
        };

        function setDrawerTheme(theme) {
            if (theme === 'system') {
                const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
                root.setAttribute('data-bs-theme', systemDark ? 'dark' : 'light');
                if (currentThemeLabel) {
                    currentThemeLabel.textContent = systemDark
                        ? THEME_LABELS.dark + ' (' + THEME_LABELS.system + ')'
                        : THEME_LABELS.light + ' (' + THEME_LABELS.system + ')';
                }
            } else {
                root.setAttribute('data-bs-theme', theme);
                if (currentThemeLabel) {
                    currentThemeLabel.textContent =
                        theme === 'dark' ? THEME_LABELS.dark : THEME_LABELS.light;
                }
            }
            localStorage.setItem('lazycent-theme', theme);
            updateThemeButtonsDrawer(theme);
        }

        function updateThemeButtonsDrawer(activeTheme) {
            themeBtns.forEach(btn => {
                btn.classList.remove('active');
                if (btn.dataset.theme === activeTheme) {
                    btn.classList.add('active');
                }
            });
        }

        const savedTheme = localStorage.getItem('lazycent-theme') || 'system';
        setDrawerTheme(savedTheme);

        themeBtns.forEach(btn => {
            btn.addEventListener('click', function () {
                setDrawerTheme(this.dataset.theme);
            });
        });

        // ==================== ВИДАЛЕННЯ КАТЕГОРІЇ ====================
        const deleteCategoryModal = document.getElementById('deleteCategoryConfirmModal');
        const deleteCategoryForm = document.getElementById('deleteCategoryForm');
        const deleteCategoryName = document.getElementById('deleteCategoryName');
        const deleteCategoryType = document.getElementById('deleteCategoryType');

        document.querySelectorAll('.js-delete-category').forEach(function (btn) {
            btn.addEventListener('click', function () {
                const id = this.dataset.id;
                const name = this.dataset.name || '—';
                const type = this.dataset.type || '—';

                if (deleteCategoryName) deleteCategoryName.textContent = name;
                if (deleteCategoryType) deleteCategoryType.textContent = type;
                if (deleteCategoryForm) deleteCategoryForm.action = '/categories/delete/' + id;
                if (deleteCategoryModal) {
                    bootstrap.Modal.getOrCreateInstance(deleteCategoryModal).show();
                }
            });
        });

        // ==================== РЕДАГУВАННЯ КАТЕГОРІЇ ====================
        const editModal = document.getElementById('editCategoryModal');
        const editCategoryForm = document.getElementById('editCategoryForm');
        const editCategoryName = document.getElementById('editCategoryName');

        if (editModal && editCategoryForm && editCategoryName) {
            document.querySelectorAll('.edit-category').forEach(btn => {
                btn.addEventListener('click', function () {
                    const id = this.dataset.id;
                    const name = this.dataset.name;
                    editCategoryName.value = name;
                    editCategoryForm.action = '/categories/update/' + id;
                    new bootstrap.Modal(editModal).show();
                });
            });
        }

        // ==================== ТАЙМЕРИ ====================
        function startTimer(secondsId, circleId, btnId, duration) {
            const secondsEl = document.getElementById(secondsId);
            const circleEl = document.getElementById(circleId);
            const confirmBtn = document.getElementById(btnId);

            if (!secondsEl || !circleEl || !confirmBtn) return;

            const originalText = confirmBtn.textContent.replace(/\(\d+s\)/, '').trim();

            let remaining = duration;
            confirmBtn.disabled = true;
            confirmBtn.textContent = originalText + ' (' + remaining + 's)';
            secondsEl.textContent = remaining;
            circleEl.textContent = remaining;

            const interval = setInterval(function () {
                remaining--;
                secondsEl.textContent = remaining;
                circleEl.textContent = remaining;

                if (remaining <= 0) {
                    clearInterval(interval);
                    secondsEl.textContent = '0';
                    circleEl.textContent = '✓';
                    confirmBtn.disabled = false;
                    confirmBtn.textContent = originalText;
                } else {
                    confirmBtn.textContent = originalText + ' (' + remaining + 's)';
                }
            }, 1000);

            const modal = confirmBtn.closest('.modal');
            if (modal) {
                modal.addEventListener('hidden.bs.modal', function () {
                    clearInterval(interval);
                    confirmBtn.disabled = true;
                    confirmBtn.textContent = originalText + ' (' + duration + 's)';
                    secondsEl.textContent = duration;
                    circleEl.textContent = duration;
                }, { once: true });
            }
        }

        const clearDataModal = document.getElementById('clearDataModal');
        const deleteAccountModal = document.getElementById('deleteAccountModal');

        if (clearDataModal) {
            clearDataModal.addEventListener('show.bs.modal', function () {
                startTimer('clearDataTimer', 'clearDataTimerCircle', 'clearDataConfirmBtn', 60);
            });
        }

        if (deleteAccountModal) {
            deleteAccountModal.addEventListener('show.bs.modal', function () {
                startTimer('deleteAccountTimer', 'deleteAccountTimerCircle',
                    'deleteAccountConfirmBtn', 60);
            });
        }

        // ==================== ВАЛІДАЦІЯ ПРОФІЛЮ ====================
        const profileForm = document.getElementById('profileForm');
        if (!profileForm) return;

        const usernameInput = document.getElementById('profileUsername');
        const emailInput = document.getElementById('profileEmail');
        const passwordInput = document.getElementById('profileNewPassword');

        if (usernameInput && !usernameInput.disabled) {
            usernameInput.addEventListener('input', function () {
                const pattern = /^[a-zA-Z0-9_]{6,30}$/;
                const valid = pattern.test(this.value);
                this.classList.toggle('is-valid', valid);
                this.classList.toggle('is-invalid', !valid && this.value.length > 0);
                const error = document.getElementById('profileUsernameError');
                if (error) error.style.display = !valid && this.value.length > 0 ? 'block' : 'none';
            });
        }

        if (emailInput && !emailInput.disabled) {
            emailInput.addEventListener('input', function () {
                const pattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
                const valid = pattern.test(this.value);
                this.classList.toggle('is-valid', valid);
                this.classList.toggle('is-invalid', !valid && this.value.length > 0);
                const error = document.getElementById('profileEmailError');
                if (error) error.style.display = !valid && this.value.length > 0 ? 'block' : 'none';
            });
        }

        if (passwordInput) {
            const reqLength = document.getElementById('profileReqLength');
            const reqLowercase = document.getElementById('profileReqLowercase');
            const reqUppercase = document.getElementById('profileReqUppercase');
            const reqDigit = document.getElementById('profileReqDigit');
            const reqSpecial = document.getElementById('profileReqSpecial');

            const REQ_TEXTS = {
                length: i18n.pwdReqLength || 'Мінімум 6 символів',
                lowercase: i18n.pwdReqLowercase || 'Мала літера (a-z)',
                uppercase: i18n.pwdReqUppercase || 'Велика літера (A-Z)',
                digit: i18n.pwdReqDigit || 'Цифра (0-9)',
                special: i18n.pwdReqSpecial || 'Спеціальний символ (@$!%*?&)'
            };

            passwordInput.addEventListener('input', function () {
                const value = this.value;
                const checks = {
                    length: value.length >= 6,
                    lowercase: /[a-z]/.test(value),
                    uppercase: /[A-Z]/.test(value),
                    digit: /\d/.test(value),
                    special: /[@$!%*?&]/.test(value)
                };

                function applyReq(el, key, ok) {
                    if (!el) return;
                    el.className = ok ? 'text-success' : 'text-danger';
                    el.innerHTML = (ok ? '<i class="fas fa-check-circle"></i>' : '<i class="fas fa-circle"></i>') + ' ' + REQ_TEXTS[key];
                }

                applyReq(reqLength, 'length', checks.length);
                applyReq(reqLowercase, 'lowercase', checks.lowercase);
                applyReq(reqUppercase, 'uppercase', checks.uppercase);
                applyReq(reqDigit, 'digit', checks.digit);
                applyReq(reqSpecial, 'special', checks.special);

                const allValid = Object.values(checks).every(Boolean);
                this.classList.toggle('is-valid', allValid && value.length > 0);
                this.classList.toggle('is-invalid', !allValid && value.length > 0);
                const error = document.getElementById('profilePasswordError');
                if (error) error.style.display = !allValid && value.length > 0 ? 'block' : 'none';
            });
        }

        profileForm.addEventListener('submit', function (e) {
            let valid = true;

            if (usernameInput && !usernameInput.disabled) {
                const pattern = /^[a-zA-Z0-9_]{6,30}$/;
                if (!pattern.test(usernameInput.value)) {
                    usernameInput.classList.add('is-invalid');
                    const error = document.getElementById('profileUsernameError');
                    if (error) error.style.display = 'block';
                    valid = false;
                }
            }

            if (emailInput && !emailInput.disabled) {
                const pattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
                if (!pattern.test(emailInput.value)) {
                    emailInput.classList.add('is-invalid');
                    const error = document.getElementById('profileEmailError');
                    if (error) error.style.display = 'block';
                    valid = false;
                }
            }

            if (passwordInput && passwordInput.value.length > 0) {
                const checks = {
                    length: passwordInput.value.length >= 6,
                    lowercase: /[a-z]/.test(passwordInput.value),
                    uppercase: /[A-Z]/.test(passwordInput.value),
                    digit: /\d/.test(passwordInput.value),
                    special: /[@$!%*?&]/.test(passwordInput.value)
                };
                const allValid = Object.values(checks).every(Boolean);
                if (!allValid) {
                    passwordInput.classList.add('is-invalid');
                    const error = document.getElementById('profilePasswordError');
                    if (error) error.style.display = 'block';
                    valid = false;
                }
            }

            if (!valid) {
                e.preventDefault();
                const firstError = profileForm.querySelector('.is-invalid');
                if (firstError) firstError.focus();
            }
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();