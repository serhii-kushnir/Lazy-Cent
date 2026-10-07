/* =====================================================================
   LazyCent — settings panel (drawer + modals + timers + validation)
   =====================================================================
   Перенесено з fragments/settings-panel.html (P1.e).

   Отримує i18n-рядки через window.LazyCentSettingsI18n, який
   встановлює інлайн-фрагмент settingsPanelScript перед завантаженням
   цього файлу.

   Завантажується з defer: не блокує парсинг HTML, виконується
   ПЕРЕД DOMContentLoaded, тому listener нижче спрацює завжди.
   ===================================================================== */

(function () {
    'use strict';

    function init() {
        const i18n = window.LazyCentSettingsI18n || {};

        const openBtn = document.getElementById('openSettingsBtn');
        const overlay = document.getElementById('settingsOverlay');
        const drawer = document.getElementById('settingsDrawer');
        const closeBtn = document.getElementById('closeSettingsBtn');

        if (!openBtn || !overlay || !drawer || !closeBtn) {
            // Сторінка без settings panel (наприклад, error/403 без
            // автентифікації) — тихо виходимо.
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
            currency: document.getElementById('d-section-currency'),
            language: document.getElementById('d-section-language'),
            theme: document.getElementById('d-section-theme'),
            data: document.getElementById('d-section-data'),
            about: document.getElementById('d-section-about')
        };

        function switchSection(sectionId) {
            menuItems.forEach(m => m.classList.remove('active'));
            const targetMenuItem = document.querySelector(
                '#drawerMenu .menu-item[data-section="' + sectionId + '"]');
            if (targetMenuItem) targetMenuItem.classList.add('active');

            Object.keys(sections).forEach(key => {
                const el = sections[key];
                if (el) el.classList.remove('active');
            });
            const targetSection = sections[sectionId];
            if (targetSection) targetSection.classList.add('active');

            localStorage.setItem('settings-drawer-tab', sectionId);
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

        // ==================== ТАЙМЕРИ (clear data / delete account) ====================

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

        // --- Username ---
        if (usernameInput && !usernameInput.disabled) {
            usernameInput.addEventListener('input', function () {
                const pattern = /^[a-zA-Z0-9_]{6,30}$/;
                const valid = pattern.test(this.value);
                this.classList.toggle('is-valid', valid);
                this.classList.toggle('is-invalid', !valid && this.value.length > 0);
                const error = document.getElementById('profileUsernameError');
                if (error) error.style.display =
                    !valid && this.value.length > 0 ? 'block' : 'none';
            });
        }

        // --- Email ---
        if (emailInput && !emailInput.disabled) {
            emailInput.addEventListener('input', function () {
                const pattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
                const valid = pattern.test(this.value);
                this.classList.toggle('is-valid', valid);
                this.classList.toggle('is-invalid', !valid && this.value.length > 0);
                const error = document.getElementById('profileEmailError');
                if (error) error.style.display =
                    !valid && this.value.length > 0 ? 'block' : 'none';
            });
        }

        // --- Password requirements ---
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
                    el.innerHTML = (ok
                            ? '<i class="fas fa-check-circle"></i>'
                            : '<i class="fas fa-circle"></i>')
                        + ' ' + REQ_TEXTS[key];
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
                if (error) error.style.display =
                    !allValid && value.length > 0 ? 'block' : 'none';
            });
        }

        // --- Submit ---
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

    // defer гарантує, що DOM уже розібрано, але listener для
    // DOMContentLoaded все одно зареєструємо — на випадок, якщо
    // хтось перемістить цей скрипт у <head> без defer.
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();