import request from "@/config/request/axios.js";

export const getRoleList = () => request.get('/superAdmin/userRole/getRoleList');

export const getEnabledRoleList = () => request.get('/superAdmin/userRole/getEnabledRoleList');

export const addRole = data => request.post('/superAdmin/userRole/addRole', data);

export const editRole = data => request.post('/superAdmin/userRole/editRole', data);

export const delRole = id => request.post('/superAdmin/userRole/delRole', null, {params: {id}});

export const changeRoleStatus = (id, targetStatus) => request.post('/superAdmin/userRole/changeRoleStatus', null, {params: {id, targetStatus}});
