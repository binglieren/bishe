import axios from 'axios';
import AsyncStorage from '@react-native-async-storage/async-storage';

const request = axios.create({
  baseURL: 'http://192.168.0.104:8088/api',
  timeout: 120000,
});

request.interceptors.request.use(async (config) => {
  const token = await AsyncStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

request.interceptors.response.use(
  (response) => {
    const res = response.data;
    console.log('[api] response:', response.config.url, 'code:', res.code);
    if (res.code !== 200) {
      console.error('[api] 业务错误:', response.config.url, res);
      return Promise.reject(new Error(res.message || '请求失败'));
    }
    return res;
  },
  async (error) => {
    console.error('[api] 网络错误:', error.config?.url, error.message, error.response?.status);
    if (error.response?.status === 403) {
      await AsyncStorage.removeItem('token');
    }
    const message = error.response?.data?.message || error.message || '请求失败';
    return Promise.reject(new Error(message));
  }
);

export default request;