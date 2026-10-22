import request from "@/config/request/axios.js";

export const login =
  (email, password) =>
    request.post("/general/user/login", {email, password});

export const logout = () => request.post("/general/user/logout");

export const uploadFile =
    (formData) =>
        request.post("/general/file/upload", formData, {headers: {'Content-Type': 'multipart/form-data'}});

export const updateProfile = (userInfo) => request.post("/general/user/updateProfile", userInfo);

export const resetOwnPassword = (userInfo) => request.post("/general/user/resetOwnPassword", userInfo);

export const updateAvailableStatus = (isAvailable) => request.post("/general/user/updateAvailableStatus", null, {params: {isAvailable}});

export const getProfile = id => request.get('/general/user/getProfile', {params: {id}});
