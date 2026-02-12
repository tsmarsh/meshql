import Alpine from 'https://cdn.jsdelivr.net/npm/alpinejs@3.14.8/dist/module.esm.js';
import { graphqlQuery, restGet, restPost, restPut } from './api.js';

window.Alpine = Alpine;

Alpine.data('opsApp', () => ({
    tab: 'customers',
    customers: [],
    selectedCustomer: null,
    delinquentBills: [],
    loading: false,
    error: null,
    success: null,
    filterStatus: '',
    filterRateClass: '',

    async init() {
        await this.loadCustomers();
    },

    async loadCustomers() {
        this.loading = true;
        this.error = null;
        try {
            const data = await graphqlQuery('/customer/graph', `{
                getAll {
                    id
                    account_number
                    first_name
                    last_name
                    rate_class
                    status
                    city
                    state
                }
            }`);
            this.customers = data.getAll || [];
        } catch (e) {
            this.error = 'Failed to load customers: ' + e.message;
        } finally {
            this.loading = false;
        }
    },

    async selectCustomer(c) {
        this.tab = 'detail';
        this.loading = true;
        this.error = null;
        try {
            const data = await graphqlQuery('/customer/graph', `{
                getById(id: "${c.id}") {
                    id
                    account_number
                    first_name
                    last_name
                    address
                    city
                    state
                    zip
                    phone
                    email
                    rate_class
                    service_type
                    status
                    connected_date
                    disconnected_date
                    budget_billing
                    paperless
                    bills {
                        id
                        bill_date
                        due_date
                        period_from
                        period_to
                        total_amount
                        kwh_used
                        status
                        late
                    }
                    meterReadings {
                        id
                        reading_date
                        reading_value
                        previous_value
                        kwh_used
                        reading_type
                        estimated
                    }
                    payments {
                        id
                        payment_date
                        amount
                        method
                        confirmation_number
                        status
                    }
                }
            }`);
            this.selectedCustomer = data.getById;
        } catch (e) {
            this.error = 'Failed to load customer details: ' + e.message;
        } finally {
            this.loading = false;
        }
    },

    async loadDelinquent() {
        this.loading = true;
        this.error = null;
        try {
            const data = await graphqlQuery('/bill/graph', `{
                getByStatus(status: "delinquent") {
                    id
                    bill_date
                    due_date
                    total_amount
                    kwh_used
                    status
                    customer {
                        first_name
                        last_name
                        account_number
                    }
                }
            }`);
            this.delinquentBills = (data.getByStatus || []).sort(
                (a, b) => new Date(a.due_date) - new Date(b.due_date)
            );
        } catch (e) {
            this.error = 'Failed to load delinquent bills: ' + e.message;
        } finally {
            this.loading = false;
        }
    },

    async updateCustomerStatus(customerId, newStatus) {
        this.error = null;
        this.success = null;
        try {
            await restPut(`/customer/api/${customerId}`, { status: newStatus });
            this.success = `Customer status updated to ${newStatus}.`;
            if (this.selectedCustomer) {
                this.selectedCustomer.status = newStatus;
            }
            await this.loadCustomers();
        } catch (e) {
            this.error = 'Failed to update customer status: ' + e.message;
        }
    },

    get filteredCustomers() {
        return this.customers.filter(c => {
            if (this.filterStatus && c.status !== this.filterStatus) return false;
            if (this.filterRateClass && c.rate_class !== this.filterRateClass) return false;
            return true;
        });
    },

    get totalBills() {
        if (!this.selectedCustomer?.bills) return 0;
        return this.selectedCustomer.bills.length;
    },

    get totalPayments() {
        if (!this.selectedCustomer?.payments) return 0;
        return this.selectedCustomer.payments.length;
    },

    get totalKwh() {
        if (!this.selectedCustomer?.bills) return 0;
        return this.selectedCustomer.bills.reduce((sum, b) => sum + (b.kwh_used || 0), 0);
    },

    formatCurrency(cents) {
        if (cents == null) return '$0.00';
        return '$' + (cents / 100).toFixed(2);
    },

    formatDate(iso) {
        if (!iso) return '--';
        const d = new Date(iso);
        if (isNaN(d.getTime())) return iso;
        return d.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
    },

    statusBadge(status) {
        const map = {
            active: 'badge-success',
            suspended: 'badge-error',
            closed: 'badge-ghost'
        };
        return map[status] || 'badge-ghost';
    },

    billStatusBadge(status) {
        const map = {
            open: 'badge-info',
            paid: 'badge-success',
            delinquent: 'badge-error',
            cancelled: 'badge-ghost'
        };
        return map[status] || 'badge-ghost';
    },

    paymentStatusBadge(status) {
        const map = {
            completed: 'badge-success',
            pending: 'badge-warning',
            failed: 'badge-error',
            refunded: 'badge-ghost'
        };
        return map[status] || 'badge-ghost';
    },

    methodBadge(method) {
        const map = {
            credit_card: 'badge-primary',
            bank_transfer: 'badge-secondary',
            check: 'badge-accent',
            cash: 'badge-ghost',
            online: 'badge-info'
        };
        return map[method] || 'badge-ghost';
    },

    readingTypeBadge(type) {
        const map = {
            actual: 'badge-success',
            estimated: 'badge-warning',
            customer: 'badge-info'
        };
        return map[type] || 'badge-ghost';
    },

    formatMethod(m) {
        return (m || '').replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
    }
}));

Alpine.start();
