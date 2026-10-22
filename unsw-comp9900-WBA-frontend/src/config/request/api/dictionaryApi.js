import request from "@/config/request/axios.js";

export const getHospitalList =
    query =>
        request.get("/general/dictionaryManage/getHospitalList", {params: query});

export const getEnabledHospitals =
    () =>
        request.get("/general/dictionaryManage/getEnabledHospitals");

export const getDepartmentList =
    query =>
        request.get("/general/dictionaryManage/getDepartmentList", {params: query});

export const getEnabledDepartments =
    () =>
        request.get("/general/dictionaryManage/getEnabledDepartments");

export const getWbaTypeList =
    query =>
        request.get("/general/dictionaryManage/getWbaTypeList", {params: query});

export const getEnabledWbaTypes =
    () =>
        request.get("/general/dictionaryManage/getEnabledWbaTypes");

export const saveHospital =
    hospital =>
        request.post("/general/dictionaryManage/saveHospital", hospital);

export const saveDepartment =
    department =>
        request.post("/general/dictionaryManage/saveDepartment", department);

export const saveWbaTypes =
    wbaType =>
        request.post("/general/dictionaryManage/saveWbaType", wbaType);

export const changeHospitalStatus = (id, targetStatus) => request.post("/general/dictionaryManage/changeHospitalStatus", null, {params: {id, targetStatus}});
export const changeDepartmentStatus = (id, targetStatus) => request.post("/general/dictionaryManage/changeDepartmentStatus", null, {params: {id, targetStatus}});
export const changeWbaTypeStatus = (id, targetStatus) => request.post("/general/dictionaryManage/changeWbaTypeStatus", null, {params: {id, targetStatus}});

export const delHospital = id => request.post("/general/dictionaryManage/delHospital", null, {params: {id}});
export const delDepartment = id => request.post("/general/dictionaryManage/delDepartment", null, {params: {id}});
export const delWbaType = id => request.post("/general/dictionaryManage/delWbaType", null, {params: {id}});

export const downloadDicImportTemplate = () =>
    request.get(
        "/general/dictionaryManage/downloadDicImportTemplate",
        {
            responseType: "blob",
        }
    );

export const importBatchHospitals = formData => request.post("/general/dictionaryManage/importBatchHospitals", formData, {headers: {'Content-Type': 'multipart/form-data'}});
export const importBatchDepartments = formData => request.post("/general/dictionaryManage/importBatchDepartments", formData, {headers: {'Content-Type': 'multipart/form-data'}});
export const importBatchWbaTypes = formData => request.post("/general/dictionaryManage/importBatchWbaTypes", formData, {headers: {'Content-Type': 'multipart/form-data'}});
