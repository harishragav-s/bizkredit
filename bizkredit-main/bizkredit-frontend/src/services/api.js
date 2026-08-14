import axios from 'axios';

const BASE_URL = 'http://localhost:8090';


const api = axios.create({
  baseURL: BASE_URL,
  headers: {

    'Content-Type': 'application/json',
  },
});


api.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('bizkredit_token');

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});


api.interceptors.response.use(

  (response) => response,

  (error) => {

    if (
      error.response &&
      error.response.status === 401 &&
      !error.config.url.includes('/api/auth/login') &&
      !error.config.url.includes('/api/auth/logout')
    ) {
      sessionStorage.removeItem('bizkredit_token');
      sessionStorage.removeItem('bizkredit_user');
    }

    // Forward the error to the caller
    return Promise.reject(error);
  }
);

export default api;