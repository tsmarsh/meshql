import Alpine from 'https://cdn.jsdelivr.net/npm/alpinejs@3.14.8/dist/module.esm.js';
import { createBarChart, createDoughnutChart, createHorizontalBarChart } from './charts.js';

window.Alpine = Alpine;
const API_BASE = window.API_BASE || '/api';

async function gqlAll(endpoint, query, field) {
    const res = await fetch(`${API_BASE}${endpoint}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ query })
    });
    if (!res.ok) throw new Error(`Failed to fetch ${endpoint}`);
    const json = await res.json();
    if (json.errors) throw new Error(json.errors[0].message);
    return json.data[field] || [];
}

Alpine.data('analyticsApp', () => ({
        customers: [],
        bills: [],
        meterReadings: [],
        payments: [],
        loading: true,
        error: null,
        charts: {},

        // Computed stats
        get totalCustomers() { return this.customers.length; },
        get totalRevenue() {
            const sum = this.bills.reduce((acc, b) => acc + (parseFloat(b.total_amount) || 0), 0);
            return this.formatCurrency(sum);
        },
        get totalKwh() {
            return this.bills.reduce((acc, b) => acc + (parseFloat(b.kwh_used) || 0), 0);
        },
        get delinquencyRate() {
            if (this.bills.length === 0) return 0;
            const delinquent = this.bills.filter(b => b.status === 'delinquent').length;
            return Math.round((delinquent / this.bills.length) * 100);
        },
        get recentPayments() {
            return [...this.payments]
                .sort((a, b) => new Date(b.payment_date) - new Date(a.payment_date))
                .slice(0, 10);
        },

        async init() {
            try {
                const [customers, bills, meterReadings, payments] = await Promise.all([
                    gqlAll('/customer/graph', '{ getAll { id account_number first_name last_name rate_class status } }', 'getAll'),
                    gqlAll('/bill/graph', '{ getAll { id customer_id bill_date total_amount kwh_used status late } }', 'getAll'),
                    gqlAll('/meter_reading/graph', '{ getAll { id customer_id reading_date kwh_used reading_type estimated } }', 'getAll'),
                    gqlAll('/payment/graph', '{ getAll { id customer_id payment_date amount method status } }', 'getAll')
                ]);
                this.customers = customers;
                this.bills = bills;
                this.meterReadings = meterReadings;
                this.payments = payments;

                this.$nextTick(() => this.renderCharts());
            } catch (e) {
                this.error = 'Failed to load data: ' + e.message;
            } finally {
                this.loading = false;
            }
        },

        renderCharts() {
            // Destroy previous charts
            Object.values(this.charts).forEach(c => c?.destroy());

            // Revenue by Rate Class
            const rateClasses = ['residential', 'commercial', 'industrial'];
            const rateLabels = ['Residential', 'Commercial', 'Industrial'];
            const revenueByRate = rateClasses.map(rc => {
                const customerIds = this.customers
                    .filter(c => c.rate_class === rc)
                    .map(c => c.id);
                return this.bills
                    .filter(b => customerIds.includes(b.customer_id))
                    .reduce((sum, b) => sum + (parseFloat(b.total_amount) || 0), 0);
            });
            this.charts.revenue = createBarChart('revenueChart', rateLabels, revenueByRate, 'Revenue ($)');

            // Monthly Consumption
            const monthlyKwh = {};
            this.bills.forEach(b => {
                if (!b.bill_date) return;
                const month = b.bill_date.substring(0, 7);
                monthlyKwh[month] = (monthlyKwh[month] || 0) + (parseFloat(b.kwh_used) || 0);
            });
            const sortedMonths = Object.keys(monthlyKwh).sort();
            this.charts.consumption = createBarChart(
                'consumptionChart',
                sortedMonths,
                sortedMonths.map(m => monthlyKwh[m]),
                'kWh'
            );

            // Payment Method Breakdown
            const methodCounts = {};
            this.payments.forEach(p => {
                const method = p.method || 'unknown';
                methodCounts[method] = (methodCounts[method] || 0) + 1;
            });
            const methodLabels = Object.keys(methodCounts).map(m =>
                m.replace(/\b\w/g, c => c.toUpperCase())
            );
            this.charts.method = createDoughnutChart('methodChart', methodLabels, Object.values(methodCounts));

            // Delinquency by Rate Class
            const delinquencyByRate = rateClasses.map(rc => {
                const customerIds = this.customers
                    .filter(c => c.rate_class === rc)
                    .map(c => c.id);
                return this.bills
                    .filter(b => customerIds.includes(b.customer_id) && b.status === 'delinquent')
                    .length;
            });
            this.charts.delinquency = createHorizontalBarChart(
                'delinquencyChart',
                rateLabels,
                delinquencyByRate,
                'Delinquent Bills'
            );
        },

        formatCurrency(n) {
            return '$' + n.toFixed(2);
        },

        formatDate(iso) {
            return new Date(iso).toLocaleDateString();
        },

        methodBadge(method) {
            const badges = {
                web: 'badge-info',
                electronic: 'badge-primary',
                check: 'badge-warning',
                cash: 'badge-success',
                phone: 'badge-ghost'
            };
            return badges[method] || 'badge-ghost';
        },

        statusBadge(status) {
            const badges = {
                completed: 'badge-success',
                pending: 'badge-warning',
                failed: 'badge-error',
                reversed: 'badge-ghost'
            };
            return badges[status] || 'badge-ghost';
        },

        titleCase(s) {
            return (s || '').replace(/\b\w/g, c => c.toUpperCase());
        }
    }));

Alpine.start();
