import "./index.css";
import { useEffect, useMemo, useState } from "react";
import {exportAssessmentHistoryExcel, getAssessmentHistory} from "@/config/request/api/assessmentBookingApi.js";
import BaseModal from "@/components/BaseModal/index.jsx";
import {downloadFile} from "@/utils/fileUtils.js";
import {Loading} from "@/components/Loading/index.jsx";

export default function AssessmentHistoryStudent() {
  const [historyList, setHistoryList] = useState([]);
  const [error, setError] = useState("");
  const [searchText, setSearchText] = useState("");
  const [selectedType, setSelectedType] = useState("All");
  const [selectedSession, setSelectedSession] = useState(null);

  const fetchAssessmentHistory = () => {
    Loading.show();
    getAssessmentHistory(1, -1)
      .then((data) => {
        setHistoryList(data.data.records);
        setError("");
      })
      .finally(() => Loading.hide());
  };

  const exportAssessmentHistory = () => {
    exportAssessmentHistoryExcel().then(data => {
      downloadFile(data.data, "Student_Scores_Report");
    });
  }

  useEffect(() => {
    fetchAssessmentHistory();
  }, []);

  const assessmentTypes = useMemo(() => {
    const types = historyList
      .map((item) => item.wbaType)
      .filter((type) => type && type !== "-");

    return ["All", ...Array.from(new Set(types))];
  }, [historyList]);

  const filteredHistoryList = useMemo(() => {
    const keyword = searchText.trim().toLowerCase();

    return historyList.filter((item) => {
      const matchesType =
        selectedType === "All" || item.wbaType === selectedType;

      const matchesSearch =
        !keyword ||
        item.wbaType.toLowerCase().includes(keyword) ||
        item.supervisorName.toLowerCase().includes(keyword) ||
        item.location.toLowerCase().includes(keyword) ||
        item.feedback.toLowerCase().includes(keyword);

      return matchesType && matchesSearch;
    });
  }, [historyList, searchText, selectedType]);

  const averageScore = useMemo(() => {
    const numericScores = historyList
      .map((item) => Number(item.score))
      .filter((score) => !Number.isNaN(score));

    if (numericScores.length === 0) {
      return "-";
    }

    const total = numericScores.reduce((sum, score) => sum + score, 0);
    return (total / numericScores.length).toFixed(1);
  }, [historyList]);

  const formatDate = (value) => {
    if (!value) return "-";

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
      return value;
    }

    return date.toLocaleString("en-AU", {
      year: "numeric",
      month: "short",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  return (
    <div className="assessment-history-page">
      <main className="assessment-history-main">
        <div className="assessment-history-content">
          <section className="assessment-history-header">
            <h1 className="assessment-history-title">Assessment History</h1>
            <p className="assessment-history-description">
              Review your completed workplace-based assessments, supervisor
              feedback, and scores.
            </p>
          </section>

          <div className="assessment-stats-grid">
            <div className="assessment-stat-card">
              <p className="assessment-stat-label">Completed Sessions</p>
              <p className="assessment-stat-value">{historyList.length}</p>
            </div>

            <div className="assessment-stat-card">
              <p className="assessment-stat-label">Average Score</p>
              <p className="assessment-stat-value">{averageScore}</p>
            </div>

            <div className="assessment-stat-card">
              <p className="assessment-stat-label">Assessment Types</p>
              <p className="assessment-stat-value">
                {assessmentTypes.length - 1}
              </p>
            </div>
          </div>

          <div className="assessment-toolbar">
            <input
              type="text"
              placeholder="Search by type, supervisor, location, or feedback"
              value={searchText}
              onChange={(event) => setSearchText(event.target.value)}
              className="assessment-search-input"
            />

            <select
              value={selectedType}
              onChange={(event) => setSelectedType(event.target.value)}
              className="assessment-select"
            >
              {assessmentTypes.map((type) => (
                <option key={type} value={type}>
                  {type}
                </option>
              ))}
            </select>

            <button
              type="button"
              onClick={fetchAssessmentHistory}
              className="assessment-refresh-button"
            >
              Refresh
            </button>
            <button
              type="button"
              onClick={exportAssessmentHistory}
              className="assessment-refresh-button"
            >
              Export History
            </button>
          </div>

          {error && (
            <div className="assessment-card assessment-error-card">
              <p className="assessment-error-text">{error}</p>
            </div>
          )}

          {!error && historyList.length === 0 && (
            <div className="assessment-card">
              <p className="assessment-message">
                No completed assessment history yet.
              </p>
            </div>
          )}

          {!error &&
            historyList.length > 0 &&
            filteredHistoryList.length === 0 && (
              <div className="assessment-card">
                <p className="assessment-message">
                  No matching assessment history found.
                </p>
              </div>
            )}

          {filteredHistoryList.length > 0 && (
            <div className="assessment-card">
              <div className="assessment-summary-row">
                <div>
                  <h2 className="assessment-section-title">
                    Completed Assessments
                  </h2>
                  <p className="assessment-section-description">
                    {filteredHistoryList.length} completed session
                    {filteredHistoryList.length === 1 ? "" : "s"} shown.
                  </p>
                </div>
              </div>

              <div className="assessment-table-container">
                <table className="assessment-table">
                  <thead>
                  <tr>
                    <th>Assessment Type</th>
                    <th>Supervisor</th>
                    <th>Date</th>
                    <th>Location</th>
                    <th>Feedback</th>
                    <th>Score</th>
                    <th>Status</th>
                    <th>Action</th>
                  </tr>
                  </thead>

                  <tbody>
                  {filteredHistoryList.map((item, index) => (
                      <tr key={item.id || item.bookingId || item.assessmentId || index}>
                        <td>{item.wbaType}</td>
                        <td>{item.supervisorName}</td>
                        <td className="assessment-date-cell">
                          {formatDate(item.completedTime)}
                        </td>
                        <td>{item.location}</td>
                        <td className="assessment-feedback-cell">
                          {item.feedback}
                        </td>
                        <td className="assessment-score-cell">{item.score}</td>
                        <td>
                      <span className="assessment-status-badge">
                        completed
                      </span>
                        </td>
                        <td>
                          <button
                              type="button"
                              onClick={() => setSelectedSession(item)}
                              className="assessment-view-button"
                          >
                            View
                          </button>
                        </td>
                      </tr>
                  ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      </main>

      <BaseModal
        isOpen={!!selectedSession}
        title="History Details"
        onClose={() => setSelectedSession(null)}
      >
        {selectedSession && (
          <div onClick={(event) => event.stopPropagation()}>
            <div className="assessment-detail-grid">
              <DetailItem
                label="Assessment Type"
                value={selectedSession.wbaType}
              />
              <DetailItem
                label="Supervisor"
                value={selectedSession.supervisorName}
              />
              <DetailItem
                label="Date"
                value={formatDate(selectedSession.completedTime)}
              />
              <DetailItem label="Location" value={selectedSession.location} />
              <DetailItem label="Score" value={selectedSession.score} />
              <DetailItem label="Status" value={selectedSession.status} />
            </div>

            <div className="assessment-feedback-detail">
              <p className="assessment-detail-label">Feedback</p>
              <p className="assessment-detail-value">
                {selectedSession.feedback}
              </p>
            </div>
          </div>
        )}
      </BaseModal>
    </div>
  );
}

function DetailItem({ label, value }) {
  return (
    <div className="assessment-detail-item">
      <p className="assessment-detail-label">{label}</p>
      <p className="assessment-detail-value">{value || "-"}</p>
    </div>
  );
}
