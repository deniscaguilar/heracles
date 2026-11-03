import { assertUserId, securePointer, secureKeyStore, type CurrentUserPointer, type KeyStore } from "./keyStore";
import { expoSqlDriver, type SqlConnection, type SqlDriver } from "./sqlDriver";

/** Health data kept on the device lives in an encrypted database that exists only while the user is signed in. */
export interface LocalStorage {
  /** Opens (creating it if needed) the encrypted database of the user. Data of any other user is erased first. */
  open(userId: string): Promise<void>;
  /** Closes the database and deletes the file and the key of the user whose data is on the device. */
  erase(): Promise<void>;
}

export interface LocalStorageDeps {
  keys: KeyStore;
  driver: SqlDriver;
  pointer: CurrentUserPointer;
}

const fileName = (userId: string) => `heracles-${userId}.db`;

export function createLocalStorage(
  deps: LocalStorageDeps = { keys: secureKeyStore, driver: expoSqlDriver, pointer: securePointer },
): LocalStorage {
  const { keys, driver, pointer } = deps;
  let connection: SqlConnection | null = null;

  async function erase() {
    const userId = await pointer.get();
    const current = connection;
    connection = null;
    const failures: unknown[] = [];
    const attempt = async (step: () => Promise<unknown>) => {
      try {
        await step();
      } catch (error) {
        failures.push(error);
      }
    };
    if (current) await attempt(() => current.close());
    if (userId) {
      await attempt(() => driver.remove(fileName(userId)));
      await attempt(() => keys.delete(userId));
    }
    // The pointer goes last and only when everything else worked: a failed erase is retried at the next start.
    if (failures.length === 0) await pointer.clear();
    else throw failures[0];
  }

  return {
    async open(userId) {
      assertUserId(userId);
      const onDevice = await pointer.get();
      if (onDevice && onDevice !== userId) await erase(); // never keep another user's data
      if (connection && onDevice === userId) return;

      const key = await keys.getOrCreate(userId);
      await pointer.set(userId);
      const opened = await driver.open(fileName(userId));
      try {
        // The key must be the first statement on the connection.
        await opened.exec(`PRAGMA key = "x'${key}'"`);
        // Without SQLCipher the key would be ignored and the data written in the clear: refuse to continue.
        if (!(await opened.scalar("PRAGMA cipher_version"))) throw new Error("SQLCipher is not available");
        await opened.exec("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        await opened.exec("INSERT OR IGNORE INTO meta (key, value) VALUES ('schema_version', '1')");
      } catch (error) {
        await opened.close().catch(() => {});
        throw error;
      }
      connection = opened;
    },
    erase,
  };
}
