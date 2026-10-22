import "./index.css";

import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from "react";

import { useNavigate } from "react-router-dom";

import {
  bookings,
  getAvailableAssessmentSlotList,
} from "@/config/request/api/assessmentBookingApi.js";

import {
  getEnabledHospitals,
  getEnabledDepartments,
  getEnabledWbaTypes,
} from "@/config/request/api/dictionaryApi.js";

import {
  BookOpen,
  CalendarDays,
  Clock,
  Filter,
  MapPin,
} from "lucide-react";

import BaseModal from "@/components/BaseModal/index.jsx";

import { sendNotification } from "@/config/request/api/sendNotificationApi.js";
import { notificationEvent } from "@/utils/sse/sseEventChannel.js";
import {Loading} from "@/components/Loading/index.jsx";
import {useUserStore} from "@/config/store/useUserStore.js";
import AssessmentSlotRepeatTypeEnum from "@/config/constant/AssessmentSlotRepeatTypeEnum.js";
import {
  EMPTY_BOOKING_FILTERS,
  buildBookingSearchParams,
  getBookingSearchError,
  formatBookingDate as formatDate,
  formatBookingTime as formatTime,
} from "@/utils/bookingSearch.js";

const DEFAULT_PAGE_QUANTITY = 10;

const EMPTY_FILTERS = EMPTY_BOOKING_FILTERS;

function extractPageData(response) {
  return (
    response?.data?.data ??
    response?.data ??
    response ??
    {}
  );
}

function extractDictionaryData(response) {
  const data =
    response?.data?.data ??
    response?.data ??
    response ??
    [];

  return Array.isArray(data) ? data : [];
}

export default function BookSlotStudent() {
  const userInfo = useUserStore((state) => state.userInfo);
  const navigate = useNavigate();
  const topRef = useRef(null);
  const [hospitalOptions, setHospitalOptions] =
    useState([]);

  const [departmentOptions, setDepartmentOptions] =
    useState([]);

  const [wbaTypeOptions, setWbaTypeOptions] =
    useState([]);

  const [filterLoading, setFilterLoading] =
    useState(false);
  const [slots, setSlots] = useState([]);
  const [pageNum, setPageNum] = useState(1);

  const [pageQuantity, setPageQuantity] =
    useState(DEFAULT_PAGE_QUANTITY);

  const [total, setTotal] = useState(0);
  const [totalPage, setTotalPage] = useState(1);
  const [hospitalFilter, setHospitalFilter] =
    useState("");

  const [departmentFilter, setDepartmentFilter] =
    useState("");

  const [typeFilter, setTypeFilter] =
    useState("");
  const [supervisorFilter, setSupervisorFilter] = useState("");
  const [startTimeFilter, setStartTimeFilter] = useState("");
  const [endTimeFilter, setEndTimeFilter] = useState("");

  const [appliedFilters, setAppliedFilters] =
    useState(EMPTY_FILTERS);
  const [selectedSlot, setSelectedSlot] =
    useState(null);

  const [
    selectedBookingTime,
    setSelectedBookingTime,
  ] = useState(null);

  const [showBookingModal, setShowBookingModal] =
    useState(false);

  const [bookingLoading, setBookingLoading] =
    useState(false);

  const [loading, setLoading] = useState(false);

  const [successMessage, setSuccessMessage] =
    useState("");

  const [errorMessage, setErrorMessage] =
    useState("");


  const buildParams = useCallback(
    ({
      targetPage = pageNum,
      filters = appliedFilters,
      quantity = pageQuantity,
    } = {}) => {
      return buildBookingSearchParams(filters, targetPage, quantity);
    },
    [
      appliedFilters,
      pageNum,
      pageQuantity,
    ]
  );

  /**
   * Load filter dropdown options.
   */
  const fetchFilterOptions = useCallback(
    async () => {
      setFilterLoading(true);

      try {
        const [
          hospitalsResponse,
          departmentsResponse,
          wbaTypesResponse,
        ] = await Promise.all([
          getEnabledHospitals(),
          getEnabledDepartments(),
          getEnabledWbaTypes(),
        ]);

        setHospitalOptions(
          extractDictionaryData(
            hospitalsResponse
          )
        );

        setDepartmentOptions(
          extractDictionaryData(
            departmentsResponse
          )
        );

        setWbaTypeOptions(
          extractDictionaryData(
            wbaTypesResponse
          )
        );
      } catch (error) {
        console.error(
          "Failed to load filter options:",
          error
        );

        setHospitalOptions([]);
        setDepartmentOptions([]);
        setWbaTypeOptions([]);

        setErrorMessage(
          error?.response?.data?.message ||
            error?.response?.data?.msg ||
            error?.message ||
            "Failed to load filter options."
        );
      } finally {
        setFilterLoading(false);
      }
    },
    []
  );

  /**
   * Load available slots from the backend.
   */
  const fetchAvailableSlots = useCallback(
    async (requestParams) => {
      Loading.show();
      setLoading(true);
      setErrorMessage("");

      try {
        const response =
          await getAvailableAssessmentSlotList(
            requestParams
          );

        const pageData =
          extractPageData(response);

        const records = Array.isArray(
          pageData.records
        )
          ? pageData.records
          : [];

        const currentPage =
          Number(pageData.current) ||
          Number(requestParams?.pageNum) ||
          1;

        const currentPageSize =
          Number(pageData.size) ||
          Number(
            requestParams?.pageQuantity
          ) ||
          DEFAULT_PAGE_QUANTITY;

        const totalRecords =
          Number(pageData.total) || 0;

        const pages =
          Number(pageData.pages) || 0;

        setSlots(records);
        setPageNum(currentPage);
        setPageQuantity(currentPageSize);
        setTotal(totalRecords);

        setTotalPage(
          pages > 0 ? pages : 1
        );
      } catch (error) {
        console.error(
          "Failed to load available slots:",
          error
        );

        setSlots([]);
        setTotal(0);
        setTotalPage(1);

        setErrorMessage(
          error?.response?.data?.message ||
            error?.response?.data?.msg ||
            error?.message ||
            "Failed to load available slots."
        );
      } finally {
        setLoading(false);
        Loading.hide();
      }
    },
    []
  );


  useEffect(() => {
    fetchFilterOptions();

    fetchAvailableSlots({
      pageNum: 1,
      pageQuantity:
        DEFAULT_PAGE_QUANTITY,
      isOrderByAvailableTime: true,
    });
  }, [
    fetchFilterOptions,
    fetchAvailableSlots,
  ]);


  const getAvailableTimes = (slot) => {
    if (
      Array.isArray(
        slot?.availableTimeList
      ) &&
      slot.availableTimeList.length > 0
    ) {
      return slot.availableTimeList
        .filter(
          (time) =>
            Array.isArray(time) &&
            time.length >= 2 &&
            time[0] &&
            time[1]
        )
        .map((time) => ({
          startTime: time[0],
          endTime: time[1],
        }));
    }

    const startTime =
      slot?.nextAvailableTime ||
      slot?.startTime;

    const endTime = slot?.endTime;

    if (!startTime || !endTime) {
      return [];
    }

    return [
      {
        startTime,
        endTime,
      },
    ];
  };

  /**
   * Check whether a slot contains multiple occurrences.
   */
  const isRepeatSlot = (slot) => {
    return AssessmentSlotRepeatTypeEnum.NO_REPEAT.code !== slot?.repeatType
  };


  const getSupervisorId = (slot) => {
    return (
      slot?.creator ??
      slot?.supervisorId ??
      slot?.creatorId ??
      slot?.creatorUserId ??
      slot?.userId ??
      slot?.createdBy ??
      null
    );
  };

  /**
   * Open supervisor profile.
   */
  const openSupervisorProfile = (slot) => {
    const supervisorId =
      getSupervisorId(slot);

    if (!supervisorId) {
      setErrorMessage(
        "Supervisor profile is not available."
      );

      return;
    }

    navigate(
      `/home/student/supervisor-profile?id=${supervisorId}`
    );
  };

  /**
   * Apply selected filters.
   */
  const handleSearch = () => {
    if (loading) {
      return;
    }

    const newFilters = {
      hospital: hospitalFilter,
      department: departmentFilter,
      type: typeFilter,
      supervisorName: supervisorFilter,
      startTime: startTimeFilter,
      endTime: endTimeFilter,
    };

    const validationError = getBookingSearchError(newFilters);
    if (validationError) {
      setErrorMessage(validationError);
      return;
    }

    setAppliedFilters(newFilters);
    setPageNum(1);

    fetchAvailableSlots(
      buildParams({
        targetPage: 1,
        filters: newFilters,
      })
    );
  };

  /**
   * Reset filters.
   */
  const handleReset = () => {
    if (loading) {
      return;
    }

    setHospitalFilter("");
    setDepartmentFilter("");
    setTypeFilter("");
    setSupervisorFilter("");
    setStartTimeFilter("");
    setEndTimeFilter("");

    setAppliedFilters(EMPTY_FILTERS);
    setPageNum(1);

    fetchAvailableSlots({
      pageNum: 1,
      pageQuantity,
      isOrderByAvailableTime: true,
    });
  };


  const handlePageChange = (newPageNum) => {
    if (
      loading ||
      newPageNum < 1 ||
      newPageNum > totalPage ||
      newPageNum === pageNum
    ) {
      return;
    }

    setPageNum(newPageNum);

    fetchAvailableSlots(
      buildParams({
        targetPage: newPageNum,
        filters: appliedFilters,
      })
    );

    topRef.current?.scrollIntoView({
      behavior: "smooth",
      block: "start",
    });
  };

  /**
   * Open booking modal.
   */
  const openBookingModal = (slot) => {
    const availableTimes =
      getAvailableTimes(slot);

    setSelectedSlot(slot);

    /*
     * Normal slots automatically select their only time.
     * Repeat slots require manual selection.
     */
    if (availableTimes.length === 1) {
      setSelectedBookingTime(
        availableTimes[0]
      );
    } else {
      setSelectedBookingTime(null);
    }

    setShowBookingModal(true);
  };

  /**
   * Close booking modal.
   */
  const closeBookingModal = () => {
    setSelectedSlot(null);
    setSelectedBookingTime(null);
    setShowBookingModal(false);
  };


  /**
   * Handle repeat slot time selection.
   */
  const handleBookingTimeChange = (
    event
  ) => {
    const selectedValue =
      event.target.value;

    if (!selectedValue || !selectedSlot) {
      setSelectedBookingTime(null);
      return;
    }

    const selectedTime =
      getAvailableTimes(
        selectedSlot
      ).find(
        (time) =>
          `${time.startTime}|${time.endTime}` ===
          selectedValue
      );

    setSelectedBookingTime(
      selectedTime || null
    );
  };

  /**
   * Confirm selected booking.
   */
  const confirmBooking = async () => {
    if (
      !selectedSlot ||
      bookingLoading
    ) {
      return;
    }

    const availableTimes =
      getAvailableTimes(selectedSlot);

    const bookingTime =
      selectedBookingTime ||
      availableTimes[0];

    if (!bookingTime) {
      setErrorMessage(
        "Please select an available time before confirming your booking."
      );

      return;
    }

    setBookingLoading(true);
    setErrorMessage("");

    const bookingStartTime =
      bookingTime.startTime;

    const bookingEndTime =
      bookingTime.endTime;

    const wbaType =
      selectedSlot.type ||
      selectedSlot.wbaType;

    const requestBody = {
      assessmentSlotId: selectedSlot.id,
      startTime: bookingStartTime,
      endTime: bookingEndTime,
      type: wbaType,
      slotVersion: selectedSlot.version,
    };

    const supervisorId =
      getSupervisorId(selectedSlot);

    try {
      await bookings(requestBody);

      closeBookingModal();

      setSuccessMessage(
        "Your assessment session has been booked successfully."
      );

      await fetchAvailableSlots(
        buildParams({
          targetPage: pageNum,
          filters: appliedFilters,
        })
      );

      if (supervisorId) {
        const sessionType =
          wbaType || "assessment";

        const sessionDate =
          formatDate(bookingStartTime);

        const sessionTime =
          formatTime(bookingStartTime);

        await sendNotification({
          receiverId: supervisorId,
          eventName: notificationEvent,
          eventContent:
              `You have a new booking request for the ` +
              `${sessionType} session on ${sessionDate} ` +
              `at ${sessionTime}.`,
          isSendEmail: true,
        })

        await sendNotification({
          receiverId: userInfo?.id,
          eventName: notificationEvent,
          eventContent:
              `You have booked for the ` +
              `${sessionType} session on ${sessionDate} ` +
              `at ${sessionTime}. You cannot cancel within 48 hours of the start time`,
          isSendEmail: true,
        })
      }
    } catch (error) {
      console.error(
        "Failed to book slot:",
        error
      );

      setErrorMessage(
        error?.response?.data?.message ||
          error?.response?.data?.msg ||
          error?.message ||
          "Failed to book the selected slot."
      );
    } finally {
      setBookingLoading(false);
    }
  };

  return (
    <div>
      <main className="book-slot-main">
        <div
          ref={topRef}
          className="book-slot-container"
        >
          <h1 className="book-slot-title">
            Available Slots
          </h1>

          <p className="book-slot-subtitle">
            Browse and book available assessment
            sessions
          </p>

          <div className="book-slot-search-details">
            <label className="book-slot-search-field">
              Supervisor name
              <input type="search" value={supervisorFilter} maxLength={50}
                placeholder="All supervisors" disabled={loading}
                onChange={(event) => setSupervisorFilter(event.target.value)} />
            </label>
            <label className="book-slot-search-field">
              From (Sydney time)
              <input type="datetime-local" value={startTimeFilter} disabled={loading}
                onChange={(event) => setStartTimeFilter(event.target.value)} />
            </label>
            <label className="book-slot-search-field">
              To (Sydney time)
              <input type="datetime-local" value={endTimeFilter} disabled={loading}
                onChange={(event) => setEndTimeFilter(event.target.value)} />
            </label>
            <p className="book-slot-search-hint">
              Times use Australia/Sydney. Without dates, search covers the next 30 days.
              You can search up to 90 days at a time.
            </p>
          </div>

          {/* Backend filter toolbar */}
          <div className="book-slot-filters">
            <Filter
              size={18}
              color="#64748B"
            />

            <select
              className="book-slot-select"
              value={hospitalFilter}
              onChange={(event) =>
                setHospitalFilter(
                  event.target.value
                )
              }
              disabled={filterLoading}
            >
              <option value="">
                {filterLoading
                  ? "Loading hospitals..."
                  : "All Hospitals"}
              </option>

              {hospitalOptions.map(
                (hospital) => (
                  <option
                    key={
                      hospital.id ??
                      hospital.name
                    }
                    value={hospital.name}
                  >
                    {hospital.name}
                  </option>
                )
              )}
            </select>

            <select
              className="book-slot-select"
              value={departmentFilter}
              onChange={(event) =>
                setDepartmentFilter(
                  event.target.value
                )
              }
              disabled={filterLoading}
            >
              <option value="">
                {filterLoading
                  ? "Loading departments..."
                  : "All Departments"}
              </option>

              {departmentOptions.map(
                (department) => (
                  <option
                    key={
                      department.id ??
                      department.name
                    }
                    value={department.name}
                  >
                    {department.name}
                  </option>
                )
              )}
            </select>

            <select
              className="book-slot-select"
              value={typeFilter}
              onChange={(event) =>
                setTypeFilter(
                  event.target.value
                )
              }
              disabled={filterLoading}
            >
              <option value="">
                {filterLoading
                  ? "Loading WBA types..."
                  : "All WBA Types"}
              </option>

              {wbaTypeOptions.map(
                (type) => (
                  <option
                    key={
                      type.id ??
                      type.name
                    }
                    value={type.name}
                  >
                    {type.name}
                  </option>
                )
              )}
            </select>

            <button
              type="button"
              className="book-slot-filter-button"
              onClick={handleSearch}
              disabled={
                loading ||
                filterLoading
              }
            >
              {loading
                ? "Searching..."
                : "Search"}
            </button>

            <button
              type="button"
              className="book-slot-reset-button"
              onClick={handleReset}
              disabled={loading}
            >
              Reset
            </button>

            <span className="book-slot-count">
              {total}{" "}
              {total === 1
                ? "slot"
                : "slots"}{" "}
              available
            </span>
          </div>

          {/* Available slot list */}
          <div className="book-slot-list">
            {slots.length === 0 ? (
              <div className="book-slot-empty">
                <CalendarDays size={34} />

                <h3>
                  No available slots found
                </h3>

                <p>
                  Try changing your filter
                  options.
                </p>
              </div>
            ) : (
              slots.map((slot) => {
                const slotDate =
                    slot.nextAvailableTime ||
                    slot.startTime;

                const slotType =
                    slot.type ||
                    slot.wbaType;

                const repeatSlot =
                    isRepeatSlot(slot);

                return (
                    <div
                        key={slot.id}
                        className="book-slot-card"
                    >
                      <div className="book-slot-card-content">
                        <div className="book-slot-top-line">
                          <button
                              type="button"
                              className="book-slot-avatar-button"
                              aria-label={`View ${
                                  slot.creatorName ||
                                  slot.supervisorName ||
                                  "supervisor"
                              } profile`}
                              onClick={(event) => {
                                event.stopPropagation();

                                openSupervisorProfile(
                                    slot
                                );
                              }}
                          >
                            {slot.creatorAvatar ? (
                                <img
                                    src={
                                      slot.creatorAvatar
                                    }
                                    alt={
                                        slot.creatorName ||
                                        slot.supervisorName ||
                                        "Supervisor profile"
                                    }
                                    className="book-slot-avatar-image"
                                />
                            ) : (
                                (
                                    slot.creatorName ||
                                    slot.supervisorName ||
                                    "S"
                                )
                                    .charAt(0)
                                    .toUpperCase()
                            )}
                          </button>

                          <h3
                              className="book-slot-name"
                              onClick={() =>
                                  openSupervisorProfile(
                                      slot
                                  )
                              }
                          >
                            {slot.creatorName ||
                                slot.supervisorName ||
                                "Supervisor"}
                          </h3>

                          {slot.title && (
                              <span className="book-slot-role">
                      {slot.title}
                    </span>
                          )}

                          {slotType && (
                              <span className="book-slot-blue-badge">
                      {slotType}
                    </span>
                          )}
                        </div>

                        <div className="book-slot-row">
                  <span className="book-slot-info">
                    <CalendarDays
                        size={14}
                    />

                    {formatDate(
                            slotDate
                        ) ||
                        "Date unavailable"}
                  </span>

                          <span className="book-slot-info">
                    <Clock size={14} />

                            {formatTime(
                                    slot.startTime ||
                                    slotDate
                                ) ||
                                "Time unavailable"}

                            {" - "}

                            {formatTime(
                                    slot.endTime
                                ) ||
                                "Time unavailable"}
                  </span>

                          <span className="book-slot-info">
                    <MapPin size={14} />

                            {slot.location ||
                                "No location"}
                  </span>

                          <span className="book-slot-info">
                    <BookOpen size={14} />

                            {slot.department ||
                                "No department"}
                  </span>
                        </div>

                        <div className="book-slot-meta">
                  <span>
                    {slot.hospital ||
                        "No hospital"}
                  </span>
                        </div>
                      </div>

                      <button
                          type="button"
                          className="book-slot-book-button"
                          onClick={() =>
                              openBookingModal(slot)
                          }
                      >
                        {repeatSlot
                            ? "Select Time"
                            : "Book"}
                      </button>
                    </div>
                );
              })
            )}
          </div>

          {/* Booking confirmation modal */}
          <BaseModal
            isOpen={
              showBookingModal &&
              Boolean(selectedSlot)
            }
            title={
              selectedSlot &&
              isRepeatSlot(selectedSlot)
                ? "Select Time"
                : "Confirm Booking"
            }
            onClose={closeBookingModal}
          >
            {selectedSlot && (
              <div>
                <div className="book-slot-modal-header">
                  <h2 className="book-slot-modal-title">
                    {isRepeatSlot(
                      selectedSlot
                    )
                      ? "Select Time"
                      : "Confirm Booking"}
                  </h2>

                  <p className="book-slot-modal-subtitle">
                    {isRepeatSlot(
                      selectedSlot
                    )
                      ? "Select an available session time before confirming"
                      : "Review session details before confirming"}
                  </p>
                </div>

                <div className="book-slot-modal-body">
                  <div className="book-slot-modal-info-box">
                    <p className="book-slot-modal-line">
                      <strong>
                        {selectedSlot.creatorName ||
                          selectedSlot.supervisorName ||
                          "Supervisor"}
                      </strong>
                    </p>

                    {isRepeatSlot(
                      selectedSlot
                    ) ? (
                      <div className="book-slot-time-selection">
                        <label
                          className="book-slot-time-label"
                          htmlFor="booking-time"
                        >
                          Available time
                        </label>

                        <select
                          id="booking-time"
                          className="book-slot-time-select"
                          value={
                            selectedBookingTime
                              ? `${selectedBookingTime.startTime}|${selectedBookingTime.endTime}`
                              : ""
                          }
                          onChange={
                            handleBookingTimeChange
                          }
                          disabled={
                            bookingLoading
                          }
                        >
                          <option value="">
                            Select a date and
                            time
                          </option>

                          {getAvailableTimes(
                            selectedSlot
                          ).map((time) => (
                            <option
                              key={`${time.startTime}-${time.endTime}`}
                              value={`${time.startTime}|${time.endTime}`}
                            >
                              {formatDate(
                                time.startTime
                              )}{" "}
                              —{" "}
                              {formatTime(
                                time.startTime
                              )}{" "}
                              to{" "}
                              {formatTime(
                                time.endTime
                              )}
                            </option>
                          ))}
                        </select>
                      </div>
                    ) : (
                      <p className="book-slot-modal-line">
                        {selectedBookingTime ? (
                          <>
                            {formatDate(
                              selectedBookingTime.startTime
                            )}{" "}
                            at{" "}
                            {formatTime(
                              selectedBookingTime.startTime
                            )}
                            {" - "}
                            {formatTime(
                              selectedBookingTime.endTime
                            )}
                          </>
                        ) : (
                          "Time unavailable"
                        )}
                      </p>
                    )}

                    <p className="book-slot-modal-line">
                      {selectedSlot.hospital ||
                        "No hospital"}
                    </p>

                    <p className="book-slot-modal-line">
                      {selectedSlot.location ||
                        "No location"}
                    </p>

                    <p className="book-slot-modal-line">
                      {selectedSlot.department ||
                        "No department"}
                    </p>

                    {(selectedSlot.type ||
                      selectedSlot.wbaType) && (
                      <p className="book-slot-modal-line">
                        <strong>
                          WBA Type:
                        </strong>{" "}
                        {selectedSlot.type ||
                          selectedSlot.wbaType}
                      </p>
                    )}
                  </div>
                </div>

                <div className="book-slot-modal-actions">
                  <button
                    type="button"
                    className="book-slot-cancel-button"
                    onClick={
                      closeBookingModal
                    }
                    disabled={bookingLoading}
                  >
                    Cancel
                  </button>

                  <button
                    type="button"
                    className={`book-slot-confirm-button ${
                      bookingLoading ||
                      (
                        isRepeatSlot(
                          selectedSlot
                        ) &&
                        !selectedBookingTime
                      )
                        ? "is-disabled"
                        : ""
                    }`}
                    onClick={confirmBooking}
                    disabled={
                      bookingLoading ||
                      (
                        isRepeatSlot(
                          selectedSlot
                        ) &&
                        !selectedBookingTime
                      )
                    }
                  >
                    {bookingLoading
                      ? "Booking..."
                      : "Confirm Booking"}
                  </button>
                </div>
              </div>
            )}
          </BaseModal>

          {/* Booking success modal */}
          <BaseModal
            isOpen={Boolean(
              successMessage
            )}
            title="Booking Successful"
            onClose={() =>
              setSuccessMessage("")
            }
          >
            <div className="book-slot-message-modal">
              <div className="book-slot-success-icon">
                ✓
              </div>

              <h2 className="book-slot-message-title">
                Booking Successful
              </h2>

              <p className="book-slot-message-text">
                {successMessage}
              </p>

              <p className="book-slot-message-note">
                Please go to My Bookings to
                check whether your booking has
                been approved by your
                supervisor.
              </p>

              <button
                type="button"
                className="book-slot-message-button"
                onClick={() =>
                  setSuccessMessage("")
                }
              >
                Done
              </button>
            </div>
          </BaseModal>


          <BaseModal
            isOpen={Boolean(
              errorMessage
            )}
            title="Request Failed"
            onClose={() =>
              setErrorMessage("")
            }
          >
            <div className="book-slot-message-modal">
              <div className="book-slot-error-icon">
                !
              </div>

              <h2 className="book-slot-message-title">
                Request Failed
              </h2>

              <p className="book-slot-message-text">
                {errorMessage}
              </p>

              <button
                type="button"
                className="book-slot-error-button"
                onClick={() =>
                  setErrorMessage("")
                }
              >
                Close
              </button>
            </div>
          </BaseModal>

          {/* Backend pagination */}
          {total > 0 && (
            <div className="book-slot-pagination">
              <button
                type="button"
                className={`book-slot-page-button ${
                  pageNum <= 1
                    ? "is-disabled"
                    : ""
                }`}
                disabled={
                  pageNum <= 1 ||
                  loading
                }
                onClick={() =>
                  handlePageChange(
                    pageNum - 1
                  )
                }
              >
                Previous
              </button>

              <span className="book-slot-page-text">
                Page {pageNum} of{" "}
                {totalPage}
              </span>

              <button
                type="button"
                className={`book-slot-page-button ${
                  pageNum >= totalPage
                    ? "is-disabled"
                    : ""
                }`}
                disabled={
                  pageNum >= totalPage ||
                  loading
                }
                onClick={() =>
                  handlePageChange(
                    pageNum + 1
                  )
                }
              >
                Next
              </button>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
