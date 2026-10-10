const base = require('./playwright.config');
module.exports = {
  ...base,
  testDir: './tests/e2e/restart',
  testIgnore: [],
  retries: 0,
};
