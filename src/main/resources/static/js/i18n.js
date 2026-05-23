(function () {
    const localeSelect = document.getElementById('localeMode');
    if (!localeSelect) {
        return;
    }

    const supportedLocales = new Set(['zh_CN', 'zh_TW', 'en']);

    function normalizeLocale(value) {
        if (!value) {
            return 'zh_CN';
        }
        const normalized = value.replace('-', '_');
        if (supportedLocales.has(normalized)) {
            return normalized;
        }
        if (normalized.startsWith('zh_TW') || normalized.startsWith('zh_HK') || normalized.startsWith('zh_MO')) {
            return 'zh_TW';
        }
        if (normalized.startsWith('en')) {
            return 'en';
        }
        return 'zh_CN';
    }

    function currentLocale() {
        const params = new URLSearchParams(window.location.search);
        return normalizeLocale(params.get('lang') || localeSelect.dataset.currentLocale || document.documentElement.lang || navigator.language);
    }

    localeSelect.value = currentLocale();
    localeSelect.addEventListener('change', () => {
        const url = new URL(window.location.href);
        url.searchParams.set('lang', localeSelect.value);
        window.location.href = url.toString();
    });
})();
