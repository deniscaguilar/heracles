import {
  initialize, getSdkStatus, SdkAvailabilityStatus, getGrantedPermissions,
  insertRecords, readRecords,
} from "react-native-health-connect";

// Protótipo descartável da P6 (Android): escreve dados SIMULADOS no Health Connect e lê de volta.
// As permissões são concedidas por fora (adb pm grant) para não depender da tela de consentimento.
export async function run() {
  const out: Record<string, unknown> = {};
  out.sdk_status = await getSdkStatus();
  out.sdk_disponivel = out.sdk_status === SdkAvailabilityStatus.SDK_AVAILABLE;
  out.initialize = await initialize();
  out.permissoes_concedidas = (await getGrantedPermissions()).map((p) => `${p.accessType}:${p.recordType}`);
  const now = new Date();
  const t = (min: number) => new Date(now.getTime() - min * 60_000).toISOString();
  const records = [
    { recordType: "Weight", time: t(5), weight: { value: 90.9, unit: "kilograms" } },
    { recordType: "BodyFat", time: t(5), percentage: 7.6 },
    { recordType: "LeanBodyMass", time: t(5), mass: { value: 84.0, unit: "kilograms" } },
    { recordType: "BasalMetabolicRate", time: t(5), basalMetabolicRate: { value: 2184 * 4.184 / 86.4, unit: "kilocaloriesPerDay" } },
    { recordType: "Steps", startTime: t(60), endTime: t(30), count: 4200 },
  ];
  const written: unknown[] = [];
  for (const r of records) {
    try { written.push(...(await insertRecords([r] as never))); }
    catch (e) { out[`erro_escrita_${r.recordType}`] = String(e); }
  }
  out.registros_escritos = written.length;
  const range = { operator: "between", startTime: t(120), endTime: now.toISOString() } as const;
  for (const type of ["Weight", "BodyFat", "LeanBodyMass", "BasalMetabolicRate", "Steps"] as const) {
    const r = await readRecords(type as never, { timeRangeFilter: range });
    out[`lido_${type}`] = r.records.length;
    const first = r.records[0] as Record<string, unknown> | undefined;
    if (first) out[`origem_${type}`] = (first.metadata as { dataOrigin?: string } | undefined)?.dataOrigin;
  }
  return out;
}
