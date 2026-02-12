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

Alpine.data('reportingApp', () => ({
        warehouses: [],
        shipments: [],
        packages: [],
        trackingUpdates: [],
        loading: true,
        error: null,
        charts: {},

        // Computed stats
        get totalWarehouses() { return this.warehouses.length; },
        get totalPackages() { return this.packages.length; },
        get activeShipments() {
            return this.shipments.filter(s => s.status !== 'delivered' && s.status !== 'cancelled').length;
        },
        get deliveryRate() {
            if (this.shipments.length === 0) return 0;
            const delivered = this.shipments.filter(s => s.status === 'delivered').length;
            return Math.round((delivered / this.shipments.length) * 100);
        },
        get recentUpdates() {
            return [...this.trackingUpdates]
                .sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp))
                .slice(0, 10);
        },

        async init() {
            try {
                const [warehouses, shipments, packages, updates] = await Promise.all([
                    gqlAll('/warehouse/graph', '{ getAll { id name city state } }', 'getAll'),
                    gqlAll('/shipment/graph', '{ getAll { id destination carrier status estimated_delivery warehouse_id } }', 'getAll'),
                    gqlAll('/package/graph', '{ getAll { id tracking_number description weight warehouse_id shipment_id } }', 'getAll'),
                    gqlAll('/tracking_update/graph', '{ getAll { id status location timestamp notes package_id } }', 'getAll')
                ]);
                this.warehouses = warehouses;
                this.shipments = shipments;
                this.packages = packages;
                this.trackingUpdates = updates;

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

            // Package volume by warehouse
            const whNames = this.warehouses.map(w => w.name?.replace('SwiftShip ', '') || 'Unknown');
            const whPackageCounts = this.warehouses.map(w =>
                this.packages.filter(p => p.warehouse_id === w.id).length
            );
            this.charts.volume = createBarChart('volumeChart', whNames, whPackageCounts, 'Packages');

            // Shipment status breakdown
            const statusCounts = {};
            this.shipments.forEach(s => {
                const st = s.status || 'unknown';
                statusCounts[st] = (statusCounts[st] || 0) + 1;
            });
            const statusLabels = Object.keys(statusCounts).map(s =>
                s.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
            );
            this.charts.status = createDoughnutChart('statusChart', statusLabels, Object.values(statusCounts));

            // Package count by carrier
            const carrierCounts = {};
            this.shipments.forEach(s => {
                const carrier = s.carrier || 'Unknown';
                const pkgCount = this.packages.filter(p => p.shipment_id === s.id).length;
                carrierCounts[carrier] = (carrierCounts[carrier] || 0) + pkgCount;
            });
            this.charts.carrier = createHorizontalBarChart(
                'carrierChart',
                Object.keys(carrierCounts),
                Object.values(carrierCounts),
                'Packages'
            );
        },

        formatStatus(s) {
            return (s || '').replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
        },

        formatDate(ts) {
            return new Date(ts).toLocaleString();
        },

        statusColor(s) {
            const colors = {
                label_created: 'badge-ghost', picked_up: 'badge-info',
                arrived_at_facility: 'badge-info', departed_facility: 'badge-info',
                in_transit: 'badge-info', out_for_delivery: 'badge-warning',
                delivered: 'badge-success', delivery_attempted: 'badge-warning',
                exception: 'badge-error', returned_to_sender: 'badge-error'
            };
            return colors[s] || 'badge-ghost';
        }
    }));

Alpine.start();
