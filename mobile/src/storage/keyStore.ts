import * as Crypto from "expo-crypto";
import * as SecureStore from "expo-secure-store";

/** One random encryption key per user, kept only in the Keychain (iOS) or the Keystore (Android). */
export interface KeyStore {
  /** Returns the user's key as 64 hex characters, creating it on first use. */
  getOrCreate(userId: string): Promise<string>;
  delete(userId: string): Promise<void>;
}

/** The user id comes from the backend (a UUID); it also names the key and the database file. */
export function assertUserId(userId: string): void {
  if (!/^[A-Za-z0-9-]{1,64}$/.test(userId)) throw new Error("invalid user id");
}

const keyName = (userId: string) => `heracles.db.key.${userId}`;

export const secureKeyStore: KeyStore = {
  async getOrCreate(userId) {
    assertUserId(userId);
    const existing = await SecureStore.getItemAsync(keyName(userId));
    if (existing && /^[0-9a-f]{64}$/.test(existing)) return existing;
    const bytes = await Crypto.getRandomBytesAsync(32);
    const key = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
    await SecureStore.setItemAsync(keyName(userId), key);
    return key;
  },
  async delete(userId) {
    assertUserId(userId);
    await SecureStore.deleteItemAsync(keyName(userId));
  },
};

/** Remembers which user's data is on the device, so it can be erased even after a crash or a 401. */
export interface CurrentUserPointer {
  get(): Promise<string | null>;
  set(userId: string): Promise<void>;
  clear(): Promise<void>;
}

const POINTER = "heracles.db.current_user";

export const securePointer: CurrentUserPointer = {
  get: () => SecureStore.getItemAsync(POINTER),
  set: (userId) => SecureStore.setItemAsync(POINTER, userId),
  clear: () => SecureStore.deleteItemAsync(POINTER),
};
