import React, { useEffect, useState } from 'react';
import { Plus, Trash2, Edit, X, Save, AlertTriangle, ArrowUp, ArrowDown } from 'lucide-react';
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
        pathPattern: '',
        allowedRequests: 100,
        windowSeconds: 60,
        active: true,
        queueEnabled: false,
        maxQueueSize: 10,
        delayPerRequestMs: 500,
        // Rate limiting type and configuration
        rateLimitType: 'IP',  // IP, JWT, HEADER, COOKIE, BODY
        // JWT fields
        jwtClaims: '',
        jwtClaimSeparator: ':',
        // Header fields
        headerName: '',
        headerLimitType: 'replace_ip',
        // Cookie fields
        cookieName: '',
        cookieLimitType: 'replace_ip',
        // Body fields
        bodyFieldPath: '',
        bodyLimitType: 'replace_ip',
        bodyContentType: 'application/json'
    });

    useEffect(() => {
        fetchPolicies();
    }, []);

    const fetchPolicies = async () => {
        try {
            const res = await api.get('/admin/rules');
            // Sort by priority (lower number = higher priority)
            const sortedPolicies = res.data.sort((a, b) => (a.priority || 0) - (b.priority || 0));
            setPolicies(sortedPolicies);
        } catch (err) {
            console.error("Failed to fetch policies", err);
        } finally {
            setLoading(false);
        }
    };

    const handlePriorityChange = async (policyId, newPriority) => {
        try {
            const policy = policies.find(p => p.id === policyId);
            if (!policy) return;

            // Update policy with new priority
            await api.put(`/admin/rules/${policyId}`, {
                ...policy,
                priority: newPriority
            });

            // Refresh the list
            fetchPolicies();
        } catch (err) {
            console.error("Failed to update priority", err);
            alert("Failed to update priority: " + (err.response?.data?.message || err.message));
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
            pathPattern: '',
            allowedRequests: 100,
            windowSeconds: 60,
            active: true,
            priority: 0,
            queueEnabled: false,
            maxQueueSize: 10,
            delayPerRequestMs: 500,
            rateLimitType: 'IP',
            jwtClaims: '',
            jwtClaimSeparator: ':',
            headerName: '',
            headerLimitType: 'replace_ip',
            cookieName: '',
            cookieLimitType: 'replace_ip',
            bodyFieldPath: '',
            bodyLimitType: 'replace_ip',
            bodyContentType: 'application/json'
        });
        setModalOpen(true);
        await prepareForm();
    };

    const openEditModal = async (policy) => {
        setEditingPolicy(policy);
        
        // Parse JWT claims from JSON array to comma-separated string
        let jwtClaimsStr = '';
        try {
            if (policy.jwtClaims) {
                const claimsArray = JSON.parse(policy.jwtClaims);
                jwtClaimsStr = claimsArray.join(', ');
            }
        } catch (e) {
            console.warn("Failed to parse JWT claims", e);
        }
        
        // Determine rate limit type based on enabled flags (priority: Header > Cookie > Body > JWT > IP)
        let rateLimitType = 'IP';
        if (policy.jwtEnabled) rateLimitType = 'JWT';
        if (policy.bodyLimitEnabled) rateLimitType = 'BODY';
        if (policy.cookieLimitEnabled) rateLimitType = 'COOKIE';
        if (policy.headerLimitEnabled) rateLimitType = 'HEADER';
        
        setFormData({
            pathPattern: policy.pathPattern,
            allowedRequests: policy.allowedRequests,
            windowSeconds: policy.windowSeconds,
            active: policy.active !== undefined ? policy.active : true,
            priority: policy.priority || 0,
            queueEnabled: policy.queueEnabled || false,
            maxQueueSize: policy.maxQueueSize || 10,
            delayPerRequestMs: policy.delayPerRequestMs || 500,
            rateLimitType: rateLimitType,
            jwtClaims: jwtClaimsStr,
            jwtClaimSeparator: policy.jwtClaimSeparator || ':',
            headerName: policy.headerName || '',
            headerLimitType: policy.headerLimitType || 'replace_ip',
            cookieName: policy.cookieName || '',
            cookieLimitType: policy.cookieLimitType || 'replace_ip',
            bodyFieldPath: policy.bodyFieldPath || '',
            bodyLimitType: policy.bodyLimitType || 'replace_ip',
            bodyContentType: policy.bodyContentType || 'application/json'
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

        // Convert rateLimitType to individual boolean flags
        const jwtEnabled = formData.rateLimitType === 'JWT';
        const headerLimitEnabled = formData.rateLimitType === 'HEADER';
        const cookieLimitEnabled = formData.rateLimitType === 'COOKIE';
        const bodyLimitEnabled = formData.rateLimitType === 'BODY';

        // Convert comma-separated JWT claims to JSON array
        let jwtClaimsJson = null;
        if (jwtEnabled && formData.jwtClaims) {
            const claimsArray = formData.jwtClaims
                .split(',')
                .map(c => c.trim())
                .filter(c => c.length > 0);
            jwtClaimsJson = JSON.stringify(claimsArray);
        }

        const payload = {
            pathPattern: formData.pathPattern,
            allowedRequests: formData.allowedRequests,
            windowSeconds: formData.windowSeconds,
            active: formData.active,
            priority: formData.priority,
            queueEnabled: formData.queueEnabled,
            maxQueueSize: formData.maxQueueSize,
            delayPerRequestMs: formData.delayPerRequestMs,
            jwtEnabled: jwtEnabled,
            jwtClaims: jwtClaimsJson,
            jwtClaimSeparator: formData.jwtClaimSeparator,
            headerLimitEnabled: headerLimitEnabled,
            headerName: formData.headerName,
            headerLimitType: formData.headerLimitType,
            cookieLimitEnabled: cookieLimitEnabled,
            cookieName: formData.cookieName,
            cookieLimitType: formData.cookieLimitType,
            bodyLimitEnabled: bodyLimitEnabled,
            bodyFieldPath: formData.bodyFieldPath,
            bodyLimitType: formData.bodyLimitType,
            bodyContentType: formData.bodyContentType
        };

        try {
            if (editingPolicy) {
                await api.put(`/admin/rules/${editingPolicy.id}`, payload, { headers });
            } else {
                await api.post('/admin/rules', payload, { headers });
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
            await api.delete(`/admin/rules/${policyId}`);
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
                <h1 className="text-2xl font-bold">Rate Limit Rules</h1>
                <button
                    onClick={openCreateModal}
                    className="flex items-center space-x-2 bg-indigo-600 text-white px-4 py-2 rounded-lg hover:bg-indigo-700 transition"
                >
                    <Plus size={20} />
                    <span>New Rule</span>
                </button>
            </div>

            <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
                <table className="w-full text-left">
                    <thead className="bg-gray-50 text-gray-500 uppercase text-xs">
                        <tr>
                            <th className="px-6 py-3">Priority</th>
                            <th className="px-6 py-3">Path Pattern</th>
                            <th className="px-6 py-3">Allowed</th>
                            <th className="px-6 py-3">Window</th>
                            <th className="px-6 py-3">Type</th>
                            <th className="px-6 py-3">Status</th>
                            <th className="px-6 py-3">Actions</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                        {loading ? (
                            <tr><td colSpan="7" className="p-6 text-center">Loading...</td></tr>
                        ) : policies.map((policy, index) => (
                            <tr key={policy.id} className="hover:bg-gray-50/50">
                                <td className="px-6 py-4">
                                    <div className="flex items-center space-x-2">
                                        <span className="font-semibold text-gray-600">{policy.priority}</span>
                                        <div className="flex flex-col">
                                            <button
                                                onClick={() => handlePriorityChange(policy.id, policy.priority - 1)}
                                                disabled={index === 0}
                                                className="text-gray-400 hover:text-indigo-600 disabled:opacity-30 disabled:cursor-not-allowed"
                                                title="Increase priority (move up)"
                                            >
                                                <ArrowUp size={14} />
                                            </button>
                                            <button
                                                onClick={() => handlePriorityChange(policy.id, policy.priority + 1)}
                                                disabled={index === policies.length - 1}
                                                className="text-gray-400 hover:text-indigo-600 disabled:opacity-30 disabled:cursor-not-allowed"
                                                title="Decrease priority (move down)"
                                            >
                                                <ArrowDown size={14} />
                                            </button>
                                        </div>
                                    </div>
                                </td>
                                <td className="px-6 py-4 font-medium font-mono text-sm">{policy.pathPattern}</td>
                                <td className="px-6 py-4">{policy.allowedRequests} req</td>
                                <td className="px-6 py-4">{policy.windowSeconds}s</td>
                                <td className="px-6 py-4">
                                    {policy.jwtEnabled ? (
                                        <span className="px-2 py-1 rounded-full text-xs font-semibold bg-purple-100 text-purple-700">
                                            JWT
                                        </span>
                                    ) : policy.headerLimitEnabled ? (
                                        <span className="px-2 py-1 rounded-full text-xs font-semibold bg-green-100 text-green-700">
                                            HEADER
                                        </span>
                                    ) : policy.cookieLimitEnabled ? (
                                        <span className="px-2 py-1 rounded-full text-xs font-semibold bg-orange-100 text-orange-700">
                                            COOKIE
                                        </span>
                                    ) : policy.bodyLimitEnabled ? (
                                        <span className="px-2 py-1 rounded-full text-xs font-semibold bg-amber-100 text-amber-700">
                                            BODY
                                        </span>
                                    ) : (
                                        <span className="px-2 py-1 rounded-full text-xs font-semibold bg-blue-100 text-blue-700">
                                            IP
                                        </span>
                                    )}
                                </td>
                                <td className="px-6 py-4">
                                    {policy.active ? (
                                        <span className="px-2 py-1 rounded-full text-xs font-semibold bg-green-100 text-green-700">
                                            Active
                                        </span>
                                    ) : (
                                        <span className="px-2 py-1 rounded-full text-xs font-semibold bg-gray-100 text-gray-700">
                                            Inactive
                                        </span>
                                    )}
                                </td>
                                <td className="px-6 py-4 flex space-x-3 text-gray-400">
                                    <button
                                        onClick={() => openEditModal(policy)}
                                        className="hover:text-blue-600 transition"
                                        title="Edit"
                                    >
                                        <Edit size={18} />
                                    </button>
                                    <button
                                        onClick={() => handleDelete(policy.id)}
                                        className="hover:text-red-600 transition"
                                        title="Delete"
                                    >
                                        <Trash2 size={18} />
                                    </button>
                                </td>
                            </tr>
                        ))}
                        {!loading && policies.length === 0 && (
                            <tr><td colSpan="7" className="p-6 text-center text-gray-500">No rules found. Click "New Rule" to create one.</td></tr>
                        )}
                    </tbody>
                </table>
            </div>

            {/* Modal */}
            {modalOpen && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-xl shadow-xl w-full max-w-2xl mx-4 max-h-[90vh] overflow-y-auto">
                        <div className="flex items-center justify-between p-4 border-b border-gray-100">
                            <h2 className="text-lg font-semibold">
                                {editingPolicy ? 'Edit Rule' : 'Create New Rule'}
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
                                    Path Pattern
                                </label>
                                <input
                                    type="text"
                                    name="pathPattern"
                                    value={formData.pathPattern}
                                    onChange={handleInputChange}
                                    placeholder="e.g., /api/v1/** or /**"
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                    required
                                />
                                <p className="text-xs text-gray-500 mt-1">Use Ant-style patterns: /** for all, /api/** for API routes</p>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">
                                        Allowed Requests
                                    </label>
                                    <input
                                        type="number"
                                        name="allowedRequests"
                                        value={formData.allowedRequests}
                                        onChange={handleInputChange}
                                        min="1"
                                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                        required
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">
                                        Window (seconds)
                                    </label>
                                    <input
                                        type="number"
                                        name="windowSeconds"
                                        value={formData.windowSeconds}
                                        onChange={handleInputChange}
                                        min="1"
                                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                        required
                                    />
                                </div>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="flex items-center space-x-2">
                                        <input
                                            type="checkbox"
                                            name="active"
                                            checked={formData.active}
                                            onChange={handleInputChange}
                                            className="w-4 h-4 rounded border-gray-300"
                                        />
                                        <span className="text-sm font-medium text-gray-700">Active</span>
                                    </label>
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">
                                        Priority
                                    </label>
                                    <input
                                        type="number"
                                        name="priority"
                                        value={formData.priority}
                                        onChange={handleInputChange}
                                        min="0"
                                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                    />
                                    <p className="text-xs text-gray-500 mt-1">Lower = higher priority (evaluated first)</p>
                                </div>
                            </div>

                            {/* Rate Limiting Type */}
                            <div className="border-t border-gray-200 pt-4">
                                <h3 className="text-sm font-semibold text-gray-700 mb-3">Rate Limiting Type</h3>
                                
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">
                                        Limit By
                                    </label>
                                    <select
                                        name="rateLimitType"
                                        value={formData.rateLimitType}
                                        onChange={handleInputChange}
                                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                    >
                                        <option value="IP">IP Address (default)</option>
                                        <option value="JWT">JWT Claims</option>
                                        <option value="HEADER">HTTP Header</option>
                                        <option value="COOKIE">Cookie</option>
                                        <option value="BODY">Request Body Field</option>
                                    </select>
                                    <p className="text-xs text-gray-500 mt-1">
                                        Choose how to identify clients for rate limiting. IP-based is the default fallback.
                                    </p>
                                </div>
                            </div>

                            {/* JWT Configuration */}
                            {formData.rateLimitType === 'JWT' && (
                                <div className="border-t border-gray-200 pt-4">
                                    <h3 className="text-sm font-semibold text-gray-700 mb-3">JWT Configuration</h3>
                                    
                                    <div className="space-y-4">
                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                                JWT Claims (comma-separated)
                                            </label>
                                            <input
                                                type="text"
                                                name="jwtClaims"
                                                value={formData.jwtClaims}
                                                onChange={handleInputChange}
                                                placeholder="e.g., sub, tenant_id, user_role"
                                                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                                required
                                            />
                                            <p className="text-xs text-gray-500 mt-1">
                                                Standard claims: sub, iss, aud, exp, iat, etc. Custom claims are also supported.
                                                Multiple claims will be concatenated.
                                            </p>
                                        </div>

                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                                Claim Separator
                                            </label>
                                            <input
                                                type="text"
                                                name="jwtClaimSeparator"
                                                value={formData.jwtClaimSeparator}
                                                onChange={handleInputChange}
                                                placeholder=":"
                                                maxLength="10"
                                                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                            />
                                            <p className="text-xs text-gray-500 mt-1">
                                                Character(s) used to join multiple claim values (default: ":")
                                            </p>
                                        </div>

                                        <div className="bg-blue-50 border border-blue-200 rounded-lg p-3">
                                            <div className="flex items-start space-x-2">
                                                <AlertTriangle className="w-5 h-5 text-blue-600 flex-shrink-0 mt-0.5" />
                                                <div className="text-sm text-blue-800">
                                                    <p className="font-semibold">JWT Rate Limiting Behavior</p>
                                                    <p className="mt-1">
                                                        When enabled, the gateway will extract the specified claims from the JWT token 
                                                        in the Authorization header. If the token is missing or invalid, the system will 
                                                        fall back to IP-based rate limiting.
                                                    </p>
                                                    <p className="mt-2">
                                                        <strong>Example:</strong> Claims "sub, tenant_id" with values "user123" and "acme-corp" 
                                                        will create identifier: "user123:acme-corp"
                                                    </p>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            )}

                            {/* Header Configuration */}
                            {formData.rateLimitType === 'HEADER' && (
                                <div className="border-t border-gray-200 pt-4">
                                    <h3 className="text-sm font-semibold text-gray-700 mb-3">Header Configuration</h3>
                                    
                                    <div className="space-y-4">
                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                                Header Name
                                            </label>
                                            <input
                                                type="text"
                                                name="headerName"
                                                value={formData.headerName}
                                                onChange={handleInputChange}
                                                placeholder="e.g., X-API-Key, X-User-Id, Authorization"
                                                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                                required
                                            />
                                            <p className="text-xs text-gray-500 mt-1">
                                                HTTP header name to extract rate limit identifier from
                                            </p>
                                        </div>

                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                                Limit Type
                                            </label>
                                            <select
                                                name="headerLimitType"
                                                value={formData.headerLimitType}
                                                onChange={handleInputChange}
                                                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                            >
                                                <option value="replace_ip">Replace IP (use header value only)</option>
                                                <option value="combine_with_ip">Combine with IP (IP + header value)</option>
                                            </select>
                                        </div>

                                        <div className="bg-green-50 border border-green-200 rounded-lg p-3">
                                            <div className="flex items-start space-x-2">
                                                <AlertTriangle className="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
                                                <div className="text-sm text-green-800">
                                                    <p className="font-semibold">Header-based Rate Limiting Behavior</p>
                                                    <p className="mt-1">
                                                        Extracts value from the specified HTTP header.
                                                        Falls back to IP-based if header is missing.
                                                    </p>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            )}

                            {/* Cookie Configuration */}
                            {formData.rateLimitType === 'COOKIE' && (
                                <div className="border-t border-gray-200 pt-4">
                                    <h3 className="text-sm font-semibold text-gray-700 mb-3">Cookie Configuration</h3>
                                    
                                    <div className="space-y-4">
                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                                Cookie Name
                                            </label>
                                            <input
                                                type="text"
                                                name="cookieName"
                                                value={formData.cookieName}
                                                onChange={handleInputChange}
                                                placeholder="e.g., session_id, user_token, JSESSIONID"
                                                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                                required
                                            />
                                            <p className="text-xs text-gray-500 mt-1">
                                                Cookie name to extract rate limit identifier from
                                            </p>
                                        </div>

                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                                Limit Type
                                            </label>
                                            <select
                                                name="cookieLimitType"
                                                value={formData.cookieLimitType}
                                                onChange={handleInputChange}
                                                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                            >
                                                <option value="replace_ip">Replace IP (use cookie value only)</option>
                                                <option value="combine_with_ip">Combine with IP (IP + cookie value)</option>
                                            </select>
                                        </div>

                                        <div className="bg-orange-50 border border-orange-200 rounded-lg p-3">
                                            <div className="flex items-start space-x-2">
                                                <AlertTriangle className="w-5 h-5 text-orange-600 flex-shrink-0 mt-0.5" />
                                                <div className="text-sm text-orange-800">
                                                    <p className="font-semibold">Cookie-based Rate Limiting Behavior</p>
                                                    <p className="mt-1">
                                                        Extracts value from the specified cookie.
                                                        Falls back to IP-based if cookie is missing.
                                                    </p>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            )}

                            {/* Body Configuration */}
                            {formData.rateLimitType === 'BODY' && (
                                <div className="border-t border-gray-200 pt-4">
                                    <h3 className="text-sm font-semibold text-gray-700 mb-3">Body Field Configuration</h3>
                                    
                                    <div className="space-y-4">
                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                                Body Field Path
                                            </label>
                                            <input
                                                type="text"
                                                name="bodyFieldPath"
                                                value={formData.bodyFieldPath}
                                                onChange={handleInputChange}
                                                placeholder="e.g., user_id, api_key, user.id (JSON), //user/id (XML)"
                                                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                                required
                                            />
                                            <p className="text-xs text-gray-500 mt-1">
                                                For JSON: dot notation (user.id). For Forms/Multipart: field name (user_id). For XML: XPath (//user/id).
                                            </p>
                                        </div>

                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                                Content Type
                                            </label>
                                            <select
                                                name="bodyContentType"
                                                value={formData.bodyContentType}
                                                onChange={handleInputChange}
                                                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                            >
                                                <option value="application/json">JSON (application/json)</option>
                                                <option value="application/x-www-form-urlencoded">Form Data (application/x-www-form-urlencoded)</option>
                                                <option value="application/xml">XML (application/xml)</option>
                                                <option value="multipart/form-data">Multipart Form Data (multipart/form-data)</option>
                                            </select>
                                            <p className="text-xs text-gray-500 mt-1">
                                                Expected content type of the request body
                                            </p>
                                        </div>

                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                                Limit Type
                                            </label>
                                            <select
                                                name="bodyLimitType"
                                                value={formData.bodyLimitType}
                                                onChange={handleInputChange}
                                                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                            >
                                                <option value="replace_ip">Replace IP (use body field only)</option>
                                                <option value="combine_with_ip">Combine with IP (IP + body field)</option>
                                            </select>
                                            <p className="text-xs text-gray-500 mt-1">
                                                Choose how to use the body field value for rate limiting
                                            </p>
                                        </div>

                                        <div className="bg-amber-50 border border-amber-200 rounded-lg p-3">
                                            <div className="flex items-start space-x-2">
                                                <AlertTriangle className="w-5 h-5 text-amber-600 flex-shrink-0 mt-0.5" />
                                                <div className="text-sm text-amber-800">
                                                    <p className="font-semibold">Body-based Rate Limiting Behavior</p>
                                                    <p className="mt-1">
                                                        When enabled, the gateway will extract the specified field from the request body.
                                                        If the field is missing or body is invalid, the system will fall back to IP-based rate limiting.
                                                    </p>
                                                    <p className="mt-2">
                                                        <strong>Example:</strong> Field "user_id" with value "user123" in replace_ip mode
                                                        will rate limit by "user123" instead of client IP.
                                                    </p>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            )}

                            {/* Queue Configuration */}
                            <div className="border-t border-gray-200 pt-4">
                                <h3 className="text-sm font-semibold text-gray-700 mb-3">Request Queueing</h3>
                                
                                <div className="space-y-4">
                                    <div>
                                        <label className="flex items-center space-x-2">
                                            <input
                                                type="checkbox"
                                                name="queueEnabled"
                                                checked={formData.queueEnabled}
                                                onChange={handleInputChange}
                                                className="w-4 h-4 rounded border-gray-300"
                                            />
                                            <span className="text-sm font-medium text-gray-700">Enable request queueing</span>
                                        </label>
                                        <p className="text-xs text-gray-500 mt-1 ml-6">
                                            Delay excess requests instead of rejecting them (leaky bucket)
                                        </p>
                                    </div>

                                    {formData.queueEnabled && (
                                        <>
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                                    Max Queue Size
                                                </label>
                                                <input
                                                    type="number"
                                                    name="maxQueueSize"
                                                    value={formData.maxQueueSize}
                                                    onChange={handleInputChange}
                                                    min="1"
                                                    max="100"
                                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                                />
                                            </div>

                                            <div>
                                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                                    Delay Per Request (ms)
                                                </label>
                                                <input
                                                    type="number"
                                                    name="delayPerRequestMs"
                                                    value={formData.delayPerRequestMs}
                                                    onChange={handleInputChange}
                                                    min="100"
                                                    max="5000"
                                                    step="100"
                                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                                                />
                                            </div>
                                        </>
                                    )}
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
