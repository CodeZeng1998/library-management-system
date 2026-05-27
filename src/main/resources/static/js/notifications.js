(function () {
    const selectAll = document.getElementById('selectAllNotifications');
    if (!selectAll) {
        return;
    }

    selectAll.addEventListener('change', () => {
        document.querySelectorAll('.notification-check').forEach((checkbox) => {
            checkbox.checked = selectAll.checked;
        });
    });

    const batchForm = document.getElementById('notificationBatchForm');
    batchForm?.addEventListener('submit', (event) => {
        if (!batchForm.querySelector('.notification-check:checked')) {
            event.preventDefault();
            const message = document.body.dataset.notificationEmpty || 'Please select at least one message.';
            window.alert(message);
        }
    });
})();
