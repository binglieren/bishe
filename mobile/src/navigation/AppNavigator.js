import React from 'react';
import { Text as RNText, StyleSheet, View, TouchableOpacity } from 'react-native';
import { createMaterialTopTabNavigator } from '@react-navigation/material-top-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { getFocusedRouteNameFromRoute } from '@react-navigation/native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Button } from 'react-native-paper';

import DashboardScreen from '../screens/DashboardScreen';
import QuestionScreen from '../screens/QuestionScreen';
import ChatListScreen from '../screens/ChatListScreen';
import ChatScreen from '../screens/ChatScreen';
import KnowledgeBaseListScreen from '../screens/KnowledgeBaseListScreen';
import KnowledgeBaseDetailScreen from '../screens/KnowledgeBaseDetailScreen';
import AnalysisScreen from '../screens/AnalysisScreen';
import KnowledgeGraphScreen from '../screens/KnowledgeGraphScreen';
import DiagnosisReportScreen from '../screens/DiagnosisReportScreen';
import LoginScreen from '../screens/LoginScreen';
import RegisterScreen from '../screens/RegisterScreen';

import { colors, radii, shadows, typography } from '../theme';

const Tab = createMaterialTopTabNavigator();
const Stack = createNativeStackNavigator();

// ── Tab 显示配置（顺序决定底部栏位置，AI 问答放中间 index=2） ──
const TAB_CONFIG = {
  ProfileTab:  { emoji: '👤', label: '我的' },
  QuestionTab: { emoji: '📝', label: '学习记录' },
  ChatTab:     { emoji: '💬', label: 'AI 问答' },  // center
  KnowledgeBaseTab: { emoji: '📚', label: '知识库' },
  AnalysisTab: { emoji: '🗺️', label: '图谱' },
};
const CENTER_TAB_NAME = 'ChatTab';

// ── 通用 header 样式 ──────────────────────────────────
const headerOptions = {
  headerStyle: {
    backgroundColor: colors.primary,
    elevation: 0,
    shadowOpacity: 0,
  },
  headerTintColor: '#fff',
  headerTitleAlign: 'center',
  headerTitleStyle: { fontWeight: '700', fontSize: 17 },
  contentStyle: { backgroundColor: colors.background },
};

function LogoutButton({ navigation }) {
  const handleLogout = async () => {
    await AsyncStorage.removeItem('token');
    await AsyncStorage.removeItem('username');
    navigation.replace('Login');
  };
  return (
    <Button textColor="#fff" onPress={handleLogout} icon="logout" compact>
      退出
    </Button>
  );
}

// ── AI 问答子栈：ChatList → ChatDetail ────────────────
function ChatStack() {
  return (
    <Stack.Navigator screenOptions={headerOptions}>
      <Stack.Screen name="ChatList" component={ChatListScreen} options={{ headerShown: false }} />
      <Stack.Screen
        name="ChatDetail"
        component={ChatScreen}
        options={({ route }) => ({
          title: route.params?.title || '对话',
          headerBackTitle: '返回',
        })}
      />
    </Stack.Navigator>
  );
}

function ProfileStack() {
  return (
    <Stack.Navigator screenOptions={headerOptions}>
      <Stack.Screen
        name="Profile"
        component={DashboardScreen}
        options={({ navigation }) => ({
          title: '我的',
          headerRight: () => <LogoutButton navigation={navigation} />,
        })}
      />
    </Stack.Navigator>
  );
}
function QuestionStack() {
  return (
    <Stack.Navigator screenOptions={headerOptions}>
      <Stack.Screen name="QuestionMain" component={QuestionScreen} options={{ title: '学习记录' }} />
    </Stack.Navigator>
  );
}
function KnowledgeBaseStack() {
  return (
    <Stack.Navigator screenOptions={headerOptions}>
      <Stack.Screen
        name="KnowledgeBaseList"
        component={KnowledgeBaseListScreen}
        options={{ title: '知识库' }}
      />
      <Stack.Screen
        name="KnowledgeBaseDetail"
        component={KnowledgeBaseDetailScreen}
        options={({ route }) => ({
          title: route.params?.name || '知识库详情',
          headerBackTitle: '返回',
        })}
      />
    </Stack.Navigator>
  );
}
function AnalysisStack() {
  return (
    <Stack.Navigator screenOptions={headerOptions}>
      <Stack.Screen
        name="KnowledgeGraph"
        component={KnowledgeGraphScreen}
        options={{ title: '知识图谱' }}
      />
      <Stack.Screen
        name="DiagnosisReport"
        component={DiagnosisReportScreen}
        options={{ title: 'AI 学习诊断', headerBackTitle: '返回' }}
      />
      <Stack.Screen
        name="AnalysisMain"
        component={AnalysisScreen}
        options={{ title: '薄弱知识点列表', headerBackTitle: '返回' }}
      />
    </Stack.Navigator>
  );
}

// ── 判断是否应隐藏底部栏（进入沉浸子页时） ─────────
const shouldHideTabBar = (state) => {
  const currentTab = state.routes[state.index];
  const nested = getFocusedRouteNameFromRoute(currentTab);
  if (currentTab.name === CENTER_TAB_NAME && nested === 'ChatDetail') return true;
  if (currentTab.name === 'KnowledgeBaseTab' && nested === 'KnowledgeBaseDetail') return true;
  if (currentTab.name === 'AnalysisTab' && (nested === 'DiagnosisReport' || nested === 'AnalysisMain')) return true;
  return false;
};

// ── 自定义底部栏（emoji + 中间凸起大按钮） ─────────────
function CustomBottomTabBar({ state, descriptors, navigation }) {
  const insets = useSafeAreaInsets();

  if (shouldHideTabBar(state)) {
    return null;
  }

  return (
    <View style={[styles.barWrap, { paddingBottom: Math.max(insets.bottom, 6) }]}>
      <View style={styles.bar}>
        {state.routes.map((route, index) => {
          const config = TAB_CONFIG[route.name] || { emoji: '·', label: route.name };
          const focused = state.index === index;
          const isCenter = route.name === CENTER_TAB_NAME;

          const onPress = () => {
            const event = navigation.emit({
              type: 'tabPress',
              target: route.key,
              canPreventDefault: true,
            });
            if (!focused && !event.defaultPrevented) {
              navigation.navigate(route.name);
            }
          };

          if (isCenter) {
            return (
              <TouchableOpacity
                key={route.key}
                style={styles.centerItem}
                onPress={onPress}
                activeOpacity={0.85}
              >
                <View style={[styles.centerBtn, focused && styles.centerBtnActive]}>
                  <RNText style={styles.centerEmoji}>{config.emoji}</RNText>
                </View>
                <RNText style={[styles.centerLabel, focused && styles.centerLabelActive]}>
                  {config.label}
                </RNText>
              </TouchableOpacity>
            );
          }

          return (
            <TouchableOpacity
              key={route.key}
              style={styles.item}
              onPress={onPress}
              activeOpacity={0.7}
            >
              <View style={styles.iconBox}>
                <RNText
                  style={[
                    styles.emoji,
                    focused && { transform: [{ scale: 1.18 }] },
                  ]}
                >
                  {config.emoji}
                </RNText>
              </View>
              <RNText
                style={[
                  styles.label,
                  focused && styles.labelActive,
                ]}
              >
                {config.label}
              </RNText>
            </TouchableOpacity>
          );
        })}
      </View>
    </View>
  );
}

// ── Tab 容器 ────────────────────────────────────────────
function MainTabs() {
  return (
    <Tab.Navigator
      tabBar={(props) => <CustomBottomTabBar {...props} />}
      tabBarPosition="bottom"
      initialRouteName="ProfileTab"
      screenOptions={({ route, navigation }) => {
        // 进入 ChatDetail 时禁用 swipe，避免和详情页手势冲突
        const state = navigation.getState();
        const hide = shouldHideTabBar(state);
        return {
          swipeEnabled: !hide,
          animationEnabled: true,
          lazy: true,
        };
      }}
    >
      <Tab.Screen name="ProfileTab"  component={ProfileStack}  options={{ tabBarLabel: '我的' }} />
      <Tab.Screen name="QuestionTab" component={QuestionStack} options={{ tabBarLabel: '学习记录' }} />
      <Tab.Screen name="ChatTab"     component={ChatStack}     options={{ tabBarLabel: 'AI 问答' }} />
      <Tab.Screen name="KnowledgeBaseTab" component={KnowledgeBaseStack} options={{ tabBarLabel: '知识库' }} />
      <Tab.Screen name="AnalysisTab" component={AnalysisStack} options={{ tabBarLabel: '薄弱点' }} />
    </Tab.Navigator>
  );
}

export default function AppNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="Login" component={LoginScreen} />
      <Stack.Screen name="Register" component={RegisterScreen} />
      <Stack.Screen name="Main" component={MainTabs} />
    </Stack.Navigator>
  );
}

// ── Styles ──────────────────────────────────────────────
const BAR_HEIGHT = 62;
const CENTER_SIZE = 56;

const styles = StyleSheet.create({
  barWrap: {
    backgroundColor: colors.surface,
    borderTopWidth: 1,
    borderTopColor: colors.borderLight,
  },
  bar: {
    flexDirection: 'row',
    height: BAR_HEIGHT,
    alignItems: 'center',
    paddingHorizontal: 4,
  },
  // 普通 tab
  item: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 2,
  },
  iconBox: {
    height: 26,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emoji: {
    fontSize: 22,
  },
  label: {
    fontSize: 11,
    color: colors.textSecondary,
    fontWeight: '600',
  },
  labelActive: {
    color: colors.primary,
    fontWeight: '700',
  },
  // 中间凸起 AI 按钮
  centerItem: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'flex-end',
  },
  centerBtn: {
    width: CENTER_SIZE,
    height: CENTER_SIZE,
    borderRadius: CENTER_SIZE / 2,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: -20, // 凸起效果
    marginBottom: 4,
    borderWidth: 4,
    borderColor: colors.surface,
    ...shadows.colored,
  },
  centerBtnActive: {
    backgroundColor: colors.primaryDark,
    transform: [{ scale: 1.05 }],
  },
  centerEmoji: {
    fontSize: 26,
  },
  centerLabel: {
    fontSize: 11,
    color: colors.textSecondary,
    fontWeight: '600',
  },
  centerLabelActive: {
    color: colors.primary,
    fontWeight: '700',
  },
});
