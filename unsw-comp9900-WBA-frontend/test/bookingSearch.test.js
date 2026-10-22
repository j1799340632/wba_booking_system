import test from "node:test";
import assert from "node:assert/strict";
import process from "node:process";
import {
  EMPTY_BOOKING_FILTERS, buildBookingSearchParams, getBookingSearchError,
  formatBookingDate, formatBookingTime,
} from "../src/utils/bookingSearch.js";

test("search keeps local date literals and all applied filters when changing pages", () => {
  const filters = {
    ...EMPTY_BOOKING_FILTERS, hospital: "Demo Hospital", department: "Medicine", type: "Mini-CEX",
    supervisorName: " Wang ", startTime: "2026-10-27T13:00", endTime: "2026-10-27T17:00",
  };
  assert.deepEqual(buildBookingSearchParams(filters, 2, 10), {
    pageNum: 2, pageQuantity: 10, isOrderByAvailableTime: true,
    hospital: "Demo Hospital", department: "Medicine", type: "Mini-CEX",
    supervisorName: "Wang", startTime: "2026-10-27T13:00", endTime: "2026-10-27T17:00",
  });
});

test("reset leaves no supervisor or date restrictions in the request", () => {
  assert.deepEqual(buildBookingSearchParams(EMPTY_BOOKING_FILTERS), {
    pageNum: 1, pageQuantity: 10, isOrderByAvailableTime: true,
  });
});

test("equal or reversed ranges are rejected", () => {
  for (const endTime of ["2026-10-27T13:00", "2026-10-27T12:00"]) {
    assert.equal(getBookingSearchError({ startTime: "2026-10-27T13:00", endTime }), "From must be before To.");
  }
});

test("range validation accepts 90 days and rejects longer windows", () => {
  assert.equal(getBookingSearchError({ startTime: "2026-01-01T00:00", endTime: "2026-04-01T00:00" }), "");
  assert.equal(getBookingSearchError({ startTime: "2026-01-01T00:00", endTime: "2026-04-01T00:01" }), "Please search up to 90 days at a time.");
  assert.equal(getBookingSearchError({ startTime: "2026-10-27T13:00", endTime: "" }), "");
});

test("business dates and times display identically across browser time zones and DST gaps", () => {
  const previous = process.env.TZ;
  try {
    for (const zone of ["UTC", "Asia/Shanghai", "Australia/Sydney", "America/Los_Angeles"]) {
      process.env.TZ = zone;
      assert.equal(formatBookingTime("2026-10-27 14:00:00"), "14:00");
      assert.equal(formatBookingDate("2026-10-27T14:00:00"), "Tue, 27 Oct 2026");
      assert.equal(formatBookingTime("2026-10-04 02:30:00"), "02:30");
    }
  } finally {
    if (previous === undefined) delete process.env.TZ;
    else process.env.TZ = previous;
  }
});

test("missing and invalid date values do not show invalid date text", () => {
  assert.equal(formatBookingDate(null), "");
  assert.equal(formatBookingTime("invalid"), "");
});
