import "./src/i18n";

// The screen flows press dozens of buttons through the testing library; on a slow CI runner the longest ones went past
// the default 5 s (the first run of E2-S11 passed, the second did not, with no change in the code under test).
jest.setTimeout(30_000);
