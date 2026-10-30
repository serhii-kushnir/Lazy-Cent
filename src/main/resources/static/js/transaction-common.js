(function () {
    'use strict';

    let cfg = null;

    function getById(id) { return document.getElementById(id); }

    // ==================== CATEGORIES ====================

    function populateCategories(selectElement, categories) {
        if (!selectElement) return;
        selectElement.innerHTML = '';
        if (!categories || categories.length === 0) {
            const opt = document.createElement('option');
            opt.value = '';
            opt.textContent = cfg.i18n.noCategories;
            opt.disabled = true;
            selectElement.appendChild(opt);
            return;
        }
        categories.forEach(cat => {
            const opt = document.createElement('option');
            opt.value = cat.id;
            opt.textContent = cat.name;
            selectElement.appendChild(opt);
        });
    }

    async function fetchCategories(type) {
        try {
            const url = type === 'EXPENSE' ? '/categories/expense' : '/categories/income';
            const response = await fetch(url);
            return await response.json();
        } catch (err) {
            console.warn('fetchCategories failed:', err);
            return [];
        }
    }

    // ==================== CATEGORY SETTINGS ====================

    function openCategorySettings() {
        const modalEl = getById('transactionModal');
        if (modalEl) {
            const modal = bootstrap.Modal.getInstance(modalEl);
            if (modal) modal.hide();
        }
        setTimeout(function () {
            const drawer = getById('settingsDrawer');
            const overlay = getById('settingsOverlay');
            if (drawer && overlay) {
                drawer.classList.add('open');
                overlay.classList.add('open');
                document.body.style.overflow = 'hidden';
                const categoriesTab = document.querySelector(
                    '#drawerMenu .menu-item[data-section="categories"]');
                if (categoriesTab) categoriesTab.click();
            }
        }, 350);
    }

    // ==================== TRANSACTION TYPE SWITCH ====================

    async function setTransactionType(type) {
        const typeExpenseBtn = getById('typeExpenseBtn');
        const typeIncomeBtn = getById('typeIncomeBtn');
        const transactionType = getById('transactionType');
        const transactionForm = getById('transactionForm');
        const categorySelect = getById('categorySelect');
        const dateInput = getById('transactionDate');

        if (!transactionType || !transactionForm) return;

        const dateVal = dateInput && dateInput.value ? dateInput.value : '';
        const cats = await fetchCategories(type);
        populateCategories(categorySelect, cats);

        transactionType.value = type;
        transactionForm.action = cfg.buildActionUrl(type, dateVal);

        if (type === 'EXPENSE') {
            if (typeExpenseBtn) typeExpenseBtn.className = 'btn active-expense';
            if (typeIncomeBtn) typeIncomeBtn.className = 'btn btn-outline-success';
        } else {
            if (typeIncomeBtn) typeIncomeBtn.className = 'btn active-income';
            if (typeExpenseBtn) typeExpenseBtn.className = 'btn btn-outline-danger';
        }
    }

    function updateFormAction() {
        const transactionType = getById('transactionType');
        const transactionForm = getById('transactionForm');
        const dateInput = getById('transactionDate');
        if (!transactionForm || !transactionType) return;
        const dateVal = dateInput ? dateInput.value : '';
        transactionForm.action = cfg.buildActionUrl(transactionType.value, dateVal);
    }

    // ==================== AMOUNT / QUANTITY ====================

    function initAmountButtons() {
        const amountInput = getById('transactionAmount');
        if (!amountInput) return;

        const minusBtn = getById('amountMinus');
        const plusBtn = getById('amountPlus');

        if (minusBtn) minusBtn.addEventListener('click', function () {
            let val = parseFloat(amountInput.value) || 0;
            if (val > 0) {
                val = Math.round((val - 0.01) * 100) / 100;
                amountInput.value = val.toFixed(2);
            }
        });
        if (plusBtn) plusBtn.addEventListener('click', function () {
            let val = parseFloat(amountInput.value) || 0;
            val = Math.round((val + 0.01) * 100) / 100;
            amountInput.value = val.toFixed(2);
        });
        amountInput.addEventListener('blur', function () {
            amountInput.value = (parseFloat(amountInput.value) || 0).toFixed(2);
        });
    }

    function initQuantityButtons() {
        const quantityInput = getById('transactionQuantity');
        if (!quantityInput) return;

        const minusBtn = getById('quantityMinus');
        const plusBtn = getById('quantityPlus');

        if (minusBtn) minusBtn.addEventListener('click', function () {
            const val = parseInt(quantityInput.value) || 1;
            if (val > 1) quantityInput.value = val - 1;
        });
        if (plusBtn) plusBtn.addEventListener('click', function () {
            const val = parseInt(quantityInput.value) || 1;
            quantityInput.value = val + 1;
        });
    }

    // ==================== CHAR COUNTER ====================

    function initCharCounter() {
        const detailsTextarea = getById('transactionDetails');
        const charCount = getById('charCount');
        if (!detailsTextarea || !charCount) return;
        detailsTextarea.addEventListener('input', function () {
            charCount.textContent = this.value.length;
            charCount.style.color = this.value.length >= 250 ? '#dc3545' : '';
        });
        charCount.textContent = detailsTextarea.value.length;
    }

    // ==================== SUBMIT VALIDATION ====================

    function initSubmitValidation() {
        const transactionForm = getById('transactionForm');
        if (!transactionForm) return;
        transactionForm.addEventListener('submit', function (e) {
            let valid = true;
            const name = getById('transactionName');
            const amount = getById('transactionAmount');
            const category = getById('categorySelect');

            if (name && !name.value.trim()) {
                name.classList.add('is-invalid'); valid = false;
            } else if (name) name.classList.remove('is-invalid');

            if (amount && parseFloat(amount.value) <= 0) {
                amount.classList.add('is-invalid'); valid = false;
            } else if (amount) amount.classList.remove('is-invalid');

            if (category && !category.value) {
                category.classList.add('is-invalid'); valid = false;
            } else if (category) category.classList.remove('is-invalid');

            if (!valid) {
                e.preventDefault();
                const firstError = transactionForm.querySelector('.is-invalid');
                if (firstError) firstError.focus();
            }
        });
    }

    // ==================== MODAL LIFECYCLE ====================

    function initModalLifecycle() {
        const modal = getById('transactionModal');
        if (!modal) return;

        modal.addEventListener('show.bs.modal', function () {
            const timeInput = getById('transactionTime');
            if (timeInput && !timeInput.value) {
                const now = new Date();
                timeInput.value = String(now.getHours()).padStart(2, '0')
                    + ':' + String(now.getMinutes()).padStart(2, '0');
            }
            const dateInput = getById('transactionDate');
            if (dateInput && !dateInput.value) {
                dateInput.value = new Date().toISOString().split('T')[0];
            }
            document.querySelectorAll('.is-invalid')
                .forEach(el => el.classList.remove('is-invalid'));
            updateFormAction();
        });

        modal.addEventListener('hidden.bs.modal', function () {
            const form = getById('transactionForm');
            if (form) form.reset();
            setTransactionType('EXPENSE');
            const quantity = getById('transactionQuantity');
            if (quantity) quantity.value = 1;
            const charCount = getById('charCount');
            if (charCount) charCount.textContent = 0;
            const amount = getById('transactionAmount');
            if (amount) amount.value = '0.00';
            document.querySelectorAll('.is-invalid')
                .forEach(el => el.classList.remove('is-invalid'));
        });
    }

    // ==================== TYPE BUTTONS ====================

    function initTypeButtons() {
        const typeExpenseBtn = getById('typeExpenseBtn');
        const typeIncomeBtn = getById('typeIncomeBtn');

        if (typeExpenseBtn) {
            typeExpenseBtn.addEventListener('click', function (e) {
                e.preventDefault();
                setTransactionType('EXPENSE');
            });
        }
        if (typeIncomeBtn) {
            typeIncomeBtn.addEventListener('click', function (e) {
                e.preventDefault();
                setTransactionType('INCOME');
            });
        }

        const dateInput = getById('transactionDate');
        if (dateInput) dateInput.addEventListener('change', updateFormAction);
    }

    // ==================== WARM KEYS ====================

    function initWarmKeys() {
        document.addEventListener('keydown', function (e) {
            const openModals = document.querySelectorAll('.modal.show');
            if (openModals.length === 0) return;

            if (e.key === 'Escape') {
                openModals.forEach(function (m) {
                    const inst = bootstrap.Modal.getInstance(m);
                    if (inst) inst.hide();
                });
                e.preventDefault();
                return;
            }
            if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
                for (const modal of openModals) {
                    const form = modal.querySelector(
                        'form:not(#deleteForm):not(#deleteDayForm)');
                    if (form) {
                        const submitBtn = form.querySelector('button[type="submit"]');
                        if (submitBtn) { submitBtn.click(); e.preventDefault(); break; }
                        else { form.submit(); e.preventDefault(); break; }
                    }
                }
            }
        });
    }

    // ==================== PUBLIC INIT ====================

    function init(config) {
        cfg = config || {};
        if (!cfg.buildActionUrl) {
            console.error('LazyCentTx: buildActionUrl is required');
            return;
        }
        if (!cfg.i18n) cfg.i18n = {};
        cfg.i18n.noCategories = cfg.i18n.noCategories || 'Немає категорій';

        // Експортуємо як глобальні — щоб інші шаблони (day-detail, totals,
        // all-days) могли викликати без переписування.
        window.populateCategories = populateCategories;
        window.fetchCategories = fetchCategories;
        window.openCategorySettings = openCategorySettings;

        initAmountButtons();
        initQuantityButtons();
        initCharCounter();
        initSubmitValidation();
        initModalLifecycle();
        initTypeButtons();
        initWarmKeys();

        // Початковий стан
        setTransactionType('EXPENSE');
    }

    window.LazyCentTx = {
        init: init,
        populateCategories: populateCategories,
        fetchCategories: fetchCategories,
        openCategorySettings: openCategorySettings,
        setTransactionType: setTransactionType
    };
})();