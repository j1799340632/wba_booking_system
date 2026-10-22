import request from "@/config/request/axios.js";

export const getGoogleCalendarStatus = () =>
  request.get("/general/calendar/google/status");

export const connectGoogleCalendar = (returnUrl) =>
  request.get("/general/calendar/google/connect", {
    params: {
      returnUrl,
    },
  });

export const addBookingToGoogleCalendar = (bookingId) =>
  request.post(
    `/general/calendar/google/booking/${bookingId}`
  );