import { useEffect, useState } from "react";
import { Filter, Search, RotateCcw } from "lucide-react";
import {
  getRegistrarAllBookings,
} from "@/config/request/api/assessmentBookingApi.js";
import "./index.css";
import {Loading} from "@/components/Loading/index.jsx";
import {getEnabledHospitals} from "@/config/request/api/dictionaryApi.js";

const PAGE_QUANTITY = 10;

const STATUS_MAP = {
  0: "Pending",
  1: "Confirmed",
  2: "Completed",
  3: "Declined",
  4: "Canceled",
};

const EMPTY_FILTERS = {
  keyword: "",
  hospital: "",
  status: "",
};

function AllBookingsAdmin() {
  const [bookings, setBookings] = useState([]);
  const [totalRecords, setTotalRecords] = useState(0);

  const [draftFilters, setDraftFilters] = useState(EMPTY_FILTERS);
  const [appliedFilters, setAppliedFilters] = useState(EMPTY_FILTERS);
  const [hospitalSelectList, setHospitalSelectList] = useState([]);

  const [pageNum, setPageNum] = useState(1);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  const totalPages = Math.max(
    1,
    Math.ceil(totalRecords / PAGE_QUANTITY)
  );

  const buildQueryParams = (targetPage, filters) => {
    const params = {
      pageNum: targetPage,
      pageQuantity: PAGE_QUANTITY,
    };

    const keyword = filters.keyword.trim();
    const hospital = filters.hospital.trim();

    if (keyword) {
      params.keyword = keyword;
    }

    if (hospital) {
      params.hospital = hospital;
    }

    if (filters.status !== "") {
      params.status = Number(filters.status);
    }

    return params;
  };

  const loadBookings = async (
    targetPage = 1,
    filters = EMPTY_FILTERS
  ) => {
    Loading.show()

    setTimeout(() => {
      setLoading(true);
      setErrorMessage("");
    }, 0);

    try {
      const params = buildQueryParams(targetPage, filters);
      const response = await getRegistrarAllBookings(params);
      const pageData = response.data;

      setBookings(pageData?.records ?? []);
      setTotalRecords(Number(pageData?.total) || 0);
      setPageNum(targetPage);
    } catch (error) {
      console.error("Failed to load registrar bookings:", error);

      setBookings([]);
      setTotalRecords(0);
      setErrorMessage(error?.message || "Failed to load bookings.");
    } finally {
      setLoading(false);
      Loading.hide()
    }
  };

  useEffect(() => {
    loadBookings(1, EMPTY_FILTERS);
    getEnabledHospitals().then(res => setHospitalSelectList(res.data))
  }, []);

  const handleFilterChange = (event) => {
    const { name, value } = event.target;

    setDraftFilters((currentFilters) => ({
      ...currentFilters,
      [name]: value,
    }));
  };

  const handleSearch = async (event) => {
    event.preventDefault();

    const nextFilters = {
      keyword: draftFilters.keyword.trim(),
      hospital: draftFilters.hospital,
      status: draftFilters.status,
    };

    setAppliedFilters(nextFilters);
    await loadBookings(1, nextFilters);
  };

  const handleReset = async () => {
    setDraftFilters(EMPTY_FILTERS);
    setAppliedFilters(EMPTY_FILTERS);

    await loadBookings(1, EMPTY_FILTERS);
  };

  const handlePreviousPage = async () => {
    if (pageNum <= 1 || loading) {
      return;
    }

    await loadBookings(pageNum - 1, appliedFilters);
  };

  const handleNextPage = async () => {
    if (pageNum >= totalPages || loading) {
      return;
    }

    await loadBookings(pageNum + 1, appliedFilters);
  };

  const getStatusLabel = (status) => {
    return STATUS_MAP[status] ?? "Unknown";
  };

  const getStatusClassName = (status) => {
    return getStatusLabel(status).toLowerCase();
  };

  const formatBookingDate = (startTime) => {
    if (!startTime) {
      return "—";
    }

    const date = new Date(startTime);

    if (Number.isNaN(date.getTime())) {
      return "—";
    }

    return date.toLocaleDateString("en-AU", {
      day: "2-digit",
      month: "long",
      year: "numeric",
    });
  };

  return (
    <main className="all-bookings-main">
      <div className="all-bookings-container">
        <header className="all-bookings-header">
          <h1 className="all-bookings-title">All Bookings</h1>

          <p className="all-bookings-record-count">
            {totalRecords} {totalRecords === 1 ? "record" : "records"}
          </p>
        </header>

        <form
          className="all-bookings-filters"
          onSubmit={handleSearch}
        >
          <Filter
            className="all-bookings-filter-icon"
            size={17}
            strokeWidth={1.8}
          />

          <input
            className="all-bookings-search"
            type="text"
            name="keyword"
            placeholder="Student, zID or supervisor"
            value={draftFilters.keyword}
            onChange={handleFilterChange}
          />

          <select
            className="all-bookings-select all-bookings-hospital-select"
            name="hospital"
            value={draftFilters.hospital}
            onChange={handleFilterChange}
          >
            <option value="">All Hospitals</option>

            {hospitalSelectList.map((hospital) => (
              <option key={hospital.id} value={hospital.name}>
                {hospital.name}
              </option>
            ))}
          </select>

          <select
            className="all-bookings-select"
            name="status"
            value={draftFilters.status}
            onChange={handleFilterChange}
          >
            <option value="">All Statuses</option>
            <option value="0">Pending</option>
            <option value="1">Confirmed</option>
            <option value="2">Completed</option>
            <option value="3">Declined</option>
            <option value="4">Canceled</option>
          </select>

          <button
            className="all-bookings-search-button"
            type="submit"
            disabled={loading}
          >
            <Search size={16} />
            Search
          </button>

          <button
            className="all-bookings-reset-button"
            type="button"
            disabled={loading}
            onClick={handleReset}
          >
            <RotateCcw size={16} />
            Reset
          </button>
        </form>

        {errorMessage && (
          <p className="all-bookings-error">
            {errorMessage}
          </p>
        )}

        <section className="all-bookings-table-card">
          <div className="all-bookings-table-wrapper">
            <table className="all-bookings-table">
              <thead>
              <tr>
                <th>Student</th>
                <th>Supervisor</th>
                <th>Hospital</th>
                <th>Department</th>
                <th>Date</th>
                <th>WBA Type</th>
                <th>Status</th>
              </tr>
              </thead>

              <tbody>
              {bookings.length > 0 ? (
                  bookings.map((booking) => {
                    const statusLabel = getStatusLabel(
                        booking.status
                    );

                    return (
                        <tr key={booking.bookingId}>
                          <td>
                            <div className="all-bookings-student">
                          <span className="all-bookings-student-name">
                            {booking.studentName || "—"}
                          </span>

                              <span className="all-bookings-student-id">
                            {booking.studentZid || "—"}
                          </span>
                            </div>
                          </td>

                          <td>
                            {booking.supervisorName || "—"}
                          </td>

                          <td>
                          <span className="all-bookings-hospital">
                            {booking.hospital || "—"}
                          </span>
                          </td>

                          <td>
                            {booking.department || "—"}
                          </td>

                          <td>
                          <span className="all-bookings-date">
                            {formatBookingDate(
                                booking.startTime
                            )}
                          </span>
                          </td>

                          <td>
                          <span className="all-bookings-wba-badge">
                            {booking.wbaType || "—"}
                          </span>
                          </td>

                          <td>
                        <span
                            className={`all-bookings-status-badge ${getStatusClassName(
                                booking.status
                            )}`}
                        >
                          {statusLabel}
                        </span>
                          </td>
                        </tr>
                    );
                  })
              ) : (
                  <tr>
                    <td
                        className="all-bookings-empty"
                        colSpan="7"
                    >
                      No bookings found.
                    </td>
                  </tr>
              )}
              </tbody>
            </table>
          </div>
        </section>

        <div className="all-bookings-pagination">
          <button
            type="button"
            disabled={pageNum <= 1 || loading}
            onClick={handlePreviousPage}
          >
            Previous
          </button>

          <span>
            Page {pageNum} of {totalPages}
          </span>

          <button
            type="button"
            disabled={pageNum >= totalPages || loading}
            onClick={handleNextPage}
          >
            Next
          </button>
        </div>
      </div>
    </main>
  );
}

export default AllBookingsAdmin;
