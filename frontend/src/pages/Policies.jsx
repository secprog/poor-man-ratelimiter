import React, { useEffect, useState } from 'react';
import { Plus, Trash2, Edit, X, Save, AlertTriangle } from 'lucide-react';
import api from '../api';
import { getFormToken, getAntiBotHeaders } from '../utils/formProtection';

export default function Policies() {
    const [policies, setPolicies] = useState([]);
    const [loading, setLoading] = useState(true);
    const [modalOpen, setModalOpen] = useState(false);
    const [editingPolicy, setEditingPolicy] = useState(null);

    // Anti-bot state
    const [formTokenData, setFormTokenData] = useState(null);
    const [honeypotValue, setHoneypotValue] = useState('');

    const [formData, setFormData] = useState({
        routePattern: '',
        limitType: 'IP_BASED',
        replenishRate: 10,
        burstCapacity: 20,
        requestedTokens: 1,
        headerName: '',
        sessionCookieName: '',
        trustProxy: false
    });

    useEffect(() => {
        fetchPolicies();
    }, []);

    const fetchPolicies = async () => {
        try {
            const res = await api.get('/admin/policies');
            setPolicies(res.data);
        } catch (err) {
            console.error("Failed to fetch policies", err);
        } finally {
            setLoading(false);
        }
    };

    const prepareForm = async () => {
        // Fetch a fresh form token for anti-bot protection
        const tokenData = await getFormToken();
        setFormTokenData(tokenData);
        setHoneypotValue(''); // Reset honeypot
    };

    const openCreateModal = async () => {
        setEditingPolicy(null);
        setFormData({
            routePattern: '',
            limitType: 'IP_BASED',
            replenishRate: 10,
            burstCapacity: 20,
            requestedTokens: 1,
            headerName: '',
            sessionCookieName: '',
            trustProxy: false
        });
        setModalOpen(true);
        await prepareForm();
    };

    const openEditModal = async (policy) => {
        setEditingPolicy(policy);
        setFormData({
            routePattern: policy.routePattern,
            limitType: policy.limitType,
            replenishRate: policy.replenishRate,
            burstCapacity: policy.burstCapacity,
            requestedTokens: policy.requestedTokens,
            headerName: policy.headerName || '',
            sessionCookieName: policy.sessionCookieName || '',
            trustProxy: policy.trustProxy || false
        });
        setModalOpen(true);
        await prepareForm();
    };

    const closeModal = () => {
        setModalOpen(false);
        setEditingPolicy(null);
        setFormTokenData(null);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        // Prepare headers with anti-bot token
        const headers = getAntiBotHeaders(formTokenData, honeypotValue);

        try {
            if (editingPolicy) {
                await api.put(`/admin/policies/${editingPolicy.policyId}`, formData, { headers });
            } else {
                await api.post('/admin/policies', formData, { headers });
            }
            closeModal();
            fetchPolicies();
        } catch (err) {
            console.error("Failed to save policy", err);
            // Check for 403 Forbidden (Bot detected)
            if (err.response && err.response.status === 403) {
                alert("Action blocked by anti-bot protection: " +
                    (err.response.headers && err.response.headers['x-rejection-reason'] || "Suspicious activity detected"));
            } else if (err.response && err.response.status === 409) {
                alert("Duplicate request detected (Idempotency check).");
            } else {
                alert("Failed to save policy: " + (err.response?.data?.message || err.message));
            }
        }
    };

    const handleDelete = async (policyId) => {
        if (!confirm("Are you sure you want to delete this policy?")) return;
        try {
            // Delete doesn't necessarily need anti-bot token as it's idempotent, but could add it if method was strict
            await api.delete(`/admin/policies/${policyId}`);
            fetchPolicies();
        } catch (err) {
            console.error("Failed to delete policy", err);
            alert("Failed to delete policy");
        }
    };

    const handleInputChange = (e) => {
        const { name, value, type, checked } = e.target;
        setFormData(prev => ({
            ...prev,
            [name]: type === 'checkbox' ? checked : type === 'number' ? parseInt(value, 10) : value
        }));
    };

    return (
        <div>
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-2xl font-bold">Rate Limit Policies</h1>
                <button
                    onClick={openCreateModal}
                    className="flex items-center space-x-2 bg-indigo-600 text-white px-4 py-2 rounded-lg hover:bg-indigo-700 transition"
                >
                    <Plus size={20} />
                    <span>New Policy</span>
                </button>
            </div>

            <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
                <table className="w-full text-left">
                    <thead className="bg-gray-50 text-gray-500 uppercase text-xs">
                        <tr>
                            <th className="px-6 py-3">Route Pattern</th>
                            <th className="px-6 py-3">Type</th>
                            <th className="px-6 py-3">Rate (req/s)</th>
                            <th className="px-6 py-3">Burst</th>
                            <th className="px-6 py-3">Actions</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                        {loading ? (
                            <tr><td colSpan="5" className="p-6 text-center">Loading...</td></tr>
                        ) : policies.map((policy) => (
                            <tr key={policy.policyId} className="hover:bg-gray-50/50">
                                <td className="px-6 py-4 font-medium font-mono text-sm">{policy.routePattern}</td>
                                <td className="px-6 py-4">
                                    <span className="px-2 py-1 rounded-full text-xs font-semibold bg-blue-100 text-blue-700">
                                        {policy.limitType}
                                    </span>
                                </td>
                                <td className="px-6 py-4">{policy.replenishRate}</td>
                                <td className="px-6 py-4">{policy.burstCapacity}</td>
                                <td className="px-6 py-4 flex space-x-3 text-gray-400">
                                    <button
                                        onClick={() => openEditModal(policy)}
                                        className="hover:text-blue-600 transition"
                                        title="Edit"
                                    >
                                        <Edit size={18} />
                                    </button>
                                    <button
                                        onClick={() => handleDelete(policy.policyId)}
                                        className="hover:text-red-600 transition"
                                        title="Delete"
                                    >
                                        <Trash2 size={18} />
                                    </button>
                                </td>
                            </tr>
                        ))}
                        {!loading && policies.length === 0 && (
                            <tr><td colSpan="5" className="p-6 text-center text-gray-500">No policies found. Click "New Policy" to create one.</td></tr>
                        )}
                    </tbody>
                </table>
            </div>

            {/* Modal */}
            {modalOpen && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-xl shadow-xl w-full max-w-md mx-4 max-h-[90vh] overflow-y-auto">
                        <div className="flex items-center justify-between p-4 border-b border-gray-100">
                            <h2 className="text-lg font-semibold">
                                {editingPolicy ? 'Edit Policy' : 'Create New Policy'}
                            </h2>
                            <button onClick={closeModal} className="text-gray-400 hover:text-gray-600">
                                <X size={20} />
                            </button>
                        </div>

                        <form onSubmit={handleSubmit} className="p-4 space-y-4">
                            {/* Honeypot Field - Hidden for users, bots might fill it */}
                            {formTokenData?.honeypotField && (
                                <div style={{ position: 'absolute', left: '-9999px' }} aria-hidden="true">
                                    <label htmlFor={formTokenData.honeypotField}>Please leave this field blank</label>
                                    <input
                                        type="text"
                                        id={formTokenData.honeypotField}
                                        name={formTokenData.honeypotField}
                                        value={honeypotValue}
                                        onChange={(e) => setHoneypotValue(e.target.value)}
                                        tabIndex="-1"
                                        autoComplete="off"
                                    />
                                </div>
                            )}

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Route Pattern
                                </label>
                                <input
                                    type="text"
                                    name="routePattern"
                                    value={formData.routePattern}
                                    onChange={handleInputChange}
                                    placeholder="e.g., /api/v1/** or /**"
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                    required
                                />
                                <p className="text-xs text-gray-500 mt-1">Use Ant-style patterns: /** for all, /api/** for API routes</p>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Limit Type
                                </label>
                                <select
                                    name="limitType"
                                    value={formData.limitType}
                                    onChange={handleInputChange}
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                >
                                    <option value="IP_BASED">IP Based (Default)</option>
                                    <option value="USER_BASED">User Based (Principal / X-User-Id)</option>
                                    <option value="API_KEY">API Key (X-API-Key)</option>
                                    <option value="SESSION_BASED">Session Cookie</option>
                                    <option value="GLOBAL">Global (All Requests)</option>
                                </select>
                            </div>

                            {/* Per-Policy Configuration Fields */}
                            {formData.limitType === 'IP_BASED' && (
                                <div className="space-y-3">
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-1">
                                            IP Header Name (optional)
                                        </label>
                                        <input
                                            type="text"
                                            name="headerName"
                                            value={formData.headerName}
                                            onChange={handleInputChange}
                                            placeholder="e.g., X-Forwarded-For (leave empty for global default)"
                                            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 text-sm"
                                        />
                                        <p className="text-xs text-gray-500 mt-1">Overrides global ip-header-name setting for this rule</p>
                                    </div>
                                    <div>
                                        <label className="flex items-center space-x-2">
                                            <input
                                                type="checkbox"
                                                name="trustProxy"
                                                checked={formData.trustProxy || false}
                                                onChange={handleInputChange}
                                                className="w-4 h-4 rounded border-gray-300"
                                            />
                                            <span className="text-sm font-medium text-gray-700">Trust X-Forwarded-For (optional)</span>
                                        </label>
                                        <p className="text-xs text-gray-500 mt-1">Overrides global trust-x-forwarded-for setting for this rule</p>
                                    </div>
                                </div>
                            )}

                            {formData.limitType === 'USER_BASED' && (
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">
                                        User ID Header (optional)
                                    </label>
                                    <input
                                        type="text"
                                        name="headerName"
                                        value={formData.headerName}
                                        onChange={handleInputChange}
                                        placeholder="e.g., X-User-Id (leave empty for global default)"
                                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                    />
                                    <p className="text-xs text-gray-500 mt-1">Overrides global user-id-header-name setting for this rule</p>
                                </div>
                            )}

                            {formData.limitType === 'API_KEY' && (
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">
                                        API Key Header (optional)
                                    </label>
                                    <input
                                        type="text"
                                        name="headerName"
                                        value={formData.headerName}
                                        onChange={handleInputChange}
                                        placeholder="e.g., X-API-Key (leave empty for global default)"
                                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                    />
                                    <p className="text-xs text-gray-500 mt-1">Overrides global api-key-header-name setting for this rule</p>
                                </div>
                            )}

                            {formData.limitType === 'SESSION_BASED' && (
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">
                                        Session Cookie Name (optional)
                                    </label>
                                    <input
                                        type="text"
                                        name="sessionCookieName"
                                        value={formData.sessionCookieName}
                                        onChange={handleInputChange}
                                        placeholder="e.g., JSESSIONID (leave empty for global default)"
                                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                    />
                                    <p className="text-xs text-gray-500 mt-1">Overrides global session-cookie-name setting for this rule</p>
                                </div>
                            )}

                            {/* Security Warning for IP_BASED */}
                            {formData.limitType === 'IP_BASED' && (
                                <div className="bg-amber-50 border border-amber-200 rounded-lg p-3">
                                    <div className="flex items-start space-x-2">
                                        <AlertTriangle className="w-5 h-5 text-amber-600 flex-shrink-0 mt-0.5" />
                                        <div className="text-sm text-amber-800">
                                            <p className="font-semibold">X-Forwarded-For Security Warning</p>
                                            <p className="mt-1">
                                                If your gateway is behind a proxy/load balancer, ensure <code className="bg-amber-100 px-1 rounded">trust-x-forwarded-for</code> is
                                                enabled in the backend config. <strong>Only enable this if you control the proxy</strong> and
                                                it correctly sets X-Forwarded-For. Otherwise, attackers can spoof their IP!
                                            </p>
                                        </div>
                                    </div>
                                </div>
                            )}

                            {/* Security Warning for SESSION_BASED */}
                            {formData.limitType === 'SESSION_BASED' && (
                                <div className="bg-blue-50 border border-blue-200 rounded-lg p-3">
                                    <div className="flex items-start space-x-2">
                                        <AlertTriangle className="w-5 h-5 text-blue-600 flex-shrink-0 mt-0.5" />
                                        <div className="text-sm text-blue-800">
                                            <p className="font-semibold">Session Cookie Configuration</p>
                                            <p className="mt-1">
                                                By default, uses <code className="bg-blue-100 px-1 rounded">JSESSIONID</code> cookie. Configure a custom session cookie name via the <code className="bg-blue-100 px-1 rounded">session-cookie-name</code> system setting if your application uses a different cookie name.
                                            </p>
                                        </div>
                                    </div>
                                </div>
                            )}

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">
                                        Rate (req/sec)
                                    </label>
                                    <input
                                        type="number"
                                        name="replenishRate"
                                        value={formData.replenishRate}
                                        onChange={handleInputChange}
                                        min="1"
                                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                        required
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">
                                        Burst Capacity
                                    </label>
                                    <input
                                        type="number"
                                        name="burstCapacity"
                                        value={formData.burstCapacity}
                                        onChange={handleInputChange}
                                        min="1"
                                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                        required
                                    />
                                </div>
                            </div>

                            <div className="flex justify-end space-x-3 pt-4">
                                <button
                                    type="button"
                                    onClick={closeModal}
                                    className="px-4 py-2 text-gray-600 hover:text-gray-800 transition"
                                >
                                    Cancel
                                </button>
                                <button
                                    type="submit"
                                    className="flex items-center space-x-2 bg-indigo-600 text-white px-4 py-2 rounded-lg hover:bg-indigo-700 transition"
                                >
                                    <Save size={18} />
                                    <span>{editingPolicy ? 'Update' : 'Create'}</span>
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}
