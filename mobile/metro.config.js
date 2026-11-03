const path = require("node:path");
const { getDefaultConfig } = require("expo/metro-config");

const config = getDefaultConfig(__dirname);

// The generated API client is a local package (`file:../packages/api-client`): Metro has to watch it and
// resolve its own dependencies (openapi-fetch) from its folder.
const apiClient = path.resolve(__dirname, "../packages/api-client");
config.watchFolders = [...(config.watchFolders ?? []), apiClient];
config.resolver.nodeModulesPaths = [
  path.resolve(__dirname, "node_modules"),
  path.join(apiClient, "node_modules"),
];

module.exports = config;
