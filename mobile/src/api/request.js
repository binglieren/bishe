import axios from 'axios';
import AsyncStorage from '@react-native-async-storage/async-storage';

const request = axios.create({
  baseURL: 'http://192.168.0.102:8088/api',
  timeout: 30000,
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
    if (res.code !== 200) {
      return Promise.reject(new Error(res.message || '请求失败'));
    }
    return res;
  },
  async (error) => {
    if (error.response?.status === 403) {
      await AsyncStorage.removeItem('token');
    }
    const message = error.response?.data?.message || error.message || '请求失败';
    return Promise.reject(new Error(message));
  }
);

export default request;