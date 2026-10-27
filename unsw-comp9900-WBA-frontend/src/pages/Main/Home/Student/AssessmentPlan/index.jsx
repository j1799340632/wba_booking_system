import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { CalendarDays, CheckCircle2, Target, Plus, RefreshCw, Trash2 } from "lucide-react";
import { getAssessmentPlan, saveAssessmentPlan } from "@/config/request/api/assessmentPlanApi.js";
import { prepareAssistantBooking, confirmAssistantBooking } from "@/config/request/api/bookingAssistantApi.js";
import { planFormValue, planSavePayload, planStatusLabel } from "@/utils/assessmentPlan.js";
import { formatBookingDate, formatBookingTime } from "@/utils/bookingSearch.js";
import BaseModal from "@/components/BaseModal/index.jsx";
import AssessmentFollowUps from "./FollowUps.jsx";
import "./index.css";

export default function AssessmentPlanPage() {
  const [plan, setPlan] = useState(null);
  const [form, setForm] = useState(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [reviewing, setReviewing] = useState(false);
  const [draft, setDraft] = useState(null);
  const [checked, setChecked] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [bookingError, setBookingError] = useState("");
  const [receipt, setReceipt] = useState(null);
  const [expandedWeeks, setExpandedWeeks] = useState(false);
  const writeLock = useRef(false);
  const reviewLock = useRef(false);
  const saveLock = useRef(false);
  const loadId = useRef(0);

  const load = useCallback((signal) => {
    const id = ++loadId.current;
    return getAssessmentPlan(signal).then(response => {
      if (id !== loadId.current || signal?.aborted) return;
      if (!response?.data?.today || !Array.isArray(response.data.availableTypes)) throw new Error("The assessment plan could not be loaded.");
      setPlan(response.data); setForm(planFormValue(response.data)); setEditing(!response.data.configured);
    }).catch(failure => {
      if (id === loadId.current && !signal?.aborted) setError(failure.message || "The assessment plan could not be loaded. Retry without changing your targets.");
    }).finally(() => { if (id === loadId.current) setLoading(false); });
  }, []);
  const refresh = () => { setLoading(true); setError(""); return load(); };

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => { loadId.current += 1; controller.abort(); };
  }, [load]);

  const updateGoal = (index, key, value) => setForm(previous => ({ ...previous, goals: previous.goals.map((g, i) => i === index ? { ...g, [key]: value } : g) }));
  const save = async event => {
    event.preventDefault();
    if (saveLock.current) return;
    let payload;
    try { payload = planSavePayload(form); } catch (failure) { setError(failure.message); return; }
    saveLock.current = true; setSaving(true); setError(""); setNotice("");
    try {
      await saveAssessmentPlan(payload);
      setNotice("Targets saved. Your progress is calculated from your assessment records.");
      await refresh(); // A refresh failure leaves the save notice and a separate refresh error visible.
    } catch (failure) { setError(failure.message || "No save response was received. Retry the same save or reload to check your targets."); }
    finally { saveLock.current = false; setSaving(false); }
  };

  const review = async candidate => {
    if (reviewLock.current || writeLock.current) return;
    reviewLock.current = true; setReviewing(true); setBookingError(""); setChecked(false);
    try {
      const response = await prepareAssistantBooking({ assessmentSlotId: candidate.slotId, slotVersion: candidate.slotVersion, startTime: candidate.startTime, endTime: candidate.endTime });
      if (!response?.data?.draftId || !response.data.session) throw new Error("This session could not be reviewed. Refresh the plan.");
      setDraft(response.data);
    } catch (failure) { setBookingError(failure.message || "This session is no longer available. Refresh for new options."); }
    finally { reviewLock.current = false; setReviewing(false); }
  };
  const confirm = async () => {
    if (!draft || !checked || writeLock.current) return;
    writeLock.current = true; setSubmitting(true); setBookingError("");
    try {
      const response = await confirmAssistantBooking(draft.draftId);
      if (!response?.data?.bookingId) throw new Error("No booking receipt was received. Retry this confirmation or check My Bookings.");
      setReceipt(response.data); setDraft(null); setNotice("");
      await refresh();
    } catch (failure) { setBookingError(`${failure.message || "The confirmation response was not received."} Retry this same confirmation or check My Bookings.`); }
    finally { writeLock.current = false; setSubmitting(false); }
  };
  const close = () => { if (!writeLock.current) { setDraft(null); setBookingError(""); } };

  return <main className="assessment-plan-page">
    <div className="assessment-plan-header" id="assessment-plan-targets">
      <div><p className="assessment-plan-eyebrow"><Target size={16} /> Your placement, your targets</p><h1>Assessment Plan</h1><p>Track what is complete, what is booked, and what still needs a session.</p></div>
      <div className="assessment-plan-actions">
        <button type="button" onClick={refresh} disabled={loading || saving || editing || reviewing || submitting}><RefreshCw size={15} /> Refresh</button>
        {plan?.configured && !editing && <button type="button" onClick={() => { setForm(planFormValue(plan)); setEditing(true); setNotice(""); }} disabled={loading || reviewing || submitting}>Edit targets</button>}
      </div>
    </div>
    {error && <p className="assessment-plan-error" role="alert">{error}</p>}
    {notice && <p className="assessment-plan-notice" role="status">{notice}</p>}
    {receipt && <div className="assessment-plan-notice" role="status"><strong>Booking request saved — awaiting supervisor approval</strong><p>Reference: {receipt.bookingId}. This request does not count as a completed assessment. <Link to="/home/student/my-bookings">View My Bookings</Link>.</p><p>{receipt.notificationStatus === "LIVE_DELAYED" ? "In-app notifications saved; live alerts are delayed." : "In-app notifications saved."}</p></div>}
    {loading && <p role="status">Loading your plan…</p>}
    {!loading && !plan && <button type="button" onClick={refresh}>Retry loading plan</button>}
    {plan && <>
      {editing && form && <section className="assessment-plan-panel" aria-label="Set assessment targets">
        <h2>{plan.configured ? "Edit your targets" : "Set your assessment targets"}</h2>
        <p>Enter the requirements agreed with your course or supervisor. These are personal targets; no course quota is assumed.</p>
        <form onSubmit={save}>
          <div className="assessment-plan-period">
            <label>Placement start<input type="date" required value={form.startDate} disabled={saving} onChange={e => setForm({ ...form, startDate: e.target.value })} /></label>
            <label>Deadline, inclusive<input type="date" required value={form.deadline} disabled={saving} onChange={e => setForm({ ...form, deadline: e.target.value })} /></label>
          </div>
          {form.goals.map((goal, index) => <div className="assessment-plan-goal-input" key={index}>
            <label>Assessment type {index + 1}<select aria-label={`Assessment type ${index + 1}`} required value={goal.typeId} disabled={saving} onChange={e => updateGoal(index, "typeId", e.target.value)}>
              <option value="">Choose an assessment</option>
              {goal.typeId && !plan.availableTypes.some(t => t.id === goal.typeId) && <option value={goal.typeId}>Unavailable type — choose another</option>}
              {plan.availableTypes.map(type => <option key={type.id} value={type.id}>{type.name}</option>)}
            </select></label>
            <label>Target count {index + 1}<input type="number" min="1" max="100" step="1" required placeholder="e.g. 3" value={goal.target} disabled={saving} onChange={e => updateGoal(index, "target", e.target.value)} /></label>
            <button type="button" aria-label={`Remove target ${index + 1}`} disabled={saving || form.goals.length === 1} onClick={() => setForm({ ...form, goals: form.goals.filter((_, i) => i !== index) })}><Trash2 size={16} /></button>
          </div>)}
          <div className="assessment-plan-actions">
            <button type="button" disabled={saving || form.goals.length >= Math.min(20, plan.availableTypes.length)} onClick={() => setForm({ ...form, goals: [...form.goals, { typeId: "", target: "" }] })}><Plus size={15} /> Add assessment type</button>
            {plan.configured && <button type="button" disabled={saving} onClick={() => { setEditing(false); setError(""); }}>Cancel edits</button>}
            <button type="submit" className="assessment-plan-primary" disabled={saving || !plan.availableTypes.length}>{saving ? "Saving…" : "Save targets"}</button>
          </div>
        </form>
      </section>}
      {plan.configured && <>
        <section className="assessment-plan-summary" aria-label="Plan summary">
          <div><span className="assessment-plan-status">{planStatusLabel(plan.status)}</span><p>{plan.startDate} → {plan.deadline} · {plan.timeZone}</p></div>
          <div className="assessment-plan-stats">
            <div><CheckCircle2 size={18} /><strong>{plan.completed}/{plan.totalTarget}</strong><span>Completed toward targets</span></div>
            <div><CalendarDays size={18} /><strong>{plan.confirmed} / {plan.pending}</strong><span>Confirmed / Pending</span></div>
            <div><Target size={18} /><strong>{plan.toRequest}</strong><span>Additional sessions to request</span></div>
          </div>
          {plan.inProgress > 0 && <p className="assessment-plan-hint">{plan.inProgress} confirmed session(s) are in progress. These provide provisional coverage and do not count as completed.</p>}
          <p className="assessment-plan-hint">{plan.guidance}</p>
          {plan.uncountedRecords > 0 && <p className="assessment-plan-warning">{plan.uncountedRecords} historical record(s) lack a session or completion date and could not be assigned reliably to this period.</p>}
        </section>
        <AssessmentFollowUps plan={plan} />
        <section className="assessment-plan-panel" aria-label="Progress by assessment type">
          <h2>Your assessment gaps</h2>
          <div className="assessment-plan-progress-grid">{plan.goals.map(goal => <article key={goal.typeId} className="assessment-plan-goal-card">
            <div className="assessment-plan-card-heading"><h3>{goal.type}</h3><strong>{goal.completed}/{goal.target} complete</strong></div>
            <progress aria-label={`${goal.type} completed progress`} value={Math.min(goal.completed, goal.target)} max={goal.target} />
            <dl><div><dt>Still to complete</dt><dd>{goal.remaining}</dd></div><div><dt>Confirmed</dt><dd>{goal.confirmed}</dd></div><div><dt>Pending approval</dt><dd>{goal.pending}</dd></div>{goal.inProgress > 0 && <div><dt>In progress</dt><dd>{goal.inProgress}</dd></div>}<div><dt>Additional requests</dt><dd>{goal.toRequest}</dd></div></dl>
            {goal.suggestedPerWeek > 0 && <p className="assessment-plan-hint">Aim to complete about {goal.suggestedPerWeek} per week across {plan.weeksRemaining} remaining calendar week(s).</p>}
            {goal.missed > 0 && <p className="assessment-plan-warning">{goal.missed} ended session(s) are still uncompleted. Check their status with your supervisor.</p>}
            <p className="assessment-plan-hint">{goal.recommendationMessage}</p>
          </article>)}</div>
        </section>
        {plan.weeks.length > 0 && <section className="assessment-plan-panel" aria-label="Weekly pacing">
          <h2>Your weekly pacing</h2><p className="assessment-plan-hint">Additional requests are spread evenly over the remaining weeks. Existing bookings stay on their actual dates. These counts do not reserve sessions.</p>
          <div className="assessment-plan-week-grid">{(expandedWeeks ? plan.weeks : plan.weeks.slice(0, 4)).map(week => <article key={week.startDate} className="assessment-plan-week"><h3>{week.startDate} → {week.endDate}</h3>{week.goals.map(goal => <p key={goal.type}><strong>{goal.type}</strong><span>Request {goal.toRequest} · Confirmed {goal.confirmed} · Pending {goal.pending}</span></p>)}</article>)}</div>
          {plan.weeks.length > 4 && <button type="button" onClick={() => setExpandedWeeks(!expandedWeeks)}>{expandedWeeks ? "Show next 4 weeks" : `Show all ${plan.weeks.length} weeks`}</button>}
        </section>}
        <section className="assessment-plan-panel" id="assessment-plan-sessions" aria-label="Recommended assessment sessions">
          <h2>Sessions for your gaps</h2><p className="assessment-plan-hint">Search covers the next 30 days within your placement period and deadline. Options for different targets may overlap; availability is checked again before confirmation.</p>
          {bookingError && !draft && <p className="assessment-plan-error" role="alert">{bookingError}</p>}
          <div className="assessment-plan-session-grid">{plan.goals.flatMap(goal => goal.candidates.map(candidate => <article className="assessment-plan-session" key={`${goal.typeId}/${candidate.slotId}/${candidate.startTime}`}>
            <span className="assessment-plan-type">{goal.type} · {goal.toRequest} additional request(s) needed</span>
            <h3>{candidate.supervisorName}</h3><p>{formatBookingDate(candidate.startTime)} · {formatBookingTime(candidate.startTime)}–{formatBookingTime(candidate.endTime)}</p>
            <p>{[candidate.hospital, candidate.department, candidate.location].filter(Boolean).join(" · ")}</p>
            <button type="button" disabled={editing || loading || saving || reviewing || submitting} onClick={() => review(candidate)}>Review session</button>
          </article>))}</div>
          {plan.toRequest > 0 && !plan.goals.some(goal => goal.candidates.length > 0) && <p>No bookable options were found in this window. <Link to="/home/student/bookSlot">Open Book Slots</Link> to try another date or supervisor.</p>}
          {plan.toRequest === 0 && <p>Your saved targets currently need no additional booking requests. Continue checking approvals and completing booked sessions.</p>}
        </section>
      </>}
    </>}
    <BaseModal isOpen={Boolean(draft)} onClose={close} title="Review assessment booking">
      {draft && <div role="dialog" aria-modal="true" aria-label="Review assessment booking" className="assessment-plan-review">
        <p>Requesting a session does not complete an assessment. This request will await supervisor approval.</p>
        <dl><dt>Assessment</dt><dd>{draft.session.type}</dd><dt>Supervisor</dt><dd>{draft.session.supervisorName}</dd><dt>Date and time</dt><dd>{formatBookingDate(draft.session.startTime)} · {formatBookingTime(draft.session.startTime)}–{formatBookingTime(draft.session.endTime)} ({draft.timeZone})</dd><dt>Location</dt><dd>{[draft.session.hospital, draft.session.department, draft.session.location].filter(Boolean).join(" · ")}</dd></dl>
        <p>Review valid until {formatBookingTime(draft.expiresAt)} ({draft.timeZone}).</p>
        <label className="assessment-plan-check"><input type="checkbox" checked={checked} disabled={submitting} onChange={e => setChecked(e.target.checked)} /> I checked these details and want to request this assessment session.</label>
        {bookingError && <p className="assessment-plan-error" role="alert">{bookingError}</p>}
        <div className="assessment-plan-actions"><button type="button" disabled={submitting} onClick={close}>Back</button><button type="button" className="assessment-plan-primary" disabled={!checked || submitting} onClick={confirm}>{submitting ? "Submitting…" : "Confirm booking request"}</button></div>
      </div>}
    </BaseModal>
  </main>;
}
