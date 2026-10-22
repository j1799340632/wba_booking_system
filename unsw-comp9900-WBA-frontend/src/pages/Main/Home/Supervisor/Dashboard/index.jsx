import "./index.css";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  ClipboardCheck,
  CalendarDays,
  Clock,
  CircleCheck,
  ChevronRight,
} from "lucide-react";
import {
  getApprovedSession,
  getAssessmentSlotList,
  getUpcomingSession,
} from "@/config/request/api/assessmentSlotApi.js";
import { getAssessmentHistory } from "@/config/request/api/assessmentBookingApi.js";
import { notify } from "@/utils/notify.js";
import { useUserStore } from "@/config/store/useUserStore.js";
import { useAvailabilityStore } from "@/config/store/useAvailabilityStore.js";
import {getProfile, updateAvailableStatus} from "@/config/request/api/userApi.js";

export default function DashboardPageSupervisor() {
  const navigate = useNavigate();

  const {userInfo, setUserInfo} = useUserStore();
  const displayName = userInfo?.name || "Supervisor";

  const [confirmed, setConfirmed] = useState([]);
  const [pending, setPending] = useState([]);
  const [history, setHistory] = useState([]);
  const [closedSlotIds, setClosedSlotIds] = useState([]);
  const [loading, setLoading] = useState(true);
  const [toggling, setToggling] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  const loadData = async () => {
    const supervisorId = userInfo?.id;

    try {
      setLoading(true);
      setErrorMessage("");

      const [approvedRes, pendingRes, slotRes, historyRes] =
        await Promise.all([
          getApprovedSession(1, -1),
          getUpcomingSession(1, -1),
          getAssessmentSlotList(1, -1, supervisorId),
          getAssessmentHistory(1, -1),
        ]);

      const approvedRecords = approvedRes?.data?.records || [];
      const pendingRecords = pendingRes?.data?.records || [];

      const allRecords = [...approvedRecords, ...pendingRecords];
      const profileMap = {};

      await Promise.all(
        [...new Set(allRecords.map((booking) => booking.studentId))]
          .filter(Boolean)
          .map(async (studentId) => {
            try {
              const response = await getProfile(studentId);
              profileMap[studentId] = response?.data || {};
            } catch (error) {
              console.error(
                `Failed to load profile for student ${studentId}:`,
                error
              );
              profileMap[studentId] = {};
            }
          })
      );

      setConfirmed(
        approvedRecords.map((record) => ({
          ...record,
          bookingStatus: 1,
          studentAvatar: profileMap[record.studentId]?.avatar || "",
        }))
      );

      setPending(
        pendingRecords.map((record) => ({
          ...record,
          bookingStatus: 0,
          studentAvatar: profileMap[record.studentId]?.avatar || "",
        }))
      );

      setHistory(historyRes?.data?.records || []);

      const slotRecords = slotRes?.data?.records || [];

      setClosedSlotIds(
        slotRecords.filter((slot) => slot.status === 2).map((slot) => slot.id)
      );
    } catch (error) {
      setErrorMessage(
        error.message || "Unable to connect to backend server."
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const upcomingBookings = [...confirmed, ...pending].sort((a, b) => {
    const da = parseDate(a.startTime);
    const db = parseDate(b.startTime);

    return (da ? da.getTime() : 0) - (db ? db.getTime() : 0);
  });

  const completedCount = history.length;

  const totalTeachingMinutes = history.reduce((sum, record) => {
    // durationMinutes comes as a string like "30min"; fall back to start/end diff.
    const parsed = parseInt(String(record?.durationMinutes ?? ""), 10);

    if (!Number.isNaN(parsed)) {
      return sum + parsed;
    }

    const start = parseDate(record?.startTime);
    const end = parseDate(record?.endTime);

    if (start && end) {
      return sum + Math.max(0, (end.getTime() - start.getTime()) / 60000);
    }

    return sum;
  }, 0);

  const teachingHours = totalTeachingMinutes / 60;
  const teachingHoursLabel =
    teachingHours >= 10 || Number.isInteger(teachingHours)
      ? Math.round(teachingHours).toString()
      : teachingHours.toFixed(1);

  const isAvailable = !!userInfo.isAvailable;
  const canReopen = closedSlotIds.length > 0;

  const setAvailable = useAvailabilityStore((state) => state.setAvailable);

  useEffect(() => {
    setAvailable(isAvailable);
  }, [isAvailable, setAvailable]);

  const handleToggleAvailability = () => {
    if (toggling) return;

    setToggling(true);
    setErrorMessage("");

    const nextStatus = !isAvailable;

    updateAvailableStatus(nextStatus)
        .then(() => {
          setAvailable(nextStatus);
          userInfo.isAvailable = nextStatus;
          setUserInfo(userInfo);
          loadData();
          notify.success("Status changes successfully");
        })
        .catch((err) => {
          setErrorMessage(
              err.message || "Failed to change status."
          );
        })
        .finally(() => setToggling(false))
  };

  return (
    <div>
      <main className="supervisor-dashboard-main">
        <div className="supervisor-dashboard-container">
          <div className="supervisor-dashboard-header">
            <div>
              <h1 className="supervisor-dashboard-title">
                Welcome, {displayName}
              </h1>

              <p className="supervisor-dashboard-subtitle">
                {userInfo?.roleName || "Placement Supervisor"}
              </p>
            </div>

          </div>

          {errorMessage && (
            <div className="supervisor-dashboard-error">
              {errorMessage}
            </div>
          )}

          <div className="supervisor-dashboard-stat-grid">
            <StatCard
              label="Completed Assessments"
              value={loading ? "—" : completedCount}
              sub="completed"
              icon={
                <ClipboardCheck
                  size={20}
                  color="#2563EB"
                />
              }
              iconBg="#DBEAFE"
              onClick={() =>
                navigate(
                  "/home/supervisor/assessment-history"
                )
              }
            />

            <StatCard
              label="Upcoming Bookings"
              value={
                loading ? "—" : upcomingBookings.length
              }
              sub={
                upcomingBookings.length === 1
                  ? "booking"
                  : "bookings"
              }
              icon={
                <CalendarDays
                  size={20}
                  color="#16A34A"
                />
              }
              iconBg="#DCFCE7"
              onClick={() =>
                navigate(
                  "/home/supervisor/upcoming-sessions"
                )
              }
            />

            <StatCard
              label="Teaching Hours"
              value={loading ? "—" : teachingHoursLabel}
              sub={
                teachingHoursLabel === "1" ? "hour" : "hours"
              }
              icon={
                <Clock
                  size={20}
                  color="#D97706"
                />
              }
              iconBg="#FEF3C7"
            />
          </div>

          <div
            className={`supervisor-dashboard-banner ${
              isAvailable ? "available" : "unavailable"
            }`}
          >
            <div className="supervisor-dashboard-banner-left">
              <CircleCheck
                size={22}
                color={
                  isAvailable ? "#16A34A" : "#EA580C"
                }
              />

              <div>
                <div className="supervisor-dashboard-banner-title">
                  {isAvailable
                    ? "You are currently available for assessments"
                    : "You are not accepting bookings"}
                </div>

                <div className="supervisor-dashboard-banner-text">
                  {isAvailable
                    ? "Students can book your open slots."
                    : canReopen
                      ? "Your available slots are closed. Turn on to let students book again."
                      : "Add availability so students can book sessions."}
                </div>

                {isAvailable && (
                  <div className="supervisor-dashboard-banner-note">
                    Turning this off only closes available
                    slots.
                  </div>
                )}
              </div>
            </div>

            <button
                onClick={handleToggleAvailability}
                disabled={toggling}
                className={`supervisor-dashboard-toggle-button ${
                    isAvailable ? "available" : "unavailable"
                } ${toggling ? "is-disabled" : ""}`}
            >
              {toggling
                  ? isAvailable
                      ? "Closing…"
                      : "Enabling…"
                  : isAvailable
                      ? "Available"
                      : "Disabled"}
            </button>
          </div>

          <div className="supervisor-dashboard-bookings-panel">
            <div className="supervisor-dashboard-panel-header">
              <h2 className="supervisor-dashboard-panel-title">
                Upcoming Student Bookings
              </h2>

              <button
                onClick={() =>
                  navigate(
                    "/home/supervisor/upcoming-sessions"
                  )
                }
                className="supervisor-dashboard-view-all-button"
              >
                View all <ChevronRight size={16} />
              </button>
            </div>

            {loading ? (
              <p className="supervisor-dashboard-empty">
                Loading…
              </p>
            ) : upcomingBookings.length === 0 ? (
              <p className="supervisor-dashboard-empty">
                No upcoming student bookings.
              </p>
            ) : (
              <div className="supervisor-dashboard-booking-list">
                {upcomingBookings
                  .slice(0, 6)
                  .map((b, i) => {
                    const studentName =
                      b.studentName ||
                      b.creatorName ||
                      "Student";

                    return (
                      <div
                        key={b.bookingId ?? i}
                        className="supervisor-dashboard-booking-card"
                      >
                        <div className="supervisor-dashboard-booking-left">
                                <div className="supervisor-dashboard-avatar">
                                  {b.studentAvatar ? (
                                    <img
                                      src={b.studentAvatar}
                                      alt={studentName}
                                      className="supervisor-dashboard-avatar-image"
                                    />
                                  ) : (
                                    initials(studentName)
                                  )}
                                </div>

                          <div>
                            <div className="supervisor-dashboard-student-name">
                              {studentName}
                              <TypeTag type={b.type} />
                            </div>

                            <div className="supervisor-dashboard-booking-meta">
                              <span className="supervisor-dashboard-meta-item">
                                <CalendarDays size={13} />
                                {formatDate(b.startTime)}
                              </span>

                              <span className="supervisor-dashboard-meta-item">
                                <Clock size={13} />
                                {formatTime(b.startTime)}
                              </span>

                              <span>
                                {b.department ||
                                  b.hospital ||
                                  "No department"}
                              </span>
                            </div>
                          </div>
                        </div>

                        <StatusPill
                          status={b.bookingStatus}
                        />
                      </div>
                    );
                  })}
              </div>
            )}
          </div>
        </div>
      </main>

    </div>
  );
}

function parseDate(value) {
  if (!value) return null;

  const d = new Date(
    value.replace?.(" ", "T") || value
  );

  return Number.isNaN(d.getTime()) ? null : d;
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

function initials(name) {
  return name
    .split(" ")
    .map((w) => w[0])
    .filter(Boolean)
    .slice(0, 2)
    .join("")
    .toUpperCase();
}

function StatCard({
  label,
  value,
  sub,
  icon,
  iconBg,
  onClick,
}) {
  return (
    <div
      className={`supervisor-dashboard-card ${
        onClick ? "is-clickable" : ""
      }`}
      onClick={onClick}
      role={onClick ? "button" : undefined}
      tabIndex={onClick ? 0 : undefined}
      onKeyDown={(e) => {
        if (
          onClick &&
          (e.key === "Enter" || e.key === " ")
        ) {
          e.preventDefault();
          onClick();
        }
      }}
    >
      <div className="supervisor-dashboard-card-header">
        <span className="supervisor-dashboard-card-label">
          {label}
        </span>

        <span
          className="supervisor-dashboard-card-icon"
          style={{ background: iconBg }}
        >
          {icon}
        </span>
      </div>

      <div className="supervisor-dashboard-card-value">
        {value}
      </div>

      <div className="supervisor-dashboard-card-sub">
        {sub}
      </div>
    </div>
  );
}

function StatusPill({ status }) {
  const map = {
    0: {
      label: "Pending",
      bg: "#FEF3C7",
      color: "#92400E",
    },
    1: {
      label: "Confirmed",
      bg: "#DCFCE7",
      color: "#15803D",
    },
  };

  const s =
    map[status] || {
      label: "Session",
      bg: "#E0F2FE",
      color: "#0B4F71",
    };

  return (
    <span
      className="supervisor-dashboard-status-pill"
      style={{
        background: s.bg,
        color: s.color,
      }}
    >
      {s.label}
    </span>
  );
}

function TypeTag({ type }) {
  if (!type) return null;

  return (
    <span className="supervisor-dashboard-type-tag">
      {type}
    </span>
  );
}