import * as SQLite from "expo-sqlite";
import type { Storage } from "./sync-client";

export function expoStorage(name: string): Storage & { db: SQLite.SQLiteDatabase } {
  const db = SQLite.openDatabaseSync(name);
  return {
    db,
    exec: (sql) => db.execSync(sql),
    run: (sql, params = []) => { db.runSync(sql, params as SQLite.SQLiteBindValue[]); },
    all: <T>(sql: string, params: unknown[] = []) => db.getAllSync(sql, params as SQLite.SQLiteBindValue[]) as T[],
    tx: (fn) => db.withTransactionSync(fn),
  };
}
