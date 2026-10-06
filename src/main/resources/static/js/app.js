// IntelliBank Main SPA Application Router & Shell
import { Api } from './api.js';
import { Toast } from './components/Toast.js';

import { LoginView } from './views/LoginView.js';
import { DashboardView } from './views/DashboardView.js';
import { CustomersView } from './views/CustomersView.js';
import { AccountsView } from './views/AccountsView.js';
import { TransactionsView } from './views/TransactionsView.js';
import { FraudAlertsView } from './views/FraudAlertsView.js';
import { RiskAnalysisView } from './views/RiskAnalysisView.js';
import { AnalyticsView } from './views/AnalyticsView.js';
import { NotificationsView } from './views/NotificationsView.js';
import { ReportsView } from './views/ReportsView.js';
import { AuditLogsView } from './views/AuditLogsView.js';
import { UserManagementView } from './views/UserManagementView.js';
import { ErrorView } from './views/ErrorView.js';

class App {
    constructor() {
        this.appElement = document.getElementById('app');
        this.routes = {
            '#/login': LoginView,
            '#/dashboard': DashboardView,
            '#/customers': CustomersView,
            '#/accounts': AccountsView,
            '#/transactions': TransactionsView,
            '#/fraud-alerts': FraudAlertsView,
            '#/risk-analysis': RiskAnalysisView,
            '#/analytics': AnalyticsView,
            '#/notifications': NotificationsView,
            '#/reports': ReportsView,
            '#/audit-log': AuditLogsView,
            '#/users': UserManagementView
        };

        this.initTheme();
        this.initGlobalSearch();
        this.bindEvents();
    }

    initTheme() {
        const theme = localStorage.getItem('intellibank_theme');
        if (theme === 'dark' || (!theme && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
            document.body.classList.add('dark-mode');
        }
    }

    initGlobalSearch() {
        document.addEventListener('keydown', (e) => {
            if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
                e.preventDefault();
                this.openGlobalSearchModal();
            }
        });
    }

    bindEvents() {
        window.addEventListener('hashchange', () => this.handleRoute());
        window.addEventListener('load', () => this.handleRoute());
    }

    async handleRoute() {
        let hash = window.location.hash || '#/dashboard';
        const cleanHash = hash.split('?')[0];

        const token = Api.getToken();
        const user = Api.getUser();

        if (!token && cleanHash !== '#/login') {
            window.location.hash = '#/login';
            return;
        }

        if (token && cleanHash === '#/login') {
            window.location.hash = '#/dashboard';
            return;
        }

        const View = this.routes[cleanHash] || ErrorView;

        if (cleanHash === '#/login') {
            this.appElement.innerHTML = View.render();
            View.bindEvents(() => {
                window.location.hash = '#/dashboard';
            });
        } else {
            this.renderShell(user, cleanHash);
            const contentBody = document.getElementById('content-body');
            contentBody.innerHTML = await View.render();
            if (View.afterRender) await View.afterRender();
            this.updateNotificationCount();
        }
    }

    renderShell(user, activeHash) {
        const role = user ? user.role : 'VIEWER';

        this.appElement.innerHTML = `
            <div class="shell-container">
                <!-- Sidebar -->
                <aside class="sidebar" id="sidebar">
                    <div class="sidebar-header">
                        <a href="#/dashboard" class="logo-brand">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                            </svg>
                            <span>IntelliBank</span>
                        </a>
                    </div>

                    <nav class="sidebar-nav">
                        <div class="nav-section-title">OVERVIEW</div>
                        <a href="#/dashboard" class="nav-item ${activeHash === '#/dashboard' ? 'active' : ''}">
                            <i data-lucide="layout-dashboard" style="width: 20px; height: 20px;"></i>
                            <span>Security Dashboard</span>
                        </a>

                        <div class="nav-section-title">BANKING</div>
                        <a href="#/customers" class="nav-item ${activeHash === '#/customers' ? 'active' : ''}">
                            <i data-lucide="users" style="width: 20px; height: 20px;"></i>
                            <span>Customers</span>
                        </a>
                        <a href="#/accounts" class="nav-item ${activeHash === '#/accounts' ? 'active' : ''}">
                            <i data-lucide="credit-card" style="width: 20px; height: 20px;"></i>
                            <span>Accounts</span>
                        </a>
                        <a href="#/transactions" class="nav-item ${activeHash === '#/transactions' ? 'active' : ''}">
                            <i data-lucide="arrow-left-right" style="width: 20px; height: 20px;"></i>
                            <span>Transactions</span>
                        </a>

                        <div class="nav-section-title">RISK &amp; FRAUD</div>
                        <a href="#/fraud-alerts" class="nav-item ${activeHash === '#/fraud-alerts' ? 'active' : ''}">
                            <i data-lucide="shield-alert" style="width: 20px; height: 20px;"></i>
                            <span>Fraud Alerts</span>
                        </a>
                        <a href="#/risk-analysis" class="nav-item ${activeHash === '#/risk-analysis' ? 'active' : ''}">
                            <i data-lucide="activity" style="width: 20px; height: 20px;"></i>
                            <span>Risk Analysis</span>
                        </a>

                        <div class="nav-section-title">INSIGHTS</div>
                        <a href="#/analytics" class="nav-item ${activeHash === '#/analytics' ? 'active' : ''}">
                            <i data-lucide="bar-chart-3" style="width: 20px; height: 20px;"></i>
                            <span>Analytics</span>
                        </a>
                        <a href="#/reports" class="nav-item ${activeHash === '#/reports' ? 'active' : ''}">
                            <i data-lucide="file-spreadsheet" style="width: 20px; height: 20px;"></i>
                            <span>Reports</span>
                        </a>

                        <div class="nav-section-title">SYSTEM</div>
                        <a href="#/audit-log" class="nav-item ${activeHash === '#/audit-log' ? 'active' : ''}">
                            <i data-lucide="file-text" style="width: 20px; height: 20px;"></i>
                            <span>Audit Trail</span>
                        </a>
                        ${role === 'ADMIN' ? `
                            <a href="#/users" class="nav-item ${activeHash === '#/users' ? 'active' : ''}">
                                <i data-lucide="user-cog" style="width: 20px; height: 20px;"></i>
                                <span>User Management</span>
                            </a>
                        ` : ''}
                    </nav>

                    <div class="sidebar-footer">
                        <div class="user-profile-badge">
                            <div class="avatar">${user ? user.fullName.charAt(0) : 'U'}</div>
                            <div class="user-info">
                                <div class="user-name">${user ? user.fullName : 'User'}</div>
                                <div class="user-role">${user ? user.role : ''}</div>
                            </div>
                        </div>
                    </div>
                </aside>

                <!-- Main Content Wrapper -->
                <div class="main-wrapper">
                    <!-- Top Header -->
                    <header class="header">
                        <div class="header-left">
                            <h1 class="page-title">${this.getPageTitle(activeHash)}</h1>
                        </div>
                        <div class="header-right">
                            <button id="btn-global-search" class="search-trigger">
                                <i data-lucide="search" style="width: 16px; height: 16px;"></i>
                                <span>Search...</span>
                                <span class="kbd-badge">Ctrl+K</span>
                            </button>

                            <a href="#/notifications" class="icon-btn" title="Notifications">
                                <i data-lucide="bell" style="width: 20px; height: 20px;"></i>
                                <span id="header-notif-dot" class="notif-dot" style="display: none;"></span>
                            </a>

                            <button id="btn-theme-toggle" class="icon-btn" title="Toggle Dark Mode">
                                <i data-lucide="moon" style="width: 20px; height: 20px;"></i>
                            </button>

                            <button id="btn-logout" class="icon-btn" title="Logout" style="color: var(--risk-critical);">
                                <i data-lucide="log-out" style="width: 20px; height: 20px;"></i>
                            </button>
                        </div>
                    </header>

                    <main id="content-body" class="content-body">
                        <!-- Dynamic View Injected Here -->
                    </main>
                </div>
            </div>
        `;

        if (window.lucide) window.lucide.createIcons();

        // Bind Shell Events
        document.getElementById('btn-global-search')?.addEventListener('click', () => this.openGlobalSearchModal());
        document.getElementById('btn-theme-toggle')?.addEventListener('click', () => this.toggleTheme());
        document.getElementById('btn-logout')?.addEventListener('click', () => this.logout());
    }

    getPageTitle(hash) {
        const titles = {
            '#/dashboard': 'Executive Dashboard',
            '#/customers': 'Customer Directory',
            '#/accounts': 'Account Operations',
            '#/transactions': 'Transaction Audit Log',
            '#/fraud-alerts': 'Fraud Investigation Center',
            '#/risk-analysis': 'Risk Analysis Engine',
            '#/analytics': 'Analytics & Intelligence',
            '#/notifications': 'Notification Center',
            '#/reports': 'Compliance Reports',
            '#/audit-log': 'System Audit Log',
            '#/users': 'User & Role Management'
        };
        return titles[hash] || 'IntelliBank';
    }

    toggleTheme() {
        document.body.classList.toggle('dark-mode');
        const isDark = document.body.classList.contains('dark-mode');
        localStorage.setItem('intellibank_theme', isDark ? 'dark' : 'light');
    }

    logout() {
        Api.setToken(null);
        Api.setUser(null);
        Toast.success('Logged out');
        window.location.hash = '#/login';
    }

    async updateNotificationCount() {
        try {
            const data = await Api.get('/notifications');
            const dot = document.getElementById('header-notif-dot');
            if (dot) {
                dot.style.display = data.unreadCount > 0 ? 'block' : 'none';
            }
        } catch (e) {}
    }

    openGlobalSearchModal() {
        let modal = document.getElementById('modal-global-search');
        if (!modal) {
            modal = document.createElement('div');
            modal.id = 'modal-global-search';
            modal.className = 'modal-overlay';
            modal.innerHTML = `
                <div class="modal-card" style="max-width: 600px;">
                    <div class="modal-header">
                        <h3 style="font-weight: 700;">Global Search</h3>
                        <button onclick="document.getElementById('modal-global-search').style.display='none'" style="background: none; border: none; font-size: 1.25rem; cursor: pointer;">&times;</button>
                    </div>
                    <div class="modal-body">
                        <input type="text" id="gsearch-input" class="form-input" placeholder="Search customers, accounts, transactions, alerts..." autofocus>
                        <div id="gsearch-results" style="margin-top: 1rem; max-height: 360px; overflow-y: auto;"></div>
                    </div>
                </div>
            `;
            document.body.appendChild(modal);

            document.getElementById('gsearch-input').addEventListener('input', async (e) => {
                const q = e.target.value.trim();
                const resContainer = document.getElementById('gsearch-results');
                if (q.length < 2) {
                    resContainer.innerHTML = '';
                    return;
                }
                try {
                    const res = await Api.get('/search', { q });
                    let html = '';
                    if (res.customers && res.customers.length > 0) {
                        html += `<div style="font-weight: 700; font-size: 0.75rem; color: var(--text-muted); margin-bottom: 0.25rem;">CUSTOMERS</div>`;
                        res.customers.forEach(c => html += `<div style="padding: 0.5rem; border-bottom: 1px solid var(--border-color); cursor: pointer;" onclick="document.getElementById('modal-global-search').style.display='none'; window.location.hash='#/customers?q=${c.customerNumber}';"><strong>${c.firstName} ${c.lastName}</strong> (${c.customerNumber})</div>`);
                    }
                    if (res.accounts && res.accounts.length > 0) {
                        html += `<div style="font-weight: 700; font-size: 0.75rem; color: var(--text-muted); margin: 0.5rem 0 0.25rem 0;">ACCOUNTS</div>`;
                        res.accounts.forEach(a => html += `<div style="padding: 0.5rem; border-bottom: 1px solid var(--border-color); cursor: pointer;" onclick="document.getElementById('modal-global-search').style.display='none'; window.location.hash='#/accounts?q=${a.accountNumber}';"><strong>${a.accountNumber}</strong> ($${a.balance})</div>`);
                    }
                    if (res.transactions && res.transactions.length > 0) {
                        html += `<div style="font-weight: 700; font-size: 0.75rem; color: var(--text-muted); margin: 0.5rem 0 0.25rem 0;">TRANSACTIONS</div>`;
                        res.transactions.forEach(t => html += `<div style="padding: 0.5rem; border-bottom: 1px solid var(--border-color); cursor: pointer;" onclick="document.getElementById('modal-global-search').style.display='none'; window.location.hash='#/transactions?id=${t.id}';"><strong>${t.referenceNumber}</strong> ($${t.amount} - ${t.riskLevel})</div>`);
                    }
                    resContainer.innerHTML = html || '<div style="color: var(--text-muted); padding: 1rem; text-align: center;">No matching records found.</div>';
                } catch (err) {}
            });
        }
        modal.style.display = 'flex';
        document.getElementById('gsearch-input')?.focus();
    }
}

new App();
