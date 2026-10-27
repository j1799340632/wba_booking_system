import request from "@/config/request/axios.js";

export const getAssessmentSlotList =
  (pageNum, pageQuantity, supervisorId) =>
    request.get("/supervisor/assessmentSlot/getAssessmentSlotList",
      {
        params: {
          pageNum,
          pageQuantity,
          supervisorId,
        }
      }
    );

export const saveAssessmentSlot =
  (slotInfo) =>
    request.post("/supervisor/assessmentSlot/saveAssessmentSlot", slotInfo);

export const delAssessmentSlot =
  (bookingId) =>
    request.post("/supervisor/assessmentSlot/delAssessmentSlot", null, {params: {id: bookingId}});

export const toggleAssessmentSlotsEnableDisable =
  (ids, isEnable) =>
    request.post("/supervisor/assessmentSlot/toggleAssessmentSlotsEnableDisable", null, {params: {ids, isEnable}});

export const getApprovedSession =
    (pageNum, pageQuantity, signal) =>
        request.get("/supervisor/assessmentSlot/getApprovedSession", {params: {pageNum, pageQuantity}, signal});

export const getUpcomingSession =
    (pageNum, pageQuantity, signal) =>
        request.get("/supervisor/assessmentSlot/getUpcomingSession", {params: {pageNum, pageQuantity}, signal});

export const approveSlot =
    (id, bookingId, version) =>
        request.post("/supervisor/assessmentSlot/approveSlot", null, {params: {id, bookingId, version}});

export const completeSlot =
    (bookingInfo) =>
        request.post("/supervisor/assessmentSlot/markAssessmentCompleted", bookingInfo);

export const updateAssessmentResult = bookingInfo => request.post("/supervisor/assessmentSlot/updateAssessmentResult", bookingInfo);

export const declineSlot =
    (id, bookingId, isEnable, version) =>
        request.post("/supervisor/assessmentSlot/declineSlot", null, {params: {id, bookingId, isEnable, version}});

export const cancelSlot =
    (id, bookingId, isEnable, version) =>
        request.post("/supervisor/assessmentSlot/cancelSlot", null, {params: {id, bookingId, isEnable, version}});
