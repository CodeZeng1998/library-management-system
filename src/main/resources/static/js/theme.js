(function () {
    const storageKey = 'lms.theme';
    const supportedModes = new Set(['auto', 'light', 'dark', 'eye']);
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const root = document.documentElement;

    function readMode() {
        try {
            const value = localStorage.getItem(storageKey);
            return supportedModes.has(value) ? value : 'auto';
        } catch (error) {
            return 'auto';
        }
    }

    function saveMode(mode) {
        try {
            localStorage.setItem(storageKey, mode);
        } catch (error) {
            // Theme persistence is optional; the current page still updates.
        }
    }

    function resolveMode(mode) {
        if (mode === 'auto') {
            return mediaQuery.matches ? 'dark' : 'light';
        }
        return mode;
    }

    function applyMode(mode) {
        const resolvedMode = resolveMode(mode);
        root.dataset.themeChoice = mode;
        root.dataset.theme = resolvedMode;
        root.style.colorScheme = resolvedMode === 'dark' ? 'dark' : 'light';

        document.querySelectorAll('#themeMode').forEach((select) => {
            select.value = mode;
        });
    }

    function bindControls() {
        document.querySelectorAll('#themeMode').forEach((select) => {
            select.value = readMode();
            select.addEventListener('change', () => {
                const mode = supportedModes.has(select.value) ? select.value : 'auto';
                saveMode(mode);
                applyMode(mode);
            });
        });
    }

    applyMode(readMode());

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', bindControls);
    } else {
        bindControls();
    }

    if (mediaQuery.addEventListener) {
        mediaQuery.addEventListener('change', () => {
            if (readMode() === 'auto') {
                applyMode('auto');
            }
        });
    } else if (mediaQuery.addListener) {
        mediaQuery.addListener(() => {
            if (readMode() === 'auto') {
                applyMode('auto');
            }
        });
    }
})();
