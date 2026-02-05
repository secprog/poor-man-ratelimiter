import React, { useEffect, useRef, useState } from 'react';
import { Activity, ShieldCheck, ShieldX, Settings } from 'lucide-react';
import api from '../api';
import adminApi from '../admin-api';
import analyticsWs from '../utils/websocket';

export default function Dashboard() {
    const [rollingWindowMinutes, setRollingWindowMinutes] = useState(5);
    const isRefreshingRef = useRef(false);
    const lastSummaryRef = useRef(null);
    const windowBucketsRef = useRef([]);
    const maxBucketMinutes = 60;
    const [stats, setStats] = useState({
        totalRules: 0,
        requestsAllowed: 0,
        requestsBlocked: 0,
        loading: true,
        error: null,
        isConnected: false
    });
    const [rules, setRules] = useState([]);
    const [trafficLogs, setTrafficLogs] = useState([]);

    useEffect(() => {
        // Initial load from REST API
        fetchInitialStats();
        
        // Subscribe to WebSocket updates
        const unsubscribe = analyticsWs.subscribe((update) => {
            const summary = {
                allowed: update.requestsAllowed || 0,
                blocked: update.requestsBlocked || 0
            };

            setStats(prev => ({
                ...prev,
                isConnected: true,
                totalRules: update.activePolicies || prev.totalRules
            }));

            if (lastSummaryRef.current) {
                const deltaAllowed = Math.max(summary.allowed - lastSummaryRef.current.allowed, 0);
                const deltaBlocked = Math.max(summary.blocked - lastSummaryRef.current.blocked, 0);

                if (deltaAllowed > 0 || deltaBlocked > 0) {
                    const bucketTime = update.timestamp ? new Date(update.timestamp) : new Date();
                    bucketTime.setSeconds(0, 0);
                    const bucketTimestamp = bucketTime.getTime();

                    const buckets = [...windowBucketsRef.current];
                    const lastBucket = buckets[buckets.length - 1];

                    if (lastBucket && lastBucket.timestamp === bucketTimestamp) {
                        buckets[buckets.length - 1] = {
                            ...lastBucket,
                            allowed: lastBucket.allowed + deltaAllowed,
                            blocked: lastBucket.blocked + deltaBlocked
                        };
                    } else {
                        buckets.push({
                            timestamp: bucketTimestamp,
                            allowed: deltaAllowed,
                            blocked: deltaBlocked
                        });
                    }

                    const cutoff = Date.now() - maxBucketMinutes * 60 * 1000;
                    windowBucketsRef.current = buckets.filter(
                        bucket => bucket.timestamp >= cutoff
                    );

                    const totals = computeWindowTotals(
                        windowBucketsRef.current,
                        rollingWindowMinutes * 60 * 1000
                    );

                    setStats(prev => ({
                        ...prev,
                        requestsAllowed: totals.allowed,
                        requestsBlocked: totals.blocked
                    }));
                }
            }

            lastSummaryRef.current = summary;
        });
        
        return () => unsubscribe();
    }, []);

    useEffect(() => {
        fetchInitialStats();
    }, [rollingWindowMinutes]);

    const fetchTrafficLogs = async () => {
        try {
            const res = await adminApi.get('/admin/analytics/traffic?limit=100');
            setTrafficLogs(res.data || []);
        } catch (err) {
            console.error("Failed to fetch traffic logs", err);
        }
    };

    useEffect(() => {
        fetchTrafficLogs();
        const interval = setInterval(fetchTrafficLogs, 2000); // Update every 2 seconds
        return () => clearInterval(interval);
    }, []);

    const computeWindowTotals = (points, windowMs) => {
        if (windowMs === 0) {
            // "Now" mode: only show current minute bucket
            const currentMinute = new Date();
            currentMinute.setSeconds(0, 0);
            const currentTimestamp = currentMinute.getTime();
            
            const currentBucket = points.find(point => point.timestamp === currentTimestamp);
            return {
                allowed: currentBucket?.allowed || 0,
                blocked: currentBucket?.blocked || 0
            };
        }

        const cutoff = Date.now() - windowMs;
        return points.reduce(
            (acc, point) => {
                const ts = new Date(point.timestamp).getTime();
                if (Number.isNaN(ts) || ts < cutoff) {
                    return acc;
                }
                return {
                    allowed: acc.allowed + (point.allowed || 0),
                    blocked: acc.blocked + (point.blocked || 0)
                };
            },
            { allowed: 0, blocked: 0 }
        );
    };

    const fetchInitialStats = async () => {
        if (isRefreshingRef.current) return;
        isRefreshingRef.current = true;
        try {
            const [rulesRes, analyticsRes] = await Promise.all([
                api.get('/admin/rules/active'),
                api.get('/analytics/timeseries?hours=1')
            ]);

            windowBucketsRef.current = (analyticsRes.data || []).map(point => ({
                timestamp: new Date(point.timestamp).getTime(),
                allowed: point.allowed || 0,
                blocked: point.blocked || 0
            }));

            const totals = computeWindowTotals(
                windowBucketsRef.current,
                rollingWindowMinutes * 60 * 1000
            );

            setRules(rulesRes.data || []);
            setStats(prev => ({
                ...prev,
                totalRules: rulesRes.data.length,
                requestsAllowed: totals.allowed,
                requestsBlocked: totals.blocked,
                loading: false,
                error: null
            }));
        } catch (err) {
            console.error("Failed to fetch stats", err);
            setStats(prev => ({
                ...prev,
                loading: false,
                error: 'Failed to fetch statistics'
            }));
        } finally {
            isRefreshingRef.current = false;
        }
    };

    if (stats.loading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
            </div>
        );
    }

    return (
        <div>
            <div className="flex flex-col gap-3 md:flex-row md:justify-between md:items-center mb-6">
                <h1 className="text-2xl font-bold">Dashboard</h1>
                <div className="flex items-center gap-3">
                    <div className="flex gap-2">
                        {([
                            { label: 'Now', value: 0 },
                            { label: '1m', value: 1 },
                            { label: '5m', value: 5 },
                            { label: '15m', value: 15 }
                        ]).map(option => (
                            <button
                                key={option.value}
                                onClick={() => setRollingWindowMinutes(option.value)}
                                className={`px-3 py-1 rounded-lg text-sm font-medium transition-colors ${
                                    rollingWindowMinutes === option.value
                                        ? 'bg-indigo-600 text-white'
                                        : 'bg-white text-gray-700 hover:bg-gray-50 border border-gray-200'
                                }`}
                            >
                                {option.label}
                            </button>
                        ))}
                    </div>
                    {stats.isConnected && (
                        <div className="flex items-center space-x-2 text-sm text-green-600 bg-green-50 px-3 py-1 rounded-full">
                            <div className="w-2 h-2 rounded-full bg-green-500 animate-pulse"></div>
                            <span>Real-time</span>
                        </div>
                    )}
                </div>
            </div>

            {stats.error && (
                <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg mb-6">
                    {stats.error}
                </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-6">
                <StatCard
                    icon={<ShieldCheck className="w-6 h-6" />}
                    label="Requests Allowed"
                    value={stats.requestsAllowed.toLocaleString()}
                    subtext={rollingWindowMinutes === 0 ? 'Latest bucket' : `Last ${rollingWindowMinutes} minutes`}
                    color="green"
                />
                <StatCard
                    icon={<ShieldX className="w-6 h-6" />}
                    label="Requests Blocked"
                    value={stats.requestsBlocked.toLocaleString()}
                    subtext={rollingWindowMinutes === 0 ? 'Latest bucket' : `Last ${rollingWindowMinutes} minutes`}
                    color="red"
                />
                <StatCard
                    icon={<Activity className="w-6 h-6" />}
                    label="Active Rules"
                    value={stats.totalRules}
                    subtext="Rate limiting rules"
                    color="indigo"
                />
            </div>

            <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
                <h2 className="text-lg font-semibold mb-4">Real-time Traffic</h2>
                <div className="overflow-x-auto max-h-96 overflow-y-auto border border-gray-200 rounded-lg">
                    <table className="min-w-full divide-y divide-gray-200">
                        <thead className="bg-gray-50 sticky top-0">
                            <tr>
                                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Time</th>
                                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Verb</th>
                                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Path</th>
                                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Host</th>
                                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">IP</th>
                                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Action</th>
                            </tr>
                        </thead>
                        <tbody className="bg-white divide-y divide-gray-200">
                            {trafficLogs.length === 0 ? (
                                <tr>
                                    <td colSpan="7" className="px-4 py-8 text-center text-gray-500">
                                        No traffic yet
                                    </td>
                                </tr>
                            ) : (
                                trafficLogs.map((log, idx) => (
                                    <tr key={idx} className="hover:bg-gray-50 text-sm">
                                        <td className="px-4 py-2 text-gray-900 font-mono text-xs">
                                            {log.timestamp ? new Date(log.timestamp).toLocaleTimeString() : '-'}
                                        </td>
                                        <td className="px-4 py-2 font-semibold">
                                            <span className="inline-flex items-center px-2 py-1 rounded text-xs font-medium bg-blue-100 text-blue-800">
                                                {log.method || 'GET'}
                                            </span>
                                        </td>
                                        <td className="px-4 py-2 font-mono text-gray-900 truncate max-w-xs" title={log.path}>
                                            {log.path}
                                        </td>
                                        <td className="px-4 py-2 text-gray-700 text-xs truncate max-w-xs" title={log.host || 'N/A'}>
                                            {log.host || '-'}
                                        </td>
                                        <td className="px-4 py-2 text-gray-600 font-mono text-xs">
                                            {log.clientIp}
                                        </td>
                                        <td className="px-4 py-2">
                                            <span className={`inline-flex items-center px-2 py-1 rounded text-xs font-medium ${
                                                log.statusCode === 200 ? 'bg-green-100 text-green-800' :
                                                log.statusCode === 429 ? 'bg-red-100 text-red-800' :
                                                log.statusCode === 403 ? 'bg-orange-100 text-orange-800' :
                                                'bg-gray-100 text-gray-800'
                                            }`}>
                                                {log.statusCode}
                                            </span>
                                        </td>
                                        <td className="px-4 py-2">
                                            {log.queued ? (
                                                <span className="inline-flex items-center px-2 py-1 rounded text-xs font-medium bg-amber-100 text-amber-800">
                                                    Queued
                                                </span>
                                            ) : log.allowed ? (
                                                <span className="inline-flex items-center px-2 py-1 rounded text-xs font-medium bg-green-100 text-green-800">
                                                    Allowed
                                                </span>
                                            ) : (
                                                <span className="inline-flex items-center px-2 py-1 rounded text-xs font-medium bg-red-100 text-red-800">
                                                    Blocked
                                                </span>
                                            )}
                                        </td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
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
