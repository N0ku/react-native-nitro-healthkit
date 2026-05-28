// Mock for react-native
module.exports = {
  Platform: {
    OS: 'ios',
    select: jest.fn((obj) => obj.ios),
  },
  NativeModules: {},
  TurboModuleRegistry: {
    get: jest.fn(),
  },
};
