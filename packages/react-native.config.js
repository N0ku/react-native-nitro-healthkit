module.exports = {
  dependency: {
    platforms: {
      ios: {
        podspecPath: './NitroHealthkit.podspec',
      },
      android: {
        packageImportPath: 'import com.margelo.nitro.packages.NitroPackagesPackage;',
        packageInstance: 'new NitroPackagesPackage()',
      },
    },
  },
};
