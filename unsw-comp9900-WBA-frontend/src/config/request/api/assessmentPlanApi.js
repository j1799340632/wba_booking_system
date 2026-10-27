import request from "@/config/request/axios.js";

export const getAssessmentPlan = (signal, summary = false) => request.get("/student/assessmentPlan", { signal, params: { summary }, timeout: 30000 });
export const saveAssessmentPlan = plan => request.post("/student/assessmentPlan", plan);
