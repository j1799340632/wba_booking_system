import { formatBookingDate, formatBookingTime } from "./bookingSearch.js";

const STATUSES = new Set(["READY", "BOOKINGS", "PLAN", "CLARIFY", "NO_RESULTS", "UNAVAILABLE", "UNSUPPORTED"]);

export function readAssistantResult(response) {
  const result = response?.data;
  if (!result || !STATUSES.has(result.status) || typeof result.message !== "string") {
    throw new Error("AI search returned an unexpected response. Please use the filters below.");
  }
  return { ...result, candidates: Array.isArray(result.candidates) ? result.candidates : [], bookings: Array.isArray(result.bookings) ? result.bookings : [],
    alternatives: Array.isArray(result.alternatives) ? result.alternatives.filter(option =>
      ["DATE", "TIME", "SUPERVISOR", "WEEKDAYS"].includes(option?.kind) && typeof option.title === "string" && option.conditions && Array.isArray(option.candidates) && option.candidates.length > 0) : [] };
}

export function alternativeChangeLabels(original, alternative) {
  const changed = alternative.conditions;
  const range = q => `${formatBookingDate(q.startTime)} ${formatBookingTime(q.startTime)} to ${formatBookingDate(q.endTime)} ${formatBookingTime(q.endTime)}`;
  if (alternative.kind === "DATE") return [`Dates: ${range(original)} → ${range(changed)}`];
  if (alternative.kind === "SUPERVISOR") return [`Supervisor: ${original.supervisorName || "Selected supervisor"} → another available supervisor`];
  if (alternative.kind === "TIME") {
    const daily = original.earliestTime || original.latestTime;
    const start = daily ? original.earliestTime?.slice(0, 5) || "00:00" : formatBookingTime(original.startTime);
    const end = daily ? original.latestTime?.slice(0, 5) || "24:00" : formatBookingTime(original.endTime);
    return [`Daily time: ${start}–${end} → any time on the same dates`];
  }
  const days = ["", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
  return [`Weekdays: ${(original.weekdays || []).map(day => days[day]).join(", ")} → any weekday within the date window`];
}

export function removeBookedCandidate(result, session) {
  const keep = item => !(item.slotId === session.slotId && item.startTime === session.startTime);
  return { ...result, candidates: (result.candidates || []).filter(keep),
    alternatives: (result.alternatives || []).map(option => ({ ...option, candidates: option.candidates.filter(keep) })).filter(option => option.candidates.length > 0) };
}

export const bookingStatusLabel = status => ["Pending", "Confirmed", "Completed", "Declined", "Cancelled"][status] || "Unknown";

export function assistantConditionLabels(result) {
  const query = result?.conditions;
  if (!query) return [];
  const labels = [];
  if (query.startTime && query.endTime) {
    labels.push(`${formatBookingDate(query.startTime)} ${formatBookingTime(query.startTime)} → ${formatBookingDate(query.endTime)} ${formatBookingTime(query.endTime)}`);
  }
  for (const [key, title] of [["hospital", "Hospital"], ["department", "Department"], ["type", "Assessment"], ["supervisorName", "Supervisor"]]) {
    if (query[key]) labels.push(`${title}: ${query[key]}`);
  }
  if (query.earliestTime || query.latestTime) {
    labels.push(`Daily: ${query.earliestTime?.slice(0, 5) || "00:00"}–${query.latestTime?.slice(0, 5) || "24:00"}`);
  }
  const days = ["", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
  if (query.weekdays?.length) labels.push(query.weekdays.map(day => days[day]).join(", "));
  if (result.preferredSupervisor) labels.push(`Preferred: ${result.preferredSupervisor}`);
  return labels;
}
