import request from "@/config/request/axios.js";

export const searchBookingAssistant = (message, history, signal, rescheduleBookingId) =>
  request.post("/student/bookingAssistant/search", { message, history, rescheduleBookingId }, { signal, timeout: 35000 });

export const prepareAssistantBooking = (selection) =>
  request.post("/student/bookingAssistant/prepare", selection);

export const confirmAssistantBooking = (draftId) =>
  request.post("/student/bookingAssistant/confirm", { draftId, confirmed: true });

export const prepareBookingManagement = (selection) => request.post("/student/bookingAssistant/management/prepare", selection);
export const confirmBookingManagement = (draftId) => request.post("/student/bookingAssistant/management/confirm", { draftId, confirmed: true });
export const findBookingReplacements = (bookingId, conditions) => request.post("/student/bookingAssistant/management/replacements", { bookingId, conditions });
