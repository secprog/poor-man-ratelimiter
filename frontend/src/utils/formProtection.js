import axios from 'axios';
import { v4 as uuidv4 } from 'uuid';

const API_BASE_URL = 'http://localhost:8080/api';

/**
 * Fetches a new anti-bot token from the backend.
 */
export const getFormToken = async () => {
    try {
        const response = await axios.get(`${API_BASE_URL}/tokens/form`);
        return response.data;
    } catch (error) {
        console.error("Failed to get form token", error);
        return null;
    }
};

/**
 * Prepares the headers required for anti-bot protection.
 * 
 * @param {Object} tokenData - The token object returned from getFormToken()
 * @param {string} honeypotValue - The value of the hidden honeypot field (should be empty)
 */
export const getAntiBotHeaders = (tokenData, honeypotValue = '') => {
    if (!tokenData) return {};

    return {
        'X-Form-Token': tokenData.token,
        'X-Form-Load-Time': tokenData.loadTime.toString(),
        'X-Honeypot': honeypotValue,
        'X-Idempotency-Key': uuidv4() // Generate a unique key for this specific submission
    };
};
