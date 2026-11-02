// Line-item handling for the purchase and sale entry form.
(function () {
    const form = document.getElementById('txn-form');
    if (!form) return;

    const isSale = form.dataset.kind === 'sale';
    const tbody = document.getElementById('lines');
    const paid = document.getElementById('paid');
    const fmt = n => n.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2});
    let subtotal = 0;

    // Keep names as lines[0].x, lines[1].x ... so Spring binds them to the list in order.
    function renumber() {
        tbody.querySelectorAll('tr').forEach((row, i) => {
            row.querySelectorAll('[data-field]').forEach(el => {
                el.name = `lines[${i}].${el.dataset.field}`;
            });
        });
    }

    // Same rounding as the server: tax per line, rounded to paise.
    const round2 = n => Math.round((n + Number.EPSILON) * 100) / 100;

    function refreshRow(row) {
        const option = row.querySelector('.product-select').selectedOptions[0];
        const qtyInput = row.querySelector('.qty');
        const qty = parseFloat(qtyInput.value) || 0;
        const price = parseFloat(row.querySelector('.price').value) || 0;
        const hasProduct = option && option.value;
        const rate = hasProduct ? parseFloat(option.dataset.gst) || 0 : 0;
        const taxable = round2(qty * price);
        const tax = round2(taxable * rate / 100);

        row.querySelector('.stock-cell').textContent =
            hasProduct ? `${option.dataset.stock} ${option.dataset.uom || ''}` : '';
        row.querySelector('.line-gst').textContent = hasProduct ? `${rate}%` : '';
        row.querySelector('.line-tax').textContent = fmt(tax);
        row.querySelector('.line-total').textContent = fmt(taxable + tax);
        qtyInput.classList.toggle('is-invalid',
            Boolean(isSale && hasProduct && qty > parseInt(option.dataset.stock, 10)));
        return {taxable, tax};
    }

    function recalc() {
        let taxable = 0, tax = 0;
        tbody.querySelectorAll('tr').forEach(row => {
            const line = refreshRow(row);
            taxable += line.taxable;
            tax += line.tax;
        });
        subtotal = round2(taxable + tax);
        const paidValue = parseFloat(paid.value) || 0;
        document.getElementById('taxable').textContent = fmt(taxable);
        document.getElementById('tax').textContent = fmt(tax);
        document.getElementById('subtotal').textContent = fmt(subtotal);
        document.getElementById('due').textContent = fmt(subtotal - paidValue);
        paid.classList.toggle('is-invalid', paidValue > subtotal);
    }

    function clearRow(row) {
        row.querySelectorAll('input').forEach(input => {
            input.value = '';
            input.classList.remove('is-invalid');
        });
        row.querySelector('select').selectedIndex = 0;
    }

    tbody.addEventListener('change', e => {
        if (e.target.classList.contains('product-select')) {
            const row = e.target.closest('tr');
            const option = e.target.selectedOptions[0];
            row.querySelector('.price').value = option && option.value ? option.dataset.price : '';
            const qty = row.querySelector('.qty');
            if (option && option.value && !qty.value) qty.value = 1;
        }
        recalc();
    });

    tbody.addEventListener('input', recalc);

    tbody.addEventListener('click', e => {
        const button = e.target.closest('.remove-line');
        if (!button) return;
        const row = button.closest('tr');
        if (tbody.querySelectorAll('tr').length === 1) clearRow(row);
        else row.remove();
        renumber();
        recalc();
    });

    document.getElementById('add-line').addEventListener('click', () => {
        const row = tbody.querySelector('tr').cloneNode(true);
        clearRow(row);
        tbody.appendChild(row);
        renumber();
        recalc();
        row.querySelector('select').focus();
    });

    document.getElementById('pay-full').addEventListener('click', () => {
        paid.value = subtotal.toFixed(2);
        recalc();
    });

    paid.addEventListener('input', recalc);
    renumber();
    recalc();
})();
