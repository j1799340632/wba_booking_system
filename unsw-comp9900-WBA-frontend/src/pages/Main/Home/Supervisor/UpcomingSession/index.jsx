import "./index.css";
import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { approveSlot, cancelSlot, completeSlot, declineSlot, getApprovedSession, getUpcomingSession } from "@/config/request/api/assessmentSlotApi.js";
import { addBookingToGoogleCalendar, connectGoogleCalendar, getGoogleCalendarStatus } from "@/config/request/api/googleCalendarApi.js";
import { supervisorBookingVersion, supervisorResultPayload } from "@/utils/supervisorBookings.js";
import { formatBookingDate, formatBookingTime } from "@/utils/bookingSearch.js";
import BaseModal from "@/components/BaseModal/index.jsx";
import { useSse } from "@/utils/sse/useSse.js";
import { notificationEvent } from "@/utils/sse/sseEventChannel.js";

const actionLabels = { APPROVE: "Approve booking", DECLINE: "Decline booking", CANCEL: "Cancel confirmed session", COMPLETE: "Complete assessment" };
const receiptLabels = { APPROVE: "Booking approved", DECLINE: "Booking declined", CANCEL: "Booking cancelled", COMPLETE: "Assessment completed" };

function SessionDetails({ booking }) {
  return <><p>{formatBookingDate(booking.startTime)} · {formatBookingTime(booking.startTime)}–{booking.startTime?.slice(0, 10) !== booking.endTime?.slice(0, 10) && `${formatBookingDate(booking.endTime)} · `}{formatBookingTime(booking.endTime)} ({booking.timeZone || "Australia/Sydney"})</p>
    <p>{[booking.hospital, booking.department, booking.location].filter(Boolean).join(" · ")}</p><small>Reference: {booking.bookingId}</small></>;
}

export default function UpcomingSessionSupervisor() {
  const [pending, setPending] = useState([]), [approved, setApproved] = useState([]);
  const [loading, setLoading] = useState(true), [loadError, setLoadError] = useState("");
  const [action, setAction] = useState(null), [busy, setBusy] = useState(false), [actionError, setActionError] = useState("");
  const [checked, setChecked] = useState(false), [reopen, setReopen] = useState(true);
  const [score, setScore] = useState(""), [feedback, setFeedback] = useState("");
  const [receipt, setReceipt] = useState(null);
  const [calendar, setCalendar] = useState(null), [calendarBusy, setCalendarBusy] = useState(null), [calendarNotice, setCalendarNotice] = useState("");
  const writeLock = useRef(false), loadId = useRef(0);

  const load = useCallback(signal => {
    const id = ++loadId.current;
    return Promise.all([getUpcomingSession(1, -1, signal), getApprovedSession(1, -1, signal)]).then(([requests, sessions]) => {
      if (id !== loadId.current || signal?.aborted) return;
      if (!Array.isArray(requests?.data?.records) || !Array.isArray(sessions?.data?.records)) throw new Error("Session records could not be loaded.");
      setPending(requests.data.records); setApproved(sessions.data.records); setLoadError("");
    }).catch(error => { if (id === loadId.current && !signal?.aborted) setLoadError(error.message || "Unable to load sessions. Refresh to try again."); })
      .finally(() => { if (id === loadId.current) setLoading(false); });
  }, []);
  const refresh = () => { setLoading(true); return load(); };
  useEffect(() => {
    const controller = new AbortController(); void load(controller.signal);
    getGoogleCalendarStatus().then(response => setCalendar(Boolean(response?.data?.connected))).catch(() => setCalendar(false));
    return () => { loadId.current += 1; controller.abort(); };
  }, [load]);
  useSse(notificationEvent, () => { if (!writeLock.current) void refresh(); });

  const open = (kind, booking) => {
    if (writeLock.current) return;
    setAction({ kind, booking }); setActionError(""); setChecked(false); setScore(""); setFeedback(""); setReopen(kind === "DECLINE");
  };
  const close = () => { if (!writeLock.current) { setAction(null); setActionError(""); } };
  const submit = async event => {
    event.preventDefault();
    if (!action || !checked || writeLock.current) return;
    writeLock.current = true; setBusy(true); setActionError("");
    const { kind, booking } = action;
    try {
      const version = supervisorBookingVersion(booking);
      const response = kind === "COMPLETE" ? await completeSlot(supervisorResultPayload(booking, score, feedback))
        : kind === "APPROVE" ? await approveSlot(booking.id, booking.bookingId, version)
          : kind === "DECLINE" ? await declineSlot(booking.id, booking.bookingId, reopen, version)
            : await cancelSlot(booking.id, booking.bookingId, reopen, version);
      if (!response?.data?.bookingId) throw new Error("No decision receipt was received.");
      setReceipt({ ...response.data, label: receiptLabels[kind] }); setAction(null);
      await refresh();
    } catch (error) { setActionError(`${error.message || "No decision response was received."} Retry the same decision or close and refresh to check its status.`); }
    finally { writeLock.current = false; setBusy(false); }
  };
  const updateCalendar = async booking => {
    if (calendarBusy) return;
    setCalendarBusy(booking.bookingId); setCalendarNotice("");
    try {
      if (calendar) { await addBookingToGoogleCalendar(booking.bookingId); setCalendarNotice("Added to Google Calendar."); }
      else { const response = await connectGoogleCalendar(window.location.pathname); if (!response?.data?.authorizationUrl) throw new Error("Calendar connection is unavailable."); window.location.href = response.data.authorizationUrl; }
    } catch (error) { setCalendarNotice(error.message || "Calendar update failed. Retry when available."); }
    finally { setCalendarBusy(null); }
  };
  const card = booking => <article className="upcoming-session-card" key={booking.bookingId} aria-label={`Assessment booking ${booking.bookingId}`}>
    <div className="upcoming-session-card-content"><div className="upcoming-session-name-row"><Link to={`/home/supervisor/student-profile?id=${encodeURIComponent(booking.studentId)}`}>{booking.studentName}</Link><span className="upcoming-session-badge">{booking.type}</span></div>
      <SessionDetails booking={booking} /><p className="supervisor-session-guidance">{booking.actionGuidance}</p></div>
    <div className="upcoming-session-actions">
      {booking.bookingStatus === 0 ? <>
        <button type="button" className="upcoming-session-button approve" disabled={loading || busy || !booking.canApprove} onClick={() => open("APPROVE", booking)}>Approve</button>
        <button type="button" className="upcoming-session-button decline" disabled={loading || busy || !booking.canDecline} onClick={() => open("DECLINE", booking)}>Decline</button>
      </> : <>
        {booking.canCancel && <button type="button" className="upcoming-session-button approve" disabled={calendar === null || Boolean(calendarBusy) || busy} onClick={() => updateCalendar(booking)}>{calendarBusy === booking.bookingId ? "Updating calendar…" : calendar ? "Add to Calendar" : "Connect Calendar"}</button>}
        <button type="button" className="upcoming-session-button complete" disabled={loading || busy || !booking.canComplete} onClick={() => open("COMPLETE", booking)}>Complete</button>
        <button type="button" className="upcoming-session-button cancel" disabled={loading || busy || !booking.canCancel} onClick={() => open("CANCEL", booking)}>Cancel</button>
      </>}
    </div>
  </article>;

  return <main className="upcoming-session-main"><div className="upcoming-session-container">
    <div className="upcoming-session-header"><h1 className="upcoming-session-title">Upcoming Sessions</h1><p className="upcoming-session-subtitle">{approved.length} confirmed · {pending.length} pending · Australia/Sydney</p>
      <button type="button" className="upcoming-session-button approve" disabled={loading || busy || Boolean(action)} onClick={refresh}>Refresh sessions</button></div>
    {loading && <p role="status">Loading sessions…</p>}
    {loadError && <p role="alert" className="supervisor-session-error">{loadError}</p>}
    {calendarNotice && <p role="status">{calendarNotice}</p>}
    {receipt && <div role="status" className="supervisor-session-receipt"><strong>{receipt.label}</strong><p>Reference: {receipt.bookingId}. {receipt.notificationStatus === "LIVE_DELAYED" ? "In-app notifications saved; live alerts delayed." : "In-app notifications saved."} {receipt.status === 2 && <Link to="/home/supervisor/assessment-history">View Assessment History</Link>}</p></div>}
    <section className="upcoming-session-section pending" aria-label="Requests awaiting approval"><h2 className="upcoming-session-section-title">Request Approval</h2>{pending.map(card)}{!loading && pending.length === 0 && <p className="upcoming-session-empty">No pending requests.</p>}</section>
    <section className="upcoming-session-section approved" aria-label="Confirmed assessment sessions"><h2 className="upcoming-session-section-title">Approved Sessions</h2>{approved.map(card)}{!loading && approved.length === 0 && <p className="upcoming-session-empty">No confirmed sessions.</p>}</section>
    <BaseModal isOpen={Boolean(action)} onClose={close} title={actionLabels[action?.kind] || "Review decision"}>
      {action && <form onSubmit={submit} role="dialog" aria-modal="true" aria-label={actionLabels[action.kind]} className="supervisor-session-review">
        <h3>{action.booking.studentName} · {action.booking.type}</h3><SessionDetails booking={action.booking} />
        {action.kind === "COMPLETE" ? <>
          <p>Record a result only if the assessment took place. Saving changes this booking to Completed and updates the student's progress.</p>
          <label>Score (0–100)<input type="number" min="0" max="100" step="0.01" required value={score} disabled={busy} onChange={e => setScore(e.target.value)} /></label>
          <label>Feedback<textarea maxLength={255} required value={feedback} disabled={busy} onChange={e => setFeedback(e.target.value)} /></label>
        </> : <p>{action.kind === "APPROVE" ? "Approve this request for the actual session shown above. Approval does not complete the assessment." : action.kind === "DECLINE" ? "Decline this Pending request. The record stays in the student's booking history." : "Cancel this confirmed session. The record stays in the student's booking history."}</p>}
        {["DECLINE", "CANCEL"].includes(action.kind) && action.booking.repeatType === 0 && <fieldset disabled={busy}><legend>Availability after this decision</legend>
          <label><input type="radio" name="availability" checked={reopen} onChange={() => setReopen(true)} /> Return this session to available slots</label>
          <label><input type="radio" name="availability" checked={!reopen} onChange={() => setReopen(false)} /> Keep this session closed</label>
        </fieldset>}
        {["DECLINE", "CANCEL"].includes(action.kind) && action.booking.repeatType !== 0 && <p>Only this occurrence is affected. Other dates and the publication's enabled setting are preserved.</p>}
        <label className="supervisor-session-check"><input type="checkbox" checked={checked} disabled={busy} onChange={e => setChecked(e.target.checked)} /> {action.kind === "COMPLETE" ? "I confirm the assessment took place and have checked this result." : "I checked the student, session and decision."}</label>
        {actionError && <p role="alert" className="supervisor-session-error">{actionError}</p>}
        <div className="upcoming-session-modal-actions"><button type="button" className="upcoming-session-secondary-button" disabled={busy} onClick={close}>Back</button><button type="submit" className="upcoming-session-success-button" disabled={busy || !checked}>{busy ? "Saving…" : `Confirm ${action.kind.toLowerCase()}`}</button></div>
      </form>}
    </BaseModal>
  </div></main>;
}
