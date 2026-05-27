(function () {
    document.addEventListener('submit', (event) => {
        const form = event.target;
        if (!(form instanceof HTMLFormElement)) {
            return;
        }
        if (form.dataset.submitting === 'true') {
            event.preventDefault();
            return;
        }
        const submitter = event.submitter;
        window.setTimeout(() => {
            if (event.defaultPrevented) {
                return;
            }
            form.dataset.submitting = 'true';
            if (submitter instanceof HTMLButtonElement || submitter instanceof HTMLInputElement) {
                submitter.disabled = true;
                submitter.setAttribute('aria-busy', 'true');
            }
            form.querySelectorAll('button[type="submit"], input[type="submit"]').forEach((button) => {
                if (button !== submitter) {
                    button.disabled = true;
                }
            });
        }, 0);
    }, true);
})();
