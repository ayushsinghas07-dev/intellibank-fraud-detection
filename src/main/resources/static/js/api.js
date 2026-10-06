// IntelliBank API Wrapper & HTTP Client
const API_BASE = '/api';

export const Api = {
    getToken() {
        return localStorage.getItem('intellibank_jwt_token');
    },

    setToken(token) {
        if (token) {
            localStorage.setItem('intellibank_jwt_token', token);
        } else {
            localStorage.removeItem('intellibank_jwt_token');
        }
    },

    getUser() {
        const str = localStorage.getItem('intellibank_user');
        try {
            return str ? JSON.parse(str) : null;
        } catch (e) {
            return null;
        }
    },

    setUser(user) {
        if (user) {
            localStorage.setItem('intellibank_user', JSON.stringify(user));
        } else {
            localStorage.removeItem('intellibank_user');
        }
    },

    async request(endpoint, options = {}) {
        const url = endpoint.startsWith('http') ? endpoint : `${API_BASE}${endpoint}`;
        const token = this.getToken();

        const headers = {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
            ...(options.headers || {})
        };

        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        const config = {
            ...options,
            headers
        };

        try {
            const response = await fetch(url, config);

            // 6. Log actual endpoint and HTTP status
            console.log(`[API ${options.method || 'GET'}] ${endpoint} -> Status ${response.status}`);

            // 2. Handle CSV Downloads
            if (headers['Accept'] === 'text/csv' || endpoint.includes('export-csv')) {
                if (!response.ok) {
                    const err = new Error(`Export failed with HTTP ${response.status}`);
                    err.status = response.status;
                    throw err;
                }
                return await response.blob();
            }

            // 2. Parse JSON only when content exists
            let json = null;
            const contentType = response.headers.get('content-type');
            const isJson = contentType && contentType.includes('application/json');

            if (response.status !== 204 && isJson) {
                const text = await response.text();
                if (text && text.trim().length > 0) {
                    try {
                        json = JSON.parse(text);
                    } catch (e) {
                        console.warn(`[API] Response from ${endpoint} was non-valid JSON:`, text);
                    }
                }
            }

            // 3 & 4. Handle Successful Responses (HTTP 200-299)
            if (response.ok) {
                if (json !== null) {
                    if (json.success !== undefined) {
                        return json.data !== undefined ? json.data : json;
                    }
                    return json;
                }
                return true; // 204 No Content or empty successful body
            }

            // 5. Correctly surface 400/401/403/404/409/422/500 errors
            if (response.status === 401) {
                this.setToken(null);
                this.setUser(null);
                if (!window.location.hash.startsWith('#/login')) {
                    window.location.hash = '#/login';
                }
            }

            let errorMessage = `HTTP ${response.status} Error`;
            if (json && json.error) {
                if (typeof json.error === 'string') {
                    errorMessage = json.error;
                } else if (json.error.message) {
                    errorMessage = json.error.message;
                }
            } else if (json && json.message) {
                errorMessage = json.message;
            }

            const errorObj = new Error(errorMessage);
            errorObj.status = response.status;
            errorObj.errorDetails = json ? json.error : null;
            throw errorObj;

        } catch (err) {
            console.error(`[API Error] ${endpoint}:`, err);
            throw err;
        }
    },

    get(endpoint, params = {}) {
        const query = new URLSearchParams(params).toString();
        const fullPath = query ? `${endpoint}?${query}` : endpoint;
        return this.request(fullPath, { method: 'GET' });
    },

    post(endpoint, body = {}, headers = {}) {
        return this.request(endpoint, {
            method: 'POST',
            body: JSON.stringify(body),
            headers
        });
    },

    put(endpoint, body = {}) {
        return this.request(endpoint, {
            method: 'PUT',
            body: JSON.stringify(body)
        });
    }
};
