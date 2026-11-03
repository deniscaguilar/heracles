import * as SQLite from "expo-sqlite";

export interface SqlConnection {
  exec(sql: string): Promise<void>;
  /** First column of the first row, or null. */
  scalar(sql: string): Promise<string | null>;
  close(): Promise<void>;
}

export interface SqlDriver {
  open(fileName: string): Promise<SqlConnection>;
  /** Deletes the database file; a file that is not there is not an error. */
  remove(fileName: string): Promise<void>;
}

export const expoSqlDriver: SqlDriver = {
  async open(fileName) {
    const db = await SQLite.openDatabaseAsync(fileName);
    return {
      exec: (sql) => db.execAsync(sql),
      async scalar(sql) {
        const row = await db.getFirstAsync<Record<string, unknown>>(sql);
        const value = row ? Object.values(row)[0] : null;
        return value === null || value === undefined ? null : String(value);
      },
      close: () => db.closeAsync(),
    };
  },
  async remove(fileName) {
    try {
      await SQLite.deleteDatabaseAsync(fileName);
    } catch (error) {
      // expo-sqlite throws when the file does not exist; that is the state we want.
      if (!/not exist|no such file|not found/i.test(String(error))) throw error;
    }
  },
};
