import "./index.css";
import {useEffect, useRef, useState} from "react";
import { Link, useSearchParams } from "react-router-dom";
import {CalendarDays, CalendarPlus, Clock, MapPin, XCircle,} from "lucide-react";
import {cancelBooking, getMyBookings,} from "@/config/request/api/assessmentBookingApi.js";
import {
  addBookingToGoogleCalendar,
  connectGoogleCalendar,
  getGoogleCalendarStatus,
} from "@/config/request/api/googleCalendarApi.js";
import BaseModal from "@/components/BaseModal/index.jsx";
import {notify} from "@/utils/notify.js";
import {Loading} from "@/components/Loading/index.jsx";

export default function MyBookingsStudent() {
  const [bookings, setBookings] = useState([]);
  const [errorMessage, setErrorMessage] = useState("");
  const [bookingsLoaded, setBookingsLoaded] = useState(false);
  const [searchParams] = useSearchParams();
  const requestedBooking = searchParams.get("booking") || "";
  const focusedBooking = /^\d+$/.test(requestedBooking) ? requestedBooking : "";
  const focusedCard = useRef(null);

  useEffect(() => {
    if (focusedCard.current) {
      focusedCard.current.scrollIntoView({ block: "center" });
      focusedCard.current.focus({ preventScroll: true });
    }
  }, [bookings, focusedBooking]);

  const [showCancelModal, setShowCancelModal] = useState(false);

  const [
    selectedCancelBooking,
    setSelectedCancelBooking,
  ] = useState(null);

  const [cancelLoading, setCancelLoading] =
    useState(false);

  const [calendarLoadingId, setCalendarLoadingId] =
    useState(null);

  const [calendarConnected, setCalendarConnected] =
    useState(false);

  const [calendarStatusLoading, setCalendarStatusLoading] =
    useState(true);

  const [calendarConnectLoading, setCalendarConnectLoading] =
    useState(false);

  const [addedCalendarBookingIds, setAddedCalendarBookingIds] =
    useState([]);

  const startGetMyBookings = () => {
    Loading.show()

    getMyBookings(1, -1)
      .then((data) => {
        setBookings(data.data.records);
        setBookingsLoaded(true);
      })
      .catch((err) => {
        setErrorMessage(
          err.message || "Failed to load bookings."
        );
      })
      .finally(() => {
        Loading.hide()
      });
  };

  const startGetGoogleCalendarStatus = () => {
    setTimeout(() => {
      setCalendarStatusLoading(true);
    })

    getGoogleCalendarStatus()
      .then((result) => {
        const connected =
          result?.data?.connected ??
          result?.data?.data?.connected ??
          false;

        setCalendarConnected(Boolean(connected));
      })
      .catch((err) => {
        console.error(
          "Failed to load Google Calendar status:",
          err
        );

        setCalendarConnected(false);
      })
      .finally(() => {
        setCalendarStatusLoading(false);
      });
  };

  useEffect(() => {
    startGetMyBookings();
    startGetGoogleCalendarStatus();
  }, []);

  const parseDateTime = (value) => {
    if (!value) {
      return null;
    }

    const date = new Date(
      value.replace?.(" ", "T") || value
    );

    if (Number.isNaN(date.getTime())) {
      return null;
    }

    return date;
  };

  const formatDate = (value) => {
    const date = parseDateTime(value);

    if (!date) {
      return "";
    }

    return date.toLocaleDateString("en-AU", {
      weekday: "long",
      day: "numeric",
      month: "short",
    });
  };

  const formatTime = (value) => {
    const date = parseDateTime(value);

    if (!date) {
      return "";
    }

    return date.toLocaleTimeString("en-AU", {
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    });
  };

  const getStatus = (status) => {
    switch (status) {
      case 0:
        return "Pending";
      case 1:
        return "Confirmed";
      case 2:
        return "Completed";
      case 3:
        return "Declined";
      case 4:
        return "Cancelled";
      default:
        return "Unknown";
    }
  };

  const getStatusClassName = (status) => {
    switch (status) {
      case 1:
        return "my-bookings-status confirmed";
      case 2:
        return "my-bookings-status completed";
      case 3:
        return "my-bookings-status declined";
      case 4:
        return "my-bookings-status cancelled";
      default:
        return "my-bookings-status pending";
    }
  };

  const canCancelBooking = (booking) => {
    return booking.canManage === true;
  };

  const handleConnectGoogleCalendar = () => {
    setCalendarConnectLoading(true);

    connectGoogleCalendar(window.location.origin + window.location.pathname)
      .then((result) => {
        window.location.href = result?.data?.authorizationUrl;
      })
      .finally(() => {
        setCalendarConnectLoading(false);
      });
  };

  const handleAddBookingToCalendar = (booking) => {
    const bookingId =
      booking.bookingId || booking.id;

    if (!bookingId) {
      notify.error("Booking ID is unavailable.");
      return;
    }

    setCalendarLoadingId(bookingId);
    setErrorMessage("");

    addBookingToGoogleCalendar(bookingId)
      .then(() => {
        setAddedCalendarBookingIds((previousIds) => {
          if (previousIds.includes(bookingId)) {
            return previousIds;
          }

          return [...previousIds, bookingId];
        });

        notify.success(
          "Booking added to Google Calendar successfully"
        );
      })
      .catch((err) => {
        const message =
          err?.message ||
          "Failed to add booking to Google Calendar.";

        setErrorMessage(message);
      })
      .finally(() => {
        setCalendarLoadingId(null);
      });
  };

  const openCancelModal = (booking) => {
    setSelectedCancelBooking(booking);
    setShowCancelModal(true);
    setErrorMessage("");
  };

  const closeCancelModal = () => {
    setSelectedCancelBooking(null);
    setShowCancelModal(false);
    setCancelLoading(false);
  };

  const confirmCancelBooking = () => {
    if (!selectedCancelBooking) {
      return;
    }

    setCancelLoading(true);
    setErrorMessage("");

    const cancelId =
      selectedCancelBooking.bookingId ||
      selectedCancelBooking.id;

    cancelBooking(cancelId)
      .then(() => {
        setBookings((previousBookings) =>
          previousBookings.map(booking => (booking.bookingId || booking.id) === cancelId
            ? { ...booking, status: 4, canManage: false } : booking)
        );

        setAddedCalendarBookingIds((previousIds) =>
          previousIds.filter(
            (bookingId) => bookingId !== cancelId
          )
        );

        notify.success(
          "Booking cancelled successfully"
        );

        closeCancelModal();
      })
      .catch((err) => {
        setErrorMessage(
          err.message ||
            "Failed to cancel booking."
        );

        setCancelLoading(false);
      });
  };

  return (
    <div>
      <main className="my-bookings-main">
        <div className="my-bookings-container">
          <h1 className="my-bookings-title">
            My Bookings
          </h1>

          <p className="my-bookings-subtitle">
            Your assessment bookings and cancelled requests
          </p>

          {errorMessage && (
            <div className="my-bookings-error-box">
              {errorMessage}
            </div>
          )}

          {focusedBooking && bookingsLoaded && !errorMessage && !bookings.some(booking => String(booking.bookingId || booking.id) === focusedBooking) && <p className="my-bookings-error-box" role="status">This booking is no longer in your available records. <Link to="/home/student/assessment-plan">Refresh your assessment plan</Link> to check its latest status.</p>}

          <div className="my-bookings-list">
            {bookings.map((booking) => {
              const bookingId =
                booking.bookingId || booking.id;

              const isCalendarLoading =
                calendarLoadingId === bookingId;

              const isAddedToCalendar =
                addedCalendarBookingIds.includes(
                  bookingId
                );

              return (
                <div
                  key={bookingId}
                  className={`my-bookings-card${String(bookingId) === focusedBooking ? " my-bookings-focused-card" : ""}`}
                  ref={String(bookingId) === focusedBooking ? focusedCard : null}
                  tabIndex={String(bookingId) === focusedBooking ? -1 : undefined}
                  aria-current={String(bookingId) === focusedBooking ? "true" : undefined}
                >
                  <div className="my-bookings-card-content">
                    {String(bookingId) === focusedBooking && <p className="my-bookings-focus-reference">Opened from your assessment plan · Reference: {bookingId}</p>}
                    <div className="my-bookings-top-line">
                      <h3 className="my-bookings-name">
                        {booking.supervisorName ||
                          booking.creatorName ||
                          "Supervisor"}
                      </h3>

                      {booking.supervisorTitle && (
                        <span className="my-bookings-role">
                          {booking.supervisorTitle}
                        </span>
                      )}

                      {(booking.type ||
                        booking.wbaType) && (
                        <span className="my-bookings-type-badge">
                          {booking.type ||
                            booking.wbaType}
                        </span>
                      )}
                    </div>

                    <div className="my-bookings-row">
                      <span className="my-bookings-info">
                        <CalendarDays size={14} />

                        {formatDate(
                          booking.startTime
                        )}
                      </span>

                      <span className="my-bookings-info">
                        <Clock size={14} />

                        {formatTime(
                          booking.startTime
                        )}
                      </span>

                      <span className="my-bookings-info">
                        <MapPin size={14} />

                        {booking.hospital ||
                          booking.location ||
                          "No location"}
                      </span>
                    </div>
                  </div>

                  <div className="my-bookings-actions">
                    <span
                      className={getStatusClassName(
                        booking.status
                      )}
                    >
                      {getStatus(booking.status)}
                    </span>

                    {booking.status === 1 && booking.managementRestriction && <small>{booking.managementRestriction}</small>}

                    {booking.status === 1 && (
                      <>
                        {calendarStatusLoading ? (
                          <button
                            type="button"
                            className="my-bookings-calendar-button is-disabled"
                            disabled
                          >
                            <CalendarPlus size={15} />
                            Checking...
                          </button>
                        ) : !calendarConnected ? (
                          <button
                            type="button"
                            className={`my-bookings-calendar-button ${
                              calendarConnectLoading
                                ? "is-disabled"
                                : ""
                            }`}
                            onClick={
                              handleConnectGoogleCalendar
                            }
                            disabled={
                              calendarConnectLoading
                            }
                          >
                            <CalendarPlus size={15} />

                            {calendarConnectLoading
                              ? "Connecting..."
                              : "Connect Calendar"}
                          </button>
                        ) : (
                          <button
                            type="button"
                            className={`my-bookings-calendar-button ${
                              isCalendarLoading ||
                              isAddedToCalendar
                                ? "is-disabled"
                                : ""
                            }`}
                            onClick={() =>
                              handleAddBookingToCalendar(
                                booking
                              )
                            }
                            disabled={
                              isCalendarLoading ||
                              isAddedToCalendar
                            }
                          >
                            <CalendarPlus size={15} />

                            {isCalendarLoading
                              ? "Adding..."
                              : isAddedToCalendar
                                ? "Added"
                                : "Add to Calendar"}
                          </button>
                        )}
                      </>
                    )}

                    {canCancelBooking(booking) && (
                      <button
                        type="button"
                        className="my-bookings-cancel-button"
                        onClick={() =>
                          openCancelModal(booking)
                        }
                      >
                        Cancel
                      </button>
                    )}
                  </div>
                </div>
              );
            })}

            {bookings.length === 0 &&
              !errorMessage && (
                <div className="my-bookings-empty-box">
                  No bookings yet. Book an available
                  slot to see it here.
                </div>
              )}
          </div>
        </div>
      </main>

      <BaseModal
        isOpen={
          showCancelModal &&
          Boolean(selectedCancelBooking)
        }
        title="Cancel Booking"
        onClose={closeCancelModal}
      >
        {selectedCancelBooking && (
          <div>
            <div className="my-bookings-modal">
              <div className="my-bookings-warning-icon">
                <XCircle size={34} />
              </div>

              <h2 className="my-bookings-modal-title">
                Cancel Booking?
              </h2>

              <p className="my-bookings-modal-text">
                Are you sure you want to cancel this
                booking?
              </p>

              <div className="my-bookings-modal-info-box">
                <strong>
                  {selectedCancelBooking.supervisorName ||
                    selectedCancelBooking.creatorName ||
                    "Supervisor"}
                </strong>

                <div className="my-bookings-modal-date">
                  {formatDate(
                    selectedCancelBooking.startTime
                  )}{" "}
                  at{" "}
                  {formatTime(
                    selectedCancelBooking.startTime
                  )}
                </div>

                <div className="my-bookings-modal-location">
                  {selectedCancelBooking.hospital ||
                    selectedCancelBooking.location ||
                    "No location"}
                </div>
              </div>

              <p className="my-bookings-modal-note">
                This booking will be removed from My
                Bookings.
              </p>

              <div className="my-bookings-modal-actions">
                <button
                  type="button"
                  className="my-bookings-modal-secondary-button"
                  onClick={closeCancelModal}
                  disabled={cancelLoading}
                >
                  Go Back
                </button>

                <button
                  type="button"
                  className={`my-bookings-modal-danger-button ${
                    cancelLoading
                      ? "is-disabled"
                      : ""
                  }`}
                  onClick={confirmCancelBooking}
                  disabled={cancelLoading}
                >
                  {cancelLoading
                    ? "Cancelling..."
                    : "Cancel Booking"}
                </button>
              </div>
            </div>
          </div>
        )}
      </BaseModal>
    </div>
  );
}
