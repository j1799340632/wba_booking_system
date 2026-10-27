import { useCallback, useEffect, useRef, useState } from "react";
import { getAssistantInsights } from "@/config/request/api/assistantInsightsApi.js";
import { confirmationSaveRate, insightCount, insightOutcomeLabel, insightPreset, insightsRange, insightStageLabel } from "@/utils/assistantInsights.js";
import { formatBookingDate, formatBookingTime } from "@/utils/bookingSearch.js";
import "./index.css";

function ActivityChart({ days }) {
  const width = 920, height = 150, max = Math.max(1, ...days.flatMap(day => [Number(day.searches), Number(day.newBookingRequests)]));
  const step = (width - 40) / Math.max(1, days.length), bar = Math.min(16, step / 3);
  return <svg viewBox={`0 0 ${width} ${height + 35}`} role="img" aria-label="Daily assistant searches and newly saved booking requests" className="assistant-insights-chart">
    <line x1="20" y1={height} x2={width - 20} y2={height} stroke="#cadce5" />
    {days.map((day, index) => <g key={day.date}><title>{day.date}: {day.searches} assistant requests, {day.newBookingRequests} new booking saves</title>
      <rect x={20 + index * step + step / 2 - bar} y={height - Number(day.searches) / max * 130} width={bar} height={Number(day.searches) / max * 130} fill="#0b6282" rx="2" />
      <rect x={20 + index * step + step / 2 + 2} y={height - Number(day.newBookingRequests) / max * 130} width={bar} height={Number(day.newBookingRequests) / max * 130} fill="#bd8126" rx="2" />
      {(days.length <= 7 || index % 5 === 0 || index === days.length - 1) && <text x={20 + index * step + step / 2} y={height + 22} textAnchor="middle" fill="#526c7c" fontSize="12">{day.date.slice(5)}</text>}
    </g>)}
  </svg>;
}

export default function AssistantInsightsPage() {
  const [report, setReport] = useState(null), [loading, setLoading] = useState(true), [error, setError] = useState("");
  const [range, setRange] = useState({ startDate: "", endDate: "" });
  const loadId = useRef(0);
  const load = useCallback((params = {}, signal) => {
    const id = ++loadId.current;
    return getAssistantInsights(params, signal).then(response => {
      if (id !== loadId.current || signal?.aborted) return;
      const data = response?.data;
      if (!data?.generatedAt || !Array.isArray(data.stages) || !Array.isArray(data.days) || !Array.isArray(data.models)) throw new Error("The observations could not be loaded.");
      setReport(data); setRange({ startDate: data.startDate, endDate: data.endDate }); setError("");
    }).catch(failure => { if (id === loadId.current && !signal?.aborted) setError(failure.message || "The observations could not be loaded. Try again."); })
      .finally(() => { if (id === loadId.current) setLoading(false); });
  }, []);
  useEffect(() => { const controller = new AbortController(); void load({}, controller.signal); return () => { loadId.current += 1; controller.abort(); }; }, [load]);
  const today = report?.generatedAt?.slice(0, 10);
  const apply = event => {
    event.preventDefault();
    try { const query = insightsRange(range.startDate, range.endDate, today); setLoading(true); setError(""); void load(query); }
    catch (failure) { setError(failure.message); }
  };
  const preset = days => { const query = insightPreset(today, days); setLoading(true); setError(""); void load(query); };
  const download = () => {
    const url = URL.createObjectURL(new Blob([JSON.stringify(report, null, 2)], { type: "application/json" }));
    const link = document.createElement("a"); link.href = url; link.download = `wba-ai-insights-${report.startDate}-${report.endDate}.json`; link.click(); URL.revokeObjectURL(url);
  };
  const rate = confirmationSaveRate(report);
  return <main className="assistant-insights-page">
    <header><div><p className="assistant-insights-eyebrow">Assistant operations</p><h1>AI Insights</h1><p>Observe requests, saved decisions and model usage across the assisted booking flow.</p></div>
      <button type="button" disabled={!report || loading} onClick={download}>Download summary</button></header>
    <form className="assistant-insights-filters" onSubmit={apply} aria-label="Observation period">
      <label>From<input type="date" required value={range.startDate} max={today} disabled={loading} onChange={event => setRange({ ...range, startDate: event.target.value })} /></label>
      <label>Through, inclusive<input type="date" required value={range.endDate} max={today} disabled={loading} onChange={event => setRange({ ...range, endDate: event.target.value })} /></label>
      <button type="submit" disabled={!report || loading}>{loading ? "Loading…" : "Apply and refresh"}</button>
      <button type="button" disabled={!report || loading} onClick={() => preset(7)}>Last 7 days</button><button type="button" disabled={!report || loading} onClick={() => preset(30)}>Last 30 days</button>
    </form>
    {error && <p role="alert" className="assistant-insights-error">{error} {report && "The last loaded report remains below."}</p>}
    {loading && <p role="status">Loading assistant observations…</p>}
    {!report && !loading && <button type="button" onClick={() => { setLoading(true); void load(); }}>Retry loading report</button>}
    {report && <>
      <p className="assistant-insights-caption">Report: {report.startDate} → {report.endDate} · As of {formatBookingDate(report.generatedAt)} {formatBookingTime(report.generatedAt)} ({report.timeZone})</p>
      {String(report.observedOperations) === "0" && <p className="assistant-insights-empty" role="status">No observations in this period. Run a request using a student account, then refresh. Recording starts with this update; earlier requests were not imported.</p>}
      {Number(report.droppedEventsSinceRestart) > 0 && <p className="assistant-insights-warning">{insightCount(report.droppedEventsSinceRestart)} observation(s) could not be saved since this server started. This report may be incomplete; business receipts remain the source of truth.</p>}
      <section className="assistant-insights-stats" aria-label="Observed assistant metrics">
        <article><h2>Assistant requests</h2><strong>{insightCount(report.searchRequests)}</strong><p>Includes search, clarification, booking management and plan intents.</p></article>
        <article><h2>New booking saves</h2><strong>{insightCount(report.newBookingRequests)}</strong><p>New Pending requests saved through booking confirmations. Replays excluded; reschedules are reported separately.</p></article>
        <article><h2>Confirmation save rate</h2><strong>{rate === null ? "—" : `${rate}%`}</strong><p>Successful confirmation attempts, including {insightCount(report.bookingConfirmReplays)} saved-result replays, out of {insightCount(report.bookingConfirmAttempts)} attempts.</p></article>
        <article><h2>Search P50 / P95</h2><strong className="assistant-insights-latency">{report.searchP50Ms == null ? "—" : `${insightCount(report.searchP50Ms)} ms`} / {report.searchP95Ms == null ? "—" : `${insightCount(report.searchP95Ms)} ms`}</strong><p>Server operation time, including model waits and unsuccessful responses. Excludes the browser and transport between browser and server.</p></article>
      </section>
      <section className="assistant-insights-panel" aria-label="Metric definitions"><h2>Read the numbers accurately</h2>
        <p>Exact sessions found means that options were returned; it does not establish intent accuracy. A booking save is a Pending request, not supervisor approval or assessment completion.</p>
        <p>Reviews and confirmations also serve assessment-plan recommendations. Stage totals are separate operation counts, not a linked search-to-booking conversion funnel. Replays describe successful retries and are not new saves. Later cancellation or completion does not rewrite the original save count. Validation or authentication failures before observation are excluded.</p>
        <p>{insightCount(report.searchWithAlternatives)} no-exact-match request(s) included alternative groups. Fixed evaluation cases measure intent extraction separately; these operational results do not replace reviewed accuracy testing.</p>
      </section>
      <section className="assistant-insights-panel" aria-label="Daily assistant activity"><h2>Daily activity</h2><p className="assistant-insights-caption"><span className="assistant-insights-key search" /> Assistant requests <span className="assistant-insights-key saved" /> New booking saves · {report.timeZone}</p>
        <p className="assistant-insights-caption">Daily details show selected outcomes. The complete operation breakdown is below.</p>
        <ActivityChart days={report.days} />
        <details><summary>View daily counts</summary><div className="assistant-insights-table"><table><thead><tr><th>Date</th><th>Requests</th><th>Exact options</th><th>Clarify</th><th>No exact options</th><th>Unavailable</th><th>New saves</th></tr></thead><tbody>{report.days.map(day => <tr key={day.date}><td>{day.date}</td>{[day.searches, day.ready, day.clarify, day.noResults, day.unavailable, day.newBookingRequests].map((count, index) => <td key={index}>{insightCount(count)}</td>)}</tr>)}</tbody></table></div></details>
      </section>
      <section className="assistant-insights-panel" aria-label="Operation outcomes"><h2>Operation outcomes</h2><div className="assistant-insights-table"><table><thead><tr><th>Operation</th><th>Result</th><th>Attempts</th></tr></thead><tbody>{report.stages.map(row => <tr key={`${row.stage}/${row.outcome}/${row.action || ""}`}><td>{insightStageLabel(row.stage)}{row.action && <small> · {row.action === "CANCEL" ? "Cancel" : row.action === "RESCHEDULE" ? "Reschedule" : row.action}</small>}</td><td>{insightOutcomeLabel(row.outcome)}</td><td>{insightCount(row.attempts)}</td></tr>)}</tbody></table></div>{report.stages.length === 0 && <p>No operations recorded in this period.</p>}</section>
      <section className="assistant-insights-panel" aria-label="Reported model usage"><h2>Reported model usage</h2>
        <div className="assistant-insights-usage"><div><strong>{insightCount(report.modelCalls)}</strong><span>Model calls attempted</span></div><div><strong>{Number(report.callsWithKnownUsage) > 0 ? insightCount(report.totalTokens) : "—"}</strong><span>Reported total tokens</span></div><div><strong>{insightCount(report.callsWithKnownUsage)} / {insightCount(report.modelCalls)}</strong><span>Calls with reported usage</span></div></div>
        <p>{insightCount(report.promptTokens)} reported input tokens · {insightCount(report.completionTokens)} reported output tokens. Missing usage is not estimated. A failed parse may still have reported usage. Reviews and confirmations do not call the model.</p>
        <div className="assistant-insights-table"><table><thead><tr><th>Configured model</th><th>Calls</th><th>Usage reported</th><th>Reported tokens</th></tr></thead><tbody>{report.models.map(model => <tr key={model.modelName || "unknown"}><td>{model.modelName || "Unknown"}</td><td>{insightCount(model.calls)}</td><td>{insightCount(model.knownUsage)}</td><td>{Number(model.knownUsage) > 0 ? insightCount(model.totalTokens) : "—"}</td></tr>)}</tbody></table></div>
        {report.models.length === 0 && <p>No model calls recorded in this period.</p>}
      </section>
      <footer>Aggregate observations only. Conversation text, session details, grades and provider credentials are not collected by this feature. {report.firstRecordedAt && `First retained event: ${formatBookingDate(report.firstRecordedAt)} ${formatBookingTime(report.firstRecordedAt)} (${report.timeZone}).`}</footer>
    </>}
  </main>;
}
