import axios from 'axios';
import {useUserStore} from "@/config/store/useUserStore.js";
import {notify} from "@/utils/notify.js";

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 30000,
  headers: {'Content-Type': 'application/json;charset=UTF-8'},
  withCredentials: true,
});

// Request interceptor
request.interceptors.request.use(
  config => {
    const token = useUserStore.getState().userInfo?.token;
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  error => {
    return Promise.reject(error);
  }
);

// Response interceptor
request.interceptors.response.use(
  response => {
    if (response.request.responseType === 'blob') {
      return response;
    }

    const data = response.data
    if (data.code !== 2000) {
      const code = data.code
      const message = data.message

      notify.error(message ?? 'Server Error')

      // 4001 is special, need to redirect to login page
      if (code === 4001) {
        window.location.href = '/'
      }

      const customError = new Error(message);
      customError.status = code;
      return Promise.reject(customError);
    }
    return response.data
  },
  error => {
    let status = 500;
    let errorObj
    let errorMsg = 'Server Error'

    if (error.response) {
      status = error.response.status
      errorObj = error.response.data
      if (errorObj && 'error' in errorObj) {
        errorMsg = errorObj.error
      } else {
        errorMsg = error.response.statusText
      }
      switch (status) {
        case 401:
          window.location.href = '/'
          break;
        default:
          notify.error(errorMsg ?? 'Server Error')
      }
    } else if (error.request) {
      notify.error(error.request.statusText);
    } else {
      notify.error(errorMsg)
    }

    const customError = new Error(errorMsg);
    customError.status = status;
    return Promise.reject(customError);
  }
);

export default request;
