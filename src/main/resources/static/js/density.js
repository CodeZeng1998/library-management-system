(function () {
    const select = document.getElementById('densityMode');
    if (!select) {
        return;
    }

    const storageKey = 'lms.ui.density';
    const compactSize = 30;
    const comfortableSize = 20;
    const page = document.documentElement;

    function currentDensity() {
        return localStorage.getItem(storageKey) || 'compact';
    }

    function pageSizeFor(value) {
        return value === 'comfortable' ? comfortableSize : compactSize;
    }

    function applyDensity(value) {
        page.dataset.density = value;
        select.value = value;
        localStorage.setItem(storageKey, value);

        const size = pageSizeFor(value);
        const sizeInput = document.querySelector('input[name="size"]');
        if (sizeInput) {
            sizeInput.value = String(size);
        }

        document.querySelectorAll('table.layui-table, .responsive-table').forEach((table) => {
            table.classList.toggle('density-comfortable', value === 'comfortable');
            table.classList.toggle('density-compact', value !== 'comfortable');
        });

        if (document.body.dataset.densityAware === 'true') {
            const url = new URL(window.location.href);
            if (['/books', '/readers', '/borrow'].some((path) => url.pathname.startsWith(path))) {
                url.searchParams.set('size', String(size));
                if (!url.searchParams.get('page')) {
                    url.searchParams.delete('page');
                }
                if (!url.searchParams.has('density')) {
                    window.history.replaceState({}, '', url.toString());
                }
            }
        }
    }

    select.addEventListener('change', () => {
        applyDensity(select.value);
        const url = new URL(window.location.href);
        if (['/books', '/readers', '/borrow'].some((path) => url.pathname.startsWith(path))) {
            url.searchParams.set('size', String(pageSizeFor(select.value)));
            window.location.href = url.toString();
        }
    });

    document.body.dataset.densityAware = 'true';
    applyDensity(currentDensity());
})();
