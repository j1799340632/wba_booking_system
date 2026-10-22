import request from "@/config/request/axios.js";

export const getUserSettings = () => request.get("/general/userSettings/getUserSettings");

export const saveUserSettings = settings => request.post("/general/userSettings/saveUserSettings", settings);
