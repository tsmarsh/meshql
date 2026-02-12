export function createBarChart(canvasId, labels, data, label, colors) {
    const ctx = document.getElementById(canvasId);
    if (!ctx) return null;
    return new Chart(ctx, {
        type: 'bar',
        data: {
            labels,
            datasets: [{
                label,
                data,
                backgroundColor: colors || [
                    'rgba(245, 158, 11, 0.7)',
                    'rgba(217, 119, 6, 0.7)',
                    'rgba(180, 83, 9, 0.7)',
                    'rgba(146, 64, 14, 0.7)',
                    'rgba(120, 53, 15, 0.7)'
                ],
                borderRadius: 6
            }]
        },
        options: {
            responsive: true,
            plugins: { legend: { display: false } },
            scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } }
        }
    });
}

export function createDoughnutChart(canvasId, labels, data, colors) {
    const ctx = document.getElementById(canvasId);
    if (!ctx) return null;
    return new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels,
            datasets: [{
                data,
                backgroundColor: colors || [
                    'rgba(34, 197, 94, 0.8)',
                    'rgba(59, 130, 246, 0.8)',
                    'rgba(234, 179, 8, 0.8)',
                    'rgba(239, 68, 68, 0.8)',
                    'rgba(107, 114, 128, 0.8)'
                ],
                borderWidth: 2,
                borderColor: '#fff'
            }]
        },
        options: {
            responsive: true,
            plugins: {
                legend: { position: 'bottom', labels: { padding: 16 } }
            }
        }
    });
}

export function createHorizontalBarChart(canvasId, labels, data, label) {
    const ctx = document.getElementById(canvasId);
    if (!ctx) return null;
    return new Chart(ctx, {
        type: 'bar',
        data: {
            labels,
            datasets: [{
                label,
                data,
                backgroundColor: [
                    'rgba(245, 158, 11, 0.7)',
                    'rgba(217, 119, 6, 0.7)',
                    'rgba(180, 83, 9, 0.7)',
                    'rgba(146, 64, 14, 0.7)',
                    'rgba(120, 53, 15, 0.7)'
                ],
                borderRadius: 6
            }]
        },
        options: {
            indexAxis: 'y',
            responsive: true,
            plugins: { legend: { display: false } },
            scales: { x: { beginAtZero: true } }
        }
    });
}
