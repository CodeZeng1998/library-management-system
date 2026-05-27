(function () {
    function normalizePath(path) {
        if (!path || path === '/') {
            return '/';
        }
        return path.replace(/\/+$/, '') || '/';
    }

    function activateCurrentNavigation() {
        var currentPath = normalizePath(window.location.pathname);
        var bestMatch = null;
        var bestLength = -1;

        document.querySelectorAll('.sidebar .nav-link[href]').forEach(function (link) {
            var href;
            try {
                href = new URL(link.getAttribute('href'), window.location.origin);
            } catch (error) {
                return;
            }

            if (href.origin !== window.location.origin) {
                return;
            }

            var linkPath = normalizePath(href.pathname);
            var exactMatch = currentPath === linkPath;
            var sectionMatch = linkPath !== '/' && currentPath.indexOf(linkPath + '/') === 0;

            if ((exactMatch || sectionMatch) && linkPath.length > bestLength) {
                bestMatch = link;
                bestLength = linkPath.length;
            }
        });

        if (bestMatch) {
            bestMatch.classList.add('is-active');
            bestMatch.setAttribute('aria-current', 'page');
        }
    }

    function addMobileTableLabels() {
        document.querySelectorAll('.responsive-table').forEach(function (table) {
            var headers = Array.from(table.querySelectorAll('thead th')).map(function (header) {
                return header.textContent.trim();
            });

            table.querySelectorAll('tbody tr').forEach(function (row) {
                Array.from(row.children).forEach(function (cell, index) {
                    if (!cell.hasAttribute('data-label') && headers[index]) {
                        cell.setAttribute('data-label', headers[index]);
                    }
                });
            });
        });
    }

    function markDangerForms() {
        document.querySelectorAll('form[action*="/delete"], form[action*="/cancel"], form[action*="/disable"]').forEach(function (form) {
            form.classList.add('danger-action-form');
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        activateCurrentNavigation();
        addMobileTableLabels();
        markDangerForms();
    });
})();
