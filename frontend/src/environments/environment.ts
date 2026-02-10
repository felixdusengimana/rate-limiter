/**
 * Development Environment Configuration
 * Uses API_URL from .env file
 */
export const environment = {
  production: false,
  apiUrl: process.env['API_URL'] || 'http://localhost:8080/api'
};
