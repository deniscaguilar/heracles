import * as SecureStore from "expo-secure-store";

export interface StoredToken {
  accessToken: string;
  /** Epoch milliseconds. */
  expiresAt: number;
}

/** Where the tokens live: the iOS Keychain and the Android Keystore, never plain storage. */
export interface TokenStore {
  load(): Promise<StoredToken | null>;
  save(token: StoredToken): Promise<void>;
  clear(): Promise<void>;
}

const KEY = "heracles.auth.token";

export const secureTokenStore: TokenStore = {
  async load() {
    const raw = await SecureStore.getItemAsync(KEY);
    if (!raw) return null;
    try {
      const parsed = JSON.parse(raw) as Partial<StoredToken>;
      return typeof parsed.accessToken === "string" && typeof parsed.expiresAt === "number"
        ? { accessToken: parsed.accessToken, expiresAt: parsed.expiresAt }
        : null;
    } catch {
      return null;
    }
  },
  save: (token) => SecureStore.setItemAsync(KEY, JSON.stringify(token)),
  clear: () => SecureStore.deleteItemAsync(KEY),
};
