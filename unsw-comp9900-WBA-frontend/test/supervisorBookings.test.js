import test from "node:test";
import assert from "node:assert/strict";
import { supervisorBookingVersion, supervisorResultPayload } from "../src/utils/supervisorBookings.js";

test("supervisor decisions use booking version zero instead of the slot template version", () => {
  assert.equal(supervisorBookingVersion({ bookingVersion: 0, version: 17 }), 0);
  assert.equal(supervisorBookingVersion({ version: 3 }), 3);
  for (const version of [undefined, -1, "0", 1.5]) assert.throws(() => supervisorBookingVersion({ version }));
});
test("result payload retains exact booking ID, normalizes feedback and includes the reviewed version", () => {
  const booking = { bookingId: "2069245030206742539", bookingVersion: 2, version: 17 };
  assert.deepEqual(supervisorResultPayload(booking, "0", "  Observed assessment  "), { bookingId: booking.bookingId, version: 2, score: 0, feedback: "Observed assessment" });
  assert.equal(booking.version, 17);
});
test("invalid scores or feedback cannot be submitted through result forms", () => {
  const booking = { bookingId: "100", version: 0 };
  for (const score of ["", "  ", "NaN", "Infinity", -1, 100.01]) assert.throws(() => supervisorResultPayload(booking, score, "Observed"));
  for (const feedback of ["", " ", "x".repeat(256)]) assert.throws(() => supervisorResultPayload(booking, 80, feedback));
  assert.doesNotThrow(() => supervisorResultPayload(booking, "100", "x".repeat(255)));
});
