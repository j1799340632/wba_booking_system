const countValue = value => {
  if (typeof value === "number" && Number.isSafeInteger(value) && value >= 0) return BigInt(value);
  if (typeof value === "string" && /^\d+$/.test(value)) return BigInt(value);
  return null;
};
export const insightCount = value => {
  const count = countValue(value);
  return count === null ? "—" : new Intl.NumberFormat("en-AU").format(count);
};
export function confirmationSaveRate(report) {
  const attempts = countValue(report?.bookingConfirmAttempts), failed = countValue(report?.bookingConfirmFailures);
  if (attempts === null || failed === null || attempts === 0n || failed > attempts) return null;
  return Number(((attempts - failed) * 1000n + attempts / 2n) / attempts) / 10;
}
function dateValue(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value || "")) return NaN;
  const time = Date.parse(`${value}T00:00:00Z`);
  return Number.isFinite(time) && new Date(time).toISOString().slice(0, 10) === value ? time : NaN;
}
export function insightsRange(startDate, endDate, today) {
  const start = dateValue(startDate), end = dateValue(endDate), current = dateValue(today);
  if (![start, end, current].every(Number.isFinite) || start > end || end > current || end - start > 30 * 86400000) throw new Error("Choose 1–31 calendar days ending no later than today in Australia/Sydney.");
  return { startDate, endDate };
}
export function insightPreset(today, days) {
  if (![7, 30].includes(days) || !Number.isFinite(dateValue(today))) throw new Error("The server date is not available. Refresh the report.");
  return { startDate: new Date(dateValue(today) - (days - 1) * 86400000).toISOString().slice(0, 10), endDate: today };
}
export const insightStageLabel = stage => ({ SEARCH: "Assistant requests", REPLACEMENTS: "Replacement searches", PREPARE_BOOKING: "Booking reviews", CONFIRM_BOOKING: "Booking confirmations", PREPARE_MANAGEMENT: "Change reviews", CONFIRM_MANAGEMENT: "Change confirmations" }[stage] || stage);
export const insightOutcomeLabel = outcome => ({ READY: "Exact sessions found", BOOKINGS: "Own bookings returned", PLAN: "Plan opened", CLARIFY: "Clarification needed", NO_RESULTS: "No exact sessions", UNAVAILABLE: "Search unavailable", UNSUPPORTED: "Unsupported request", REVIEW_READY: "Review prepared", SAVED: "New save", REPLAYED: "Saved result replayed", REJECTED: "Rejected by a rule", ERROR: "Unexpected error" }[outcome] || outcome);
