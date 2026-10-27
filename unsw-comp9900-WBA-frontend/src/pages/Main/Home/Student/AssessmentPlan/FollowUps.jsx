import { useState } from "react";
import { Link } from "react-router-dom";
import { followUpBookingLink, followUpPresentation, planDeadlineLabel } from "@/utils/assessmentPlan.js";
import { formatBookingDate, formatBookingTime } from "@/utils/bookingSearch.js";

export default function AssessmentFollowUps({ plan }) {
  const [expanded, setExpanded] = useState(false);
  const counts = plan.followUpCounts || {};
  const items = plan.followUps || [];
  const urgentDeadline = plan.remaining > 0 && Number.isInteger(plan.daysToDeadline) && plan.daysToDeadline <= 7;
  return <section className="assessment-plan-panel" aria-label="Assessment follow-ups">
    <div className="assessment-plan-card-heading"><h2>Your next actions</h2><Link to="/home/student/my-bookings">All my bookings</Link></div>
    <div className={`assessment-plan-deadline${urgentDeadline ? " is-urgent" : ""}`}>
      <strong>{planDeadlineLabel(plan)}</strong>
      <p>{plan.remaining} assessment(s) still to complete · {plan.toRequest} additional session request(s) needed.</p>
      {urgentDeadline && <p>Review the remaining gap and pending approvals with your supervisor. The deadline includes all of {plan.deadline} in {plan.timeZone}.</p>}
      {plan.toRequest > 0 && <a href={plan.daysToDeadline < 0 ? "#assessment-plan-targets" : "#assessment-plan-sessions"}>{plan.daysToDeadline < 0 ? "Review plan dates and targets" : "Find sessions for your gaps"}</a>}
    </div>
    <dl className="assessment-plan-follow-up-counts">
      <div><dt>Status to check</dt><dd>{counts.needsAttention || 0}</dd></div>
      <div><dt>Awaiting approval</dt><dd>{counts.awaitingApproval || 0}</dd></div>
      <div><dt>In progress</dt><dd>{counts.inProgress || 0}</dd></div>
      <div><dt>Confirmed in next 48h</dt><dd>{counts.upcoming || 0}</dd></div>
    </dl>
    <p className="assessment-plan-hint">Only bookings for your saved assessment types and placement period appear here. Status checks come first, followed by ongoing and upcoming sessions. {counts.scheduled > 0 && `${counts.scheduled} confirmed session(s) are scheduled later. `}As of {formatBookingDate(plan.generatedAt)} · {formatBookingTime(plan.generatedAt)} ({plan.timeZone}). Refresh the plan to check for changes.</p>
    <div className="assessment-plan-follow-up-list">{(expanded ? items : items.slice(0, 4)).map(item => {
      const presentation = followUpPresentation(item.kind);
      return <article key={item.bookingId} className={`assessment-plan-follow-up${presentation.attention ? " needs-attention" : ""}`} aria-label={`${presentation.label}: ${item.bookingId}`}>
        <div><span className="assessment-plan-type">{presentation.label}</span><h3>{item.type} · {item.supervisorName}</h3>
          <p>{formatBookingDate(item.startTime)} · {formatBookingTime(item.startTime)}–{item.startTime?.slice(0, 10) !== item.endTime?.slice(0, 10) && `${formatBookingDate(item.endTime)} · `}{formatBookingTime(item.endTime)} ({plan.timeZone})</p>
          <p>{[item.hospital, item.department, item.location].filter(Boolean).join(" · ")}</p>
          <p className="assessment-plan-hint">{presentation.detail}</p><small>Reference: {item.bookingId}</small>
        </div>
        <Link to={followUpBookingLink(item.bookingId)}>View booking</Link>
      </article>;
    })}</div>
    {items.length === 0 && <p>No pending or confirmed bookings need follow-up in this plan period. {plan.remaining > 0 && "Your assessment gaps are shown below."}</p>}
    {items.length > 4 && <button type="button" onClick={() => setExpanded(!expanded)}>{expanded ? "Show first 4 actions" : `Show all ${items.length} actions`}</button>}
    {plan.hasMoreFollowUps && <p className="assessment-plan-hint">Showing the first {items.length} of {plan.followUpTotal} bookings, ordered by action and date. <Link to="/home/student/my-bookings">View the rest in My Bookings</Link>.</p>}
  </section>;
}
