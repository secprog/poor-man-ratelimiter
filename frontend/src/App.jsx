import React, { useState } from 'react';
import { LayoutDashboard, Shield, Settings as SettingsIcon, Menu } from 'lucide-react';
import { clsx } from 'clsx';
import Dashboard from './pages/Dashboard';
import Policies from './pages/Policies';
import Settings from './pages/Settings';

function App() {
    const [currentPage, setCurrentPage] = useState('dashboard');
    const [sidebarOpen, setSidebarOpen] = useState(true);

    return (
        <div className="flex h-screen bg-gray-50 text-gray-900 font-sans">
            {/* Sidebar */}
            <aside
                className={clsx(
                    "bg-white border-r border-gray-200 transition-all duration-300 flex flex-col",
                    sidebarOpen ? "w-64" : "w-20"
                )}
            >
                <div className="h-16 flex items-center justify-center border-b border-gray-100">
                    <Shield className="w-8 h-8 text-indigo-600" />
                    {sidebarOpen && <span className="ml-3 font-bold text-xl text-gray-800">GatewayGuard</span>}
                </div>

                <nav className="flex-1 p-4 space-y-2">
                    <SidebarItem
                        icon={<LayoutDashboard size={20} />}
                        label="Dashboard"
                        active={currentPage === 'dashboard'}
                        collapsed={!sidebarOpen}
                        onClick={() => setCurrentPage('dashboard')}
                    />
                    <SidebarItem
                        icon={<Shield size={20} />}
                        label="Policies"
                        active={currentPage === 'policies'}
                        collapsed={!sidebarOpen}
                        onClick={() => setCurrentPage('policies')}
                    />
                    <SidebarItem
                        icon={<SettingsIcon size={20} />}
                        label="Settings"
                        active={currentPage === 'settings'}
                        collapsed={!sidebarOpen}
                        onClick={() => setCurrentPage('settings')}
                    />
                </nav>

                <div className="p-4 border-t border-gray-100">
                    <button
                        onClick={() => setSidebarOpen(!sidebarOpen)}
                        className="w-full flex items-center justify-center p-2 rounded-lg hover:bg-gray-100 text-gray-500"
                    >
                        <Menu size={20} />
                    </button>
                </div>
            </aside>

            {/* Main Content */}
            <main className="flex-1 overflow-auto">
                <div className="p-8">
                    {currentPage === 'dashboard' && <Dashboard />}
                    {currentPage === 'policies' && <Policies />}
                    {currentPage === 'settings' && <Settings />}
                </div>
            </main>
        </div>
    );
}

function SidebarItem({ icon, label, active, collapsed, onClick }) {
    return (
        <button
            onClick={onClick}
            className={clsx(
                "flex items-center w-full p-3 rounded-xl transition-all duration-200 group",
                active
                    ? "bg-indigo-50 text-indigo-700 shadow-sm"
                    : "text-gray-500 hover:bg-gray-50 hover:text-gray-700",
                collapsed && "justify-center"
            )}
        >
            {icon}
            {!collapsed && <span className="ml-3 font-medium">{label}</span>}
            {!collapsed && active && <div className="ml-auto w-1.5 h-1.5 rounded-full bg-indigo-500" />}
        </button>
    );
}

export default App;
