module.exports = {
  dependency: {
    platforms: {
      ios: {
        podspecPath: './NitroHealthkit.podspec',
      },
      android: {
        packageImportPath: 'import com.margelo.nitro.healthkit.NitroHealthkitPackage;',
        packageInstance: 'new NitroHealthkitPackage()',
      },
    },
  },
};
