import test from "node:test";
import assert from "node:assert/strict";
import { confirmationSaveRate, insightCount, insightPreset, insightsRange } from "../src/utils/assistantInsights.js";

test("aggregate counts preserve large JSON integer strings instead of rounding", () => {
  assert.equal(insightCount("9007199254740993"), "9,007,199,254,740,993");
  assert.equal(insightCount(0), "0");
  for (const value of [null, undefined, -1, "NaN"]) assert.equal(insightCount(value), "—");
});
test("confirmation attempt rate includes replayed successes while no attempts remains unknown", () => {
  assert.equal(confirmationSaveRate({ bookingConfirmAttempts: 3, bookingConfirmFailures: 1, bookingConfirmReplays: 1 }), 66.7);
  assert.equal(confirmationSaveRate({ bookingConfirmAttempts: "9007199254740994", bookingConfirmFailures: "4503599627370497" }), 50);
  assert.equal(confirmationSaveRate({ bookingConfirmAttempts: 0, bookingConfirmFailures: 0 }), null);
  assert.equal(confirmationSaveRate({ bookingConfirmAttempts: 1, bookingConfirmFailures: 2 }), null);
});
test("period validation respects inclusive calendar days and rejects future, reversed and impossible dates", () => {
  assert.deepEqual(insightsRange("2026-09-26", "2026-10-26", "2026-10-26"), { startDate: "2026-09-26", endDate: "2026-10-26" });
  for (const [start, end] of [["2026-09-25", "2026-10-26"], ["2026-10-27", "2026-10-27"], ["2026-10-25", "2026-10-24"], ["2026-02-31", "2026-03-01"]]) assert.throws(() => insightsRange(start, end, "2026-10-26"));
});
test("date shortcuts use the server's Sydney date across month and daylight saving boundaries", () => {
  assert.deepEqual(insightPreset("2026-10-05", 7), { startDate: "2026-09-29", endDate: "2026-10-05" });
  assert.deepEqual(insightPreset("2026-10-26", 30), { startDate: "2026-09-27", endDate: "2026-10-26" });
  assert.throws(() => insightPreset(undefined, 7));
});
