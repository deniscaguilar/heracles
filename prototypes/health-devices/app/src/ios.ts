import {
  isHealthDataAvailable, requestAuthorization, saveQuantitySample, queryQuantitySamples,
} from "@kingstinct/react-native-healthkit";

// Protótipo descartável da P6 (iOS): escreve dados SIMULADOS no HealthKit e lê de volta.
const TYPES = [
  ["HKQuantityTypeIdentifierBodyMass", "kg", 90.9],
  ["HKQuantityTypeIdentifierBodyFatPercentage", "%", 7.6],
  ["HKQuantityTypeIdentifierLeanBodyMass", "kg", 84.0],
  ["HKQuantityTypeIdentifierBasalEnergyBurned", "kcal", 2184],
  ["HKQuantityTypeIdentifierStepCount", "count", 4200],
] as const;

export async function run() {
  const out: Record<string, unknown> = {};
  out.disponivel = await isHealthDataAvailable();
  const ids = TYPES.map((t) => t[0]);
  out.autorizacao = await requestAuthorization({ toRead: ids, toShare: ids } as never);
  const end = new Date();
  const start = new Date(end.getTime() - 30 * 60_000);
  let written = 0;
  for (const [id, unit, value] of TYPES) {
    try { const r = await saveQuantitySample(id as never, unit as never, value, start, end); if (r) written++; }
    catch (e) { out[`erro_escrita_${id}`] = String(e); }
  }
  out.registros_escritos = written;
  for (const [id, unit] of TYPES) {
    try {
      const r = await queryQuantitySamples(id as never, { limit: 5, unit } as never);
      out[`lido_${id.replace("HKQuantityTypeIdentifier", "")}`] = r.length;
      const first = r[0] as { quantity?: number; sourceRevision?: { source?: { bundleIdentifier?: string } } } | undefined;
      if (first) { out[`valor_${id.replace("HKQuantityTypeIdentifier", "")}`] = first.quantity; out[`origem_${id.replace("HKQuantityTypeIdentifier", "")}`] = first.sourceRevision?.source?.bundleIdentifier; }
    } catch (e) { out[`erro_leitura_${id}`] = String(e); }
  }
  return out;
}
