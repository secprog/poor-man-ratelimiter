import React, { useEffect, useState } from 'react';
import { Activity, ShieldCheck, ShieldX, Settings } from 'lucide-react';
import api from '../api';

export default function Dashboard() {
    const [stats, setStats] = useState({
        totalPolicies: 0,
        loading: true
    });

    useEffect(() => {
        fetchStats();
    }, []);

    const fetchStats = async () => {
        try {
            const res = await api.get('/policies');
            setStats({
                totalPolicies: res.data.length,
                loading: false
            });
        } catch (err) {
            console.error("Failed to fetch stats", err);
            if (loading) {
                return (
                    <div className="flex items-center justify-center h-64">
                        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
                    </div>
                );
            }

            return (
        <div>
            <h1 className="text-2xl font-bold mb-6">Dashboard</h1>

                    color="red"
                />
            </div>

            <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
                <h2 className="text-lg font-semibold mb-4">System Status</h2>
                <div className="flex items-center space-x-3">
                    <div className="w-3 h-3 rounded-full bg-green-500 animate-pulse"></div>
                    <span className="text-gray-700">Gateway is running and accepting requests</span>
                </div>
                <div className="mt-4 text-sm text-gray-500">
                    <p>Backend API: <code className="bg-gray-100 px-2 py-1 rounded">http://localhost:8080</code></p>
                    <p className="mt-1">Frontend: <code className="bg-gray-100 px-2 py-1 rounded">http://localhost:3000</code></p>
                </div>
            </div>
        </div >
    );
}

function StatCard({ icon, label, value, subtext, color }) {
    const colorClasses = {
        indigo: 'bg-indigo-50 text-indigo-600',
        green: 'bg-green-50 text-green-600',
        red: 'bg-red-50 text-red-600',
    };

    return (
        <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
            <div className="flex items-center justify-between">
                <div className={`p-3 rounded-lg ${colorClasses[color]}`}>
                    {icon}
                </div>
            </div>
            <p className="text-gray-500 text-sm font-medium mt-4">{label}</p>
            <p className="text-3xl font-bold mt-1">{value}</p>
            {subtext && <p className="text-xs text-gray-400 mt-1">{subtext}</p>}
        </div>
    );
}
