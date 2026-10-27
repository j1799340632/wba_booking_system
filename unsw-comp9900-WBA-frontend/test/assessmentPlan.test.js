import test from "node:test";
import assert from "node:assert/strict";
import { followUpBookingLink, followUpPresentation, planDeadlineLabel, planFormValue, planSavePayload, planStatusLabel } from "../src/utils/assessmentPlan.js";

const form = () => ({ startDate: "2026-10-26", deadline: "2026-11-23", expectedVersion: 2, goals: [{ typeId: "2069245030206742539", target: "3" }] });

test("new plans have a suggested period but no invented course quotas", () => {
  const result = planFormValue({ today: "2026-10-26", version: 0, goals: [] });
  assert.equal(result.deadline, "2026-11-23"); assert.deepEqual(result.goals, [{ typeId: "", target: "" }]);
  assert.throws(() => planSavePayload(result));
});
test("goal payload preserves exact long IDs and optimistic version without mutating form", () => {
  const input = form(), result = planSavePayload(input);
  assert.equal(result.goals[0].typeId, "2069245030206742539"); assert.equal(result.goals[0].target, 3); assert.equal(result.expectedVersion, 2);
  assert.equal(input.goals[0].target, "3");
});
test("duplicate types, fractional or out-of-range quotas are rejected", () => {
  for (const target of ["", "0", "-1", "1.5", "101", "3e1"]) assert.throws(() => planSavePayload({ ...form(), goals: [{ typeId: "100", target }] }));
  assert.throws(() => planSavePayload({ ...form(), goals: [{ typeId: "100", target: "2" }, { typeId: "100", target: "3" }] }));
});
test("period validation rejects reversed and impossible dates and allows 366 inclusive days", () => {
  assert.doesNotThrow(() => planSavePayload({ ...form(), startDate: "2026-01-01", deadline: "2027-01-01" }));
  for (const [startDate, deadline] of [["2026-11-01", "2026-10-01"], ["2026-02-31", "2026-03-31"], ["2026-01-01", "2027-01-02"]]) assert.throws(() => planSavePayload({ ...form(), startDate, deadline }));
});
test("saved target hydration retains exact goals instead of resetting to a fixed ten", () => {
  const result = planFormValue({ today: "2026-10-26", startDate: "2026-09-01", deadline: "2026-11-30", version: 4, goals: [{ typeId: "100", target: 2, completed: 1 }] });
  assert.equal(result.expectedVersion, 4); assert.equal(result.startDate, "2026-09-01"); assert.equal(result.goals[0].target, "2");
  assert.equal(planStatusLabel("AWAITING_APPROVAL"), "Check pending approvals");
});

test("deadline messages use the server's Sydney calendar days, including today and completed plans", () => {
  const plan = { configured: true, remaining: 2, daysToDeadline: 0 };
  assert.equal(planDeadlineLabel(plan), "Deadline is today");
  assert.equal(planDeadlineLabel({ ...plan, daysToDeadline: -1 }), "Deadline passed 1 calendar day(s) ago");
  assert.equal(planDeadlineLabel({ ...plan, daysToDeadline: 7 }), "7 calendar day(s) until the deadline");
  assert.equal(planDeadlineLabel({ ...plan, daysToDeadline: -5, remaining: 0 }), "Targets completed for this period");
  assert.equal(planDeadlineLabel({ configured: false }), "");
});

test("booking focus links preserve long IDs and reject untrusted or absent references", () => {
  assert.equal(followUpBookingLink("2069245030206742539"), "/home/student/my-bookings?booking=2069245030206742539");
  for (const id of [undefined, "", "../other", "100&student=2"]) assert.equal(followUpBookingLink(id), "/home/student/my-bookings");
});

test("ended and ongoing records carry different actions and never imply automatic completion or approval", () => {
  assert.equal(followUpPresentation("AWAITING_COMPLETION").attention, true);
  assert.equal(followUpPresentation("UNAPPROVED_SESSION").attention, true);
  assert.equal(followUpPresentation("IN_PROGRESS").attention, undefined);
  assert.match(followUpPresentation("IN_PROGRESS").detail, /only after it is marked Completed/);
  assert.match(followUpPresentation("UNAPPROVED_SESSION").detail, /still Pending/);
  assert.equal(followUpPresentation("unknown").label, "Check booking status");
});
