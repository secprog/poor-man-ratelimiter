import React, { useEffect, useRef, useState } from 'react';
import { Activity, TrendingUp, TrendingDown, BarChart3 } from 'lucide-react';
import {
    LineChart,
    Line,
    AreaChart,
    Area,
    PieChart,
    Pie,
    Cell,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    Legend,
    ResponsiveContainer
} from 'recharts';
import analyticsWs from '../utils/websocket';

const COLORS = {
    allowed: '#10b981',
    blocked: '#ef4444',
    primary: '#6366f1',
    secondary: '#8b5cf6'
};

export default function Analytics() {
    const [timeSeriesData, setTimeSeriesData] = useState([]);
    const [stats, setStats] = useState({
        requestsAllowed: 0,
        requestsBlocked: 0,
        activePolicies: 0,
        isConnected: false
    });
    const [timeRange, setTimeRange] = useState(1); // hours
    const [loading, setLoading] = useState(true);
    const fullSeriesRef = useRef([]);
    const lastSummaryRef = useRef(null);

    useEffect(() => {
        // Subscribe to WebSocket for real-time updates
        const unsubscribe = analyticsWs.subscribe((message) => {
            if (!message || !message.type) {
                return;
            }

            if (message.type === 'snapshot') {
                handleSnapshot(message.payload);
                return;
            }

            if (message.type === 'summary') {
                applySummaryUpdate(message.payload);
            }
        });

        return () => unsubscribe();
    }, []);

    useEffect(() => {
        const filtered = filterSeries(fullSeriesRef.current, timeRange);
        setTimeSeriesData(filtered);

        const totals = computeTotals(filtered);
        setStats(prev => ({
            ...prev,
            requestsAllowed: totals.allowed,
            requestsBlocked: totals.blocked
        }));
    }, [timeRange]);

    const computeTotals = (series) => series.reduce(
        (acc, point) => ({
            allowed: acc.allowed + (point.allowed || 0),
            blocked: acc.blocked + (point.blocked || 0)
        }),
        { allowed: 0, blocked: 0 }
    );

    const formatSeries = (series) => (series || []).map(point => ({
        time: new Date(point.timestamp).toLocaleTimeString(),
        timestamp: new Date(point.timestamp).getTime(),
        allowed: point.allowed || 0,
        blocked: point.blocked || 0,
        total: (point.allowed || 0) + (point.blocked || 0)
    }));

    const filterSeries = (series, hours) => {
        if (!Array.isArray(series) || series.length === 0) {
            return [];
        }
        const cutoff = Date.now() - hours * 60 * 60 * 1000;
        return series.filter(point => point.timestamp >= cutoff);
    };

    const handleSnapshot = (snapshot) => {
        if (!snapshot) {
            return;
        }

        const summary = snapshot.summary || {};
        const formattedSeries = formatSeries(snapshot.timeseries || []);

        fullSeriesRef.current = formattedSeries;

        const filtered = filterSeries(formattedSeries, timeRange);
        setTimeSeriesData(filtered);

        const totals = computeTotals(filtered);
        setStats(prev => ({
            ...prev,
            requestsAllowed: totals.allowed,
            requestsBlocked: totals.blocked,
            activePolicies: summary.activePolicies || prev.activePolicies,
            isConnected: true
        }));

        lastSummaryRef.current = {
            allowed: summary.requestsAllowed || 0,
            blocked: summary.requestsBlocked || 0
        };

        setLoading(false);
    };

    const applySummaryUpdate = (update) => {
        if (!update) {
            return;
        }

        const summary = {
            allowed: update.requestsAllowed || 0,
            blocked: update.requestsBlocked || 0
        };

        setStats(prev => ({
            ...prev,
            activePolicies: update.activePolicies || prev.activePolicies,
            isConnected: true
        }));

        if (lastSummaryRef.current) {
            const deltaAllowed = Math.max(summary.allowed - lastSummaryRef.current.allowed, 0);
            const deltaBlocked = Math.max(summary.blocked - lastSummaryRef.current.blocked, 0);

            if (deltaAllowed > 0 || deltaBlocked > 0) {
                const bucketTime = update.timestamp ? new Date(update.timestamp) : new Date();
                bucketTime.setSeconds(0, 0);
                const bucketTimestamp = bucketTime.getTime();

                const updated = [...fullSeriesRef.current];
                const lastPoint = updated[updated.length - 1];

                if (lastPoint && lastPoint.timestamp === bucketTimestamp) {
                    lastPoint.allowed += deltaAllowed;
                    lastPoint.blocked += deltaBlocked;
                    lastPoint.total = lastPoint.allowed + lastPoint.blocked;
                } else {
                    updated.push({
                        time: bucketTime.toLocaleTimeString(),
                        timestamp: bucketTimestamp,
                        allowed: deltaAllowed,
                        blocked: deltaBlocked,
                        total: deltaAllowed + deltaBlocked
                    });
                }

                fullSeriesRef.current = updated.slice(-2000);

                const filtered = filterSeries(fullSeriesRef.current, timeRange);
                setTimeSeriesData(filtered);

                const totals = computeTotals(filtered);
                setStats(prevStats => ({
                    ...prevStats,
                    requestsAllowed: totals.allowed,
                    requestsBlocked: totals.blocked
                }));
            }
        }

        lastSummaryRef.current = summary;
    };

    const pieData = [
        { name: 'Allowed', value: stats.requestsAllowed, color: COLORS.allowed },
        { name: 'Blocked', value: stats.requestsBlocked, color: COLORS.blocked }
    ];

    const totalRequests = stats.requestsAllowed + stats.requestsBlocked;
    const blockRate = totalRequests > 0 ? ((stats.requestsBlocked / totalRequests) * 100).toFixed(1) : 0;
    const allowRate = totalRequests > 0 ? ((stats.requestsAllowed / totalRequests) * 100).toFixed(1) : 0;

    if (loading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex justify-between items-center">
                <div>
                    <h1 className="text-2xl font-bold flex items-center gap-2">
                        <BarChart3 className="w-7 h-7 text-indigo-600" />
                        Real-Time Analytics
                    </h1>
                    <p className="text-gray-500 mt-1">Monitor traffic patterns and system performance</p>
                </div>
                {stats.isConnected && (
                    <div className="flex items-center space-x-2 text-sm text-green-600 bg-green-50 px-4 py-2 rounded-full">
                        <div className="w-2 h-2 rounded-full bg-green-500 animate-pulse"></div>
                        <span className="font-medium">Live Updates</span>
                    </div>
                )}
            </div>

            {/* Time Range Selector */}
            <div className="flex gap-2">
                {[1, 6, 24].map(hours => (
                    <button
                        key={hours}
                        onClick={() => setTimeRange(hours)}
                        className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                            timeRange === hours
                                ? 'bg-indigo-600 text-white'
                                : 'bg-white text-gray-700 hover:bg-gray-50 border border-gray-200'
                        }`}
                    >
                        Last {hours}h
                    </button>
                ))}
            </div>

            {/* Stats Cards */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <StatCard
                    icon={<Activity className="w-6 h-6" />}
                    title="Total Requests"
                    value={totalRequests.toLocaleString()}
                    trend={null}
                    color="bg-blue-500"
                />
                <StatCard
                    icon={<TrendingUp className="w-6 h-6" />}
                    title="Allowed"
                    value={stats.requestsAllowed.toLocaleString()}
                    trend={`${allowRate}%`}
                    color="bg-green-500"
                />
                <StatCard
                    icon={<TrendingDown className="w-6 h-6" />}
                    title="Blocked"
                    value={stats.requestsBlocked.toLocaleString()}
                    trend={`${blockRate}%`}
                    color="bg-red-500"
                />
                <StatCard
                    icon={<BarChart3 className="w-6 h-6" />}
                    title="Active Rules"
                    value={stats.activePolicies}
                    trend={null}
                    color="bg-purple-500"
                />
            </div>

            {/* Charts Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* Line Chart - Request Trends */}
                <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
                    <h3 className="text-lg font-semibold mb-4 flex items-center gap-2">
                        <Activity className="w-5 h-5 text-indigo-600" />
                        Request Trends
                    </h3>
                    <ResponsiveContainer width="100%" height={300}>
                        <LineChart data={timeSeriesData}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                            <XAxis 
                                dataKey="time" 
                                tick={{ fontSize: 12 }}
                                tickMargin={10}
                            />
                            <YAxis tick={{ fontSize: 12 }} />
                            <Tooltip 
                                contentStyle={{ 
                                    backgroundColor: 'rgba(255, 255, 255, 0.95)',
                                    border: '1px solid #e5e7eb',
                                    borderRadius: '8px',
                                    padding: '8px'
                                }}
                            />
                            <Legend />
                            <Line 
                                type="monotone" 
                                dataKey="allowed" 
                                stroke={COLORS.allowed} 
                                strokeWidth={2}
                                name="Allowed"
                                dot={false}
                            />
                            <Line 
                                type="monotone" 
                                dataKey="blocked" 
                                stroke={COLORS.blocked} 
                                strokeWidth={2}
                                name="Blocked"
                                dot={false}
                            />
                        </LineChart>
                    </ResponsiveContainer>
                </div>

                {/* Area Chart - Traffic Volume */}
                <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
                    <h3 className="text-lg font-semibold mb-4 flex items-center gap-2">
                        <TrendingUp className="w-5 h-5 text-indigo-600" />
                        Traffic Volume
                    </h3>
                    <ResponsiveContainer width="100%" height={300}>
                        <AreaChart data={timeSeriesData}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                            <XAxis 
                                dataKey="time" 
                                tick={{ fontSize: 12 }}
                                tickMargin={10}
                            />
                            <YAxis tick={{ fontSize: 12 }} />
                            <Tooltip 
                                contentStyle={{ 
                                    backgroundColor: 'rgba(255, 255, 255, 0.95)',
                                    border: '1px solid #e5e7eb',
                                    borderRadius: '8px',
                                    padding: '8px'
                                }}
                            />
                            <Legend />
                            <Area 
                                type="monotone" 
                                dataKey="total" 
                                stroke={COLORS.primary} 
                                fill={COLORS.primary}
                                fillOpacity={0.3}
                                name="Total Requests"
                            />
                        </AreaChart>
                    </ResponsiveContainer>
                </div>

                {/* Pie Chart - Request Distribution */}
                <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
                    <h3 className="text-lg font-semibold mb-4">Request Distribution</h3>
                    <ResponsiveContainer width="100%" height={300}>
                        <PieChart>
                            <Pie
                                data={pieData}
                                cx="50%"
                                cy="50%"
                                labelLine={false}
                                label={({ name, percent }) => `${name}: ${(percent * 100).toFixed(0)}%`}
                                outerRadius={100}
                                fill="#8884d8"
                                dataKey="value"
                            >
                                {pieData.map((entry, index) => (
                                    <Cell key={`cell-${index}`} fill={entry.color} />
                                ))}
                            </Pie>
                            <Tooltip />
                        </PieChart>
                    </ResponsiveContainer>
                    <div className="mt-4 flex justify-center gap-6">
                        <div className="flex items-center gap-2">
                            <div className="w-3 h-3 rounded-full bg-green-500"></div>
                            <span className="text-sm text-gray-600">Allowed: {stats.requestsAllowed.toLocaleString()}</span>
                        </div>
                        <div className="flex items-center gap-2">
                            <div className="w-3 h-3 rounded-full bg-red-500"></div>
                            <span className="text-sm text-gray-600">Blocked: {stats.requestsBlocked.toLocaleString()}</span>
                        </div>
                    </div>
                </div>

                {/* Performance Metrics */}
                <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
                    <h3 className="text-lg font-semibold mb-4">Performance Metrics</h3>
                    <div className="space-y-4">
                        <MetricBar 
                            label="Block Rate" 
                            value={blockRate} 
                            max={100}
                            color={COLORS.blocked}
                            suffix="%"
                        />
                        <MetricBar 
                            label="Allow Rate" 
                            value={allowRate} 
                            max={100}
                            color={COLORS.allowed}
                            suffix="%"
                        />
                        <MetricBar 
                            label="Active Rules" 
                            value={stats.activePolicies} 
                            max={20}
                            color={COLORS.primary}
                            suffix=""
                        />
                        <div className="pt-4 border-t border-gray-100">
                            <div className="flex justify-between items-center mb-2">
                                <span className="text-sm font-medium text-gray-700">System Health</span>
                                <span className="text-sm font-semibold text-green-600">Excellent</span>
                            </div>
                            <div className="w-full bg-gray-100 rounded-full h-2">
                                <div 
                                    className="bg-gradient-to-r from-green-400 to-green-600 h-2 rounded-full transition-all duration-500"
                                    style={{ width: '95%' }}
                                ></div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

function StatCard({ icon, title, value, trend, color }) {
    return (
        <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100 hover:shadow-md transition-shadow">
            <div className="flex items-start justify-between">
                <div>
                    <p className="text-sm font-medium text-gray-500 mb-1">{title}</p>
                    <p className="text-2xl font-bold text-gray-900">{value}</p>
                    {trend && (
                        <p className="text-sm text-gray-500 mt-1">{trend} of total</p>
                    )}
                </div>
                <div className={`${color} p-3 rounded-lg text-white`}>
                    {icon}
                </div>
            </div>
        </div>
    );
}

function MetricBar({ label, value, max, color, suffix }) {
    const percentage = Math.min((value / max) * 100, 100);
    
    return (
        <div>
            <div className="flex justify-between items-center mb-2">
                <span className="text-sm font-medium text-gray-700">{label}</span>
                <span className="text-sm font-semibold text-gray-900">{value}{suffix}</span>
            </div>
            <div className="w-full bg-gray-100 rounded-full h-2">
                <div 
                    className="h-2 rounded-full transition-all duration-500"
                    style={{ 
                        width: `${percentage}%`,
                        backgroundColor: color
                    }}
                ></div>
            </div>
        </div>
    );
}
