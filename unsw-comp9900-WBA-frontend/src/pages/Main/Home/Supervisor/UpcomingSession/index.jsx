import "./index.css";
import {useEffect, useState} from "react";
import {useNavigate} from "react-router-dom";
import {Building2, CalendarDays, Check, Clock3, TriangleAlert, X,} from "lucide-react";
import {
  approveSlot,
  cancelSlot,
  completeSlot,
  declineSlot,
  getApprovedSession,
  getUpcomingSession,
} from "@/config/request/api/assessmentSlotApi.js";
import {
  addBookingToGoogleCalendar,
  connectGoogleCalendar,
  getGoogleCalendarStatus,
} from "@/config/request/api/googleCalendarApi.js";
import {notify} from "@/utils/notify.js";
import BaseModal from "@/components/BaseModal/index.jsx";
import {useConfirmStore} from "@/config/store/useConfirmStore.js";
import {useSse} from "@/utils/sse/useSse.js";
import {notificationEvent} from "@/utils/sse/sseEventChannel.js";
import {sendNotification} from "@/config/request/api/sendNotificationApi.js";
import {Loading} from "@/components/Loading/index.jsx";
import AssessmentSlotRepeatTypeEnum from "@/config/constant/AssessmentSlotRepeatTypeEnum.js";

function WBABadge({ type }) {
  if (!type) return null;

  return <span className="upcoming-session-badge">{type}</span>;
}

export default function UpcomingSessionSupervisor() {
  const navigate = useNavigate();
  const [approvedBookings, setApprovedBookings] = useState([]);
  const [pendingBookings, setPendingBookings] = useState([]);

  const [showDeclineModal, setShowDeclineModal] = useState(false);
  const [selectedBooking, setSelectedBooking] = useState(null);
  const [returnSlot, setReturnSlot] = useState(true);

  const [showCompleteModal, setShowCompleteModal] = useState(false);
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [selectedCancelBooking, setSelectedCancelBooking] = useState(null);
  const [calendarConnected, setCalendarConnected] = useState(false);

  const [calendarStatusLoading, setCalendarStatusLoading] = useState(true);

  const [calendarConnectLoading, setCalendarConnectLoading] = useState(false);

  const [calendarLoadingId, setCalendarLoadingId] = useState(null);

  const [addedCalendarBookingIds, setAddedCalendarBookingIds] = useState([]);

  const loadGoogleCalendarStatus = () => {
    setTimeout(() => {
      setCalendarStatusLoading(true);
    }, 0)

    getGoogleCalendarStatus()
      .then((result) => {
        const connected =
          result?.data?.connected ??
          result?.data?.data?.connected ??
          false;

        setCalendarConnected(Boolean(connected));
      })
      .catch(() => {
        setCalendarConnected(false);
      })
      .finally(() => {
        setCalendarStatusLoading(false);
      });
  };

  const handleConnectGoogleCalendar = () => {
    setCalendarConnectLoading(true);

    connectGoogleCalendar(window.location.pathname)
      .then((result) => {
        window.location.href = result?.data?.authorizationUrl;
      })
      .finally(() => {
        setCalendarConnectLoading(false);
      });
  };

  const handleAddBookingToCalendar = (booking) => {
    const bookingId = booking.bookingId || booking.id;

    setCalendarLoadingId(bookingId);

    addBookingToGoogleCalendar(bookingId)
      .then(() => {
        setAddedCalendarBookingIds((previous) => [
          ...previous,
          bookingId,
        ]);

        notify.success(
          "Added to Google Calendar."
        );
      })
      .finally(() => {
        setCalendarLoadingId(null);
      });
  };

  const showConfirm = useConfirmStore((state) => state.showConfirm);

  const formatDate = (time) => {
    if (!time) return "No date";
    const date = new Date(time.replace(" ", "T"));

    return date.toLocaleDateString("en-AU", {
      weekday: "long",
      day: "numeric",
      month: "long",
    });
  };

  const formatTime = (time) => {
    if (!time) return "No time";
    return time.slice(11, 16);
  };

  const getStudentName = (booking) => {
    return booking.studentName || "Student";
  };

  const getInitials = (name) => {
    return name
      .split(" ")
      .map((word) => word[0])
      .join("")
      .toUpperCase()
      .slice(0, 2);
  };

  const formatBooking = (booking) => ({
    ...booking,
    id: booking.id,
    bookingId: booking.bookingId,
    studentId: booking.studentId,
    studentName: getStudentName(booking),
    studentAvatar: booking.creatorAvatar || "",
    type: booking.type || "WBA",
    date: formatDate(booking.startTime),
    time: formatTime(booking.startTime),
    department: booking.department || booking.hospital || "No department",
    status: booking.status,
    raw: booking,
  });

  const loadSessions = async () => {
    try {
      Loading.show()

      const [pendingRes, approvedRes] = await Promise.all([
        getUpcomingSession(1, -1),
        getApprovedSession(1, -1),
      ]);

      const pendingRecords = pendingRes?.data?.records || [];
      const approvedRecords = approvedRes?.data?.records || [];

      setPendingBookings(
        pendingRecords.map((booking) =>
          formatBooking(booking)
        )
      );

      setApprovedBookings(
        approvedRecords.map((booking) =>
          formatBooking(booking)
        )
      );
    } finally {
      Loading.hide()
    }
  };

  useEffect(() => {
    void loadSessions();
    loadGoogleCalendarStatus();
  }, []);

  useSse(notificationEvent, () => {
    void loadSessions()
  });

  const notifyStudent = (studentId, eventContent) => {
    if (!studentId) return;
    sendNotification({
      receiverId: studentId,
      eventName: notificationEvent,
      eventContent,
      isSendEmail: true,
    }).catch((err) => console.log(err));
  };

  const handleApprove = (booking) => {
    showConfirm("Reminder", "Are you sure to approve the booking?", () => {
      approveSlot(booking.id, booking.bookingId).then(() => {
        notify.success("Approved successfully!");
        notifyStudent(
          booking.studentId,
          `Your booking for the ${booking.type} session on ${booking.date} at ${booking.time} has been approved.`
        );
        loadSessions();
      });
    });
  };

  const openDeclineModal = (booking) => {
    setSelectedBooking(booking);
    setReturnSlot(true);
    setShowDeclineModal(true);
  };

  const closeDeclineModal = () => {
    setShowDeclineModal(false);
    setSelectedBooking(null);
    setReturnSlot(true);
  };

  const confirmDecline = () => {
    declineSlot(selectedBooking.id, selectedBooking.bookingId, returnSlot).then(
      () => {
        notify.success("Declined successfully!");
        notifyStudent(
          selectedBooking.studentId,
          `Your booking for the ${selectedBooking.type} session on ${selectedBooking.date} at ${selectedBooking.time} has been declined.`
        );
        closeDeclineModal();
        loadSessions();
      }
    );
  };

  const confirmComplete = (e) => {
    e.preventDefault();

    const formData = new FormData(e.currentTarget);
    const data = Object.fromEntries(formData.entries());
    const score = data.score;
    const feedback = data.feedback;

    completeSlot({
      bookingId: selectedCancelBooking.bookingId,
      score,
      feedback,
    }).then(() => {
      notify.success("Completed successfully!");
      notifyStudent(
        selectedCancelBooking.studentId,
        `Your assessment results for the ${selectedCancelBooking.type} session on ${selectedCancelBooking.date} are now available.`
      );
      closeComCompleteModal();
      loadSessions();
    });
  };

  const openCompleteModal = (booking) => {
    setSelectedCancelBooking(booking);
    setShowCompleteModal(true);
  };

  const closeComCompleteModal = () => {
    setShowCompleteModal(false);
    setSelectedCancelBooking(null);
  };

  const openCancelModal = (booking) => {
    setSelectedCancelBooking(booking);
    setShowCancelModal(true);
  };

  const closeCancelModal = () => {
    setShowCancelModal(false);
    setSelectedCancelBooking(null);
  };

  const confirmCancel = async () => {
    cancelSlot(selectedCancelBooking.id, selectedCancelBooking.bookingId, returnSlot).then(
      () => {
        notify.success("Cancelled successfully!");
        notifyStudent(
          selectedCancelBooking.studentId,
          `Your booking for the ${selectedCancelBooking.type} session on ${selectedCancelBooking.date} at ${selectedCancelBooking.time} has been cancelled.`
        );
        closeCancelModal();
        loadSessions();
      }
    );
  };

  const BookingCard = ({ booking, showActions }) => (
    <div className="upcoming-session-card">
      <div className="upcoming-session-card-left">
        <div
          className="upcoming-session-avatar upcoming-session-clickable"
          onClick={() =>
            navigate(`/home/supervisor/student-profile?id=${booking.studentId}`)
          }
        >
          {booking.studentAvatar ? (
            <img
              src={booking.studentAvatar}
              alt={`${booking.studentName} avatar`}
              className="upcoming-session-avatar-image"
            />
          ) : (
            getInitials(booking.studentName)
          )}
        </div>

        <div className="upcoming-session-card-content">
        <div className="upcoming-session-name-row">
            <button
              type="button"
              className="upcoming-session-student-name"
              onClick={() => {
                if (!booking.studentId) {
                  notify.error("Student ID is unavailable.");
                  return;
                }

                navigate(`/home/supervisor/student-profile?id=${booking.studentId}`);
              }}
            >
              {booking.studentName}
            </button>

            <WBABadge type={booking.type} />
          </div>

          <div className="upcoming-session-detail-row">
            <span className="upcoming-session-icon-text">
              <CalendarDays size={14} />
              {booking.date}
            </span>

            <span className="upcoming-session-icon-text">
              <Clock3 size={14} />
              {booking.time}
            </span>

            <span className="upcoming-session-icon-text">
              <Building2 size={14} />
              {booking.department}
            </span>
          </div>
        </div>
      </div>
      {showActions ? (
        <div className="upcoming-session-actions">
          <button
            onClick={() => handleApprove(booking)}
            className="upcoming-session-button approve"
          >
            <Check size={15} />
            Approve
          </button>

          <button
            onClick={() => openDeclineModal(booking)}
            className="upcoming-session-button decline"
          >
            <X size={15} />
            Decline
          </button>
        </div>
      ) : (
        <div className="upcoming-session-actions">

          {calendarStatusLoading ? (
            <button
              className="upcoming-session-button"
              disabled
            >
              Checking...
            </button>
          ) : !calendarConnected ? (
            <button
              onClick={handleConnectGoogleCalendar}
              className="upcoming-session-button approve"
              disabled={calendarConnectLoading}
            >
              Connect Calendar
            </button>
          ) : (
            <button
              onClick={() =>
                handleAddBookingToCalendar(booking)
              }
              className="upcoming-session-button approve"
            >
              Add to Calendar
            </button>
          )}
          <button
            onClick={() => openCompleteModal(booking)}
            className="upcoming-session-button complete"
          >
            <Check size={15} />
            Complete
          </button>

          <button
            onClick={() => openCancelModal(booking)}
            className="upcoming-session-button cancel"
          >
            <X size={15} />
            Cancel
          </button>
        </div>
      )}
    </div>
  );

  return (
    <div>
      <main className="upcoming-session-main">
        <div className="upcoming-session-container">
          <div className="upcoming-session-header">
            <h1 className="upcoming-session-title">Upcoming Sessions</h1>

            <p className="upcoming-session-subtitle">
              {approvedBookings.length} scheduled sessions
            </p>
          </div>

          <section className="upcoming-session-section approved">
            <h2 className="upcoming-session-section-title">
              Approved Sessions
            </h2>

            {approvedBookings.length === 0 ? (
              <p className="upcoming-session-empty">
                No approved sessions yet.
              </p>
            ) : (
              approvedBookings.map((booking) => (
                <BookingCard
                  key={booking.bookingId}
                  booking={booking}
                  showActions={false}
                />
              ))
            )}
          </section>

          <section className="upcoming-session-section pending">
            <h2 className="upcoming-session-section-title">
              Request Approval
            </h2>

            {pendingBookings.length === 0 ? (
              <p className="upcoming-session-empty">
                No pending requests.
              </p>
            ) : (
              pendingBookings.map((booking) => (
                <BookingCard
                  key={booking.bookingId}
                  booking={booking}
                  showActions={true}
                />
              ))
            )}
          </section>
        </div>
      </main>

      <BaseModal
        isOpen={showDeclineModal && !!selectedBooking}
        title="Decline Booking"
        onClose={closeDeclineModal}
      >
        {selectedBooking && (
          <div>
            <div>
              <ModalTitle title="Decline Booking" />

              <p className="upcoming-session-modal-description">
                Are you sure you want to decline{" "}
                <strong>{selectedBooking.studentName}</strong>'s booking request?
              </p>

              {AssessmentSlotRepeatTypeEnum.NO_REPEAT.code === selectedBooking.repeatType && (
                  <div className="upcoming-session-info-box">
                    <p className="upcoming-session-info-title">
                      Return this slot to Available Slots?
                    </p>

                    <label className="upcoming-session-radio-option">
                      <input
                          type="radio"
                          checked={returnSlot === true}
                          onChange={() => setReturnSlot(true)}
                          className="upcoming-session-radio-input"
                      />

                      <div>
                        <div className="upcoming-session-radio-title">
                          Return to Available Slots
                        </div>

                        <div className="upcoming-session-radio-description">
                          Other students can book this session again.
                        </div>
                      </div>
                    </label>

                    <label className="upcoming-session-radio-option">
                      <input
                          type="radio"
                          checked={returnSlot === false}
                          onChange={() => setReturnSlot(false)}
                          className="upcoming-session-radio-input"
                      />

                      <div>
                        <div className="upcoming-session-radio-title">
                          Keep Slot Closed
                        </div>

                        <div className="upcoming-session-radio-description">
                          This session will not appear in Available Slots.
                        </div>
                      </div>
                    </label>
                  </div>
              )}

              <div className="upcoming-session-modal-actions">
                <button
                  onClick={closeDeclineModal}
                  className="upcoming-session-secondary-button"
                >
                  Cancel
                </button>

                <button
                  onClick={confirmDecline}
                  className="upcoming-session-danger-button"
                >
                  Confirm Decline
                </button>
              </div>
            </div>
          </div>
        )}
      </BaseModal>

      <BaseModal
        isOpen={showCompleteModal && !!selectedCancelBooking}
        title="Complete Booking"
        onClose={closeComCompleteModal}
      >
        {selectedCancelBooking && (
          <form onSubmit={confirmComplete}>
            <div className="upcoming-session-complete-form">
              <label htmlFor="score">Score: </label>

              <input
                className="upcoming-session-score-input"
                required
                type="number"
                name="score"
                id="score"
                max={100}
                min={0}
                step={0.01}
              />

              <label htmlFor="feedback">Feedback: </label>

              <textarea
                name="feedback"
                id="feedback"
                maxLength={255}
                className="upcoming-session-feedback-input"
                required
              />
            </div>

            <div className="upcoming-session-modal-actions">
              <button
                onClick={closeComCompleteModal}
                className="upcoming-session-secondary-button"
              >
                Go Back
              </button>

              <button
                type="submit"
                className="upcoming-session-success-button"
              >
                Complete Session
              </button>
            </div>
          </form>
        )}
      </BaseModal>

      <BaseModal
        isOpen={showCancelModal && !!selectedCancelBooking}
        title="Cancle Booking"
        onClose={closeCancelModal}
      >
        {selectedCancelBooking && (
          <div>
            <div>
              <ModalTitle title="Cancel Approved Session" />

              <p className="upcoming-session-modal-description">
                This will permanently cancel{" "}
                <strong>{selectedCancelBooking.studentName}</strong>'s approved
                session.
              </p>

              <div className="upcoming-session-info-box">
                <div className="upcoming-session-info-title">
                  What happens next?
                </div>

                <div className="upcoming-session-info-list">
                  <div>• The approved session will be cancelled.</div>
                  <div>• The booking will be removed from Upcoming Sessions.</div>
                  <div className="upcoming-session-warning-text">
                    • This assessment slot will remain closed and will NOT be
                    returned to Available Slots.
                  </div>
                </div>
              </div>

              <div className="upcoming-session-modal-actions">
                <button
                  onClick={closeCancelModal}
                  className="upcoming-session-secondary-button"
                >
                  Go Back
                </button>

                <button
                  onClick={confirmCancel}
                  className="upcoming-session-danger-button"
                >
                  Cancel Session
                </button>
              </div>
            </div>
          </div>
        )}
      </BaseModal>
    </div>
  );
}

function ModalTitle({ title }) {
  return (
    <div className="upcoming-session-modal-title-row">
      <TriangleAlert size={26} color="#DC2626" />

      <h2 className="upcoming-session-modal-title">
        {title}
      </h2>
    </div>
  );
}
