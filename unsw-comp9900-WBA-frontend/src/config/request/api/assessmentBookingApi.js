import request from "@/config/request/axios.js";

export const getAvailableAssessmentSlotList = (params) =>
  request.get(
    "/student/assessmentBooking/getAvailableAssessmentSlotList",
    {
      params,
    }
  );

export const bookings = (bookingInfo) =>
  request.post(
    "/student/assessmentBooking/bookings",
    bookingInfo
  );

export const getRegistrarAllBookings = (params) =>
  request.get(
    "/registrar/assessmentBooking/getAllBookings",
    {
      params,
    }
  );

export const getMyBookings = (pageNum, pageQuantity) =>
  request.get(
    "/student/assessmentBooking/bookings/my",
    {
      params: {
        pageNum,
        pageQuantity,
      },
    }
  );

export const cancelBooking = (bookingId) =>
  request.post(
    "/student/assessmentBooking/cancelBooking",
    null,
    {
      params: {
        id: bookingId,
      },
    }
  );

export const getAssessmentHistory = (
  pageNum,
  pageQuantity
) =>
  request.get(
    "/student/assessmentBooking/bookings/history",
    {
      params: {
        pageNum,
        pageQuantity,
      },
    }
  );

export const exportAssessmentHistoryExcel = (
  startTime,
  endTime
) =>
  request.get(
    "/student/assessmentBooking/exportAssessmentHistoryExcel",
    {
      params: {
        startTime,
        endTime,
      },
      responseType: "blob",
    }
  );