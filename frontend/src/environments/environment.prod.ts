/**
 * Production Environment Configuration
 * Update apiUrl with your Cloud Run service URL
 */
export const environment = {
  production: true,
  apiUrl: process.env['API_URL']
};
