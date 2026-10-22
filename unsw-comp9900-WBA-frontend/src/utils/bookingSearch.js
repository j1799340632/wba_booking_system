export const EMPTY_BOOKING_FILTERS = {
  hospital: "", department: "", type: "", supervisorName: "", startTime: "", endTime: "",
};

export function buildBookingSearchParams(filters, pageNum = 1, pageQuantity = 10) {
  const params = { pageNum, pageQuantity, isOrderByAvailableTime: true };
  for (const field of Object.keys(EMPTY_BOOKING_FILTERS)) {
    const value = filters[field]?.trim();
    if (value) params[field] = value;
  }
  return params;
}

export function getBookingSearchError(filters) {
  if (!filters.startTime || !filters.endTime) return "";
  if (filters.startTime >= filters.endTime) return "From must be before To.";
  const span = Date.parse(`${filters.endTime}Z`) - Date.parse(`${filters.startTime}Z`);
  return span > 90 * 24 * 60 * 60 * 1000 ? "Please search up to 90 days at a time." : "";
}

// The API returns business-local wall times without an offset. Avoid browser-zone conversion.
function businessDate(value) {
  if (!value) return null;
  const date = new Date(`${value.replace(" ", "T")}Z`);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatBookingDate(value) {
  return businessDate(value)?.toLocaleDateString("en-AU", {
    weekday: "short", day: "numeric", month: "short", year: "numeric", timeZone: "UTC",
  }) ?? "";
}

export function formatBookingTime(value) {
  return businessDate(value)?.toLocaleTimeString("en-AU", {
    hour: "2-digit", minute: "2-digit", hour12: false, timeZone: "UTC",
  }) ?? "";
}
