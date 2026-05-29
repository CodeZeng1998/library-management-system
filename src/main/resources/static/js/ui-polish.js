(function () {
    var LABEL_ICON_RULES = [
        { test: /\b(search|filter|query)\b/i, icon: 'search' },
        { test: /\b(reset|clear)\b/i, icon: 'rotate-ccw' },
        { test: /\b(save|submit|confirm|apply)\b/i, icon: 'save' },
        { test: /\b(add|create|new)\b/i, icon: 'plus' },
        { test: /\b(edit|update)\b/i, icon: 'pencil-line' },
        { test: /\b(delete|remove|cancel)\b/i, icon: 'trash-2' },
        { test: /\b(export|download)\b/i, icon: 'download' },
        { test: /\b(import|upload)\b/i, icon: 'upload' },
        { test: /\b(previous|prev)\b/i, icon: 'chevron-left' },
        { test: /\b(next)\b/i, icon: 'chevron-right' },
        { test: /\b(login|sign in)\b/i, icon: 'log-in' },
        { test: /\b(logout|sign out)\b/i, icon: 'log-out' },
        { test: /\b(pay|payment)\b/i, icon: 'credit-card' },
        { test: /\b(waive)\b/i, icon: 'badge-minus' },
        { test: /\b(mark read|read)\b/i, icon: 'check-check' },
        { test: /\b(borrow|checkout)\b/i, icon: 'scan-line' },
        { test: /\b(return)\b/i, icon: 'undo-2' },
        { test: /\b(renew|refresh)\b/i, icon: 'refresh-cw' },
        { test: /\b(detail|view)\b/i, icon: 'file-text' },
        { test: /\b(back)\b/i, icon: 'arrow-left' }
    ];

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
        document.querySelectorAll('form[action*="/delete"], form[action*="/cancel"], form[action*="/disable"], form[action*="/waive"]').forEach(function (form) {
            form.classList.add('danger-action-form');
        });
    }

    function actionPathFor(element) {
        if (element.form && element.form.getAttribute('action')) {
            return element.form.getAttribute('action').toLowerCase();
        }
        return (element.getAttribute('href') || '').toLowerCase();
    }

    function iconNameForAction(element) {
        var action = actionPathFor(element);
        if (action.indexOf('/export') >= 0 || action.indexOf('/logs/export') >= 0) {
            return 'download';
        }
        if (action.indexOf('/template') >= 0) {
            return 'file-down';
        }
        if (action.indexOf('/errors') >= 0) {
            return 'file-warning';
        }
        if (action.indexOf('/new') >= 0) {
            return 'plus';
        }
        if (action.indexOf('/edit') >= 0) {
            return 'pencil-line';
        }
        if (action.indexOf('/import') >= 0) {
            return 'upload';
        }
        if (action.indexOf('/delete') >= 0) {
            return 'trash-2';
        }
        if (action.indexOf('/cancel') >= 0) {
            return 'x-circle';
        }
        if (action.indexOf('/disable') >= 0) {
            return 'ban';
        }
        if (action.indexOf('/enable') >= 0) {
            return 'circle-check';
        }
        if (action.indexOf('/pay') >= 0) {
            return 'credit-card';
        }
        if (action.indexOf('/waive') >= 0) {
            return 'badge-minus';
        }
        if (action.indexOf('/renew') >= 0) {
            return 'refresh-cw';
        }
        if (action.indexOf('/return') >= 0) {
            return 'undo-2';
        }
        if (action.indexOf('/read') >= 0) {
            return 'check-check';
        }
        return null;
    }

    function iconNameForLabel(label, element) {
        var actionIcon = element ? iconNameForAction(element) : null;
        var text = (label || '').trim();

        if (actionIcon) {
            return actionIcon;
        }
        if (element && element.classList.contains('layui-btn-danger')) {
            return 'trash-2';
        }
        for (var i = 0; i < LABEL_ICON_RULES.length; i += 1) {
            if (LABEL_ICON_RULES[i].test.test(text)) {
                return LABEL_ICON_RULES[i].icon;
            }
        }
        return null;
    }

    function iconizeControls() {
        if (!window.lucide || typeof window.lucide.createElement !== 'function') {
            return;
        }
        document.querySelectorAll('.layui-btn, .settings-button, .text-button').forEach(function (element) {
            if (element.dataset.iconized === 'true') {
                return;
            }
            if (element.querySelector('[data-lucide]')) {
                element.dataset.iconized = 'true';
                return;
            }
            var iconName = iconNameForLabel(element.textContent, element);
            if (!iconName) {
                return;
            }
            var icon = window.lucide.createElement(iconName);
            icon.classList.add('button-icon');
            icon.setAttribute('aria-hidden', 'true');
            element.prepend(icon);
            element.classList.add('has-icon');
            element.dataset.iconized = 'true';
        });
    }

    function enhanceKeywordInputs() {
        document.querySelectorAll('form input[name="keyword"]:not([data-clear-ready])').forEach(function (input) {
            if (input.closest('.clearable-input')) {
                return;
            }
            var wrapper = document.createElement('span');
            wrapper.className = 'clearable-input';
            input.parentNode.insertBefore(wrapper, input);
            wrapper.appendChild(input);
            input.dataset.clearReady = 'true';

            var button = document.createElement('button');
            button.className = 'clear-input-button';
            button.type = 'button';
            button.title = 'Clear';
            button.setAttribute('aria-label', 'Clear search');
            button.innerHTML = '&times;';
            button.hidden = !input.value;
            wrapper.appendChild(button);

            input.addEventListener('input', function () {
                button.hidden = !input.value;
            });
            button.addEventListener('click', function () {
                input.value = '';
                button.hidden = true;
                input.focus();
            });
        });
    }

    function markPaginationBars() {
        document.querySelectorAll('.table-actions').forEach(function (container) {
            var links = Array.from(container.querySelectorAll('a'));
            var looksLikePagination = links.some(function (link) {
                return /page=/.test(link.getAttribute('href') || '');
            });
            if (looksLikePagination) {
                container.classList.add('pagination-bar');
            }
        });
    }

    function enrichEmptyStates() {
        document.querySelectorAll('.empty-result:not([data-empty-enhanced])').forEach(function (empty) {
            empty.dataset.emptyEnhanced = 'true';
            var icon = document.createElement('span');
            icon.className = 'empty-icon';
            icon.setAttribute('aria-hidden', 'true');
            icon.textContent = '!';
            empty.prepend(icon);
        });
    }

    function decorateSubmittingForms() {
        document.addEventListener('submit', function (event) {
            var form = event.target;
            if (!(form instanceof HTMLFormElement)) {
                return;
            }
            var submitter = event.submitter;
            window.setTimeout(function () {
                if (event.defaultPrevented) {
                    return;
                }
                form.classList.add('is-submitting');
                if (submitter instanceof HTMLElement) {
                    submitter.classList.add('is-loading');
                }
            }, 0);
        }, true);
    }

    function boot() {
        activateCurrentNavigation();
        addMobileTableLabels();
        markDangerForms();
        enhanceKeywordInputs();
        markPaginationBars();
        enrichEmptyStates();
        iconizeControls();
        if (window.lucide && typeof window.lucide.createIcons === 'function') {
            window.lucide.createIcons();
        }
    }

    document.addEventListener('DOMContentLoaded', boot);
    decorateSubmittingForms();
})();
