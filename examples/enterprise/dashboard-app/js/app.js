import Alpine from 'https://cdn.jsdelivr.net/npm/alpinejs@3.14.8/dist/module.esm.js';
import { createBarChart, createDoughnutChart, createHorizontalBarChart, createLineChart } from './charts.js';

window.Alpine = Alpine;
const LAKE_API = window.LAKE_API || '/lake-api';

async function fetchLake(endpoint) {
    const res = await fetch(`${LAKE_API}${endpoint}`);
    if (!res.ok) throw new Error(`Failed to fetch ${endpoint}`);
    return res.json();
}

Alpine.data('dashboard', () => ({
    summary: {},
    farmOutputs: [],
    henProductivities: [],
    containerInventories: [],
    loading: true,
    error: null,
    charts: {},

    get totalInStorage() {
        return this.containerInventories.reduce((s, ci) => s + (ci.current_eggs || 0), 0);
    },
    get totalDeposited() {
        return this.containerInventories.reduce((s, ci) => s + (ci.total_deposits || 0), 0);
    },
    get totalConsumed() {
        return this.containerInventories.reduce((s, ci) => s + (ci.total_consumed || 0), 0);
    },

    async init() {
        try {
            const [summary, farmOutput, henProd, containerInv] = await Promise.all([
                fetchLake('/api/v1/summary'),
                fetchLake('/api/v1/farm_output'),
                fetchLake('/api/v1/hen_productivity'),
                fetchLake('/api/v1/container_inventory')
            ]);
            this.summary = summary;
            this.farmOutputs = farmOutput.data || [];
            this.henProductivities = henProd.data || [];
            this.containerInventories = containerInv.data || [];

            this.$nextTick(() => this.renderCharts());
        } catch (e) {
            this.error = 'Failed to load data: ' + e.message;
        } finally {
            this.loading = false;
        }
    },

    renderCharts() {
        Object.values(this.charts).forEach(c => c?.destroy());

        // Farm output - total eggs
        const farmLabels = this.farmOutputs.map((f, i) => `Farm ${i + 1}`);
        const farmEggs = this.farmOutputs.map(f => f.total_eggs || 0);
        this.charts.farmOutput = createBarChart('farmOutputChart', farmLabels, farmEggs, 'Total Eggs');

        // Farm output - avg eggs per report
        const farmAvg = this.farmOutputs.map(f => f.avg_eggs_per_report || 0);
        this.charts.farmAvg = createBarChart('farmAvgChart', farmLabels, farmAvg, 'Avg Eggs/Report');

        // Hen productivity - top 10 by total eggs
        const topHens = [...this.henProductivities].sort((a, b) => (b.total_eggs || 0) - (a.total_eggs || 0)).slice(0, 10);
        const henLabels = topHens.map((h, i) => `Hen ${i + 1}`);
        const henEggs = topHens.map(h => h.total_eggs || 0);
        this.charts.henProd = createBarChart('henProductivityChart', henLabels, henEggs, 'Total Eggs');

        // Hen quality rate
        const qualityLabels = topHens.map((h, i) => `Hen ${i + 1}`);
        const qualityRates = topHens.map(h => ((h.quality_rate || 0) * 100));
        this.charts.henQuality = createHorizontalBarChart('henQualityChart', qualityLabels, qualityRates, 'Quality %');

        // Container inventory
        const containerLabels = this.containerInventories.map((ci, i) => `Container ${i + 1}`);
        const containerEggs = this.containerInventories.map(ci => ci.current_eggs || 0);
        this.charts.inventory = createBarChart('inventoryChart', containerLabels, containerEggs, 'Current Eggs');
    }
}));

Alpine.start();
