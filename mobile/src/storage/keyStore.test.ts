import * as Crypto from "expo-crypto";
import * as SecureStore from "expo-secure-store";
import { secureKeyStore } from "./keyStore";

jest.mock("expo-crypto", () => ({ getRandomBytesAsync: jest.fn() }));
jest.mock("expo-secure-store", () => ({
  getItemAsync: jest.fn(),
  setItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

const get = SecureStore.getItemAsync as jest.Mock;
const random = Crypto.getRandomBytesAsync as jest.Mock;
const USER = "11111111-1111-1111-1111-111111111111";

describe("secure key store", () => {
  beforeEach(() => jest.clearAllMocks());

  it("creates a random 256-bit key on first use and keeps it in the secure store under the user's name", async () => {
    get.mockResolvedValue(null);
    random.mockResolvedValue(Uint8Array.from({ length: 32 }, (_, i) => i));
    const key = await secureKeyStore.getOrCreate(USER);
    expect(random).toHaveBeenCalledWith(32);
    expect(key).toMatch(/^[0-9a-f]{64}$/);
    expect(SecureStore.setItemAsync).toHaveBeenCalledWith(`heracles.db.key.${USER}`, key);
  });

  it("returns the stored key without making a new one", async () => {
    get.mockResolvedValue("ab".repeat(32));
    expect(await secureKeyStore.getOrCreate(USER)).toBe("ab".repeat(32));
    expect(random).not.toHaveBeenCalled();
    expect(SecureStore.setItemAsync).not.toHaveBeenCalled();
  });

  it("replaces a stored value that is not a valid key", async () => {
    get.mockResolvedValue("garbage");
    random.mockResolvedValue(new Uint8Array(32).fill(255));
    expect(await secureKeyStore.getOrCreate(USER)).toBe("ff".repeat(32));
  });

  it("deletes the user's key, and refuses an id that is not an identifier", async () => {
    await secureKeyStore.delete(USER);
    expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith(`heracles.db.key.${USER}`);
    await expect(secureKeyStore.getOrCreate("a/b")).rejects.toThrow("invalid user id");
    await expect(secureKeyStore.delete("a b")).rejects.toThrow("invalid user id");
  });
});
