import React from 'react';
import { PaperProvider } from 'react-native-paper';
import { NavigationContainer, DefaultTheme as NavDefaultTheme } from '@react-navigation/native';
import AppNavigator from './src/navigation/AppNavigator';
import { paperTheme, colors } from './src/theme';

const navTheme = {
  ...NavDefaultTheme,
  colors: {
    ...NavDefaultTheme.colors,
    primary: colors.primary,
    background: colors.background,
    card: colors.surface,
    text: colors.textPrimary,
    border: colors.border,
    notification: colors.danger,
  },
};

export default function App() {
  return (
    <PaperProvider theme={paperTheme}>
      <NavigationContainer theme={navTheme}>
        <AppNavigator />
      </NavigationContainer>
    </PaperProvider>
  );
}
