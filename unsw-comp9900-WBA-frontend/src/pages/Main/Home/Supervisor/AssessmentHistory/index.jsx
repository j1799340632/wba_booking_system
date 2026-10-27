import "./index.css";
import { useEffect, useRef, useState } from "react";
import { Pencil } from "lucide-react";
import {exportAssessmentHistoryExcel, getAssessmentHistory} from "@/config/request/api/assessmentBookingApi.js";
import {updateAssessmentResult} from "@/config/request/api/assessmentSlotApi.js";
import { supervisorResultPayload } from "@/utils/supervisorBookings.js";
import {downloadFile} from "@/utils/fileUtils.js";
import {notify} from "@/utils/notify.js";
import {Loading} from "@/components/Loading/index.jsx";
import BaseModal from "@/components/BaseModal/index.jsx";

export default function AssessmentHistorySupervisor() {
  const [historyList, setHistoryList] = useState([]);
  const [error, setError] = useState("");

  const [editItem, setEditItem] = useState(null);
  const [editScore, setEditScore] = useState("");
  const [editFeedback, setEditFeedback] = useState("");
  const [saving, setSaving] = useState(false);
  const [editError, setEditError] = useState("");
  const writeLock = useRef(false);

  const fetchAssessmentHistory = () => {
    Loading.show()
    getAssessmentHistory(1, -1)
      .then((data) => {
        setError("");
        setHistoryList(data.data.records);
      })
      .catch(failure => setError(failure.message || "Assessment history could not be loaded. Refresh to try again."))
      .finally(() => {
        Loading.hide()
      });
  };

  useEffect(() => {
    fetchAssessmentHistory();
  }, []);

  const formatDate = (value) => {
    if (!value) return "-";

    const date = new Date(String(value).replace(" ", "T"));

    if (Number.isNaN(date.getTime())) {
      return value;
    }

    return date.toLocaleDateString("en-AU", {
      day: "2-digit",
      month: "long",
      year: "numeric",
    });
  };

  const getBadgeClass = (type) => {
    if (String(type).toLowerCase().includes("cbd")) {
      return "assessment-history-badge cbd";
    }

    return "assessment-history-badge";
  };

  const getScoreClass = (score, maxScore) => {
    const numericScore = Number(score);
    const numericMaxScore = Number(maxScore);

    if (
      !Number.isNaN(numericScore) &&
      !Number.isNaN(numericMaxScore) &&
      numericScore / numericMaxScore >= 0.85
    ) {
      return "assessment-history-score excellent";
    }

    return "assessment-history-score";
  };

  const exportAssessmentHistory = () => {
    exportAssessmentHistoryExcel().then(data => {
      downloadFile(data.data, "Student_Scores_Report");
    });
  }

  const openEditModal = (item) => {
    setEditError("");
    setEditItem(item);
    setEditScore(item.score ?? "");
    setEditFeedback(item.feedback ?? "");
  };

  const closeEditModal = () => {
    if (writeLock.current) return;
    setEditItem(null);
    setEditScore("");
    setEditFeedback("");
  };

  const submitEdit = async (e) => {
    e.preventDefault();

    if (!editItem || writeLock.current) return;
    writeLock.current = true; setSaving(true); setEditError("");
    try {
      const response = await updateAssessmentResult(supervisorResultPayload(editItem, editScore, editFeedback));
      if (!response?.data?.bookingId) throw new Error("No update receipt was received.");
        notify.success("Assessment updated successfully!");
        setEditItem(null);
        setEditScore("");
        setEditFeedback("");
        fetchAssessmentHistory();
    } catch (failure) { setEditError(`${failure.message || "No update response was received."} Retry the same update or close and refresh history.`); }
    finally { writeLock.current = false; setSaving(false); }
  };

  return (
    <div className="assessment-history-page">
      <main className="assessment-history-main">
        <div className="assessment-history-content">
          <section className="assessment-history-header">
            <h1 className="assessment-history-title">
              Completed Assessments
            </h1>

            <p className="assessment-history-subtitle">
              {historyList.length} assessment
              {historyList.length === 1 ? "" : "s"} conducted
            </p>
          </section>

          <button type="button" className="assessment-refresh-button" disabled={saving || Boolean(editItem)} onClick={fetchAssessmentHistory}>Refresh history</button>
          <button
              type="button"
              onClick={exportAssessmentHistory}
              className="assessment-refresh-button"
          >
            Export History
          </button>

          {error && (
            <div className="assessment-history-error-card">
              <p className="assessment-history-error-text">{error}</p>
            </div>
          )}

          {!error && historyList.length === 0 && (
            <div className="assessment-history-message-card">
              <p className="assessment-history-message">
                No completed assessments yet.
              </p>
            </div>
          )}

          {!error && historyList.length > 0 && (
            <div className="assessment-history-list">
              {historyList.map((item, index) => (
                  <article
                    key={item.id || item.bookingId || item.assessmentId || index}
                    className="assessment-history-card"
                  >
                  <div className="assessment-history-card-top">
                    <div>
                      <div className="assessment-history-name-row">
                        <h2 className="assessment-history-student-name">
                          {item.studentName}
                        </h2>

                        <span className={getBadgeClass(item.wbaType)}>
                          {item.wbaType}
                        </span>
                      </div>

                      <p className="assessment-history-date">
                        {formatDate(item.completedTime)}
                      </p>
                    </div>

                    <div className="assessment-history-card-actions">
                      <div className="assessment-history-score-wrapper">
                        <span
                          className={getScoreClass(item.score, 100)}
                        >
                          {item.score}
                        </span>

                        <span className="assessment-history-score-divider">
                          /
                        </span>

                        <span>100</span>
                      </div>

                      <button
                        type="button"
                        className="assessment-history-edit-button"
                        onClick={() => openEditModal(item)}
                        title="Edit assessment"
                      >
                        <Pencil size={15} />

                        <span>Edit</span>
                      </button>
                    </div>
                  </div>

                  <div className="assessment-history-feedback-box">
                    <p className="assessment-history-feedback">
                      {item.feedback}
                    </p>
                  </div>
                </article>
              ))}
            </div>
          )}
        </div>
      </main>

      <BaseModal
        isOpen={!!editItem}
        title="Edit Assessment"
        onClose={closeEditModal}
      >
        {editItem && (
          <form onSubmit={submitEdit} className="assessment-history-edit-card" role="dialog" aria-modal="true" aria-label="Edit assessment result">
            <div className="assessment-history-edit-heading">
              <h3 className="assessment-history-edit-form-title">
                Edit Assessment
              </h3>
              <p className="assessment-history-edit-form-subtitle">
                Update the score and feedback. The original completion date and completed count are preserved.
              </p>
            </div>

            <div className="assessment-history-edit-student">
              {editItem.studentName}
              <span className={getBadgeClass(editItem.wbaType)}>
                {editItem.wbaType}
              </span>
            </div>

            <label className="assessment-history-edit-label" htmlFor="edit-score">
              Score
              <input
                id="edit-score"
                aria-label="Score"
                name="score"
                type="number"
                className="assessment-history-edit-input"
                value={editScore}
                disabled={saving}
                onChange={(e) => setEditScore(e.target.value)}
                max={100}
                min={0}
                step={0.01}
                required
              />
            </label>

            <label
              className="assessment-history-edit-label"
              htmlFor="edit-feedback"
            >
              Feedback
              <textarea
                id="edit-feedback"
                aria-label="Feedback"
                name="feedback"
                className="assessment-history-edit-textarea"
                value={editFeedback}
                disabled={saving}
                onChange={(e) => setEditFeedback(e.target.value)}
                maxLength={255}
                required
              />
            </label>

            {editError && <p role="alert" className="supervisor-session-error">{editError}</p>}
            <div className="assessment-history-edit-actions">
              <button
                type="button"
                onClick={closeEditModal}
                className="assessment-history-cancel-button"
                disabled={saving}
              >
                Cancel
              </button>

              <button
                type="submit"
                className="assessment-history-submit-button"
                disabled={saving}
              >
                {saving ? "Saving..." : "Save Changes"}
              </button>
            </div>
          </form>
        )}
      </BaseModal>
    </div>
  );
}
