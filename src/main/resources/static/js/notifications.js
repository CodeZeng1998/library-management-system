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
})();
