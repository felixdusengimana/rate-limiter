/**
 * Development Environment Configuration
 * Uses API_URL from .env file
 */
export const environment = {
  production: false,
  apiUrl: process.env['API_URL'] || 'https://rate-limiter-3224.onrender.com/api'
};
