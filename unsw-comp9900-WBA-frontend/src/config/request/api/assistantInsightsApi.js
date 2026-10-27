import request from "@/config/request/axios.js";
export const getAssistantInsights = (params, signal) => request.get("/registrar/assistantInsights", { params, signal, timeout: 30000 });
