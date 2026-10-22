import request from "@/config/request/axios.js";

export const getRegistrarDashboard = (params = {}) =>
    request.get("/registrar/dashboard/getDashboard", { params });

export const getUserList = (params = {}) =>
    request.get("/registrar/userManage/getUserList", { params });

export const updateOtherProfile = (userInfo) => request.post("/registrar/userManage/updateOtherProfile", userInfo);

export const resetPassword = (userInfo) => request.post("/registrar/userManage/resetPassword", userInfo);

export const addUser = (user) => request.post("/registrar/userManage/addUser", user);

export const importBatchUser =
    (formData) =>
        request.post("/registrar/userManage/importBatchUser", formData, {headers: {'Content-Type': 'multipart/form-data'}});

export const downloadUserImportTemplate = () =>
    request.get(
        "/registrar/userManage/downloadUserImportTemplate",
        {
            responseType: "blob",
        }
    );

export const changeUserStatus = (userId, targetStatus) => request.post("/registrar/userManage/changeUserStatus", null, {params: {userId, targetStatus}});

export const delUser = targetUserId => request.post('/registrar/userManage/delUser', null, {params: {targetUserId}});
