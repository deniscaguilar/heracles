import type { CurrentUserPointer, KeyStore } from "./keyStore";
import { createLocalStorage } from "./localStorage";
import type { SqlConnection, SqlDriver } from "./sqlDriver";

const A = "11111111-1111-1111-1111-111111111111";
const B = "22222222-2222-2222-2222-222222222222";

/** In-memory stand-ins that remember what happened, so the order of statements can be checked. */
function harness(options: { cipherVersion?: string | null } = {}) {
  const cipherVersion = options.cipherVersion === undefined ? "4.6.1" : options.cipherVersion;
  const keys = new Map<string, string>();
  const files = new Set<string>();
  const statements: Record<string, string[]> = {};
  const closed: string[] = [];
  let pointerValue: string | null = null;
  let counter = 0;

  const keyStore: KeyStore = {
    getOrCreate: async (id) => {
      if (!keys.has(id)) keys.set(id, String(++counter).padStart(64, "0"));
      return keys.get(id)!;
    },
    delete: async (id) => {
      keys.delete(id);
    },
  };
  const pointer: CurrentUserPointer = {
    get: async () => pointerValue,
    set: async (id) => {
      pointerValue = id;
    },
    clear: async () => {
      pointerValue = null;
    },
  };
  const driver: SqlDriver & { failRemove?: boolean } = {
    open: async (fileName): Promise<SqlConnection> => {
      files.add(fileName);
      statements[fileName] = [];
      return {
        exec: async (sql) => void statements[fileName]!.push(sql),
        scalar: async (sql) => {
          statements[fileName]!.push(sql);
          return cipherVersion;
        },
        close: async () => void closed.push(fileName),
      };
    },
    remove: async (fileName) => {
      if (driver.failRemove) throw new Error("disk");
      files.delete(fileName);
    },
  };
  const storage = createLocalStorage({ keys: keyStore, driver, pointer });
  return { storage, keys, files, statements, closed, driver, pointer: () => pointerValue };
}

describe("encrypted local database", () => {
  it("sets the key before anything else, as a raw 256-bit key, and checks that SQLCipher is there", async () => {
    const h = harness();
    await h.storage.open(A);
    const [first, second] = h.statements[`heracles-${A}.db`]!;
    expect(first).toMatch(/^PRAGMA key = "x'[0-9a-f]{64}'"$/);
    expect(second).toBe("PRAGMA cipher_version");
  });

  it("refuses to work, and leaves nothing open, when SQLCipher is not linked (the key would be ignored)", async () => {
    const h = harness({ cipherVersion: null });
    await expect(h.storage.open(A)).rejects.toThrow("SQLCipher is not available");
    expect(h.closed).toEqual([`heracles-${A}.db`]);
    expect(h.statements[`heracles-${A}.db`]!.some((s) => s.startsWith("CREATE TABLE"))).toBe(false);
  });

  it("gives each user their own key and file, and reuses the key on the next start", async () => {
    const h = harness();
    await h.storage.open(A);
    const keyOfA = h.keys.get(A);
    await h.storage.open(B);
    expect(h.keys.get(B)).not.toBe(keyOfA);
    expect(h.files.has(`heracles-${A}.db`)).toBe(false); // see the next test

    const again = harness();
    await again.storage.open(A);
    const k = again.keys.get(A);
    const restarted = createLocalStorage({
      keys: { getOrCreate: async () => k!, delete: async () => {} },
      driver: again.driver,
      pointer: { get: async () => A, set: async () => {}, clear: async () => {} },
    });
    await restarted.open(A);
    expect(again.statements[`heracles-${A}.db`]![0]).toContain(k!);
  });

  it("erases the data of another user before opening a new one", async () => {
    const h = harness();
    await h.storage.open(A);
    await h.storage.open(B);
    expect(h.files).toEqual(new Set([`heracles-${B}.db`]));
    expect(h.keys.has(A)).toBe(false);
    expect(h.keys.has(B)).toBe(true);
    expect(h.pointer()).toBe(B);
  });

  it("sign-out erases the file, the key and the pointer, and closes the connection", async () => {
    const h = harness();
    await h.storage.open(A);
    await h.storage.erase();
    expect(h.closed).toEqual([`heracles-${A}.db`]);
    expect(h.files.size).toBe(0);
    expect(h.keys.size).toBe(0);
    expect(h.pointer()).toBeNull();
  });

  it("erasing is safe to repeat and does nothing when there is nothing on the device", async () => {
    const h = harness();
    await h.storage.erase();
    await h.storage.open(A);
    await h.storage.erase();
    await h.storage.erase();
    expect(h.files.size).toBe(0);
  });

  it("when removing the file fails, still deletes the key and keeps the pointer so the next start retries", async () => {
    const h = harness();
    await h.storage.open(A);
    h.driver.failRemove = true;
    await expect(h.storage.erase()).rejects.toThrow("disk");
    expect(h.keys.has(A)).toBe(false); // without the key the file is unreadable noise
    expect(h.pointer()).toBe(A);

    h.driver.failRemove = false;
    await h.storage.erase();
    expect(h.files.size).toBe(0);
    expect(h.pointer()).toBeNull();
  });

  it("rejects a user id that could escape the file name", async () => {
    const h = harness();
    await expect(h.storage.open("../../etc/passwd")).rejects.toThrow("invalid user id");
    await expect(h.storage.open("")).rejects.toThrow("invalid user id");
    expect(h.files.size).toBe(0);
  });
});
