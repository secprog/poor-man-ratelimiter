import axios from 'axios';

const api = axios.create({
    baseURL: 'http://localhost:8080/api/admin', // Gateway Admin API
    headers: {
        'Content-Type': 'application/json',
    },
});

export default api;
