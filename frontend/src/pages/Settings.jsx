import React, { useEffect, useState } from 'react';
import { Save, RefreshCw } from 'lucide-react';
import api from '../api';
import { getAntiBotHeaders, getFormToken } from '../utils/formProtection';

export default function Settings() {
    const [configs, setConfigs] = useState({});
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        fetchConfigs();
    }, []);

    const fetchConfigs = async () => {
        try {
            const res = await api.get('/config');
            const configMap = {};
            res.data.forEach(item => {
                configMap[item.configKey] = item.configValue;
            });
            setConfigs(configMap);
        } catch (err) {
            console.error("Failed to fetch configs", err);
        } finally {
            setLoading(false);
        }
    };

    const handleCreateAndUpdate = async (key, value) => {
        // Optimistic update
        setConfigs(prev => ({ ...prev, [key]: value }));

        try {
            // Get logic protection
            const tokenData = await getFormToken();
            const headers = getAntiBotHeaders(tokenData);

            await api.post(`/config/${key}`, { value }, { headers });
        } catch (err) {
            console.error(`Failed to update ${key}`, err);
            alert(`Failed to update setting: ${key}`);
            fetchConfigs(); // Revert on failure
        }
    };

    const handleChange = (key, value) => {
        setConfigs(prev => ({ ...prev, [key]: value }));
    };

    const handleSaveAll = async () => {
        setSaving(true);
        try {
            const tokenData = await getFormToken();
            const headers = getAntiBotHeaders(tokenData);

            await Promise.all(Object.entries(configs).map(([key, value]) =>
                api.post(`/config/${key}`, { value }, { headers })
            ));
            alert("Settings saved successfully!");
        } catch (err) {
            console.error("Failed to save settings", err);
            alert("Failed to save settings.");
        } finally {
            setSaving(false);
        }
    };

    if (loading) return <div className="p-8 text-center">Loading settings...</div>;

    return (
        <div>
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-2xl font-bold">System Settings</h1>
                <button
                    onClick={handleSaveAll}
                    disabled={saving}
                    className="flex items-center space-x-2 bg-indigo-600 text-white px-4 py-2 rounded-lg hover:bg-indigo-700 transition disabled:opacity-50"
                >
                    {saving ? <RefreshCw className="animate-spin" size={18} /> : <Save size={18} />}
                    <span>Save Changes</span>
                </button>
            </div>

            <div className="grid grid-cols-1 gap-6">

                {/* IP Resolution Settings */}
                <section className="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
                    <h2 className="text-lg font-semibold mb-4 text-gray-800">IP Resolution & Proxy</h2>
                    <div className="space-y-4">
                        <div>
                            <label className="flex items-center space-x-3 cursor-pointer">
                                <input
                                    type="checkbox"
                                    checked={configs['trust-x-forwarded-for'] === 'true'}
                                    onChange={(e) => handleChange('trust-x-forwarded-for', String(e.target.checked))}
                                    className="w-5 h-5 text-indigo-600 rounded focus:ring-indigo-500"
                                />
                                <div>
                                    <span className="font-medium text-gray-700">Trust Proxy Headers</span>
                                    <p className="text-sm text-gray-500">Enable only if this gateway is behind a trusted load balancer (e.g. AWS ALB, Nginx, Cloudflare).</p>
                                </div>
                            </label>
                        </div>

                        <div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">IP Header Name</label>
                                <input
                                    type="text"
                                    value={configs['ip-header-name'] || 'X-Forwarded-For'}
                                    onChange={(e) => handleChange('ip-header-name', e.target.value)}
                                    className="w-full max-w-md px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500"
                                />
                                <p className="text-xs text-gray-500 mt-1">Default: X-Forwarded-For. For Cloudflare use 'CF-Connecting-IP'.</p>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">API Key Header Name</label>
                                <input
                                    type="text"
                                    value={configs['api-key-header-name'] || 'X-API-Key'}
                                    onChange={(e) => handleChange('api-key-header-name', e.target.value)}
                                    className="w-full max-w-md px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500"
                                />
                                <p className="text-xs text-gray-500 mt-1">Header used for 'API Key' rate limiting (e.g., X-API-Key, X-IBM-Client-Id).</p>
                            </div>
                        </div>
                    </div>
                </section>

                {/* Anti-Bot Settings */}
                <section className="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
                    <h2 className="text-lg font-semibold mb-4 text-gray-800">Anti-Bot Protection</h2>
                    <div className="space-y-4">
                        <div>
                            <label className="flex items-center space-x-3 cursor-pointer">
                                <input
                                    type="checkbox"
                                    checked={configs['antibot-enabled'] === 'true'}
                                    onChange={(e) => handleChange('antibot-enabled', String(e.target.checked))}
                                    className="w-5 h-5 text-indigo-600 rounded focus:ring-indigo-500"
                                />
                                <span className="font-medium text-gray-700">Enable Anti-Bot Filters</span>
                            </label>
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Min Submit Time (ms)</label>
                                <input
                                    type="number"
                                    value={configs['antibot-min-submit-time'] || '2000'}
                                    onChange={(e) => handleChange('antibot-min-submit-time', e.target.value)}
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500"
                                />
                                <p className="text-xs text-gray-500 mt-1">Forms submitted faster than this are blocked.</p>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Honeypot Field Name</label>
                                <input
                                    type="text"
                                    value={configs['antibot-honeypot-field'] || '_hp_email'}
                                    onChange={(e) => handleChange('antibot-honeypot-field', e.target.value)}
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500"
                                />
                                <p className="text-xs text-gray-500 mt-1">Hidden field name to trap bots.</p>
                            </div>
                        </div>
                    </div>
                </section>

            </div>
        </div>
    );
}
