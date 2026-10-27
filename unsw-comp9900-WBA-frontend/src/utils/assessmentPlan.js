export const planStatusLabel = status => ({
  NOT_CONFIGURED: "Set your targets", COMPLETE: "Targets completed", OVERDUE: "Deadline passed",
  NEEDS_BOOKING: "More sessions needed", AWAITING_APPROVAL: "Check pending approvals", SCHEDULED: "Complete your booked assessments",
}[status] || "Assessment plan");

export function planDeadlineLabel(plan) {
  if (!plan?.configured || !Number.isInteger(plan.daysToDeadline)) return "";
  if (plan.remaining === 0) return "Targets completed for this period";
  if (plan.daysToDeadline < 0) return `Deadline passed ${Math.abs(plan.daysToDeadline)} calendar day(s) ago`;
  if (plan.daysToDeadline === 0) return "Deadline is today";
  return `${plan.daysToDeadline} calendar day(s) until the deadline`;
}

export const followUpPresentation = kind => ({
  UNAPPROVED_SESSION: { label: "Scheduled start passed — approval missing", attention: true, detail: "This request is still Pending after its scheduled start. Check its status with your supervisor; it does not count as a completed assessment." },
  AWAITING_COMPLETION: { label: "Scheduled end passed — check completion", attention: true, detail: "This record is still Confirmed. Ask your supervisor to check completion or update its status; elapsed time alone does not complete an assessment." },
  IN_PROGRESS: { label: "Session in progress", detail: "The confirmed session is within its scheduled time window. It counts toward your target only after it is marked Completed." },
  UPCOMING: { label: "Confirmed — starts within 48 hours", detail: "Check the session details and prepare for your assessment." },
  PENDING_APPROVAL: { label: "Awaiting supervisor approval", detail: "This request is Pending. Check the latest status before making plans to attend." },
  SCHEDULED: { label: "Confirmed — scheduled later", detail: "Keep this session in your plan and check its status closer to the date." },
}[kind] || { label: "Check booking status", detail: "Open this booking to check its latest status." });

export const followUpBookingLink = bookingId => /^\d+$/.test(String(bookingId || ""))
  ? `/home/student/my-bookings?booking=${encodeURIComponent(String(bookingId))}` : "/home/student/my-bookings";

export function planFormValue(plan) {
  const today = plan.today;
  const deadline = new Date(`${today}T00:00:00Z`);
  deadline.setUTCDate(deadline.getUTCDate() + 28);
  return { startDate: plan.startDate || today, deadline: plan.deadline || deadline.toISOString().slice(0, 10), expectedVersion: plan.version || 0,
    goals: plan.goals?.length ? plan.goals.map(g => ({ typeId: String(g.typeId), target: String(g.target) })) : [{ typeId: "", target: "" }] };
}

export function planSavePayload(form) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(form.startDate || "") || !/^\d{4}-\d{2}-\d{2}$/.test(form.deadline || "")) throw new Error("Choose the start date and deadline.");
  const start = Date.parse(`${form.startDate}T00:00:00Z`), end = Date.parse(`${form.deadline}T00:00:00Z`);
  if (!Number.isFinite(start) || !Number.isFinite(end) || new Date(start).toISOString().slice(0, 10) !== form.startDate || new Date(end).toISOString().slice(0, 10) !== form.deadline || end < start || end - start > 365 * 86400000) throw new Error("Choose a period of 1–366 days, including the deadline.");
  if (!form.goals?.length || form.goals.length > 20) throw new Error("Set between 1 and 20 assessment targets.");
  const seen = new Set();
  const goals = form.goals.map(goal => {
    if (!/^\d+$/.test(goal.typeId) || seen.has(goal.typeId)) throw new Error("Choose each assessment type only once.");
    seen.add(goal.typeId);
    if (!/^\d+$/.test(String(goal.target)) || Number(goal.target) < 1 || Number(goal.target) > 100) throw new Error("Each target must be a whole number between 1 and 100.");
    return { typeId: goal.typeId, target: Number(goal.target) };
  });
  if (goals.reduce((sum, goal) => sum + goal.target, 0) > 1000) throw new Error("The plan cannot contain more than 1000 assessments.");
  return { startDate: form.startDate, deadline: form.deadline, expectedVersion: form.expectedVersion, goals };
}
