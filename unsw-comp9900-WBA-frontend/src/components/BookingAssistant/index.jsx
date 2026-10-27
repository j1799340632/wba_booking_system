import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { Sparkles, ArrowUp, RotateCcw, CalendarDays, MapPin } from "lucide-react";
import { searchBookingAssistant, prepareAssistantBooking, confirmAssistantBooking, prepareBookingManagement, confirmBookingManagement, findBookingReplacements } from "@/config/request/api/bookingAssistantApi.js";
import BaseModal from "@/components/BaseModal/index.jsx";
import { readAssistantResult, assistantConditionLabels, bookingStatusLabel, alternativeChangeLabels, removeBookedCandidate } from "@/utils/bookingAssistant.js";
import { formatBookingDate, formatBookingTime } from "@/utils/bookingSearch.js";
import "./index.css";

const EXAMPLES = ["帮我找下周二下午的 Mini-CEX", "查看我的预约", "取消我的预约", "帮我做评估规划"];

function SessionDetails({ session, timeZone }) {
  return <dl>
    <dt>Supervisor</dt><dd>{session.supervisorName}</dd>
    <dt>Session</dt><dd>{session.type || session.wbaType} {session.status != null && `· ${bookingStatusLabel(session.status)}`}</dd>
    <dt>Date and time</dt><dd>{formatBookingDate(session.startTime)} · {formatBookingTime(session.startTime)}–{formatBookingTime(session.endTime)} ({timeZone})</dd>
    <dt>Location</dt><dd>{[session.hospital, session.department, session.location].filter(Boolean).join(" · ")}</dd>
    {session.bookingId && <><dt>Reference</dt><dd>{session.bookingId}</dd></>}
  </dl>;
}

export default function BookingAssistant({ onBooked }) {
  const [message, setMessage] = useState("");
  const [history, setHistory] = useState([]);
  const [turns, setTurns] = useState([]);
  const [result, setResult] = useState(null);
  const [busy, setBusy] = useState(false);
  const [reviewing, setReviewing] = useState(false);
  const [draft, setDraft] = useState(null);
  const [checked, setChecked] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [bookingError, setBookingError] = useState("");
  const [saved, setSaved] = useState(null);
  const [original, setOriginal] = useState(null);
  const [bookingList, setBookingList] = useState(null);
  const submissionLock = useRef(false);
  const active = useRef(0);
  const controller = useRef(null);
  const input = useRef(null);
  const conversation = useRef(null);

  useEffect(() => {
    const pane = conversation.current;
    if (pane) pane.scrollTop = pane.scrollHeight;
  }, [turns]);

  useEffect(() => () => {
    active.current += 1;
    controller.current?.abort();
  }, []);

  const reset = () => {
    if (reviewing || submissionLock.current) return;
    active.current += 1;
    controller.current?.abort();
    setBusy(false);
    setMessage("");
    setHistory([]);
    setTurns([]);
    setResult(null);
    setDraft(null);
    setBookingError("");
    setSaved(null);
    setOriginal(null);
    setBookingList(null);
    input.current?.focus();
  };

  const submit = async (event) => {
    event.preventDefault();
    const text = message.trim();
    if (!text || busy || history.length >= 6) return;
    const requestId = ++active.current;
    const abort = new AbortController();
    controller.current = abort;
    setBusy(true);
    setResult(null);
    setSaved(null);
    setBookingError("");
    setMessage("");
    setTurns(previous => [...previous, { role: "user", text }].slice(-12));
    try {
      const response = await searchBookingAssistant(text, history, abort.signal, original?.bookingId);
      if (requestId !== active.current) return;
      const next = readAssistantResult(response);
      setResult(next);
      if (next.status === "BOOKINGS") setBookingList(next);
      setTurns(previous => [...previous, { role: "assistant", text: next.message }].slice(-12));
      if (["READY", "BOOKINGS", "PLAN", "CLARIFY", "NO_RESULTS"].includes(next.status)) {
        setHistory(previous => [...previous, text]);
      } else {
        setMessage(text);
      }
    } catch (error) {
      if (requestId !== active.current || abort.signal.aborted) return;
      setResult({ status: "UNAVAILABLE", candidates: [], message: error.message || "AI search is unavailable. Please use the filters below." });
      setMessage(text);
    } finally {
      if (requestId === active.current) setBusy(false);
    }
  };

  const reviewSession = async (candidate, alternative) => {
    if (original) return reviewChange(original, "RESCHEDULE", candidate, alternative);
    if (reviewing || submissionLock.current) return;
    setReviewing(true);
    setBookingError("");
    setChecked(false);
    try {
      const response = await prepareAssistantBooking({ assessmentSlotId: candidate.slotId,
        slotVersion: candidate.slotVersion, startTime: candidate.startTime, endTime: candidate.endTime });
      if (!response?.data?.draftId || !response.data.session) throw new Error("The session could not be reviewed. Search again.");
      setDraft({ ...response.data, alternative: alternative ? { title: alternative.title, changes: alternativeChangeLabels(result.conditions, alternative) } : null });
    } catch (error) {
      setBookingError(error.message || "The session changed. Search again.");
    } finally { setReviewing(false); }
  };

  const reviewChange = async (booking, action, candidate, alternative) => {
    if (reviewing || submissionLock.current) return;
    setReviewing(true); setBookingError(""); setChecked(false);
    try {
      const response = await prepareBookingManagement({ bookingId: booking.bookingId, bookingVersion: booking.version, action,
        ...(candidate ? { replacement: { assessmentSlotId: candidate.slotId, slotVersion: candidate.slotVersion, startTime: candidate.startTime, endTime: candidate.endTime } } : {}) });
      if (!response?.data?.draftId || !response.data.original) throw new Error("The booking could not be reviewed. Refresh your bookings.");
      setDraft({ ...response.data, management: true, alternative: alternative ? { title: alternative.title, changes: alternativeChangeLabels(result.conditions, alternative) } : null });
    } catch (error) { setBookingError(error.message || "Booking details changed. Refresh your bookings."); }
    finally { setReviewing(false); }
  };

  const startReschedule = async booking => {
    if (busy || reviewing || submissionLock.current) return;
    setOriginal(booking); setHistory([]); setMessage(""); setBookingError(""); setSaved(null); setResult(null);
    setTurns([{ role: "assistant", text: "Describe your replacement session below. Your original booking remains active until you confirm a successful reschedule." }]);
    if (bookingList?.replacementConditions) {
      setBusy(true);
      try { setResult(readAssistantResult(await findBookingReplacements(booking.bookingId, bookingList.replacementConditions))); }
      catch (error) { setBookingError(error.message || "Replacement search failed. Describe another session below."); }
      finally { setBusy(false); }
    }
    input.current?.focus();
  };

  const confirm = async () => {
    if (!draft || !checked || submissionLock.current) return;
    submissionLock.current = true;
    setSubmitting(true);
    setBookingError("");
    try {
      const response = await (draft.management ? confirmBookingManagement(draft.draftId) : confirmAssistantBooking(draft.draftId));
      if (draft.management ? !response?.data?.original?.bookingId : !response?.data?.bookingId) throw new Error("No booking receipt was received. Retry this confirmation or check My Bookings.");
      const booking = response.data;
      setSaved({ ...booking, management: draft.management });
      if (draft.management) { setOriginal(null); setResult(null); setBookingList(null); setHistory([]); }
      else setResult(previous => previous ? removeBookedCandidate(previous, draft.session) : previous);
      setDraft(null);
      // A list refresh cannot change the outcome of a saved booking request.
      try { await onBooked?.(booking); } catch { /* Receipt remains visible; the user can refresh the list. */ }
    } catch (error) {
      setBookingError(`${error.message || "No confirmation response was received."} If the connection was interrupted, retry this same confirmation or check My Bookings.`);
    } finally {
      submissionLock.current = false;
      setSubmitting(false);
    }
  };

  const labels = assistantConditionLabels(result);
  const unavailable = result?.status === "UNAVAILABLE";

  return (
    <section className="booking-assistant" aria-label="AI booking assistant" aria-busy={busy}>
      <div className="booking-assistant-heading">
        <div className="booking-assistant-title"><Sparkles size={19} /><h2>AI booking assistant</h2><span>Review before changes</span></div>
        <button type="button" className="booking-assistant-reset" onClick={reset} disabled={reviewing || submitting}><RotateCcw size={14} /> New search</button>
      </div>
      <p className="booking-assistant-intro">Find sessions, manage bookings, or plan your remaining assessments.</p>
      {original && <div className="booking-assistant-original" aria-label="Original booking">
        <strong>Rescheduling booking {original.bookingId}</strong>
        <p>{original.supervisorName} · {original.wbaType} · {formatBookingDate(original.startTime)} {formatBookingTime(original.startTime)}</p>
        <button type="button" disabled={busy || reviewing || submitting} onClick={() => { setOriginal(null); setResult(bookingList); setHistory([]); setMessage(""); setTurns([]); setBookingError(""); }}>Back to bookings</button>
      </div>}

      {turns.length > 0 && (
        <div ref={conversation} className="booking-assistant-conversation" aria-label="Search conversation" aria-live="polite">
          {turns.map((turn, index) => <p key={index} className={`booking-assistant-turn ${turn.role}`}><strong>{turn.role === "user" ? "You" : "Assistant"}</strong>{turn.text}</p>)}
        </div>
      )}

      <form onSubmit={submit}>
        <label className="booking-assistant-input-label" htmlFor="booking-assistant-message">
          {result?.status === "CLARIFY" ? "Add a detail to continue" : original ? "What replacement session would you like?" : "How can I help with your bookings?"}
        </label>
        <div className="booking-assistant-compose">
          <textarea id="booking-assistant-message" ref={input} value={message} maxLength={1500} rows={2}
            placeholder="For example: Mini-CEX next Tuesday afternoon, preferably with Dr Wang"
            disabled={busy || reviewing || submitting || history.length >= 6} onChange={event => setMessage(event.target.value)}
            aria-describedby="booking-assistant-scope" />
          <button type="submit" disabled={busy || reviewing || submitting || !message.trim() || history.length >= 6}>
            <ArrowUp size={17} /> {busy ? "Searching…" : original ? "Find replacements" : "Send request"}
          </button>
        </div>
      </form>
      {turns.length === 0 && <div className="booking-assistant-examples">{EXAMPLES.map(example => (
        <button key={example} type="button" disabled={busy} onClick={() => { setMessage(example); input.current?.focus(); }}>{example}</button>
      ))}</div>}
      <p id="booking-assistant-scope" className="booking-assistant-hint">
        Australia/Sydney time. Changes require your confirmation. Confirmed bookings cannot be changed within 48 hours of the session.
      </p>

      {busy && <p role="status" className="booking-assistant-feedback">Searching available sessions…</p>}
      {history.length >= 6 && <p role="status" className="booking-assistant-feedback">Start a new search to continue with a fresh set of conditions.</p>}
      {unavailable && <p role="alert" className="booking-assistant-error">{result.message}</p>}
      {bookingError && !draft && <p role="alert" className="booking-assistant-error">{bookingError}</p>}
      {saved?.management && <div role="status" className="booking-assistant-receipt">
        <strong>{saved.action === "RESCHEDULE" ? "Reschedule saved — replacement awaits supervisor approval" : "Booking cancelled"}</strong>
        <p>Original booking {saved.original.bookingId}: {bookingStatusLabel(saved.original.status)}.</p>
        {saved.newBooking && <p>Replacement {saved.newBooking.bookingId}: {bookingStatusLabel(saved.newBooking.status)} · {formatBookingDate(saved.newBooking.startTime)} {formatBookingTime(saved.newBooking.startTime)}.</p>}
        <p>{saved.notificationStatus === "LIVE_DELAYED" ? "In-app notifications saved; live alerts are delayed." : "In-app notifications saved."} View the current status in My Bookings.</p>
      </div>}
      {saved && !saved.management && <div role="status" className="booking-assistant-receipt">
        <strong>{saved.status === 0 ? "Request saved — awaiting supervisor approval" : "Booking request already recorded"}</strong>
        <p>Reference: {saved.bookingId}. View its current status in My Bookings.</p>
        <p>{saved.notificationStatus === "LIVE_DELAYED" ? "Your in-app notifications are saved. Live alerts are delayed." : "In-app notifications saved."}</p>
      </div>}
      {labels.length > 0 && <ul className="booking-assistant-conditions" aria-label="Understood search conditions">{labels.map(label => <li key={label}>{label}</li>)}</ul>}
      {result?.status === "NO_RESULTS" && <p role="status" className="booking-assistant-feedback"><strong>No exact matches.</strong> {result.alternatives?.length ? "The options below require a change to your original conditions." : result.message}</p>}
      {result?.alternatives?.length > 0 && <section className="booking-assistant-alternatives" aria-label="Alternative sessions">
        <h3>Alternatives — one condition changes</h3>
        <p className="booking-assistant-hint">Assessment type, hospital and department filters stay the same. These are examples, not an exhaustive availability check.</p>
        {result.alternatives.map(alternative => <section key={alternative.kind} className="booking-assistant-alternative-group" aria-label={alternative.title}>
          <h4>{alternative.title}</h4><p>{alternative.explanation}</p>
          <ul className="booking-assistant-changes">{alternativeChangeLabels(result.conditions, alternative).map(change => <li key={change}>{change}</li>)}</ul>
          <ol className="booking-assistant-candidates">{alternative.candidates.map(candidate => <li key={`${candidate.slotId}/${candidate.startTime}`}>
            <div className="booking-assistant-candidate-title"><strong>{candidate.supervisorName || "Supervisor"}</strong>{candidate.preferredSupervisor && <span>Preferred supervisor</span>}</div>
            <p><CalendarDays size={14} /> {formatBookingDate(candidate.startTime)} · {formatBookingTime(candidate.startTime)}–{formatBookingTime(candidate.endTime)}</p>
            <p><MapPin size={14} /> {[candidate.hospital, candidate.department, candidate.location].filter(Boolean).join(" · ")}</p>
            {candidate.type && <span className="booking-assistant-type">{candidate.type}</span>}
            <button type="button" className="booking-assistant-review-button" disabled={busy || reviewing || submitting} onClick={() => reviewSession(candidate, alternative)}>{original ? "Review alternative reschedule" : "Review alternative"}</button>
          </li>)}</ol>
        </section>)}
      </section>}
      {result?.status === "PLAN" && <div className="booking-assistant-original" aria-label="Assessment plan summary">
        <strong>Your assessment plan</strong>
        {result.assessmentPlan?.configured ? <p>{result.assessmentPlan.completed}/{result.assessmentPlan.totalTarget} complete · {result.assessmentPlan.remaining} still to complete · {result.assessmentPlan.toRequest} additional request(s) needed. Deadline: {result.assessmentPlan.deadline}.</p> : <p>Set your own targets and placement dates to calculate your gaps and weekly pacing.</p>}
        <Link to="/home/student/assessment-plan">Open assessment plan</Link>
      </div>}

      {result?.status === "BOOKINGS" && <>
        <p className="booking-assistant-results-title">Your bookings <small>{result.timeZone}</small></p>
        {result.bookings.length === 0 && <p role="status" className="booking-assistant-feedback">{result.message}</p>}
        <ol className="booking-assistant-candidates" aria-label="Your bookings">
          {result.bookings.map(booking => <li key={booking.bookingId}>
            <div className="booking-assistant-candidate-title"><strong>{booking.supervisorName}</strong><span>{bookingStatusLabel(booking.status)}</span></div>
            <p>{booking.wbaType} · {formatBookingDate(booking.startTime)} · {formatBookingTime(booking.startTime)}–{formatBookingTime(booking.endTime)}</p>
            <p>{[booking.hospital, booking.department, booking.location].filter(Boolean).join(" · ")}</p>
            <p className="booking-assistant-hint">Reference: {booking.bookingId}</p>
            {booking.canManage ? <div className="booking-assistant-management-actions">
              <button type="button" disabled={busy || reviewing || submitting} onClick={() => startReschedule(booking)}>Reschedule</button>
              <button type="button" disabled={busy || reviewing || submitting} onClick={() => reviewChange(booking, "CANCEL")}>Review cancellation</button>
            </div> : <p className="booking-assistant-hint">{booking.managementRestriction}</p>}
          </li>)}
        </ol>
        {result.hasMoreBookings && <p className="booking-assistant-hint">Showing the first 20 matches. Add a date or supervisor to narrow the list.</p>}
      </>}

      {result?.candidates?.length > 0 && (
        <>
          <p className="booking-assistant-results-title">Available sessions <small>{result.timeZone}</small></p>
          <ol className="booking-assistant-candidates">
            {result.candidates.map(candidate => (
              <li key={`${candidate.slotId}/${candidate.startTime}`}>
                <div className="booking-assistant-candidate-title"><strong>{candidate.supervisorName || "Supervisor"}</strong>
                  {candidate.preferredSupervisor && <span>Preferred supervisor</span>}
                </div>
                <p><CalendarDays size={14} /> {formatBookingDate(candidate.startTime)} · {formatBookingTime(candidate.startTime)}–{formatBookingTime(candidate.endTime)}</p>
                <p><MapPin size={14} /> {[candidate.hospital, candidate.department, candidate.location].filter(Boolean).join(" · ")}</p>
                {candidate.type && <span className="booking-assistant-type">{candidate.type}</span>}
                <button type="button" className="booking-assistant-review-button" disabled={busy || reviewing || submitting}
                  onClick={() => reviewSession(candidate)}>{original ? "Review reschedule" : "Review session"}</button>
              </li>
            ))}
          </ol>
          <p className="booking-assistant-hint">Availability is checked again when you confirm. Requests await supervisor approval.</p>
        </>
      )}
      <BaseModal isOpen={Boolean(draft)} onClose={() => { if (!submissionLock.current) { setDraft(null); setBookingError(""); } }} title={draft?.management ? "Review booking changes" : "Review booking request"}>
        {draft && <div role="dialog" aria-modal="true" aria-label={draft.management ? "Review booking changes" : "Review booking request"} className="booking-assistant-review">
          {draft.alternative && <div className="booking-assistant-alternative-warning"><strong>This option changes your original search conditions.</strong><p>{draft.alternative.title}</p><ul>{draft.alternative.changes.map(change => <li key={change}>{change}</li>)}</ul></div>}
          {draft.management ? <>
            <h3>{draft.action === "RESCHEDULE" ? "Cancel original and request replacement" : "Cancel this booking"}</h3>
            <SessionDetails session={draft.original} timeZone={draft.timeZone} />
            {draft.replacement && <><h3>Replacement — awaiting supervisor approval after confirmation</h3><SessionDetails session={draft.replacement} timeZone={draft.timeZone} /></>}
            <p>{draft.replacement ? "If the replacement cannot be booked, your original booking stays active." : "The cancelled booking will remain in your booking history."}</p>
          </> : <><p>{draft.message}</p><SessionDetails session={draft.session} timeZone={draft.timeZone} /></>}
          <p className="booking-assistant-hint">Review valid until {formatBookingTime(draft.expiresAt)} ({draft.timeZone}).</p>
          <label className="booking-assistant-check"><input type="checkbox" checked={checked} disabled={submitting} onChange={event => setChecked(event.target.checked)} /> {draft.alternative ? "I accept the changed search conditions, checked the session details, and want to confirm this request." : draft.management ? "I reviewed these details and want to confirm this change." : "I checked the session details and want to request this booking."}</label>
          {bookingError && <p role="alert" className="booking-assistant-error">{bookingError}</p>}
          <div className="booking-assistant-review-actions">
            <button type="button" disabled={submitting} onClick={() => { setDraft(null); setBookingError(""); }}>Back</button>
            <button type="button" disabled={!checked || submitting} onClick={confirm}>{submitting ? "Submitting…" : draft.action === "CANCEL" ? "Confirm cancellation" : draft.management ? "Confirm reschedule" : "Confirm booking request"}</button>
          </div>
        </div>}
      </BaseModal>
    </section>
  );
}
