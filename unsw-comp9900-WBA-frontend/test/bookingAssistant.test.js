import test from "node:test";
import assert from "node:assert/strict";
import { assistantConditionLabels, readAssistantResult, bookingStatusLabel, alternativeChangeLabels, removeBookedCandidate } from "../src/utils/bookingAssistant.js";

test("alternatives retain NO_RESULTS and original conditions with explicit supervisor change", () => {
  const conditions = { supervisorName: "Dr Wang", type: "Mini-CEX" };
  const alternative = { kind: "SUPERVISOR", title: "Another supervisor", conditions: { type: "Mini-CEX" }, candidates: [{ slotId: "2069245030206742539", startTime: "2026-10-27 14:00:00" }] };
  const result = readAssistantResult({ data: { status: "NO_RESULTS", message: "No exact matches", conditions, alternatives: [alternative] } });
  assert.deepEqual(result.candidates, []); assert.equal(result.alternatives[0].candidates[0].slotId, "2069245030206742539");
  assert.equal(result.conditions.supervisorName, "Dr Wang"); assert.match(alternativeChangeLabels(conditions, alternative)[0], /Dr Wang → another/);
});
test("removing a booked alternative retains original query and removes its occurrence from every group", () => {
  const booked = { slotId: "100", startTime: "2026-10-27 14:00:00" }, later = { slotId: "100", startTime: "2026-11-03 14:00:00" };
  const result = { conditions: { type: "Mini-CEX" }, candidates: [], alternatives: [{ kind: "DATE", candidates: [booked, later] }, { kind: "TIME", candidates: [booked] }] };
  const next = removeBookedCandidate(result, booked);
  assert.equal(next.alternatives.length, 1); assert.deepEqual(next.alternatives[0].candidates, [later]); assert.equal(result.alternatives[0].candidates.length, 2); assert.equal(next.conditions.type, "Mini-CEX");
});
test("malformed and unknown alternative options do not appear as real suggestions", () => {
  const result = readAssistantResult({ data: { status: "NO_RESULTS", message: "No match", alternatives: [{ kind: "CHANGE_TYPE", title: "Different type", conditions: {}, candidates: [{}] }, { kind: "TIME", title: "Other time", conditions: {}, candidates: [] }] } });
  assert.deepEqual(result.alternatives, []);
});
test("open daily time bounds display as midnight to end-of-day rather than a reversed window", () => {
  const labels = alternativeChangeLabels({ earliestTime: "14:00:00", endTime: "2026-11-02 00:00:00" }, { kind: "TIME", conditions: {} });
  assert.match(labels[0], /14:00–24:00/);
});

test("management results retain exact booking identities, status and separate replacement conditions", () => {
  const id = "2069245030206742539";
  const response = { data: { status: "BOOKINGS", message: "Select your booking", bookings: [{ bookingId: id, status: 4, canManage: false }], replacementConditions: { startTime: "2026-10-29T12:00:00" } } };
  const result = readAssistantResult(response);
  assert.equal(result.bookings[0].bookingId, id);
  assert.equal(result.bookings[0].canManage, false);
  assert.equal(bookingStatusLabel(result.bookings[0].status), "Cancelled");
  assert.equal(result.replacementConditions.startTime, "2026-10-29T12:00:00");
  assert.deepEqual(result.candidates, []);
  assert.equal(bookingStatusLabel(99), "Unknown");
});

test("assistant result preserves string IDs and only accepts defined result statuses", () => {
  const id = "2069245030206742539";
  const result = readAssistantResult({ data: { status: "READY", message: "Found", candidates: [{ slotId: id }] } });
  assert.equal(result.candidates[0].slotId, id);
  assert.throws(() => readAssistantResult({ data: { status: "BOOKED", message: "Done" } }));
  assert.throws(() => readAssistantResult({}));
});

test("clarification, unavailable and no-results responses can have empty candidates without mutating input", () => {
  for (const status of ["CLARIFY", "NO_RESULTS", "UNAVAILABLE", "UNSUPPORTED"]) {
    const input = Object.freeze({ status, message: "Message" });
    assert.deepEqual(readAssistantResult({ data: input }).candidates, []);
    assert.equal(input.candidates, undefined);
  }
});

test("condition labels expose dates, daily time, weekdays and soft preference separately", () => {
  const labels = assistantConditionLabels({
    preferredSupervisor: "Dr Wang",
    conditions: {
      startTime: "2026-10-26 00:00:00", endTime: "2026-11-02 00:00:00",
      earliestTime: "12:00:00", latestTime: "18:00:00", weekdays: [2, 4], type: "Mini-CEX",
    },
  });
  assert.ok(labels.some(label => label.includes("26 Oct 2026 00:00") && label.includes("2 Nov 2026 00:00")));
  assert.ok(labels.includes("Daily: 12:00–18:00"));
  assert.ok(labels.includes("Tue, Thu"));
  assert.ok(labels.includes("Preferred: Dr Wang"));
  assert.ok(!labels.some(label => label === "Supervisor: Dr Wang"));
});
