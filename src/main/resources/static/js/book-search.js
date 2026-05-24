(function () {
    const form = document.getElementById('bookSearchForm');
    if (!form) {
        return;
    }

    const historyBox = document.getElementById('bookSearchHistory');
    const favoritesBox = document.getElementById('bookSearchFavorites');
    const saveButton = document.getElementById('saveBookSearch');
    const clearHistoryButton = document.getElementById('clearBookSearchHistory');
    const clearFavoritesButton = document.getElementById('clearBookSearchFavorites');
    const historyKey = 'lms.bookSearch.history';
    const favoritesKey = 'lms.bookSearch.favorites';

    renderSavedSearches();

    form.addEventListener('submit', () => {
        const query = currentQuery();
        if (query) {
            saveSearch(historyKey, query, currentLabel(), 10);
        }
    });

    saveButton.addEventListener('click', () => {
        const query = currentQuery();
        if (!query) {
            return;
        }
        saveSearch(favoritesKey, query, currentLabel(), 8);
        renderSavedSearches();
    });

    clearHistoryButton?.addEventListener('click', () => {
        localStorage.removeItem(historyKey);
        renderSavedSearches();
    });

    clearFavoritesButton?.addEventListener('click', () => {
        localStorage.removeItem(favoritesKey);
        renderSavedSearches();
    });

    document.querySelectorAll('input[name="sort"], input[name="view"]').forEach((input) => {
        input.addEventListener('change', () => form.requestSubmit());
    });

    function currentQuery() {
        const params = new URLSearchParams(new FormData(form));
        params.delete('page');
        return params.toString();
    }

    function currentLabel() {
        const data = new FormData(form);
        const parts = [];
        const keyword = data.get('keyword');
        const title = data.get('title');
        const author = data.get('author');
        if (keyword) {
            parts.push(keyword);
        }
        if (title) {
            parts.push(`书名:${title}`);
        }
        if (author) {
            parts.push(`作者:${author}`);
        }
        const categories = Array.from(form.querySelectorAll('input[name="categoryIds"]:checked'))
                .map((input) => input.closest('label').innerText.trim());
        if (categories.length) {
            parts.push(categories.join('/'));
        }
        const from = data.get('publishYearFrom');
        const to = data.get('publishYearTo');
        if (from || to) {
            parts.push(`${from || '不限'}-${to || '不限'}`);
        }
        if (data.get('availableOnly')) {
            parts.push('有库存');
        }
        return parts.join(' · ') || '全部图书';
    }

    function saveSearch(key, query, label, limit) {
        const items = readItems(key).filter((item) => item.query !== query);
        items.unshift({ query, label, savedAt: Date.now() });
        localStorage.setItem(key, JSON.stringify(items.slice(0, limit)));
    }

    function readItems(key) {
        try {
            return JSON.parse(localStorage.getItem(key) || '[]');
        } catch (error) {
            return [];
        }
    }

    function renderSavedSearches() {
        renderBox(historyBox, readItems(historyKey), '暂无历史');
        renderBox(favoritesBox, readItems(favoritesKey), '暂无常用检索');
    }

    function renderBox(box, items, emptyText) {
        if (!box) {
            return;
        }
        if (!items.length) {
            box.innerHTML = `<span class="search-chip empty">${emptyText}</span>`;
            return;
        }
        box.innerHTML = items.map((item) => `
            <a class="search-chip" href="/books?${item.query}" title="${escapeHtml(item.label)}">${escapeHtml(item.label)}</a>
        `).join('');
    }

    function escapeHtml(value) {
        return String(value ?? '')
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#39;');
    }
})();
