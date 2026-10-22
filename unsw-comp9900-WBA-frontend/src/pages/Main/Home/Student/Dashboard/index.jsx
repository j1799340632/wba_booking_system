import "./index.css";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  CheckCircle2,
  CalendarDays,
  Clock,
  MapPin,
  Award,
  ChevronRight,
} from "lucide-react";
import {
  getMyBookings,
  getAssessmentHistory,
} from "@/config/request/api/assessmentBookingApi.js";
import { useUserStore } from "@/config/store/useUserStore.js";

export default function DashboardPageStudent() {
  const navigate = useNavigate();

  const user = useUserStore((state) => state.userInfo);
  const firstName = (user?.name || "there").split(" ")[0];

  const [bookings, setBookings] = useState([]);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  const startGetDashboardData = () => {
    Promise.all([getMyBookings(1, -1), getAssessmentHistory(1, -1)])
      .then(([bookingRes, historyRes]) => {
        setBookings(bookingRes.data.records ?? []);
        setHistory(historyRes.data.records ?? []);
      })
      .catch((err) =>
        setErrorMessage(err.message || "Unable to connect to backend server.")
      )
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    startGetDashboardData();
  }, []);

  const completedCount = history.length;

  const upcomingSessions = bookings
    .filter((b) => b.status === 0 || b.status === 1)
    .sort((a, b) => {
      const da = parseDate(a.startTime);
      const db = parseDate(b.startTime);
      return (da ? da.getTime() : 0) - (db ? db.getTime() : 0);
    });

  const scores = history
    .map((h) => Number(h.score))
    .filter((n) => !Number.isNaN(n));

  const averageScore =
    scores.length === 0
      ? "—"
      : (scores.reduce((sum, n) => sum + n, 0) / scores.length).toFixed(1);

  const typeCounts = new Map();

  history.forEach((h) => {
    const type = (h.wbaType || "").trim() || "Unspecified";
    typeCounts.set(type, (typeCounts.get(type) ?? 0) + 1);
  });

  const typeBreakdown = Array.from(typeCounts, ([key, done]) => ({
    key,
    done,
  })).sort((a, b) => b.done - a.done);

  const REQUIRED_PER_TYPE = 10;

  return (
    <div>
      <main className="student-dashboard-main">
        <div className="student-dashboard-container">
          <div className="student-dashboard-header">
            <div>
              <h1 className="student-dashboard-title">
                Good morning, {firstName}
              </h1>

              <p className="student-dashboard-subtitle">
                {user?.roleName || "Student"} · Clinical Placement
              </p>
            </div>

          </div>

          {errorMessage && (
            <div className="student-dashboard-error">{errorMessage}</div>
          )}

          <div className="student-dashboard-stat-grid">
            <StatCard
              label="Completed WBAs"
              value={loading ? "—" : completedCount}
              sub={
                completedCount === 1
                  ? "assessment completed"
                  : "assessments completed"
              }
              icon={<CheckCircle2 size={20} color="#16A34A" />}
              iconBg="#DCFCE7"
              onClick={() => navigate("/home/student/assessment-history")}
            />

            <StatCard
              label="Upcoming Sessions"
              value={loading ? "—" : upcomingSessions.length}
              sub={
                upcomingSessions.length === 1
                  ? "session booked"
                  : "sessions booked"
              }
              icon={<CalendarDays size={20} color="#2563EB" />}
              iconBg="#DBEAFE"
              onClick={() => navigate("/home/student/my-bookings")}
            />

            <StatCard
              label="Average Score"
              value={loading ? "—" : averageScore}
              sub="across completed WBAs"
              icon={<Award size={20} color="#7C3AED" />}
              iconBg="#EDE9FE"
              onClick={() => navigate("/home/student/assessment-history")}
            />
          </div>

          <div className="student-dashboard-panel-grid">
            <Panel>
              <div className="student-dashboard-panel-header">
                <h2 className="student-dashboard-panel-title">
                  Upcoming Sessions
                </h2>

                <button
                  onClick={() => navigate("/home/student/my-bookings")}
                  className="student-dashboard-view-all-button"
                >
                  View all <ChevronRight size={16} />
                </button>
              </div>

              {loading ? (
                <p className="student-dashboard-empty">Loading…</p>
              ) : upcomingSessions.length === 0 ? (
                <div className="student-dashboard-empty-box">
                  <p className="student-dashboard-empty">
                    No upcoming sessions yet.
                  </p>

                  <button
                    onClick={() => navigate("/home/student/bookSlot")}
                    className="student-dashboard-book-button"
                  >
                    Book a slot
                  </button>
                </div>
              ) : (
                <div className="student-dashboard-session-list">
                  {upcomingSessions.slice(0, 4).map((s, i) => (
                    <div
                      key={s.bookingId ?? i}
                      className="student-dashboard-session-card"
                    >
                      <div className="student-dashboard-session-header">
                        <span className="student-dashboard-session-name-row">
                          <span className="student-dashboard-supervisor-name">
                            {s.supervisorName || "Supervisor"}
                          </span>

                          <TypeTag type={s.wbaType} />
                        </span>

                        <StatusPill status={s.status} />
                      </div>

                      <div className="student-dashboard-session-meta">
                        <Meta icon={<CalendarDays size={14} />}>
                          {formatDate(s.startTime)}
                        </Meta>

                        <Meta icon={<Clock size={14} />}>
                          {formatTime(s.startTime)} – {formatTime(s.endTime)}
                        </Meta>

                        <Meta icon={<MapPin size={14} />}>
                          {s.hospital || s.location || "No location"}
                        </Meta>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </Panel>

            <Panel>
              <div className="student-dashboard-panel-header">
                <h2 className="student-dashboard-panel-title">
                  WBA Progress by Type
                </h2>

                <button
                  onClick={() => navigate("/home/student/assessment-history")}
                  className="student-dashboard-view-all-button"
                >
                  View all <ChevronRight size={16} />
                </button>
              </div>

              {loading ? (
                <p className="student-dashboard-empty">Loading…</p>
              ) : typeBreakdown.length === 0 ? (
                <p className="student-dashboard-empty">
                  No completed assessments yet.
                </p>
              ) : (
                <div className="student-dashboard-progress-list">
                  {typeBreakdown.map((t) => (
                    <div key={t.key}>
                      <div className="student-dashboard-progress-row">
                        <span className="student-dashboard-progress-name">
                          {t.key}
                        </span>

                        <span className="student-dashboard-progress-count">
                          {t.done}/{REQUIRED_PER_TYPE}
                        </span>
                      </div>

                      <div className="student-dashboard-progress-track">
                        <div
                          className="student-dashboard-progress-fill"
                          style={{
                            width: `${Math.min(
                              100,
                              Math.round((t.done / REQUIRED_PER_TYPE) * 100)
                            )}%`,
                          }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </Panel>
          </div>
        </div>
      </main>

    </div>
  );
}

function parseDate(value) {
  if (!value) return null;
  const d = new Date(value.replace?.(" ", "T") || value);
  return isNaN(d.getTime()) ? null : d;
}

function formatDate(value) {
  const d = parseDate(value);
  if (!d) return "";
  return d.toLocaleDateString("en-AU", {
    weekday: "short",
    day: "numeric",
    month: "short",
  });
}

function formatTime(value) {
  const d = parseDate(value);
  if (!d) return "";
  return d.toLocaleTimeString("en-AU", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}

function StatCard({ label, value, sub, icon, iconBg, onClick }) {
  return (
    <div
      className={`student-dashboard-card ${
        onClick ? "is-clickable" : ""
      }`}
      onClick={onClick}
      role={onClick ? "button" : undefined}
      tabIndex={onClick ? 0 : undefined}
      onKeyDown={(e) => {
        if (onClick && (e.key === "Enter" || e.key === " ")) {
          e.preventDefault();
          onClick();
        }
      }}
    >
      <div className="student-dashboard-card-header">
        <span className="student-dashboard-card-label">{label}</span>

        <span
          className="student-dashboard-card-icon"
          style={{ background: iconBg }}
        >
          {icon}
        </span>
      </div>

      <div className="student-dashboard-card-value">{value}</div>

      <div className="student-dashboard-card-sub">{sub}</div>
    </div>
  );
}

function Panel({ children }) {
  return <div className="student-dashboard-panel">{children}</div>;
}

function Meta({ icon, children }) {
  return (
    <span className="student-dashboard-meta">
      {icon}
      {children}
    </span>
  );
}

function StatusPill({ status }) {
  const map = {
    0: { label: "Pending", bg: "#FEF3C7", color: "#92400E" },
    1: { label: "Confirmed", bg: "#DCFCE7", color: "#15803D" },
  };

  const s =
    map[status] || { label: "Session", bg: "#E0F2FE", color: "#0B4F71" };

  return (
    <span
      className="student-dashboard-status-pill"
      style={{ background: s.bg, color: s.color }}
    >
      {s.label}
    </span>
  );
}

function TypeTag({ type }) {
  if (!type) return null;

  return <span className="student-dashboard-type-tag">{type}</span>;
}
