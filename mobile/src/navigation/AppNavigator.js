import React, { useEffect, useState } from 'react';
import { createDrawerNavigator } from '@react-navigation/drawer';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { Drawer } from 'react-native-paper';
import AsyncStorage from '@react-native-async-storage/async-storage';

import DashboardScreen from '../screens/DashboardScreen';
import QuestionScreen from '../screens/QuestionScreen';
import ChatScreen from '../screens/ChatScreen';
import DocumentScreen from '../screens/DocumentScreen';
import ExamScreen from '../screens/ExamScreen';
import AnalysisScreen from '../screens/AnalysisScreen';
import AiConfigScreen from '../screens/AiConfigScreen';
import LoginScreen from '../screens/LoginScreen';
import RegisterScreen from '../screens/RegisterScreen';

const DrawerNav = createDrawerNavigator();
const Stack = createNativeStackNavigator();

const menuItems = [
  { name: 'Dashboard', label: '学习仪表盘', icon: 'view-dashboard' },
  { name: 'Question', label: '智能题库', icon: 'book-open-variant' },
  { name: 'Chat', label: 'AI 智能问答', icon: 'robot' },
  { name: 'Document', label: '资料管理', icon: 'file-document' },
  { name: 'Exam', label: '模拟考试', icon: 'clipboard-text' },
  { name: 'Analysis', label: '薄弱知识点', icon: 'chart-bar' },
  { name: 'AiConfig', label: 'AI 配置', icon: 'cog' },
];

function CustomDrawerContent({ navigation, state }) {
  const [username, setUsername] = useState('');

  useEffect(() => {
    const loadUser = async () => {
      const name = await AsyncStorage.getItem('username');
      if (name) setUsername(name);
    };
    loadUser();
  }, []);

  const handleLogout = async () => {
    await AsyncStorage.removeItem('token');
    await AsyncStorage.removeItem('username');
    navigation.replace('Login');
  };

  return (
    <>
      <Drawer.Section title={`你好，${username || '用户'}`}>
        {menuItems.map((item) => {
          const focused = state.routes[state.index]?.name === item.name;
          return (
            <Drawer.Item
              key={item.name}
              label={item.label}
              active={focused}
              onPress={() => navigation.navigate(item.name)}
            />
          );
        })}
      </Drawer.Section>
      <Drawer.Item
        label="退出登录"
        onPress={handleLogout}
      />
    </>
  );
}

function MainDrawer() {
  return (
    <DrawerNav.Navigator
      drawerContent={(props) => <CustomDrawerContent {...props} />}
      screenOptions={{
        headerStyle: { backgroundColor: '#1677ff' },
        headerTintColor: '#fff',
        headerTitleAlign: 'center',
      }}
    >
      <DrawerNav.Screen name="Dashboard" component={DashboardScreen} options={{ title: '学习仪表盘' }} />
      <DrawerNav.Screen name="Question" component={QuestionScreen} options={{ title: '智能题库' }} />
      <DrawerNav.Screen name="Chat" component={ChatScreen} options={{ title: 'AI 智能问答' }} />
      <DrawerNav.Screen name="Document" component={DocumentScreen} options={{ title: '资料管理' }} />
      <DrawerNav.Screen name="Exam" component={ExamScreen} options={{ title: '模拟考试' }} />
      <DrawerNav.Screen name="Analysis" component={AnalysisScreen} options={{ title: '薄弱知识点' }} />
      <DrawerNav.Screen name="AiConfig" component={AiConfigScreen} options={{ title: 'AI 配置' }} />
    </DrawerNav.Navigator>
  );
}

export default function AppNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="Login" component={LoginScreen} />
      <Stack.Screen name="Register" component={RegisterScreen} />
      <Stack.Screen name="Main" component={MainDrawer} />
    </Stack.Navigator>
  );
}