import "./index.css";
import { useEffect, useState } from "react";
import {
  ClipboardCheck,
  CalendarDays,
  TrendingUp,
  Award,
} from "lucide-react";
import { getRegistrarDashboard } from "@/config/request/api/registrarApi.js";

const TYPE_COLORS = [
  "#2563eb",
  "#f97316",
  "#16a34a",
  "#a855f7",
  "#eab308",
  "#ec4899",
  "#06b6d4",
  "#64748b",
];

const WEEKLY_SERIES = [
  { key: "slots", label: "Slots", color: "#1d4ed8" },
  { key: "bookings", label: "Bookings", color: "#f97316" },
  { key: "completed", label: "Completed", color: "#16a34a" },
];

export default function DashboardPageAdmin() {
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const [summary, setSummary] = useState(null);
  const [weeklyActivity, setWeeklyActivity] = useState([]);
  const [wbaTypeBreakdown, setWbaTypeBreakdown] = useState([]);
  const [supervisorParticipation, setSupervisorParticipation] = useState([]);

  useEffect(() => {
    setLoading(true);
    getRegistrarDashboard()
      .then((res) => {
        const data = res?.data || {};
        setSummary(data.summary || null);
        setWeeklyActivity(data.weeklyActivity || []);
        setWbaTypeBreakdown(data.wbaTypeBreakdown || []);
        setSupervisorParticipation(data.supervisorParticipation || []);
        setErrorMessage("");
      })
      .catch(() => setErrorMessage("Unable to load dashboard data."))
      .finally(() => setLoading(false));
  }, []);

  const statCards = [
    {
      key: "completedWbas",
      label: "Completed WBAs",
      value: summary?.completedWbas ?? 0,
      hint: "Assessments completed",
      icon: <ClipboardCheck size={18} />,
      tone: "green",
    },
    {
      key: "totalBookings",
      label: "Total Bookings",
      value: summary?.totalBookings ?? 0,
      hint: "All bookings",
      icon: <CalendarDays size={18} />,
      tone: "blue",
    },
    {
      key: "availableSlots",
      label: "Available Slots",
      value: summary?.availableSlots ?? 0,
      hint: "Open for booking",
      icon: <TrendingUp size={18} />,
      tone: "amber",
    },
    {
      key: "activeSupervisors",
      label: "Active Supervisors",
      value: summary?.activeSupervisors ?? 0,
      hint: "With open slots",
      icon: <Award size={18} />,
      tone: "purple",
    },
  ];

  return (
    <main className="admin-dashboard-main">
      <div className="admin-dashboard-container">
        <h1 className="admin-dashboard-title">Administrator Dashboard</h1>
        <p className="admin-dashboard-subtitle">
          Platform overview across all sites
        </p>

        {errorMessage && (
          <div className="admin-dashboard-error">{errorMessage}</div>
        )}

        {loading ? (
          <div className="admin-dashboard-loading">Loading dashboard…</div>
        ) : (
          <>
            <section className="admin-dashboard-stat-grid">
              {statCards.map((card) => (
                <article className="admin-dashboard-card" key={card.key}>
                  <div className="admin-dashboard-stat-head">
                    <span className="admin-dashboard-stat-label">
                      {card.label}
                    </span>
                    <span
                      className={`admin-dashboard-stat-icon tone-${card.tone}`}
                    >
                      {card.icon}
                    </span>
                  </div>
                  <div className="admin-dashboard-stat-value">{card.value}</div>
                  <div className="admin-dashboard-stat-hint">{card.hint}</div>
                </article>
              ))}
            </section>

            <section className="admin-dashboard-panel-grid">
              <article className="admin-dashboard-card">
                <div className="admin-dashboard-card-head">
                  <h2 className="admin-dashboard-card-title">Weekly Activity</h2>

                  {weeklyActivity.length > 0 && (
                    <div className="admin-dashboard-legend">
                      {WEEKLY_SERIES.map((s) => (
                        <span className="admin-dashboard-legend-item" key={s.key}>
                          <i style={{ background: s.color }} />
                          {s.label}
                        </span>
                      ))}
                    </div>
                  )}
                </div>

                <WeeklyActivityChart data={weeklyActivity} />
              </article>

              <article className="admin-dashboard-card">
                <h2 className="admin-dashboard-card-title">WBA Type Breakdown</h2>
                <WbaTypeChart data={wbaTypeBreakdown} />
              </article>
            </section>

            <section className="admin-dashboard-single">
              <article className="admin-dashboard-card">
                <h2 className="admin-dashboard-card-title is-centered">
                  Supervisor Participation
                </h2>
                <SupervisorChart data={supervisorParticipation} />
              </article>
            </section>
          </>
        )}
      </div>
    </main>
  );
}

function WeeklyActivityChart({ data }) {
  if (!data || data.length === 0) {
    return <div className="admin-dashboard-empty">No activity data yet.</div>;
  }

  const width = 640;
  const height = 240;
  const padL = 40;
  const padR = 16;
  const padT = 16;
  const padB = 34;
  const innerW = width - padL - padR;
  const innerH = height - padT - padB;

  const series = WEEKLY_SERIES;

  const maxValue = Math.max(
    1,
    ...data.flatMap((d) => series.map((s) => Number(d[s.key]) || 0))
  );
  const { niceMax, ticks } = buildTicks(maxValue);

  const xAt = (i) =>
    data.length === 1 ? padL + innerW / 2 : padL + (innerW * i) / (data.length - 1);
  const yAt = (v) => padT + innerH - (innerH * (Number(v) || 0)) / niceMax;

  return (
    <div className="admin-dashboard-chart">
      <svg viewBox={`0 0 ${width} ${height}`} className="admin-dashboard-svg">
        {ticks.map((t) => (
          <g key={t}>
            <line
              x1={padL}
              x2={width - padR}
              y1={yAt(t)}
              y2={yAt(t)}
              stroke="#eef2f6"
              strokeWidth="1"
            />
            <text x={padL - 8} y={yAt(t) + 4} textAnchor="end" className="admin-dashboard-axis">
              {t}
            </text>
          </g>
        ))}

        {series.map((s) => {
          const points = data.map((d, i) => `${xAt(i)},${yAt(d[s.key])}`).join(" ");
          return (
            <polyline
              key={s.key}
              points={points}
              fill="none"
              stroke={s.color}
              strokeWidth="2.5"
              strokeLinejoin="round"
              strokeLinecap="round"
            />
          );
        })}

        {series.map((s) =>
          data.map((d, i) => (
            <circle
              key={`${s.key}-${i}`}
              cx={xAt(i)}
              cy={yAt(d[s.key])}
              r="3.5"
              fill={s.color}
              stroke="#ffffff"
              strokeWidth="1.5"
            />
          ))
        )}

        {data.map((d, i) => (
          <text
            key={`x-${i}`}
            x={xAt(i)}
            y={height - 12}
            textAnchor="middle"
            className="admin-dashboard-axis"
          >
            {d.date}
          </text>
        ))}
      </svg>
    </div>
  );
}

function WbaTypeChart({ data }) {
  if (!data || data.length === 0) {
    return <div className="admin-dashboard-empty">No WBA data yet.</div>;
  }

  const size = 190;
  const cx = size / 2;
  const cy = size / 2;
  const rOuter = 78;
  const rInner = 46;

  const total = data.reduce((sum, d) => sum + (Number(d.count) || 0), 0);

  let startAngle = -Math.PI / 2;

  const arcs = data.map((d, i) => {
    const value = Number(d.count) || 0;
    const ratio = total > 0 ? value / total : 0;
    const angle = ratio * Math.PI * 2;
    const endAngle = startAngle + angle;

    const x1 = cx + rOuter * Math.cos(startAngle);
    const y1 = cy + rOuter * Math.sin(startAngle);
    const x2 = cx + rOuter * Math.cos(endAngle);
    const y2 = cy + rOuter * Math.sin(endAngle);
    const x3 = cx + rInner * Math.cos(endAngle);
    const y3 = cy + rInner * Math.sin(endAngle);
    const x4 = cx + rInner * Math.cos(startAngle);
    const y4 = cy + rInner * Math.sin(startAngle);

    const largeArc = angle > Math.PI ? 1 : 0;
    const path =
      ratio >= 0.999
        ? `M ${cx - rOuter} ${cy} A ${rOuter} ${rOuter} 0 1 1 ${cx + rOuter} ${cy} A ${rOuter} ${rOuter} 0 1 1 ${cx - rOuter} ${cy} M ${cx - rInner} ${cy} A ${rInner} ${rInner} 0 1 0 ${cx + rInner} ${cy} A ${rInner} ${rInner} 0 1 0 ${cx - rInner} ${cy}`
        : `M ${x1} ${y1} A ${rOuter} ${rOuter} 0 ${largeArc} 1 ${x2} ${y2} L ${x3} ${y3} A ${rInner} ${rInner} 0 ${largeArc} 0 ${x4} ${y4} Z`;

    startAngle = endAngle;

    return { path, color: TYPE_COLORS[i % TYPE_COLORS.length], item: d };
  });

  return (
    <div className="admin-dashboard-donut-wrap">
      <svg viewBox={`0 0 ${size} ${size}`} className="admin-dashboard-donut">
        {arcs.map((a, i) => (
          <path
            key={i}
            d={a.path}
            fill={a.color}
            fillRule="evenodd"
            stroke="#ffffff"
            strokeWidth="1"
          />
        ))}
      </svg>

      <ul className="admin-dashboard-donut-legend">
        {data.map((d, i) => (
          <li key={d.type ?? i}>
            <span
              className="admin-dashboard-dot"
              style={{ background: TYPE_COLORS[i % TYPE_COLORS.length] }}
            />
            <span className="admin-dashboard-donut-type">{d.type}</span>
            <span className="admin-dashboard-donut-count">{d.count}</span>
            <span className="admin-dashboard-donut-pct">
              {formatPercentage(d.percentage, d.count, total)}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function SupervisorChart({ data }) {
  if (!data || data.length === 0) {
    return (
      <div className="admin-dashboard-empty">No supervisor data yet.</div>
    );
  }

  const width = 900;
  const height = 260;
  const padL = 44;
  const padR = 16;
  const padT = 24;
  const padB = 40;
  const innerW = width - padL - padR;
  const innerH = height - padT - padB;

  const maxCount = Math.max(1, ...data.map((d) => Number(d.count) || 0));
  const { niceMax, ticks } = buildTicks(maxCount);

  const slotW = innerW / data.length;
  const barW = Math.min(72, slotW * 0.6);

  const yAt = (v) => padT + innerH - (innerH * (Number(v) || 0)) / niceMax;

  return (
    <div className="admin-dashboard-chart">
      <svg viewBox={`0 0 ${width} ${height}`} className="admin-dashboard-svg">
        {ticks.map((t) => (
          <g key={t}>
            <line
              x1={padL}
              x2={width - padR}
              y1={yAt(t)}
              y2={yAt(t)}
              stroke="#eef2f6"
              strokeWidth="1"
            />
            <text
              x={padL - 10}
              y={yAt(t) + 4}
              textAnchor="end"
              className="admin-dashboard-axis"
            >
              {t}
            </text>
          </g>
        ))}

        {data.map((d, i) => {
          const value = Number(d.count) || 0;
          const x = padL + slotW * i + (slotW - barW) / 2;
          const y = yAt(value);
          const h = Math.max(2, padT + innerH - y);

          return (
            <g key={d.supervisorId ?? i}>
              <rect
                x={x}
                y={y}
                width={barW}
                height={h}
                rx="6"
                fill="#fbbf24"
              >
                <title>{`${d.supervisorName}: ${value}`}</title>
              </rect>

              <text
                x={x + barW / 2}
                y={y - 8}
                textAnchor="middle"
                className="admin-dashboard-bar-label"
              >
                {value}
              </text>

              <text
                x={padL + slotW * i + slotW / 2}
                y={height - 14}
                textAnchor="middle"
                className="admin-dashboard-axis"
              >
                {truncate(d.supervisorName, Math.floor(slotW / 7))}
              </text>
            </g>
          );
        })}
      </svg>
    </div>
  );
}

function buildTicks(maxValue, tickCount = 4) {
  const rawStep = Math.max(1, maxValue / tickCount);
  const magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));
  const candidates = [1, 2, 2.5, 5, 10].map((m) => m * magnitude);
  const step = Math.max(
    1,
    Math.ceil(candidates.find((c) => c >= rawStep) || magnitude * 10)
  );
  const niceMax = step * tickCount;

  const ticks = [];
  for (let i = 0; i <= tickCount; i += 1) {
    ticks.push(step * i);
  }

  return { niceMax, ticks };
}

function truncate(text, max) {
  const str = String(text ?? "");
  if (max < 3) return str.slice(0, 3);
  return str.length > max ? `${str.slice(0, max - 1)}…` : str;
}

function formatPercentage(percentage, count, total) {
  const pct =
    percentage !== null && percentage !== undefined
      ? Number(percentage)
      : total > 0
        ? ((Number(count) || 0) / total) * 100
        : 0;

  if (Number.isNaN(pct)) return "0%";

  return `${Math.round(pct)}%`;
}
