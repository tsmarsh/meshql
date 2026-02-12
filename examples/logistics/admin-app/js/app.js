import Alpine from 'https://cdn.jsdelivr.net/npm/alpinejs@3.14.8/dist/module.esm.js';
import { graphqlQuery, restGet, restPost, restPut } from './api.js';

window.Alpine = Alpine;

Alpine.data('adminApp', () => ({
        tab: 'dashboard',
        warehouses: [],
        shipments: [],
        packages: [],
        trackingUpdates: [],
        selectedWarehouse: null,
        loading: false,
        error: null,
        success: null,

        // Forms
        newPackage: {
            tracking_number: '',
            description: '',
            weight: '',
            recipient: '',
            recipient_address: '',
            warehouse_id: '',
            shipment_id: ''
        },
        newShipment: {
            destination: '',
            carrier: 'FedEx',
            status: 'preparing',
            estimated_delivery: '',
            warehouse_id: ''
        },
        newUpdate: {
            package_id: '',
            status: 'label_created',
            location: '',
            timestamp: new Date().toISOString().slice(0, 16),
            notes: ''
        },
        selectedPackage: null,
        packageUpdates: [],

        async init() {
            await this.loadWarehouses();
        },

        async loadWarehouses() {
            try {
                const data = await graphqlQuery('/warehouse/graph', '{ getAll { id name address city state zip capacity } }');
                this.warehouses = data.getAll || [];
            } catch (e) {
                this.error = 'Failed to load warehouses: ' + e.message;
            }
        },

        async selectWarehouse(w) {
            this.selectedWarehouse = w;
            this.loading = true;
            this.error = null;
            try {
                const data = await graphqlQuery('/warehouse/graph', `{
                    getById(id: "${w.id}") {
                        id name city state
                        shipments { id destination carrier status estimated_delivery }
                        packages { id tracking_number description weight recipient }
                    }
                }`);
                const wh = data.getById;
                this.shipments = wh.shipments || [];
                this.packages = wh.packages || [];
            } catch (e) {
                this.error = 'Failed to load warehouse data: ' + e.message;
            } finally {
                this.loading = false;
            }
        },

        // Package creation
        async createPackage() {
            this.error = null;
            this.success = null;
            if (!this.newPackage.tracking_number || !this.newPackage.description) {
                this.error = 'Tracking number and description are required.';
                return;
            }
            try {
                const pkg = await restPost('/package/api', {
                    ...this.newPackage,
                    weight: parseFloat(this.newPackage.weight) || 1.0
                });
                // Create initial tracking update
                await restPost('/tracking_update/api', {
                    package_id: pkg.id,
                    status: 'label_created',
                    location: this.selectedWarehouse
                        ? `${this.selectedWarehouse.city}, ${this.selectedWarehouse.state}`
                        : 'Unknown',
                    timestamp: new Date().toISOString(),
                    notes: 'Shipping label created'
                });
                this.success = `Package ${pkg.tracking_number} created successfully!`;
                this.resetPackageForm();
                if (this.selectedWarehouse) await this.selectWarehouse(this.selectedWarehouse);
            } catch (e) {
                this.error = 'Failed to create package: ' + e.message;
            }
        },

        resetPackageForm() {
            this.newPackage = {
                tracking_number: '',
                description: '',
                weight: '',
                recipient: '',
                recipient_address: '',
                warehouse_id: this.selectedWarehouse?.id || '',
                shipment_id: ''
            };
        },

        generateTrackingNumber() {
            const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
            let code = 'PKG-';
            for (let i = 0; i < 8; i++) code += chars[Math.floor(Math.random() * chars.length)];
            this.newPackage.tracking_number = code;
        },

        // Shipment creation
        async createShipment() {
            this.error = null;
            this.success = null;
            if (!this.newShipment.destination) {
                this.error = 'Destination is required.';
                return;
            }
            try {
                const shipment = await restPost('/shipment/api', this.newShipment);
                this.success = `Shipment to ${shipment.destination} created!`;
                this.newShipment = {
                    destination: '',
                    carrier: 'FedEx',
                    status: 'preparing',
                    estimated_delivery: '',
                    warehouse_id: this.selectedWarehouse?.id || ''
                };
                if (this.selectedWarehouse) await this.selectWarehouse(this.selectedWarehouse);
            } catch (e) {
                this.error = 'Failed to create shipment: ' + e.message;
            }
        },

        // Tracking
        async selectPackageForTracking(pkg) {
            this.selectedPackage = pkg;
            this.newUpdate.package_id = pkg.id;
            try {
                const data = await graphqlQuery('/package/graph', `{
                    getById(id: "${pkg.id}") {
                        trackingUpdates { id status location timestamp notes }
                    }
                }`);
                this.packageUpdates = (data.getById.trackingUpdates || []).sort(
                    (a, b) => new Date(b.timestamp) - new Date(a.timestamp)
                );
            } catch (e) {
                this.error = 'Failed to load tracking: ' + e.message;
            }
        },

        async addTrackingUpdate() {
            this.error = null;
            this.success = null;
            try {
                await restPost('/tracking_update/api', {
                    ...this.newUpdate,
                    timestamp: new Date(this.newUpdate.timestamp).toISOString()
                });
                this.success = 'Tracking update added!';
                this.newUpdate.notes = '';
                this.newUpdate.timestamp = new Date().toISOString().slice(0, 16);
                await this.selectPackageForTracking(this.selectedPackage);
            } catch (e) {
                this.error = 'Failed to add update: ' + e.message;
            }
        },

        async markDelivered() {
            this.newUpdate.status = 'delivered';
            this.newUpdate.notes = 'Package delivered';
            this.newUpdate.timestamp = new Date().toISOString().slice(0, 16);
            await this.addTrackingUpdate();
        },

        formatStatus(s) {
            return (s || '').replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
        },

        statusColor(s) {
            const colors = {
                preparing: 'badge-ghost', in_transit: 'badge-info',
                out_for_delivery: 'badge-warning', delivered: 'badge-success',
                delayed: 'badge-error', cancelled: 'badge-error',
                label_created: 'badge-ghost', picked_up: 'badge-info',
                arrived_at_facility: 'badge-info', departed_facility: 'badge-info',
                exception: 'badge-error', delivery_attempted: 'badge-warning',
                returned_to_sender: 'badge-error'
            };
            return colors[s] || 'badge-ghost';
        }
    }));

Alpine.start();
