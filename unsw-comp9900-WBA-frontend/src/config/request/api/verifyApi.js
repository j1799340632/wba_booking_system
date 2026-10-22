import request from "@/config/request/axios.js";

export const getEmailVerificationCode = () => request.get("/general/verify/getEmailVerificationCode");
