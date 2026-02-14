import Alpine from 'https://cdn.jsdelivr.net/npm/alpinejs@3.14.8/dist/module.esm.js';
import { createBarChart, createDoughnutChart, createHorizontalBarChart, createLineChart } from './charts.js';

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

Alpine.data('dashboard', () => ({
    farmOutputs: [],
    containerInventories: [],
    containers: [],
    henProductivities: [],
    consumptionReports: [],
    loading: true,
    error: null,
    charts: {},
    selectedZone: 'all',

    get zones() {
        const z = new Set(this.farmOutputs.map(f => f.farm?.zone).filter(Boolean));
        return ['all', ...Array.from(z).sort()];
    },

    get filtered() {
        if (this.selectedZone === 'all') return {
            farmOutputs: this.farmOutputs,
            containerInventories: this.containerInventories,
            containers: this.containers,
            henProductivities: this.henProductivities,
            consumptionReports: this.consumptionReports
        };
        const zone = this.selectedZone;
        const farmOutputs = this.farmOutputs.filter(f => f.farm?.zone === zone);
        const containers = this.containers.filter(c => c.zone === zone);
        const containerIds = new Set(containers.map(c => c.id));
        const containerInventories = this.containerInventories.filter(ci => containerIds.has(ci.container_id));
        const consumptionReports = this.consumptionReports.filter(cr => {
            const cont = this.containers.find(c => c.id === cr.container_id);
            return cont && cont.zone === zone;
        });
        return { farmOutputs, containerInventories, containers, henProductivities: this.henProductivities, consumptionReports };
    },

    get totalFarms() { return this.filtered.farmOutputs.length; },
    get totalEggsWeek() { return this.filtered.farmOutputs.reduce((s, f) => s + (f.eggs_week || 0), 0); },
    get activeHens() { return this.filtered.farmOutputs.reduce((s, f) => s + (f.active_hens || 0), 0); },
    get avgEggsPerHenPerWeek() {
        if (this.activeHens === 0) return '0.0';
        return (this.totalEggsWeek / this.activeHens).toFixed(1);
    },

    get topFarms() {
        return [...this.filtered.farmOutputs]
            .sort((a, b) => (b.eggs_week || 0) - (a.eggs_week || 0))
            .slice(0, 10);
    },

    get topHens() {
        return [...this.filtered.henProductivities]
            .sort((a, b) => (b.total_eggs || 0) - (a.total_eggs || 0))
            .slice(0, 10);
    },

    get totalInStorage() {
        return this.filtered.containerInventories.reduce((s, ci) => s + (ci.current_eggs || 0), 0);
    },
    get totalConsumed() {
        return this.filtered.consumptionReports.reduce((s, cr) => s + (cr.eggs || 0), 0);
    },

    async init() {
        try {
            const [farmOutputs, containerInventories, containers, henProductivities, consumptionReports] = await Promise.all([
                gqlAll('/farm_output/graph', '{ getAll { id farm_id farm_type eggs_today eggs_week eggs_month active_hens total_hens avg_per_hen_per_week farm { id name farm_type zone } } }', 'getAll'),
                gqlAll('/container_inventory/graph', '{ getAll { id container_id current_eggs total_deposits total_withdrawals total_transfers_in total_transfers_out total_consumed utilization_pct container { id name container_type capacity zone } } }', 'getAll'),
                gqlAll('/container/graph', '{ getAll { id name container_type capacity zone } }', 'getAll'),
                gqlAll('/hen_productivity/graph', '{ getAll { id hen_id eggs_today eggs_week eggs_month avg_per_week total_eggs quality_rate hen { id name breed status } } }', 'getAll'),
                gqlAll('/consumption_report/graph', '{ getAll { id consumer_id container_id eggs timestamp purpose consumer { id name consumer_type zone } container { id name container_type zone } } }', 'getAll')
            ]);
            this.farmOutputs = farmOutputs;
            this.containerInventories = containerInventories;
            this.containers = containers;
            this.henProductivities = henProductivities;
            this.consumptionReports = consumptionReports;

            this.$watch('selectedZone', () => {
                this.$nextTick(() => this.renderCharts());
            });

            this.$nextTick(() => this.renderCharts());
        } catch (e) {
            this.error = 'Failed to load data: ' + e.message;
        } finally {
            this.loading = false;
        }
    },

    renderCharts() {
        Object.values(this.charts).forEach(c => c?.destroy());
        const d = this.filtered;

        // Eggs by farm_type
        const farmTypes = ['megafarm', 'local_farm', 'homestead'];
        const farmTypeLabels = ['Megafarm', 'Local Farm', 'Homestead'];
        const eggsByType = farmTypes.map(ft =>
            d.farmOutputs.filter(f => f.farm_type === ft).reduce((s, f) => s + (f.eggs_week || 0), 0)
        );
        this.charts.eggsByType = createBarChart('eggsByTypeChart', farmTypeLabels, eggsByType, 'Eggs/Week');

        // Farm type distribution
        const farmTypeCounts = farmTypes.map(ft =>
            d.farmOutputs.filter(f => f.farm_type === ft).length
        );
        this.charts.farmDist = createDoughnutChart('farmDistChart', farmTypeLabels, farmTypeCounts);

        // Container utilization
        const containerLabels = d.containerInventories.map(ci => ci.container?.name || ci.container_id);
        const utilPcts = d.containerInventories.map(ci => ci.utilization_pct || 0);
        this.charts.utilization = createHorizontalBarChart('utilizationChart', containerLabels, utilPcts, 'Utilization %');

        // Consumption by purpose
        const purposeCounts = {};
        d.consumptionReports.forEach(cr => {
            const p = cr.purpose || 'unknown';
            purposeCounts[p] = (purposeCounts[p] || 0) + cr.eggs;
        });
        const purposeLabels = Object.keys(purposeCounts).map(p =>
            p.replace(/\b\w/g, c => c.toUpperCase())
        );
        this.charts.consumption = createDoughnutChart('consumptionChart', purposeLabels, Object.values(purposeCounts));
    },

    titleCase(s) {
        return (s || '').replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
    },

    formatDate(iso) {
        return new Date(iso).toLocaleDateString();
    }
}));

Alpine.start();
