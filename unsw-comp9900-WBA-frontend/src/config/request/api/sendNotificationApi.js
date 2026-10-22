import request from "@/config/request/axios.js";

export const applyDisconnect = userId => request.get(`/general/sse/disconnect/${userId}`);

export const getTicket = () => request.get("/general/sse/getTicket");

export const sendNotification =
    data =>
        request.post("/general/sse/sendNotification", data);
