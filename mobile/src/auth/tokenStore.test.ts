import * as SecureStore from "expo-secure-store";
import { secureTokenStore } from "./tokenStore";

jest.mock("expo-secure-store", () => ({
  getItemAsync: jest.fn(),
  setItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

const get = SecureStore.getItemAsync as jest.Mock;

describe("secure token store", () => {
  beforeEach(() => jest.clearAllMocks());

  it("keeps the token in the secure store (Keychain or Keystore), not in plain storage", async () => {
    await secureTokenStore.save({ accessToken: "t", expiresAt: 10 });
    expect(SecureStore.setItemAsync).toHaveBeenCalledWith(expect.any(String), JSON.stringify({ accessToken: "t", expiresAt: 10 }));
    await secureTokenStore.clear();
    expect(SecureStore.deleteItemAsync).toHaveBeenCalledTimes(1);
  });

  it("reads back what was saved", async () => {
    get.mockResolvedValue(JSON.stringify({ accessToken: "t", expiresAt: 10 }));
    expect(await secureTokenStore.load()).toEqual({ accessToken: "t", expiresAt: 10 });
  });

  it.each([null, "not json", JSON.stringify({ accessToken: 1 }), JSON.stringify({})])("ignores a bad value (%s)", async (raw) => {
    get.mockResolvedValue(raw);
    expect(await secureTokenStore.load()).toBeNull();
  });
});
