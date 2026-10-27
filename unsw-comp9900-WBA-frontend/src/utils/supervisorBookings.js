export function supervisorBookingVersion(booking) {
  const version = booking.bookingVersion ?? booking.version;
  if (!Number.isInteger(version) || version < 0) throw new Error("Refresh to obtain the booking's current version.");
  return version;
}

export function supervisorResultPayload(booking, score, feedback) {
  if (!/^\d+$/.test(String(booking.bookingId || ""))) throw new Error("Select an assessment record first.");
  if (String(score).trim() === "" || !Number.isFinite(Number(score)) || Number(score) < 0 || Number(score) > 100) throw new Error("Enter a score from 0 to 100.");
  if (!feedback?.trim() || feedback.length > 255) throw new Error("Enter feedback of 1–255 characters.");
  return { bookingId: String(booking.bookingId), version: supervisorBookingVersion(booking), score: Number(score), feedback: feedback.trim() };
}
